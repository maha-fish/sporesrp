package com.maha_fish.sporesrp.battle;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.block.ModBlocks;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

    public static void generateArena(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
                if (pos.equals(corePos)) {
                    continue;
                }
                BlockState state = shouldUseObsidian(dx, dz) ?
                        Blocks.OBSIDIAN.defaultBlockState() :
                        Blocks.COBBLESTONE.defaultBlockState();
                level.setBlock(pos, state, 3);
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
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

    public static void repairArena(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
                if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
                    continue;
                }
                if (pos.equals(corePos)) {
                    continue;
                }
                if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                    BlockState state = shouldUseObsidian(dx, dz) ?
                            Blocks.OBSIDIAN.defaultBlockState() :
                            Blocks.COBBLESTONE.defaultBlockState();
                    level.setBlock(pos, state, 3);
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
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

    public static boolean checkCoreAndBeaconExist(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        if (!level.getBlockState(beaconPos).is(Blocks.BEACON)) {
            return false;
        }
        if (!level.getBlockState(corePos).is(ModBlocks.WORLD_PURIFICATION_CORE.get())) {
            return false;
        }
        return true;
    }

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

    public static void purifyWorld(ServerLevel level) {
        var border = level.getWorldBorder();
        int minX = (int) border.getMinX();
        int maxX = (int) border.getMaxX();
        int minZ = (int) border.getMinZ();
        int maxZ = (int) border.getMaxZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        // ★ 修改点：使用 NeoForge 方式获取命令源
        String cmd = String.format("fillbiome %d %d %d %d %d %d minecraft:plains",
                minX, minY, minZ, maxX, maxY, maxZ);
        var source = level.getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, cmd);

        SporeSRP.LOGGER.info("World purified: infected biomes removed");
    }

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
        level.playSound(null, corePos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.MASTER, 2.0f, 0.5f);
    }

    private static int getSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY() + 1;
    }
}