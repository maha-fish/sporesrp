package com.maha_fish.sporesrp.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.Random;

/**
 * 实体生成位置检测工具类
 * 用于查找安全的生成位置，避免生物卡在墙里
 */
public class SpawnPositionHelper {
    private static final Random RANDOM = new Random();
    private static final int MAX_SPAWN_ATTEMPTS = 10;
    private static final double SEARCH_RADIUS = 3.0;

    /**
     * 在指定中心点周围查找一个安全的生成位置
     *
     * @param world   世界
     * @param center  中心实体
     * @param minDist 最小生成距离
     * @param maxDist 最大生成距离
     * @return 安全的生成位置，如果找不到则返回 null
     */
    public static BlockPos findSpawnPos(ServerLevel world, LivingEntity center, double minDist, double maxDist) {
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double distance = minDist + RANDOM.nextDouble() * (maxDist - minDist);
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            double y = center.getY();

            BlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));

            // 尝试当前位置
            if (canSpawnAt(world, pos)) {
                return pos;
            }

            // 如果脚部被阻挡但上方一格可生成，则使用上方一格
            BlockPos posAbove = pos.above();
            if (canSpawnAt(world, posAbove)) {
                return posAbove;
            }

            // 附近搜索
            BlockPos nearby = searchNearbyValidPos(world, pos);
            if (nearby != null) {
                return nearby;
            }
        }
        return null;
    }

    /**
     * 在指定位置附近搜索安全的生成位置
     *
     * @param world  世界
     * @param center 中心位置
     * @return 安全的生成位置，如果找不到则返回 null
     */
    private static BlockPos searchNearbyValidPos(ServerLevel world, BlockPos center) {
        int radius = (int) SEARCH_RADIUS;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    BlockPos checkPos = center.offset(dx, dy, dz);

                    if (canSpawnAt(world, checkPos)) {
                        return checkPos;
                    }

                    // 如果当前位置不行，尝试上方一格
                    BlockPos checkPosAbove = checkPos.above();
                    if (canSpawnAt(world, checkPosAbove)) {
                        return checkPosAbove;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 检查指定位置是否可生成实体
     *
     * @param world 世界
     * @param pos   要检查的位置
     * @return true 表示可以生成
     */
    public static boolean canSpawnAt(ServerLevel world, BlockPos pos) {
        // 脚部位置必须是空气或可替换方块
        if (!world.getBlockState(pos).isAir() && !world.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        // 头部位置（上方一格）必须是空气或可替换方块
        if (!world.getBlockState(pos.above()).isAir() && !world.getBlockState(pos.above()).canBeReplaced()) {
            return false;
        }
        // 下方方块必须是固体（可站立）
        if (!world.getBlockState(pos.below()).isSolid()) {
            return false;
        }
        return true;
    }

    /**
     * 获取最大尝试次数（用于外部配置或调试）
     */
    public static int getMaxSpawnAttempts() {
        return MAX_SPAWN_ATTEMPTS;
    }

    /**
     * 获取搜索半径（用于外部配置或调试）
     */
    public static double getSearchRadius() {
        return SEARCH_RADIUS;
    }
}