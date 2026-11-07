package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.BiomeRegionConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDU 的群系净化 —— **绑定在原模组的 CDU 方块上**，不是自建倒计时。
 *
 * <p>原模组 {@code CDUBlockEntity.serverTick} 的逻辑（反编译确认）：
 * <pre>
 *   if (CDUBlock.isCDUUsable(pos, level) &amp;&amp; e.getFuel() &gt; 0) {
 *       e.fuel--;
 *       if (e.getFuel() % 200 == 0) e.cleanInfection(pos);   // 每 200 tick 净化一次方块
 *   }
 * </pre>
 * 也就是说 CDU 每消耗 200 点燃料就净化一波。这里完全跟随它：
 * 读取同一个 {@code fuel}，在 {@code fuel % 200 == 0} 的那一 tick 顺带把周围群系改回平原。
 *
 * <p>好处：
 * <ul>
 *   <li>没有燃料就不会净化（以前是自己数 60 秒，与方块状态无关）；</li>
 *   <li>净化节奏与方块本身完全一致，不会出现"方块停了群系还在变"；</li>
 *   <li>不再需要玩家拿冰罐右击来启动。</li>
 * </ul>
 */
public class CDUCommandHandler {

    /** 原模组 CDU 的方块实体注册名。 */
    private static final ResourceLocation CDU_BLOCK_ENTITY_ID = ResourceLocation.tryParse("spore:cdu");
    /** 原模组的净化脉冲间隔（tick），与其 {@code fuel % 200 == 0} 对齐。 */
    private static final int PULSE_TICKS = 200;
    /** 发现 CDU 的扫描间隔（tick）。 */
    private static final int DISCOVERY_INTERVAL = 100;
    /** 扫描玩家周围多少区块。 */
    private static final int DISCOVERY_CHUNK_RADIUS = 3;

    private static final int MAX_COMMANDS_PER_TICK = 20;

    /** 维度 -> 已发现的 CDU 位置 -> 上次读到的燃料值。 */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Integer>> KNOWN_CDUS = new ConcurrentHashMap<>();
    /** 待处理的群系转换任务。 */
    private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> COMMAND_QUEUE = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** 本 tick 有群系改动的区块，攒起来统一下发。 */
    private static final java.util.List<LevelChunk> PENDING_CLEAR_CHUNKS = new java.util.ArrayList<>();

    private static Method getFuelMethod;
    private static boolean getFuelResolved;
    private static final Map<BlockEntityType<?>, Boolean> CDU_TYPE_CACHE = new ConcurrentHashMap<>();
    private static int discoveryTimer;

    // ==================== 反射读取原模组 CDU 的燃料 ====================

    private static int readFuel(BlockEntity blockEntity) {
        if (!getFuelResolved) {
            getFuelResolved = true;
            try {
                getFuelMethod = blockEntity.getClass().getMethod("getFuel");
            } catch (NoSuchMethodException e) {
                getFuelMethod = null;
            }
        }
        if (getFuelMethod == null) return -1;
        try {
            Object value = getFuelMethod.invoke(blockEntity);
            return value instanceof Integer fuel ? fuel : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static boolean isCdu(BlockEntity blockEntity) {
        if (blockEntity == null) return false;
        BlockEntityType<?> type = blockEntity.getType();
        Boolean cached = CDU_TYPE_CACHE.get(type);
        if (cached != null) return cached;
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
        boolean match = id != null && CDU_BLOCK_ENTITY_ID.equals(id);
        CDU_TYPE_CACHE.put(type, match);
        return match;
    }

    // ==================== 群系净化 ====================

    /** 目标群系 / 只清理的来源群系；均为注册表 holder，比较用引用相等。 */
    private static Holder<Biome> holder(ServerLevel level, ResourceLocation id) {
        return level.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(ResourceKey.create(Registries.BIOME, id));
    }

    /**
     * 把 CDU 周围的感染群系改回平原。
     *
     * <p>按区块直写 biome palette，且**只替换当前确实是感染群系的格子** ——
     * 原生群系（沙漠、针叶林等）不会被一并抹成平原。
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

        Holder<Biome> target = holder(level, ResourceLocation.tryParse("minecraft:plains"));
        Holder<Biome> infected = holder(level, ResourceLocation.tryParse(SporeSRP.MOD_ID + ":infected_biome"));

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

    /** CDU 被破坏时停止跟踪。 */
    public static void stopPurification(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Integer> map = KNOWN_CDUS.get(level.dimension());
        if (map != null) {
            map.remove(pos);
            if (map.isEmpty()) KNOWN_CDUS.remove(level.dimension());
        }
    }

    // ==================== 发现 CDU ====================

    private static void discoverCdus(ServerLevel level) {
        Map<BlockPos, Integer> known = KNOWN_CDUS.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());

        for (ServerPlayer player : level.players()) {
            int pcx = player.chunkPosition().x;
            int pcz = player.chunkPosition().z;
            for (int dx = -DISCOVERY_CHUNK_RADIUS; dx <= DISCOVERY_CHUNK_RADIUS; dx++) {
                for (int dz = -DISCOVERY_CHUNK_RADIUS; dz <= DISCOVERY_CHUNK_RADIUS; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                    if (chunk == null) continue;
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        if (!isCdu(blockEntity)) continue;
                        known.putIfAbsent(blockEntity.getBlockPos(), -1);
                    }
                }
            }
        }
    }

    // ==================== ServerTick 处理 ====================

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 1) 处理群系转换队列
        int executed = 0;
        while (!COMMAND_QUEUE.isEmpty() && executed < MAX_COMMANDS_PER_TICK) {
            try {
                COMMAND_QUEUE.poll().run();
                executed++;
            } catch (Exception e) {
                SporeSRP.LOGGER.warn("Failed to convert CDU biome", e);
            }
        }

        // 2) 本 tick 改动过的区块合并成一个群系包发出
        if (!PENDING_CLEAR_CHUNKS.isEmpty()) {
            try {
                BiomeRegionConverter.sendBiomePacket(PENDING_CLEAR_CHUNKS);
            } catch (Exception e) {
                SporeSRP.LOGGER.warn("Failed to send CDU biome packet", e);
            } finally {
                PENDING_CLEAR_CHUNKS.clear();
            }
        }

        // 3) 定期发现附近的 CDU
        if (++discoveryTimer >= DISCOVERY_INTERVAL) {
            discoveryTimer = 0;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                if (!level.players().isEmpty()) discoverCdus(level);
            }
        }

        // 4) 跟随原模组 CDU 的净化脉冲
        KNOWN_CDUS.forEach((dimension, positions) -> {
            if (positions.isEmpty()) return;
            ServerLevel level = event.getServer().getLevel(dimension);
            if (level == null) return;

            // 注意：ConcurrentHashMap 的 entrySet().removeIf() 传给谓词的是**不可变 Entry**，
            // 在谓词里调用 entry.setValue(...) 会抛 UnsupportedOperationException 并打崩服务器线程。
            // 这里改为普通迭代 + CHM 自身允许的 put/remove。
            for (Map.Entry<BlockPos, Integer> entry : positions.entrySet()) {
                BlockPos cduPos = entry.getKey();
                BlockEntity blockEntity = level.getBlockEntity(cduPos);
                if (!isCdu(blockEntity)) {
                    positions.remove(cduPos); // 方块不在了，停止跟踪
                    continue;
                }

                int fuel = readFuel(blockEntity);
                if (fuel < 0) continue;

                Integer lastFuel = entry.getValue();
                positions.put(cduPos, fuel);

                // 与原模组 cleanInfection 同一个判定：fuel % 200 == 0 且燃料确实在下降
                if (fuel > 0 && fuel % PULSE_TICKS == 0 && (lastFuel == null || lastFuel != fuel)) {
                    submitClearCommand(level, cduPos);
                    SporeSRP.LOGGER.debug("CDU pulse at {} (fuel={}): purifying biome", cduPos, fuel);
                }
            }
        });
    }
}