package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * 实体的「强制移除」工具（转化系统专用）。
 *
 * <p>按原版语义走 {@link Entity#discard()}；
 * 只有当 {@code discard()} 之后实体仍未被标记移除时，才做兜底：
 * <ol>
 *   <li>强制 {@code setRemoved(RemovalReason.DISCARDED)}；</li>
 *   <li>手动广播 {@link ClientboundRemoveEntitiesPacket}，客户端立刻消失；</li>
 * </ol>
 * 打上移除标记后，实体管理器会在下一 tick 把它从所在的 EntitySection 中清掉，
 * 从而避免转化后残留「僵尸实体」占位。
 */
public final class EntityRemoval {

    private EntityRemoval() {
    }

    /** 移除实体：优先 discard()，失败则强制标记移除并通知客户端。 */
    public static void removeUnsafe(Entity entity) {
        if (entity == null || entity.isRemoved()) return;

        try {
            entity.discard();
        } catch (Throwable t) {
            SporeSRP.LOGGER.warn("discard() 抛出异常，转入兜底移除: {}", t.toString());
        }

        if (entity.isRemoved()) return;

        // ---- 兜底：强制标记移除 ----
        try {
            entity.setRemoved(Entity.RemovalReason.DISCARDED);
        } catch (Throwable t) {
            SporeSRP.LOGGER.warn("setRemoved(DISCARDED) 失败，实体可能残留在 EntitySection: {}", t.toString());
        }

        if (entity.level() instanceof ServerLevel level) {
            sendRemovalPacket(level, entity);
        }
        SporeSRP.LOGGER.debug("discard() 未生效，已强制移除实体 {}", entity);
    }

    /** 只发给同维度的玩家，避免跨维度无谓发包。 */
    private static void sendRemovalPacket(ServerLevel level, Entity entity) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(entity.getId());
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }
}