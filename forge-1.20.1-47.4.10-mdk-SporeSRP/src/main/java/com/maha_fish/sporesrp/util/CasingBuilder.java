package com.maha_fish.sporesrp.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CasingBuilder {

    private static List<BlockState> cachedBlockPool = null;

    /** 壳层偏移预计算缓存：key = "radius:thickness" */
    private static final Map<String, List<BlockPos>> SHELL_OFFSET_CACHE = new ConcurrentHashMap<>();

    /** 任务：按维度 + 中心点分组 */
    private static final Map<ResourceKey<Level>, Map<BlockPos, CasingTask>> TASKS = new ConcurrentHashMap<>();

    /** 每 tick 总预算（遍历坐标数） */
    public static final int DEFAULT_BUDGET_PER_TICK = 4096;

    /** 无新请求超时时间，超时后自动清理（30 秒） */
    private static final long TASK_TIMEOUT_TICKS = 30 * 20;

    private static class CasingTask {
        final BlockPos center;
        final List<int[]> shellConfigs = new ArrayList<>();  // 每个元素 {radius, thickness}
        int configIndex = 0;
        int offsetIndex = 0;
        long lastRequestTick;

        CasingTask(BlockPos center, long currentTick) {
            this.center = center;
            this.lastRequestTick = currentTick;
        }

        synchronized boolean addConfig(int radius, int thickness) {
            for (int[] cfg : shellConfigs) {
                if (cfg[0] == radius && cfg[1] == thickness) return false;
            }
            shellConfigs.add(new int[]{radius, thickness});
            return true;
        }

        synchronized boolean isDone() {
            return configIndex >= shellConfigs.size();
        }
    }

    // ==================== 方块池 ====================
    private static List<BlockState> getDefaultBlockPool() {
        if (cachedBlockPool != null) return cachedBlockPool;
        List<BlockState> pool = new ArrayList<>();
        String[] blockIds = {
                "spore:biomass_block",
                "spore:rooted_biomass",
                "spore:calcified_biomass_block",
                "spore:sicken_biomass_block",
                "spore:gastric_biomass_block"
        };
        for (String id : blockIds) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
            if (block != null && block != Blocks.AIR) {
                for (int i = 0; i < 3; i++) pool.add(block.defaultBlockState());
            } else {
                for (int i = 0; i < 3; i++) pool.add(Blocks.STONE.defaultBlockState());
            }
        }
        if (pool.isEmpty()) {
            for (int i = 0; i < 15; i++) pool.add(Blocks.STONE.defaultBlockState());
        }
        cachedBlockPool = pool;
        return pool;
    }

    // ==================== 壳层偏移预计算 ====================
    private static List<BlockPos> getShellOffsets(int radius, int thickness) {
        String key = radius + ":" + thickness;
        return SHELL_OFFSET_CACHE.computeIfAbsent(key, k -> {
            List<BlockPos> list = new ArrayList<>();
            double inner = radius - thickness / 2d;
            double outer = radius + thickness / 2d;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        double distance = Mth.sqrt(dx * dx + dy * dy + dz * dz);
                        if (distance > inner && distance < outer) {
                            list.add(new BlockPos(dx, dy, dz));
                        }
                    }
                }
            }
            return Collections.unmodifiableList(list);
        });
    }

    // ==================== 对外 API（签名不变） ====================
    /**
     * 保持原签名。内部不再同步放置，而是按 (dimension, center) 分组入队。
     * 同一 center 相同 radius/thickness 自动去重。
     */
    public static void buildCasing(Level level, BlockPos center, int radius, int thickness) {
        if (level.isClientSide) return;
        ResourceKey<Level> dim = level.dimension();
        long now = level.getGameTime();

        Map<BlockPos, CasingTask> map = TASKS.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        BlockPos key = center.immutable();
        CasingTask task = map.computeIfAbsent(key, k -> new CasingTask(key, now));

        synchronized (task) {
            task.lastRequestTick = now;
            task.addConfig(radius, thickness);
        }
    }

    /** 每 tick 调用，分帧处理方块放置 */
    public static void tick(ServerLevel level, int budget) {
        ResourceKey<Level> dim = level.dimension();
        Map<BlockPos, CasingTask> map = TASKS.get(dim);
        if (map == null || map.isEmpty()) return;

        List<BlockState> blockPool = getDefaultBlockPool();
        RandomSource random = RandomSource.create();
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, CasingTask>> it = map.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<BlockPos, CasingTask> entry = it.next();
            CasingTask task = entry.getValue();

            // 无新请求超时 → 清理（含未完成任务，实体可能已消失）
            if (now - task.lastRequestTick > TASK_TIMEOUT_TICKS) {
                it.remove();
                continue;
            }
            if (task.isDone()) continue;

            int processed = 0;
            while (processed < budget && !task.isDone()) {
                int[] cfg;
                synchronized (task) {
                    if (task.configIndex >= task.shellConfigs.size()) break;
                    cfg = task.shellConfigs.get(task.configIndex);
                }
                List<BlockPos> offsets = getShellOffsets(cfg[0], cfg[1]);

                while (task.offsetIndex < offsets.size() && processed < budget) {
                    BlockPos target = task.center.offset(offsets.get(task.offsetIndex++));
                    BlockState current = level.getBlockState(target);
                    if (random.nextDouble() < 0.1
                            && !current.isSolidRender(level, target)
                            && !current.is(Blocks.BARRIER)) {
                        BlockState selected = blockPool.get(random.nextInt(blockPool.size()));
                        level.setBlock(target, selected, 3);
                    }
                    processed++;
                }

                if (task.offsetIndex >= offsets.size()) {
                    synchronized (task) {
                        task.configIndex++;
                        task.offsetIndex = 0;
                    }
                }
            }
        }

        if (map.isEmpty()) TASKS.remove(dim);
    }

    /** 主动清理某个中心点的任务（实体死亡时调用） */
    public static void clearTasksAt(ResourceKey<Level> dim, BlockPos center) {
        Map<BlockPos, CasingTask> map = TASKS.get(dim);
        if (map == null) return;
        map.remove(center.immutable());
        if (map.isEmpty()) TASKS.remove(dim);
    }

    public static void clearCache() {
        cachedBlockPool = null;
        SHELL_OFFSET_CACHE.clear();
        TASKS.clear();
    }
}