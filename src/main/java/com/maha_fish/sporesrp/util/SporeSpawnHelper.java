package com.maha_fish.sporesrp.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

/**
 * 生成 Spore 实体的统一入口。
 *
 * <p><b>为什么不能直接 {@code addFreshEntity}：</b>
 * Spore 的很多实体把随机内容放在 {@code finalizeSpawn} 里决定，而
 * {@code defineSynchedData} 只给了一个默认值。以 {@code spore:verwa} 为例：
 * <pre>
 *   defineSynchedData:  STORED_MOB = "spore:knight"        // 默认值
 *   finalizeSpawn:      STORED_MOB = verwa_summons 随机一项  // 真正决定内容物
 * </pre>
 * 直接 {@code addFreshEntity} 不会调用 {@code finalizeSpawn}，于是 Verwa 里
 * **永远是 knight**。{@code /summon} 命令没问题，是因为它底层走
 * {@code EntityType#spawn}，那条路径会调 {@code finalizeSpawn}。
 *
 * <p>这里用同一套语义、但不经过命令解析：create → 摆位置 → finalizeSpawn → 入场。
 */
public final class SporeSpawnHelper {

    private SporeSpawnHelper() {
    }

    /**
     * 生成一只「已 finalize」的实体。
     *
     * @return 生成的实体；类型为空或创建失败返回 {@code null}
     */
    public static LivingEntity spawnFinalized(ServerLevel level, EntityType<?> type, double x, double y, double z) {
        if (level == null || type == null) return null;

        Entity created = type.create(level);
        if (!(created instanceof LivingEntity living)) return null;

        living.moveTo(x, y, z, living.getYRot(), living.getXRot());

        // 关键一步：等价于 /summon 走的那条路径
        if (living instanceof Mob mob) {
            // 1.21 的签名是 4 参（1.20.1 还有第 5 个 CompoundTag）
            mob.finalizeSpawn(level,
                    level.getCurrentDifficultyAt(living.blockPosition()),
                    MobSpawnType.SPAWNER, null);
        }

        level.addFreshEntity(living);
        return living;
    }

    /** 以方块居中生成。 */
    public static LivingEntity spawnFinalized(ServerLevel level, EntityType<?> type, BlockPos pos) {
        if (pos == null) return null;
        return spawnFinalized(level, type, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
    /**
     * 只做 finalizeSpawn、不入场 —— 给需要在入场前再设置状态（标记、效果、目标）的调用方用。
     *
     * <p>必须在 {@code addFreshEntity} **之前**调用，否则 Spore 的基类初始化已经错过了。
     */
    public static void finalizeBeforeAdd(Mob mob) {
        if (mob == null) return;
        if (!(mob.level() instanceof ServerLevel level)) return;
        // 1.21 的签名是 4 参（1.20.1 还有第 5 个 CompoundTag）
        mob.finalizeSpawn(level,
                level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.SPAWNER, null);
    }
}
