package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 **Spore 的继承体系** 推导生物等级，取代手写刷怪名单。
 *
 * <p>Spore 的实体基类树（反编译 1.20.1-2.2.0j 确认）：
 * <pre>
 *   Monster
 *   └── Infected                       → small
 *       ├── BasicInfected.*            → small
 *       ├── EvolvedInfected            → medium
 *       ├── Hyper / Experiment         → large
 *   PathfinderMob
 *   └── UtilityEntity
 *       ├── Organoid                   → organoids（等级需额外标注）
 *       └── Calamity                   → boss
 * </pre>
 *
 * <p>注意：{@code Organoid} / {@code Calamity} 继承的是 {@code UtilityEntity} 而不是
 * {@code Infected}，所以不能只判 {@code Infected}；而 {@code UtilityEntity} 的其它子类
 * （ScentEntity、WaveEntity、Illusion…）都不是战斗生物，必须排除。
 *
 * <p>零编译依赖：只比较类名字符串，不 import 任何 Spore 类。
 *
 * <p><b>玩家入口</b>（配置 {@code spore_tier} 段）：
 * <ul>
 *   <li>{@code organoidTier} —— 类器官的等级标注（Spore 内部用）；</li>
 *   <li>{@code utilityTier} —— 无继承等级的敌对生物标注（Spore 内部用）；</li>
 *   <li>{@code extraTier} —— <b>玩家添加/覆盖</b>：{@code "modid:entity=medium"}，
 *       等级写 {@code organoids} 表示只加入类器官组；同一实体可写多行；</li>
 *   <li>{@code exclude} —— <b>玩家排除</b>：从所有池中移除（含继承派生出来的）。</li>
 * </ul>
 *
 * <p>性能：等级推导结果按 {@code Class} 永久缓存；刷怪池快照在配置变更时重建。
 * 首次构建需要为每个 Spore 实体创建一次探针实例（不加入世界），约百次分配，仅一次。
 */
public final class SporeTierHelper {

    /** 生物等级。 */
    public enum Tier { SMALL, MEDIUM, LARGE, BOSS }

    private static final String SPORE_NS = "spore";

    private static final String C_INFECTED = "com.Harbinger.Spore.Sentities.BaseEntities.Infected";
    private static final String C_EVOLVED = "com.Harbinger.Spore.Sentities.BaseEntities.EvolvedInfected";
    private static final String C_HYPER = "com.Harbinger.Spore.Sentities.BaseEntities.Hyper";
    private static final String C_EXPERIMENT = "com.Harbinger.Spore.Sentities.BaseEntities.Experiment";
    private static final String C_ORGANOID = "com.Harbinger.Spore.Sentities.BaseEntities.Organoid";
    private static final String C_CALAMITY = "com.Harbinger.Spore.Sentities.BaseEntities.Calamity";

    /** extraTier 里表示「只进类器官组、不给等级」的关键字。 */
    private static final String ORGANOID_KEYWORD = "organoids";

    /** Class -> 等级（不含类器官）。永久缓存。 */
    private static final Map<Class<?>, Tier> CLASS_TIER = new ConcurrentHashMap<>();
    /** Class -> 是否为类器官。永久缓存。 */
    private static final Map<Class<?>, Boolean> CLASS_ORGANOID = new ConcurrentHashMap<>();
    /** EntityType -> 实体类。避免每次重建快照都重新实例化探针。 */
    private static final Map<EntityType<?>, Class<?>> TYPE_CLASS = new ConcurrentHashMap<>();

    private static volatile Snapshot snapshot;

    private SporeTierHelper() {
    }

    // ==================== 等级推导 ====================

    /**
     * 沿父类链推导等级。类器官返回 {@code null}（其等级由配置单独标注），
     * 非战斗实体（UtilityEntity 的其它子类）也返回 {@code null}。
     */
    private static Tier tierOfClass(Class<?> cls) {
        Tier cached = CLASS_TIER.get(cls);
        if (cached != null) return cached;

        Tier result = null;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getName();
            if (C_ORGANOID.equals(name)) {
                result = null;
                break;
            }
            if (C_CALAMITY.equals(name)) {
                result = Tier.BOSS;
                break;
            }
            if (C_HYPER.equals(name) || C_EXPERIMENT.equals(name)) {
                result = Tier.LARGE;
                break;
            }
            if (C_EVOLVED.equals(name)) {
                result = Tier.MEDIUM;
                break;
            }
            if (C_INFECTED.equals(name)) {
                result = Tier.SMALL;
                break;
            }
        }
        if (result != null) CLASS_TIER.put(cls, result);
        return result;
    }

    /** 是否为类器官（沿父类链判断，避免 instanceof 的编译依赖）。 */
    public static boolean isOrganoidClass(Class<?> cls) {
        if (cls == null) return false;
        Boolean cached = CLASS_ORGANOID.get(cls);
        if (cached != null) return cached;
        boolean result = false;
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            if (C_ORGANOID.equals(c.getName())) {
                result = true;
                break;
            }
        }
        CLASS_ORGANOID.put(cls, result);
        return result;
    }

    /** 实体实体的等级；类器官返回其配置等级，无等级返回 {@code null}。 */
    public static Tier tierOf(Entity entity) {
        if (entity == null) return null;
        Tier fromSnapshot = tierById(entity.getType());
        if (fromSnapshot != null) return fromSnapshot;
        Class<?> cls = entity.getClass();
        if (isOrganoidClass(cls)) return null;
        return tierOfClass(cls);
    }

    /** 由 EntityType 取等级；优先查快照（含玩家配置），再退回类推导。 */
    public static Tier tierOf(EntityType<?> type) {
        Tier fromSnapshot = tierById(type);
        if (fromSnapshot != null) return fromSnapshot;
        Class<?> cls = TYPE_CLASS.get(type);
        if (cls == null || isOrganoidClass(cls)) return null;
        return tierOfClass(cls);
    }

    private static Tier tierById(EntityType<?> type) {
        if (type == null) return null;
        ResourceLocation id = SporeEntityHelper.getEntityId(type);
        if (id == null) return null;
        return poolSnapshot().tierById.get(id.toString());
    }

    /** 是否为类器官实体（含玩家用 {@code extraTier} 标注为类器官的）。 */
    public static boolean isOrganoid(Entity entity) {
        if (entity == null) return false;
        ResourceLocation id = SporeEntityHelper.getEntityId(entity.getType());
        if (id != null && poolSnapshot().organoids.contains(id.toString())) return true;
        return isOrganoidClass(entity.getClass());
    }

    // ==================== 快照 ====================

    /** 各等级的实体 ID 集合（含 "modid:" 前缀）+ ID -> 等级索引。 */
    public static final class Snapshot {
        final Set<String> small = new LinkedHashSet<>();
        final Set<String> medium = new LinkedHashSet<>();
        final Set<String> large = new LinkedHashSet<>();
        final Set<String> boss = new LinkedHashSet<>();
        final Set<String> organoids = new LinkedHashSet<>();
        /** 类器官的等级标注（Spore 内部用）。 */
        final Map<String, Tier> organoidTier = new HashMap<>();
        /** 全部已知 ID -> 等级（派生 + 玩家添加/覆盖），供 tierOf 查询。 */
        final Map<String, Tier> tierById = new HashMap<>();

        public Set<String> pool(Tier tier) {
            if (tier == null) return Collections.emptySet();
            switch (tier) {
                case SMALL: return small;
                case MEDIUM: return medium;
                case LARGE: return large;
                case BOSS: return boss;
                default: return Collections.emptySet();
            }
        }

        public Set<String> organoids() {
            return organoids;
        }

        /** 查询任意实体 ID 的等级（含其它模组）。 */
        public Tier tierOfId(String id) {
            return id == null ? null : tierById.get(normalize(id));
        }
    }

    private static volatile List<? extends String> snapshotOrganoidCfg;
    private static volatile List<? extends String> snapshotUtilityCfg;
    private static volatile List<? extends String> snapshotExtraCfg;
    private static volatile List<? extends String> snapshotExcludeCfg;

    /** 取快照；任一等级相关配置变化时重建。 */
    public static Snapshot poolSnapshot() {
        List<? extends String> organoidCfg = Config.COMMON.sporeTierOrganoid.get();
        List<? extends String> utilityCfg = Config.COMMON.sporeTierUtility.get();
        List<? extends String> extraCfg = Config.COMMON.sporeTierExtra.get();
        List<? extends String> excludeCfg = Config.COMMON.sporeTierExclude.get();

        Snapshot current = snapshot;
        if (current != null && unchanged(organoidCfg, utilityCfg, extraCfg, excludeCfg)) {
            return current;
        }
        synchronized (SporeTierHelper.class) {
            current = snapshot;
            if (current != null && unchanged(organoidCfg, utilityCfg, extraCfg, excludeCfg)) {
                return current;
            }
            Snapshot built = buildSnapshot(organoidCfg, utilityCfg, extraCfg, excludeCfg);
            if (built == null) {
                // 世界还没就绪：返回临时空快照但**不缓存**，避免永久空池
                return new Snapshot();
            }
            snapshotOrganoidCfg = organoidCfg;
            snapshotUtilityCfg = utilityCfg;
            snapshotExtraCfg = extraCfg;
            snapshotExcludeCfg = excludeCfg;
            snapshot = built;
            com.maha_fish.sporesrp.SporeSRP.LOGGER.info(
                    "Spore tiers: small={}, medium={}, large={}, boss={}, organoids={} (extra rows={}, exclude rows={})",
                    built.small.size(), built.medium.size(), built.large.size(),
                    built.boss.size(), built.organoids.size(),
                    extraCfg == null ? 0 : extraCfg.size(),
                    excludeCfg == null ? 0 : excludeCfg.size());
            return built;
        }
    }

    private static boolean unchanged(List<? extends String> organoidCfg,
                                     List<? extends String> utilityCfg,
                                     List<? extends String> extraCfg,
                                     List<? extends String> excludeCfg) {
        return sameContent(organoidCfg, snapshotOrganoidCfg)
                && sameContent(utilityCfg, snapshotUtilityCfg)
                && sameContent(extraCfg, snapshotExtraCfg)
                && sameContent(excludeCfg, snapshotExcludeCfg);
    }

    private static boolean sameContent(List<? extends String> a, List<? extends String> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /** 统一 ID 写法：缺命名空间时补 {@code spore:}。 */
    private static String normalize(String raw) {
        if (raw == null) return null;
        String id = raw.trim();
        if (id.isEmpty()) return null;
        return id.indexOf(':') < 0 ? SPORE_NS + ":" + id : id;
    }

    /**
     * 解析 {@code "modid:entity=tier"} 形式的配置项。
     *
     * @param out          解析出的 id -> 等级
     * @param organoidOnly 等级写 {@code organoids} 的条目（只进类器官组）
     */
    private static void parseTierEntries(List<? extends String> list,
                                         Map<String, Tier> out,
                                         Set<String> organoidOnly) {
        if (list == null) return;
        for (String entry : list) {
            if (entry == null) continue;
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String id = normalize(entry.substring(0, eq));
            if (id == null) continue;
            String tierName = entry.substring(eq + 1).trim();
            if (ORGANOID_KEYWORD.equalsIgnoreCase(tierName)) {
                organoidOnly.add(id);
                continue;
            }
            try {
                out.put(id, Tier.valueOf(tierName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // 配置写错不影响其它条目
            }
        }
    }

    /** 解析 {@code exclude} 列表（纯 ID）。 */
    private static Set<String> parseIdList(List<? extends String> list) {
        Set<String> out = new HashSet<>();
        if (list == null) return out;
        for (String entry : list) {
            String id = normalize(entry);
            if (id != null) out.add(id);
        }
        return out;
    }

    private static Snapshot buildSnapshot(List<? extends String> organoidCfg,
                                          List<? extends String> utilityCfg,
                                          List<? extends String> extraCfg,
                                          List<? extends String> excludeCfg) {
        Snapshot snap = new Snapshot();

        Map<String, Tier> organoidTiers = new HashMap<>();
        Set<String> organoidOnly = new HashSet<>();
        parseTierEntries(organoidCfg, organoidTiers, organoidOnly);
        snap.organoidTier.putAll(organoidTiers);

        Map<String, Tier> utilityTiers = new HashMap<>();
        parseTierEntries(utilityCfg, utilityTiers, new HashSet<>());

        // 服务器/世界尚未就绪时返回 null：调用方不会缓存，下次调用会重试。
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        ServerLevel probeLevel = server.overworld();
        if (probeLevel == null) return null;

        // ---- 1) 继承体系派生 ----
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null || !SPORE_NS.equals(id.getNamespace())) continue;
            Class<?> cls = classOf(type, probeLevel);
            if (cls == null) continue;
            String fullId = id.toString();

            if (isOrganoidClass(cls)) {
                snap.organoids.add(fullId);
                Tier tier = organoidTiers.get(fullId);
                if (tier != null) put(snap, fullId, tier);
                continue;
            }

            Tier tier = tierOfClass(cls);
            if (tier == null) tier = utilityTiers.get(fullId);
            if (tier != null) put(snap, fullId, tier);
        }

        // ---- 2) 玩家添加/覆盖（可指向其它模组的生物） ----
        Map<String, Tier> extraTiers = new HashMap<>();
        parseTierEntries(extraCfg, extraTiers, organoidOnly);
        for (String id : organoidOnly) {
            snap.organoids.add(id);
        }
        for (Map.Entry<String, Tier> e : extraTiers.entrySet()) {
            put(snap, e.getKey(), e.getValue()); // put 会先从其它池移除，实现覆盖语义
        }

        // ---- 3) 玩家排除（含派生结果） ----
        for (String id : parseIdList(excludeCfg)) {
            snap.small.remove(id);
            snap.medium.remove(id);
            snap.large.remove(id);
            snap.boss.remove(id);
            snap.organoids.remove(id);
            snap.tierById.remove(id);
        }
        return snap;
    }

    /** 把 id 放入指定等级池（先从所有池移除，实现覆盖语义）。 */
    private static void put(Snapshot snap, String id, Tier tier) {
        snap.small.remove(id);
        snap.medium.remove(id);
        snap.large.remove(id);
        snap.boss.remove(id);
        snap.pool(tier).add(id);
        snap.tierById.put(id, tier);
    }

    /**
     * 取实体对应的 Java 类。
     *
     * <p>vanilla 的 {@code EntityType} 不暴露实体类，唯一途径是创建一次实例；
     * 这里只为每个类型创建一次探针（不加入世界、随即丢弃），并永久缓存结果。
     */
    private static Class<?> classOf(EntityType<?> type, ServerLevel level) {
        Class<?> cached = TYPE_CLASS.get(type);
        if (cached != null) return cached;
        try {
            Entity probe = type.create(level);
            if (probe == null) return null;
            Class<?> cls = probe.getClass();
            TYPE_CLASS.put(type, cls);
            return cls;
        } catch (Throwable t) {
            return null;
        }
    }
}
