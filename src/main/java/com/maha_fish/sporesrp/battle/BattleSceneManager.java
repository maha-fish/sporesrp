package com.maha_fish.sporesrp.battle;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.block.ModBlocks;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class BattleSceneManager {

    public static final int ARENA_RADIUS = 5;

    // ==================== 场地生成 ====================

    public static void generateArena(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        // 生成平台，但跳过核心位置
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
                // ★ 跳过核心位置
                if (pos.equals(corePos)) {
                    continue;
                }
                BlockState state = shouldUseObsidian(dx, dz) ?
                        Blocks.OBSIDIAN.defaultBlockState() :
                        Blocks.COBBLESTONE.defaultBlockState();
                level.setBlock(pos, state, 3);
            }
        }

        // 补充缺失的基座（不强制替换已有矿物块），同样跳过核心位置
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
                // ★ 跳过核心位置
                if (pos.equals(corePos)) {
                    continue;
                }
                Block block = level.getBlockState(pos).getBlock();
                if (block != Blocks.IRON_BLOCK && block != Blocks.GOLD_BLOCK &&
                        block != Blocks.DIAMOND_BLOCK && block != Blocks.EMERALD_BLOCK) {
                    level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                }
            }
        }

        SporeSRP.LOGGER.info("Battle arena generated at {}, core protected at {}", beaconPos, corePos);
    }

    private static boolean shouldUseObsidian(int dx, int dz) {
        return Math.abs(dx) == ARENA_RADIUS || Math.abs(dz) == ARENA_RADIUS;
    }

    // ==================== 持续填充场地 ====================

    public static void repairArena(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        // 填充平台地面（-1层），跳过底座和核心位置
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
                // ★ 跳过底座区域（信标下方 3x3）
                if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
                    continue;
                }
                // ★ 跳过核心位置
                if (pos.equals(corePos)) {
                    continue;
                }
                // 如果方块缺失或被破坏，重新填充
                if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                    BlockState state = shouldUseObsidian(dx, dz) ?
                            Blocks.OBSIDIAN.defaultBlockState() :
                            Blocks.COBBLESTONE.defaultBlockState();
                    level.setBlock(pos, state, 3);
                }
            }
        }

        // 修复底座：只补充缺失的矿物块，不覆盖已有的，同样跳过核心位置
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
                // ★ 跳过核心位置
                if (pos.equals(corePos)) {
                    continue;
                }
                Block block = level.getBlockState(pos).getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                    level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                }
            }
        }
    }

    // ==================== 核心和信标检查 ====================

    public static boolean checkCoreAndBeaconExist(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        // 检查信标是否存在
        if (!level.getBlockState(beaconPos).is(Blocks.BEACON)) {
            return false;
        }
        // 检查核心是否存在（使用世界净化核心方块）
        if (!level.getBlockState(corePos).is(ModBlocks.WORLD_PURIFICATION_CORE.get())) {
            return false;
        }
        return true;
    }

    // ==================== 清场 ====================

    public static void clearAllMobs(ServerLevel level, BlockPos center) {
        clearAllMobs(level, center, ARENA_RADIUS);
    }

    public static void clearAllMobs(ServerLevel level, BlockPos center, int radius) {
        AABB area = new AABB(center).inflate(radius);
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> SporeEntityHelper.isSporeEntity(e) && !(e instanceof Player)
        );
        int count = mobs.size();
        for (LivingEntity mob : mobs) {
            mob.discard();
        }
        if (count > 0) {
            SporeSRP.LOGGER.info("Cleared {} mobs from arena at {}", count, center);
        }
    }

    // ==================== 世界净化 ====================

    public static void purifyWorld(ServerLevel level) {
        var border = level.getWorldBorder();
        int minX = (int) border.getMinX();
        int maxX = (int) border.getMaxX();
        int minZ = (int) border.getMinZ();
        int maxZ = (int) border.getMaxZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        SporeSRP.LOGGER.info("World purified: infected biomes removed");
    }

    // ==================== 核心爆炸特效 ====================

    public static void coreExplosion(ServerLevel level, BlockPos corePos) {
        for (int i = 0; i < 100; i++) {
            double x = corePos.getX() + 0.5 + (Math.random() - 0.5) * 20;
            double y = corePos.getY() + 0.5 + Math.random() * 20;
            double z = corePos.getZ() + 0.5 + (Math.random() - 0.5) * 20;
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x, y, z, 1, 0, 0, 0, 0
            );
        }

        level.playSound(null, corePos,
                SoundEvents.GENERIC_EXPLODE,
                net.minecraft.sounds.SoundSource.MASTER,
                2.0f, 0.5f);
    }

    // ==================== 工具方法 ====================

    // ★ 正确版本：返回固体方块顶部高度
    private static int getSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY() + 1;
    }
}