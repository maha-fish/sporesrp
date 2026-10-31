package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.maha_fish.sporesrp.util.SporeBridge;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InfectedBiomeHandler {






    // ==================== 配置读取 ====================
    private static long getProtoIntervalTicks() {
        return (long) (Config.COMMON.protoIncreaseIntervalMinutes.get() * 60 * 20);
    }
    private static int getProtoInitialRadius() { return Config.COMMON.protoInitialRadius.get(); }
    private static int getProtoRadiusIncrement() { return Config.COMMON.protoRadiusIncrement.get(); }
    private static int getVerticalHeight() { return Config.COMMON.biomeVerticalHeight.get(); }
    private static int getMaxCommandsPerTick() { return Config.COMMON.maxCommandsPerTick.get(); }

    // ==================== 实体类型判断 ====================
    private static boolean isProto(LivingEntity entity) {
        // 走 SporeEntityHelper 的缓存，避免每次实体加入都做注册表反查 + toString 比较
        return SporeEntityHelper.isProto(entity);
    }
    private static boolean isMound(LivingEntity entity) {
        return SporeEntityHelper.isMound(entity);
    }

    // ==================== 前提条件检查（使用缓存） ====================
    private static boolean shouldSpread(ServerLevel level) {
        if (!Config.COMMON.enableInfectedBiome.get()) return false;

        // 条件1：阶段 >= 6
        WorldEvolutionData data = WorldEvolutionData.get(level);
        if (data.getEvolutionPhase() >= 6) {
            return true;
        }

        // 条件2：存在存活的 Proto（通过缓存检查）
        ResourceKey<Level> dim = level.dimension();
        for (Map.Entry<UUID, SporeEntityCache.ProtoInfo> entry : SporeEntityCache.PROTO_CACHE.entrySet()) {
            SporeEntityCache.ProtoInfo info = entry.getValue();
            if (!info.dimension.equals(dim)) continue;
            LivingEntity entity = (LivingEntity) level.getEntity(entry.getKey());
            if (entity != null && entity.isAlive()) {
                return true;
            }
        }
        return false;
    }

    // ==================== 命令队列 ====================
    private static final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();

    private static void scheduleFillBiome(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int step = 16;
        for (int x = minX; x <= maxX; x += step) {
            int x1 = x, x2 = Math.min(x + step - 1, maxX);
            for (int z = minZ; z <= maxZ; z += step) {
                int z1 = z, z2 = Math.min(z + step - 1, maxZ);
                for (int y = minY; y <= maxY; y += step) {
                    int y1 = y, y2 = Math.min(y + step - 1, maxY);
                    commandQueue.add(() -> {
                        String cmd = String.format("fillbiome %d %d %d %d %d %d sporesrp:infected_biome", x1, y1, z1, x2, y2, z2);
                        var source = level.getServer().createCommandSourceStack().withPermission(4).withSuppressedOutput();
                        level.getServer().getCommands().performPrefixedCommand(source, cmd);
                    });
                }
            }
        }
    }

    private static void replaceBiomeAround(ServerLevel level, BlockPos center, int radius) {
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        int centerY = center.getY();
        int halfHeight = getVerticalHeight() / 2;
        int minY = Math.max(level.getMinBuildHeight(), centerY - halfHeight);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, centerY + halfHeight);
        scheduleFillBiome(level, minX, minY, minZ, maxX, maxY, maxZ);
        SporeSRP.LOGGER.info("Scheduled biome replacement for radius {} around {}", radius, center);
    }

    // ==================== 扩散核心逻辑 ====================
    private static void tickProto(ServerLevel level) {
        if (!shouldSpread(level)) return;

        long currentTick = level.getServer().getTickCount();
        long interval = getProtoIntervalTicks();
        int increment = getProtoRadiusIncrement();
        ResourceKey<Level> dim = level.dimension();

        // 遍历缓存（使用迭代器以便清理）
        for (Iterator<Map.Entry<UUID, SporeEntityCache.ProtoInfo>> it =
             SporeEntityCache.PROTO_CACHE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, SporeEntityCache.ProtoInfo> entry = it.next();
            UUID uuid = entry.getKey();
            SporeEntityCache.ProtoInfo info = entry.getValue();

            if (!info.dimension.equals(dim)) continue;

            LivingEntity entity = (LivingEntity) level.getEntity(uuid);
            // 区块未加载时 getEntity 返回 null，不能据此判定为死亡：否则
            // currentRadius / lastIncreaseTick / center 会被丢弃，实体重新加载后半径归零。
            // 真正死亡的条目交给 SporeEntityCache 的存活刷新清理。
            if (entity == null) continue;
            if (!entity.isAlive()) {
                it.remove();
                continue;
            }

            BlockPos center = entity.blockPosition();
            if (currentTick - info.lastIncreaseTick >= interval) {
                int steps = (int) ((currentTick - info.lastIncreaseTick) / interval);
                info.currentRadius += steps * increment;
                info.lastIncreaseTick = currentTick;
                replaceBiomeAround(level, center, info.currentRadius);
                SporeSRP.LOGGER.debug("Proto radius increased to {} at {}", info.currentRadius, center);
            }
        }
    }

    private static void tickMounds(ServerLevel level) {
        if (!shouldSpread(level)) return;

        ResourceKey<Level> dim = level.dimension();

        for (Iterator<Map.Entry<UUID, SporeEntityCache.MoundInfo>> it =
             SporeEntityCache.MOUND_CACHE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, SporeEntityCache.MoundInfo> entry = it.next();
            UUID uuid = entry.getKey();
            SporeEntityCache.MoundInfo info = entry.getValue();

            if (!info.dimension.equals(dim)) continue;

            LivingEntity entity = (LivingEntity) level.getEntity(uuid);
            // 区块未加载时 getEntity 返回 null，不能据此判定为死亡：否则
            // currentRadius / lastIncreaseTick / center 会被丢弃，实体重新加载后半径归零。
            // 真正死亡的条目交给 SporeEntityCache 的存活刷新清理。
            if (entity == null) continue;
            if (!entity.isAlive()) {
                it.remove();
                continue;
            }

            // 半径改由 Spore 的成长阶段派生（生效值 15/20/30/40），不再按时间连续增长
            int age = SporeBridge.getMoundAge(entity);
            if (age < 0) age = 1;

            BlockPos center = entity.blockPosition();
            boolean moved = !info.center.equals(center);
            boolean aged = info.lastSeenAge != age;
            if (moved || aged) {
                info.center = center;
                info.lastSeenAge = age;
                int radius = (int) Math.round(SporeBridge.getMoundRange(age));
                replaceBiomeAround(level, center, radius);
                SporeSRP.LOGGER.debug("Mound biome refresh: age={} radius={} at {}", age, radius, center);
            }
        }
    }

    // ==================== 命令队列处理 ====================
    private static void processCommandQueue() {
        int max = getMaxCommandsPerTick();
        int executed = 0;
        while (!commandQueue.isEmpty() && executed < max) {
            Runnable task = commandQueue.poll();
            if (task == null) break;
            try {
                task.run();
                executed++;
            } catch (Exception e) {
                SporeSRP.LOGGER.warn("Failed to execute fillbiome command", e);
            }
        }
    }

    /** 服务器停止时清空队列：队列里的 lambda 持有 ServerLevel，跨存档会残留旧世界引用。 */
    @SubscribeEvent
    public void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        commandQueue.clear();
    }

    // ==================== 事件监听 ====================
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        

    if (event.getLevel().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        long currentTick = level.getServer().getTickCount();

        // ---- Proto ----
        if (isProto(entity)) {
            SporeEntityCache.addProto(entity.getUUID(), level.dimension(), currentTick);
            if (shouldSpread(level)) {
                replaceBiomeAround(level, entity.blockPosition(), Config.COMMON.protoInitialRadius.get());
            }
            SporeSRP.LOGGER.debug("Proto added to cache");
            return;
        }

        // ---- Mound ----
        if (isMound(entity)) {
            SporeEntityCache.addMound(entity.getUUID(), entity.blockPosition(), level.dimension(), currentTick);
            if (shouldSpread(level)) {
                replaceBiomeAround(level, entity.blockPosition(),
                        (int) Math.round(SporeBridge.getMoundRange(Math.max(1, SporeBridge.getMoundAge(entity)))));
            }
            SporeSRP.LOGGER.debug("Mound added to cache");
            return;
        }

        // ---- ★ 新增：LargePool ----
        if (SporeEntityHelper.isLargePool(entity)) {
            SporeEntityCache.addLargePool(entity.getUUID(), level.dimension());
            return;
        }

        // ---- ★ 新增：Boss ----
        if (SporeEntityHelper.isBoss(entity)) {
            SporeEntityCache.addBoss(entity.getUUID(), level.dimension());
            return;
        }

        // ---- ★ 新增：Builder ----
        if (GastgaberBuilderHandler.isBuilder(entity)) {
            SporeEntityCache.addBuilder(entity.getUUID(), level.dimension());
            return;
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;LivingEntity entity = event.getEntity();
        if (isProto(entity)) {
            SporeEntityCache.removeProto(entity.getUUID());
        } else if (isMound(entity)) {
            SporeEntityCache.removeMound(entity.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {



    if (!Config.COMMON_SPEC.isLoaded()) return;processCommandQueue();

        if (event.getServer().getTickCount() % 40 == 0) {
            // 原实现只处理主世界，下界/末地的 Proto 与菌囊因此永不扩散；
            // tickProto/tickMounds 内部已按维度过滤，逐个维度处理即可。
            for (ServerLevel level : event.getServer().getAllLevels()) {
                tickProto(level);
                tickMounds(level);
            }
        }
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;if (!(event.getLevel() instanceof ServerLevel level)) return;
        // 保留首次全服扫描，不跳过
        SporeEntityCache.initCache(level);
    }
}