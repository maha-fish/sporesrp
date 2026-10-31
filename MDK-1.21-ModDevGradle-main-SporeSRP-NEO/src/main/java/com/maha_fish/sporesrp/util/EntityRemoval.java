package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;

/**
 * 实体的「强制移除」工具。
 *
 * <p>转化系统需要在同一 tick 内让原生物消失并换成寄生体。走 {@code Entity#discard()} 的问题是：
 * <ul>
 *   <li>它会额外触发 {@code invalidateCaps} / {@code stopRiding} 等副作用，
 *       正在被转化中的实体身上可能抛出异常或引发重复处理；</li>
 *   <li>移除包要等实体管理器的下一轮处理才广播，客户端可能残留「幽灵实体」。</li>
 * </ul>
 *
 * <p>这里改为两步：
 * <ol>
 *   <li><b>unsafe 直写</b> {@code Entity#removalReason} —— 只把标记打上，
 *       不跑 {@code setRemoved} 的副作用；{@code isRemoved()} 随即返回 true；</li>
 *   <li><b>手动广播</b> {@link ClientboundRemoveEntitiesPacket} —— 客户端立刻删除。</li>
 * </ol>
 *
 * <p>NeoForge 1.21 生产环境使用官方映射名，因此直接反射即可。
 * 失败时自动退回 {@code discard()}。
 */
public final class EntityRemoval {

    private static Field removalReasonField;
    private static boolean reflectionUnavailable;

    private EntityRemoval() {
    }

    /** 移除实体：unsafe 写 removalReason + 发送移除包。 */
    public static void removeUnsafe(Entity entity) {
        if (entity == null || entity.isRemoved()) return;

        if (!(entity.level() instanceof ServerLevel level)) {
            entity.discard();
            return;
        }

        if (reflectionUnavailable || !writeRemovalReason(entity)) {
            entity.discard();
            return;
        }

        sendRemovalPacket(level, entity);
    }

    /** 直写 {@code removalReason} 字段。 */
    private static boolean writeRemovalReason(Entity entity) {
        try {
            if (removalReasonField == null) {
                removalReasonField = Entity.class.getDeclaredField("removalReason");
                removalReasonField.setAccessible(true);
            }
            removalReasonField.set(entity, Entity.RemovalReason.DISCARDED);
            return true;
        } catch (Throwable t) {
            reflectionUnavailable = true;
            SporeSRP.LOGGER.warn("unsafe removal 反射不可用，退回 discard(): {}", t.toString());
            return false;
        }
    }

    /** 只发给同维度的玩家，避免跨维度无谓发包。 */
    private static void sendRemovalPacket(ServerLevel level, Entity entity) {
        ClientboundRemoveEntitiesPacket packet =
                new ClientboundRemoveEntitiesPacket(entity.getId());
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }
}
