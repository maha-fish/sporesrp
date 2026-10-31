package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.handler.GastgaberBuilderHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 统一实体缓存管理器
 *
 * 管理五种实体列表：Proto / Mound / LargePool / Boss / Builder
 *
 * 设计思路：
 * 1. initCache() 在服务器启动时使用 AABB 进行一次全服扫描，作为缓存种子
 * 2. onServerTick() 每 2 秒遍历缓存 Map 验证存活，更新预计算列表
 * 3. 各 Handler 通过 getAliveXxx() 直接获取列表，零开销
 */
public class SporeEntityCache {

    // ==================== 原始缓存 Map ====================
    public static final Map<UUID, ProtoInfo> PROTO_CACHE = new ConcurrentHashMap<>();
    public static final Map<UUID, MoundInfo> MOUND_CACHE = new ConcurrentHashMap<>();
    public static final Map<UUID, LargePoolInfo> LARGE_POOL_CACHE = new ConcurrentHashMap<>();
    public static final Map<UUID, BossInfo> BOSS_CACHE = new ConcurrentHashMap<>();
    public static final Map<UUID, BuilderInfo> BUILDER_CACHE = new ConcurrentHashMap<>();

    // ==================== 预计算存活列表（每2秒刷新） ====================
    private static final Map<ResourceKey<Level>, List<LivingEntity>> ALIVE_PROTOS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, List<LivingEntity>> ALIVE_MOUNDS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, List<LivingEntity>> ALIVE_LARGE_POOLS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, List<LivingEntity>> ALIVE_BOSSES = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, List<LivingEntity>> ALIVE_BUILDERS = new ConcurrentHashMap<>();

    // ==================== 更新调度 ====================
    private static final int UPDATE_INTERVAL = 40; // 40 Tick = 2 秒
    private static int tickCounter = 0;

    // ==================== 内部数据类 ====================

    public static class ProtoInfo {
        public final ResourceKey<Level> dimension;
        public int currentRadius;
        public long lastIncreaseTick;

        public ProtoInfo(ResourceKey<Level> dim, long currentTick) {
            this.dimension = dim;
            this.currentRadius = Config.COMMON.protoInitialRadius.get();
            this.lastIncreaseTick = currentTick;
        }
    }

    /**
     * 菌囊缓存。半径不再由本模组自算，改由 Spore 的成长阶段派生：
     * 这里只镜像「上次见到的阶段」与「位置」，用于判断是否需要重新铺群系。
     */
    public static class MoundInfo {
        public final ResourceKey<Level> dimension;
        public BlockPos center;
        /** 上次见到的 Spore 成长阶段（1..4）；-1 表示尚未读到 */
        public int lastSeenAge = -1;

        public MoundInfo(BlockPos center, ResourceKey<Level> dim, long currentTick) {
            this.center = center;
            this.dimension = dim;
        }
    }

    public static class LargePoolInfo {
        public final ResourceKey<Level> dimension;

        public LargePoolInfo(ResourceKey<Level> dim) {
            this.dimension = dim;
        }
    }

    public static class BossInfo {
        public final ResourceKey<Level> dimension;

        public BossInfo(ResourceKey<Level> dim) {
            this.dimension = dim;
        }
    }

    public static class BuilderInfo {
        public final ResourceKey<Level> dimension;

        public BuilderInfo(ResourceKey<Level> dim) {
            this.dimension = dim;
        }
    }

    // ==================== 定时更新 ====================

    /**
     * 原实现按「维度数量 × 缓存总数」重复遍历每个缓存 Map；现在每个缓存只遍历一次，
     * 在一次遍历中同时完成「按维度分组」与「清理已死亡实体」，结果为等价集合。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // 演化数据把所有写入都打上「待同步」标记，这里每 tick 统一发一次
        com.maha_fish.sporesrp.data.WorldEvolutionData.flushPendingSync();
        tickCounter++;
        if (tickCounter < UPDATE_INTERVAL) return;
        tickCounter = 0;

        if (event.getServer().getPlayerList().getPlayerCount() == 0) return;

        // 本次需要校验的维度
        Map<ResourceKey<Level>, ServerLevel> levels = new HashMap<>();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            levels.put(level.dimension(), level);
        }
        if (levels.isEmpty()) return;

        Map<ResourceKey<Level>, List<LivingEntity>> protos =
                collect(PROTO_CACHE, levels, i -> i.dimension, e -> true);
        Map<ResourceKey<Level>, List<LivingEntity>> mounds =
                collect(MOUND_CACHE, levels, i -> i.dimension, e -> true);
        Map<ResourceKey<Level>, List<LivingEntity>> largePools =
                collect(LARGE_POOL_CACHE, levels, i -> i.dimension, e -> true);
        Map<ResourceKey<Level>, List<LivingEntity>> bosses =
                collect(BOSS_CACHE, levels, i -> i.dimension, e -> true);
        Map<ResourceKey<Level>, List<LivingEntity>> builders =
                collect(BUILDER_CACHE, levels, i -> i.dimension, e -> GastgaberBuilderHandler.isBuilder(e));

        // 保证每个已加载维度都有一条记录（与原实现一致）
        for (ResourceKey<Level> dim : levels.keySet()) {
            ALIVE_PROTOS.put(dim, freeze(protos.get(dim)));
            ALIVE_MOUNDS.put(dim, freeze(mounds.get(dim)));
            ALIVE_LARGE_POOLS.put(dim, freeze(largePools.get(dim)));
            ALIVE_BOSSES.put(dim, freeze(bosses.get(dim)));
            ALIVE_BUILDERS.put(dim, freeze(builders.get(dim)));
        }
    }

    /**
     * 单次遍历缓存：按维度分组存活实体，同时移除已死亡/已卸载的条目。
     * 维度不在本次处理集合中的条目保持原样（不清理），与原实现语义一致。
     */
    private static <T> Map<ResourceKey<Level>, List<LivingEntity>> collect(
            Map<UUID, T> cache,
            Map<ResourceKey<Level>, ServerLevel> levels,
            Function<T, ResourceKey<Level>> dimensionOf,
            Predicate<LivingEntity> extraCheck) {

        Map<ResourceKey<Level>, List<LivingEntity>> grouped = new HashMap<>();
        Iterator<Map.Entry<UUID, T>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, T> entry = it.next();
            ResourceKey<Level> dim = dimensionOf.apply(entry.getValue());
            ServerLevel level = levels.get(dim);
            if (level == null) continue; // 该维度本次不处理

            LivingEntity entity = (LivingEntity) level.getEntity(entry.getKey());
            if (entity != null && entity.isAlive() && extraCheck.test(entity)) {
                List<LivingEntity> bucket = grouped.get(dim);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    grouped.put(dim, bucket);
                }
                bucket.add(entity);
            } else {
                it.remove();
            }
        }
        return grouped;
    }

    private static List<LivingEntity> freeze(List<LivingEntity> list) {
        return (list == null || list.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(list);
    }

    /** 服务器停止时清空静态缓存，避免同一 JVM 换存档后残留上一世界的实体 UUID。 */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clearCache();
        tickCounter = 0;
    }

    // ==================== 公共 API ====================

    public static List<LivingEntity> getAliveProtos(ServerLevel level) {
        return ALIVE_PROTOS.getOrDefault(level.dimension(), Collections.emptyList());
    }

    public static List<LivingEntity> getAliveMounds(ServerLevel level) {
        return ALIVE_MOUNDS.getOrDefault(level.dimension(), Collections.emptyList());
    }

    public static List<LivingEntity> getAliveLargePools(ServerLevel level) {
        return ALIVE_LARGE_POOLS.getOrDefault(level.dimension(), Collections.emptyList());
    }

    public static List<LivingEntity> getAliveBosses(ServerLevel level) {
        return ALIVE_BOSSES.getOrDefault(level.dimension(), Collections.emptyList());
    }

    public static List<LivingEntity> getAliveBuilders(ServerLevel level) {
        return ALIVE_BUILDERS.getOrDefault(level.dimension(), Collections.emptyList());
    }

    // ==================== 数量查询 ====================

    public static int getAliveProtoCount(ServerLevel level) {
        return getAliveProtos(level).size();
    }

    public static int getAliveMoundCount(ServerLevel level) {
        return getAliveMounds(level).size();
    }

    public static int getAliveLargePoolCount(ServerLevel level) {
        return getAliveLargePools(level).size();
    }

    public static int getAliveBossCount(ServerLevel level) {
        return getAliveBosses(level).size();
    }

    public static int getAliveBuilderCount(ServerLevel level) {
        return getAliveBuilders(level).size();
    }

    public static boolean hasAliveProto(ServerLevel level) {
        return !getAliveProtos(level).isEmpty();
    }

    // ==================== 缓存操作 ====================

    public static void addProto(UUID uuid, ResourceKey<Level> dim, long currentTick) {
        PROTO_CACHE.put(uuid, new ProtoInfo(dim, currentTick));
    }

    public static void addMound(UUID uuid, BlockPos center, ResourceKey<Level> dim, long currentTick) {
        MOUND_CACHE.put(uuid, new MoundInfo(center, dim, currentTick));
    }

    public static void addLargePool(UUID uuid, ResourceKey<Level> dim) {
        LARGE_POOL_CACHE.put(uuid, new LargePoolInfo(dim));
    }

    public static void addBoss(UUID uuid, ResourceKey<Level> dim) {
        BOSS_CACHE.put(uuid, new BossInfo(dim));
    }

    public static void addBuilder(UUID uuid, ResourceKey<Level> dim) {
        BUILDER_CACHE.put(uuid, new BuilderInfo(dim));
    }

    public static void removeProto(UUID uuid) {
        PROTO_CACHE.remove(uuid);
    }

    public static void removeMound(UUID uuid) {
        MOUND_CACHE.remove(uuid);
    }

    public static void removeLargePool(UUID uuid) {
        LARGE_POOL_CACHE.remove(uuid);
    }

    public static void removeBoss(UUID uuid) {
        BOSS_CACHE.remove(uuid);
    }

    public static void removeBuilder(UUID uuid) {
        BUILDER_CACHE.remove(uuid);
    }

    public static void removeEntity(UUID uuid) {
        PROTO_CACHE.remove(uuid);
        MOUND_CACHE.remove(uuid);
        LARGE_POOL_CACHE.remove(uuid);
        BOSS_CACHE.remove(uuid);
        BUILDER_CACHE.remove(uuid);
    }

    // ==================== 初始化扫描 ====================

    public static void initCache(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        long currentTick = level.getServer().getTickCount();

        AABB full = new AABB(
                level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
        );

        List<LivingEntity> allEntities = level.getEntitiesOfClass(LivingEntity.class, full, e -> true);

        int protoAdded = 0, moundAdded = 0, largePoolAdded = 0, bossAdded = 0, builderAdded = 0;

        for (LivingEntity entity : allEntities) {
            UUID uuid = entity.getUUID();

            if (SporeEntityHelper.isProto(entity)) {
                if (!PROTO_CACHE.containsKey(uuid)) {
                    PROTO_CACHE.put(uuid, new ProtoInfo(dim, currentTick));
                    protoAdded++;
                }
            } else if (SporeEntityHelper.isMound(entity)) {
                if (!MOUND_CACHE.containsKey(uuid)) {
                    MOUND_CACHE.put(uuid, new MoundInfo(entity.blockPosition(), dim, currentTick));
                    moundAdded++;
                }
            }

            // LargePool（isLargePool 内部已排除 Proto）
            if (SporeEntityHelper.isLargePool(entity)) {
                if (!LARGE_POOL_CACHE.containsKey(uuid)) {
                    LARGE_POOL_CACHE.put(uuid, new LargePoolInfo(dim));
                    largePoolAdded++;
                }
            }

            // Boss
            if (SporeEntityHelper.isBoss(entity)) {
                if (!BOSS_CACHE.containsKey(uuid)) {
                    BOSS_CACHE.put(uuid, new BossInfo(dim));
                    bossAdded++;
                }
            }

            // Builder
            if (GastgaberBuilderHandler.isBuilder(entity)) {
                if (!BUILDER_CACHE.containsKey(uuid)) {
                    BUILDER_CACHE.put(uuid, new BuilderInfo(dim));
                    builderAdded++;
                }
            }
        }

        SporeSRP.LOGGER.info("Cache init scan in {}: added {} Proto, {} Mound, {} LargePool, {} Boss, {} Builder",
                dim.location(), protoAdded, moundAdded, largePoolAdded, bossAdded, builderAdded);
    }

    // ==================== 清理 ====================

    public static void clearCache() {
        PROTO_CACHE.clear();
        MOUND_CACHE.clear();
        LARGE_POOL_CACHE.clear();
        BOSS_CACHE.clear();
        BUILDER_CACHE.clear();
        ALIVE_PROTOS.clear();
        ALIVE_MOUNDS.clear();
        ALIVE_LARGE_POOLS.clear();
        ALIVE_BOSSES.clear();
        ALIVE_BUILDERS.clear();
    }

    // ==================== 调试信息 ====================

    public static int getProtoCacheSize() { return PROTO_CACHE.size(); }

    public static int getMoundCacheSize() { return MOUND_CACHE.size(); }

    public static int getLargePoolCacheSize() { return LARGE_POOL_CACHE.size(); }

    public static int getBossCacheSize() { return BOSS_CACHE.size(); }

    public static int getBuilderCacheSize() { return BUILDER_CACHE.size(); }
}
