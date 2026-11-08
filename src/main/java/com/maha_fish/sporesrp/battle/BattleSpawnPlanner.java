package com.maha_fish.sporesrp.battle;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 刷怪位置规划：开战/换阶段时预计算「锚点」，之后每波从锚点取位。
 *
 * <p>解决原实现的两个问题：
 * <ul>
 *   <li>每只怪独立上下扫描，场地在海上/高空时**全部失败 → 整波不刷**；</li>
 *   <li>只校验「下方固体 + 空气」，会刷进 1 格缝隙导致**卡住/窒息**。</li>
 * </ul>
 *
 * <p>锚点可重复使用，因此永远不会因为「没有位置」而少刷。
 */
public final class BattleSpawnPlanner {

    /** 一场战斗的刷怪规划。 */
    public static final class Plan {
        public final List<BlockPos> groundAnchors = new ArrayList<>();
        public final List<BlockPos> airAnchors = new ArrayList<>();
        /** 为海上/高空临时搭建的平台：位置 -> 原始方块状态（用于战后恢复）。 */
        public final Map<BlockPos, BlockState> pads = new HashMap<>();
        public boolean platformMode;
    }

    private BattleSpawnPlanner() {
    }

    // ==================== 预计算 ====================

    public static Plan prepare(ServerLevel level, BlockPos center, BlockPos corePos) {
        Plan plan = new Plan();
        int count = Math.max(4, Config.COMMON.spawnAnchorCount.get());

        for (int i = 0; i < count; i++) {
            BlockPos anchor = findSurfaceAnchor(level, center, corePos, i, count);
            if (anchor != null) plan.groundAnchors.add(anchor);
        }

        double ratio = (double) plan.groundAnchors.size() / count;
        if (ratio < 0.5 && Config.COMMON.autoPlatformSpawns.get()) {
            // 海上 / 高空：搭建临时刷怪平台
            plan.platformMode = true;
            plan.groundAnchors.clear();
            plan.groundAnchors.addAll(buildPads(level, center, corePos, plan));
            SporeSRP.LOGGER.info("Battle spawn planner: platform mode, {} pads built", plan.pads.size());
        }

        // 空中锚点（飞行生物用）：没有地面锚点时以中心为基准
        for (int i = 0; i < count; i++) {
            BlockPos base = plan.groundAnchors.isEmpty()
                    ? center.offset(0, 8, 0)
                    : plan.groundAnchors.get(i % plan.groundAnchors.size());
            plan.airAnchors.add(base.above(8));
        }

        SporeSRP.LOGGER.info("Battle spawn planner: {} ground anchors, {} air anchors",
                plan.groundAnchors.size(), plan.airAnchors.size());
        return plan;
    }

    /** 战后恢复平台原始方块。 */
    public static void restorePads(ServerLevel level, Plan plan) {
        if (plan == null || plan.pads.isEmpty()) return;
        if (!Config.COMMON.restoreSpawnPads.get()) return;
        for (Map.Entry<BlockPos, BlockState> entry : plan.pads.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 3);
        }
        SporeSRP.LOGGER.info("Battle spawn planner: {} spawn pads restored", plan.pads.size());
        plan.pads.clear();
    }

    // ==================== 取位 ====================

    /** 取一个地面生成点（可重复使用，永不返回 null）。 */
    public static BlockPos ground(ServerLevel level, Plan plan, RandomSource rnd) {
        if (plan == null || plan.groundAnchors.isEmpty()) return null;
        int jitter = Math.max(0, Config.COMMON.spawnJitter.get());
        BlockPos anchor = plan.groundAnchors.get(rnd.nextInt(plan.groundAnchors.size()));
        for (int attempt = 0; attempt < Math.max(1, Config.COMMON.spawnAttemptsPerMob.get()); attempt++) {
            BlockPos candidate = anchor.offset(
                    rnd.nextInt(jitter * 2 + 1) - jitter, 0,
                    rnd.nextInt(jitter * 2 + 1) - jitter);
            if (isStandable(level, candidate)) return candidate;
        }
        // 锚点本身已校验过：直接用它，保证一定刷出
        return anchor;
    }

    /** 取一个空中生成点（飞行生物）。 */
    public static BlockPos air(ServerLevel level, Plan plan, RandomSource rnd) {
        if (plan == null || plan.airAnchors.isEmpty()) return null;
        int jitter = Math.max(0, Config.COMMON.spawnJitter.get());
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos anchor = plan.airAnchors.get(rnd.nextInt(plan.airAnchors.size()));
            BlockPos candidate = anchor.offset(
                    rnd.nextInt(jitter * 2 + 1) - jitter, 0,
                    rnd.nextInt(jitter * 2 + 1) - jitter);
            if (hasHeadroom(level, candidate, 2)) return candidate;
        }
        return plan.airAnchors.get(0);
    }

    // ==================== 内部 ====================

    private static BlockPos findSurfaceAnchor(ServerLevel level, BlockPos center, BlockPos corePos,
                                              int index, int count) {
        RandomSource rnd = level.random;
        int minRadius = Config.COMMON.spawnRingMinRadius.get();
        int maxRadius = Math.max(minRadius + 2, Config.COMMON.spawnRingMaxRadius.get());
        int maxDelta = Config.COMMON.spawnMaxSurfaceDelta.get();
        int minCore = Config.COMMON.spawnMinCoreDistance.get();
        double minCoreSq = (double) minCore * minCore;

        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = Math.PI * 2.0 * index / count + (rnd.nextDouble() - 0.5) * 0.35;
            // 前几轮用基础半径，后续轮次逐步外扩，保证核心方向的点也能补上
            int radius = minRadius + rnd.nextInt(maxRadius - minRadius + 1) + (attempt / 8) * 4;
            int x = center.getX() + (int) (Math.cos(angle) * radius);
            int z = center.getZ() + (int) (Math.sin(angle) * radius);

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            if (Math.abs(surface.getY() - center.getY()) > maxDelta) continue;
            if (corePos != null && surface.distSqr(corePos) < minCoreSq) continue;
            if (surface.distSqr(center) < (double) minRadius * minRadius) continue;
            if (!isStandable(level, surface)) continue;
            return surface;
        }
        return null;
    }

    private static List<BlockPos> buildPads(ServerLevel level, BlockPos center, BlockPos corePos, Plan plan) {
        List<BlockPos> result = new ArrayList<>();
        int count = Math.max(1, Config.COMMON.spawnPadCount.get());
        int radius = Config.COMMON.spawnPadRadius.get();
        int minCore = Config.COMMON.spawnMinCoreDistance.get();
        double minCoreSq = (double) minCore * minCore;
        RandomSource rnd = level.random;
        int seaLevel = level.getSeaLevel();
        int baseY = Math.max(center.getY(), seaLevel + 1);
        BlockState padState = padBlock();

        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            int r = radius + rnd.nextInt(5) - 2;
            int x = center.getX() + (int) (Math.cos(angle) * r);
            int z = center.getZ() + (int) (Math.sin(angle) * r);
            BlockPos stand = new BlockPos(x, baseY, z);
            if (corePos != null && stand.distSqr(corePos) < minCoreSq) continue;

            // 只在水/空气/岩浆/树叶上搭平台，不破坏已有地形
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos padPos = stand.offset(dx, -1, dz);
                    BlockState old = level.getBlockState(padPos);
                    if (old.isAir() || old.getBlock() == Blocks.WATER || old.getBlock() == Blocks.LAVA
                            || old.is(net.minecraft.tags.BlockTags.LEAVES)) {
                        plan.pads.putIfAbsent(padPos, old);
                        level.setBlock(padPos, padState, 3);
                    }
                }
            }
            result.add(stand);
        }
        return result;
    }

    private static BlockState padBlock() {
        var id = net.minecraft.resources.ResourceLocation.tryParse(Config.COMMON.spawnPadBlock.get());
        var block = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id);
        if (block == null || block == Blocks.AIR) return Blocks.OBSIDIAN.defaultBlockState();
        return block.defaultBlockState();
    }

    /** 站得住：脚下固体、脚部不是液体、需要净空、体型不碰撞。 */
    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 2) return false;
        BlockState below = level.getBlockState(pos.below());
        if (!below.isSolid()) return false;
        if (below.is(net.minecraft.tags.BlockTags.LEAVES)) return false;
        BlockState feet = level.getBlockState(pos);
        if (!feet.getFluidState().isEmpty()) return false;
        if (!feet.isAir()) return false;
        int air = Math.max(2, Config.COMMON.spawnRequireAirAbove.get());
        if (!hasHeadroom(level, pos, air)) return false;
        AABB box = new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 1.9, pos.getZ() + 0.8);
        return level.noCollision(box);
    }

    private static boolean hasHeadroom(ServerLevel level, BlockPos pos, int height) {
        for (int i = 0; i < height; i++) {
            if (!level.getBlockState(pos.above(i)).isAir()) return false;
        }
        return true;
    }
}