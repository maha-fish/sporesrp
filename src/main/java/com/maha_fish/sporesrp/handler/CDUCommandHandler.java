package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理 CDU 方块的净化任务，支持多维度
 */
public class CDUCommandHandler {

    private static final int DURATION_TICKS = 60 * 20; // 60 seconds
    private static final int CLEAR_INTERVAL = 5 * 20;  // 5 seconds

    // 维度 -> (方块位置 -> 净化数据)
    private static final Map<ResourceKey<Level>, Map<BlockPos, PurificationData>> ACTIVE_CDUS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> COMMAND_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final int MAX_COMMANDS_PER_TICK = 20;

    private static class PurificationData {
        int totalRemaining;
        int cooldown;

        PurificationData() {
            this.totalRemaining = DURATION_TICKS;
            this.cooldown = 0;
        }

        void tick() {
            if (totalRemaining > 0) {
                totalRemaining--;
                if (cooldown > 0) {
                    cooldown--;
                }
            }
        }

        boolean shouldClear() {
            return totalRemaining > 0 && cooldown == 0;
        }

        void resetCooldown() {
            cooldown = CLEAR_INTERVAL;
        }

        boolean isExpired() {
            return totalRemaining <= 0;
        }
    }

    private static Map<BlockPos, PurificationData> getDimensionMap(ResourceKey<Level> dimension) {
        return ACTIVE_CDUS.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
    }

    // ========== 提交清除命令 ==========
    private static void submitClearCommand(ServerLevel level, BlockPos pos) {
        int radius = Config.COMMON.cduPurifyRadius.get();
        int halfHeight = Config.COMMON.cduPurifyHalfHeight.get();
        int minX = pos.getX() - radius;
        int maxX = pos.getX() + radius;
        int minZ = pos.getZ() - radius;
        int maxZ = pos.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), pos.getY() - halfHeight);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + halfHeight);

        int step = 16;
        for (int x = minX; x <= maxX; x += step) {
            int x1 = x, x2 = Math.min(x + step - 1, maxX);
            for (int z = minZ; z <= maxZ; z += step) {
                int z1 = z, z2 = Math.min(z + step - 1, maxZ);
                for (int y = minY; y <= maxY; y += step) {
                    int y1 = y, y2 = Math.min(y + step - 1, maxY);
                    COMMAND_QUEUE.add(() -> {
                        String cmd = String.format("fillbiome %d %d %d %d %d %d minecraft:plains", x1, y1, z1, x2, y2, z2);
                        var source = level.getServer().createCommandSourceStack()
                                .withPermission(4)
                                .withSuppressedOutput();
                        level.getServer().getCommands().performPrefixedCommand(source, cmd);
                    });
                }
            }
        }
    }

    // ========== 对外 API ==========
    public static void startPurification(ServerLevel level, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Map<BlockPos, PurificationData> map = getDimensionMap(dim);
        PurificationData data = map.get(pos);
        if (data == null) {
            data = new PurificationData();
            map.put(pos.immutable(), data);
        } else {
            data.totalRemaining = DURATION_TICKS;
            data.cooldown = 0;
        }
        SporeSRP.LOGGER.debug("CDU purification started/reset at {} in {}", pos, dim);
    }

    public static void stopPurification(ServerLevel level, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Map<BlockPos, PurificationData> map = ACTIVE_CDUS.get(dim);
        if (map != null) {
            map.remove(pos.immutable());
            if (map.isEmpty()) {
                ACTIVE_CDUS.remove(dim);
            }
        }
        SporeSRP.LOGGER.debug("CDU purification stopped at {} in {}", pos, dim);
    }

    // ========== ServerTick 处理 ==========
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
    if (!Config.COMMON_SPEC.isLoaded()) return;// 处理命令队列
        int executed = 0;
        while (!COMMAND_QUEUE.isEmpty() && executed < MAX_COMMANDS_PER_TICK) {
            try {
                COMMAND_QUEUE.poll().run();
                executed++;
            } catch (Exception e) {
                SporeSRP.LOGGER.warn("Failed to execute CDU fillbiome command", e);
            }
        }

        // 遍历所有维度
        Iterator<Map.Entry<ResourceKey<Level>, Map<BlockPos, PurificationData>>> dimIt = ACTIVE_CDUS.entrySet().iterator();
        while (dimIt.hasNext()) {
            Map.Entry<ResourceKey<Level>, Map<BlockPos, PurificationData>> dimEntry = dimIt.next();
            ResourceKey<Level> dimKey = dimEntry.getKey();
            Map<BlockPos, PurificationData> map = dimEntry.getValue();

            // 尝试获取该维度的 ServerLevel
            ServerLevel level = event.getServer().getLevel(dimKey);
            if (level == null) {
                // 维度未加载，跳过此维度（数据保留，等加载后再处理）
                continue;
            }

            Iterator<Map.Entry<BlockPos, PurificationData>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<BlockPos, PurificationData> entry = it.next();
                BlockPos pos = entry.getKey();
                PurificationData data = entry.getValue();

                data.tick();

                if (data.isExpired()) {
                    it.remove();
                    SporeSRP.LOGGER.debug("CDU purification expired at {} in {}", pos, dimKey);
                    continue;
                }

                if (data.shouldClear()) {
                    // 执行清除
                    submitClearCommand(level, pos);
                    data.resetCooldown();
                    SporeSRP.LOGGER.debug("CDU clear executed at {} in {}", pos, dimKey);
                }
            }

            // 如果该维度 map 为空，移除维度条目
            if (map.isEmpty()) {
                dimIt.remove();
            }
        }
    }
}