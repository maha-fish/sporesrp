package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.BiomeRegionConverter;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
    /** 本 tick 有群系改动的区块，攒起来统一下发。 */
    private static final java.util.List<LevelChunk> PENDING_CLEAR_CHUNKS = new java.util.ArrayList<>();

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

    // ========== 提交清除任务 ==========
    /** 目标群系 / 只清理的来源群系；均为注册表 holder，比较用引用相等。 */
    private static Holder<Biome> holder(ServerLevel level, ResourceLocation id) {
        return level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(ResourceKey.create(Registries.BIOME, id));
    }

    /**
     * 把 CDU 周围的感染群系改回平原。
     *
     * <p>不再逐方块 {@code fillbiome}，而是按区块直写 biome palette，
     * 且**只替换当前确实是感染群系的格子** —— 原生群系（沙漠、针叶林等）不会被一并抹成平原。
     */
    private static void submitClearCommand(ServerLevel level, BlockPos pos) {
        int radius = Config.COMMON.cduPurifyRadius.get();
        int halfHeight = Config.COMMON.cduPurifyHalfHeight.get();

        int minX = pos.getX() - radius;
        int maxX = pos.getX() + radius;
        int minZ = pos.getZ() - radius;
        int maxZ = pos.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), pos.getY() - halfHeight);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + halfHeight);

        Holder<Biome> target = holder(level, new ResourceLocation("minecraft", "plains"));
        Holder<Biome> infected = holder(level, new ResourceLocation(SporeSRP.MOD_ID, "infected_biome"));

        int minCX = minX >> 4, maxCX = maxX >> 4;
        int minCZ = minZ >> 4, maxCZ = maxZ >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                final int fcx = cx, fcz = cz;
                COMMAND_QUEUE.add(() -> {
                    LevelChunk changed = BiomeRegionConverter.convertChunk(
                            level, target, infected, fcx, fcz, minX, minY, minZ, maxX, maxY, maxZ);
                    if (changed != null) PENDING_CLEAR_CHUNKS.add(changed);
                });
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 处理命令队列
        int executed = 0;
        while (!COMMAND_QUEUE.isEmpty() && executed < MAX_COMMANDS_PER_TICK) {
            try {
                COMMAND_QUEUE.poll().run();
                executed++;
            } catch (Exception e) {
                SporeSRP.LOGGER.warn("Failed to convert CDU biome", e);
            }
        }

        // 本 tick 改动过的区块合并成一个群系包发出
        if (!PENDING_CLEAR_CHUNKS.isEmpty()) {
            try {
                BiomeRegionConverter.sendBiomePacket(PENDING_CLEAR_CHUNKS);
            } catch (Exception e) {
                SporeSRP.LOGGER.warn("Failed to send CDU biome packet", e);
            } finally {
                PENDING_CLEAR_CHUNKS.clear();
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