package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 实体的「强制移除」工具（转化系统专用）。
 *
 * <p>执行顺序：
 * <ol>
 *   <li>{@link Entity#discard()}（原版语义）；</li>
 *   <li>失败则 {@code setRemoved(RemovalReason.DISCARDED)}；</li>
 *   <li>仍失败则直接从 {@code ServerLevel} 内部的实体存储
 *       （{@code PersistentEntitySectionManager} → {@code EntityLookup}）按 UUID 摘除；</li>
 *   <li>广播 {@link ClientboundRemoveEntitiesPacket} 让客户端立即删除。</li>
 * </ol>
 *
 * <p>说明：{@code LevelEntityGetter} 是只读视图，无法删除；真正可按 UUID 增删的是其底层的
 * {@code EntityLookup}，因此这里操作的是后者。
 */
public final class EntityRemoval {

    private EntityRemoval() {
    }

    public static void removeUnsafe(Entity entity) {
        if (entity == null || entity.isRemoved()) return;

        try {
            entity.discard();
        } catch (Throwable t) {
            SporeSRP.LOGGER.warn("discard() 抛出异常，转入兜底移除: {}", t.toString());
        }
        if (entity.isRemoved()) return;

        try {
            entity.setRemoved(Entity.RemovalReason.DISCARDED);
        } catch (Throwable t) {
            SporeSRP.LOGGER.warn("setRemoved(DISCARDED) 失败: {}", t.toString());
        }

        if (entity.level() instanceof ServerLevel level) {
            removeFromEntityLookup(level, entity);
            sendRemovalPacket(level, entity);
        }
    }

    /** 从 ServerLevel 内部实体存储中按 UUID 摘除。 */
    private static void removeFromEntityLookup(ServerLevel level, Entity entity) {
        try {
            Object manager = fieldValue(level, "entityManager", "f_143244_");
            if (manager == null) return;
            Object lookup = fieldValue(manager, "entityLookup", "f_157494_");
            if (lookup == null) return;
            Method remove = methodByName(lookup.getClass(), "remove", "m_156822_", EntityAccess.class);
            if (remove == null) return;
            remove.setAccessible(true);
            remove.invoke(lookup, entity);
        } catch (Throwable t) {
            SporeSRP.LOGGER.warn("从 EntityLookup 摘除实体失败: {}", t.toString());
        }
    }

    private static Object fieldValue(Object holder, String mojmapName, String srgName) throws Exception {
        Field field = findField(holder.getClass(), mojmapName, srgName);
        if (field == null) return null;
        field.setAccessible(true);
        return field.get(holder);
    }

    private static Field findField(Class<?> type, String mojmapName, String srgName) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(mojmapName) || f.getName().equals(srgName)) return f;
            }
        }
        return null;
    }

    private static Method methodByName(Class<?> type, String mojmapName, String srgName, Class<?>... params) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(mojmapName) && !m.getName().equals(srgName)) continue;
                if (m.getParameterCount() == params.length) return m;
            }
        }
        return null;
    }

    private static void sendRemovalPacket(ServerLevel level, Entity entity) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(entity.getId());
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }
}