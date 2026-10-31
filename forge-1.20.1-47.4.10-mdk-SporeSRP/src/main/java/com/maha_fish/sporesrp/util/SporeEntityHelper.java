package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spore 生物识别工具。
 *
 * <p>性能说明：注册表查询（{@code BuiltInRegistries.ENTITY_TYPE.getKey}）与
 * 配置列表的 {@code List.contains} 原本在每次调用时都会执行，而调用点大量位于
 * 每 tick 循环和实体扫描谓词中。这里对二者做**结果缓存**：
 * <ul>
 *   <li>实体类型 -> 注册名：注册表在冻结后不可变，因此可永久缓存；</li>
 *   <li>配置列表 -> HashSet：以列表实例做失效判断，配置重载后实例变化会自动重建。</li>
 * </ul>
 * 缓存只改变查询方式，不改变任何判定结果，因此不影响游戏内表现。
 */
public final class SporeEntityHelper {

    private SporeEntityHelper() {
    }

    /** spore 命名空间下的固定实体 ID，避免重复构造 ResourceLocation。 */
    private static final ResourceLocation PROTO_ID = new ResourceLocation("spore", "proto");
    private static final ResourceLocation MOUND_ID = new ResourceLocation("spore", "mound");
    private static final ResourceLocation SCENT_ID = new ResourceLocation("spore", "scent");

    private static final String SPORE_NAMESPACE = "spore";

    /** EntityType -> 是否属于 spore 命名空间。 */
    private static final ConcurrentHashMap<EntityType<?>, Boolean> SPORE_TYPE_CACHE =
            new ConcurrentHashMap<>();
    /** EntityType -> 注册名。注册表冻结后注册名不会变化，可永久缓存。 */
    private static final ConcurrentHashMap<EntityType<?>, ResourceLocation> ID_CACHE =
            new ConcurrentHashMap<>();

    // ==================== 配置列表的集合视图（按列表实例失效） ====================

    private static final PoolView SMALL = new PoolView();
    private static final PoolView MEDIUM = new PoolView();
    private static final PoolView LARGE = new PoolView();
    private static final PoolView BOSS = new PoolView();
    private static final PoolView ORGANOIDS = new PoolView();

    private static final class PoolView {
        private List<? extends String> source;
        private Set<String> view = Collections.emptySet();

        Set<String> of(List<? extends String> list) {
            if (list == this.source) return this.view;
            // 同一个配置项可能每次 get() 都返回新的 List 实例：此时先比较内容，
            // 内容相同只换引用、不重建 HashSet，避免缓存反而变成每调用一次就重建。
            if (list != null && this.source != null && list.equals(this.source)) {
                this.source = list;
                return this.view;
            }
            this.source = list;
            this.view = (list == null || list.isEmpty())
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new HashSet<>(list));
            return this.view;
        }
    }

    public static List<? extends String> getSmallPool() { return Config.COMMON.poolSmall.get(); }
    public static List<? extends String> getMediumPool() { return Config.COMMON.poolMedium.get(); }
    public static List<? extends String> getLargePool() { return Config.COMMON.poolLarge.get(); }
    public static List<? extends String> getBossPool() { return Config.COMMON.poolBoss.get(); }
    public static List<? extends String> getOrganoids() { return Config.COMMON.organoids.get(); }

    /** 缓存版小生物池（O(1) 查询）。 */
    public static Set<String> smallPoolSet() { return SMALL.of(getSmallPool()); }
    /** 缓存版中生物池（O(1) 查询）。 */
    public static Set<String> mediumPoolSet() { return MEDIUM.of(getMediumPool()); }
    /** 缓存版大生物池（O(1) 查询）。 */
    public static Set<String> largePoolSet() { return LARGE.of(getLargePool()); }
    /** 缓存版 Boss 生物池（O(1) 查询）。 */
    public static Set<String> bossPoolSet() { return BOSS.of(getBossPool()); }
    /** 缓存版类器官体列表（O(1) 查询）。 */
    public static Set<String> organoidSet() { return ORGANOIDS.of(getOrganoids()); }

    // ==================== 注册名查询（带缓存） ====================

    /**
     * 等价于 {@code BuiltInRegistries.ENTITY_TYPE.getKey(type)}，但命中缓存。
     * 未注册的类型返回 {@code null}（与原始行为一致，只是不缓存 null 结果）。
     */
    public static ResourceLocation getEntityId(EntityType<?> type) {
        if (type == null) return null;
        ResourceLocation cached = ID_CACHE.get(type);
        if (cached != null) return cached;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id != null) ID_CACHE.put(type, id);
        return id;
    }

    /** 等价于 {@code getEntityId(entity.getType())}。 */
    public static ResourceLocation getEntityId(LivingEntity entity) {
        return entity == null ? null : getEntityId(entity.getType());
    }

    // ==================== 常用 EntityType（惰性解析，避免循环内重复构造 ResourceLocation） ====================

    private static EntityType<?> moundTypeCache;
    private static EntityType<?> scentTypeCache;
    private static EntityType<?> protoTypeCache;

    /** {@code spore:mound} 的 EntityType；未安装 Spore 时返回 null。 */
    public static EntityType<?> getMoundType() {
        if (moundTypeCache == null) moundTypeCache = BuiltInRegistries.ENTITY_TYPE.get(MOUND_ID);
        return moundTypeCache;
    }

    /** {@code spore:scent} 的 EntityType；未安装 Spore 时返回 null。 */
    public static EntityType<?> getScentType() {
        if (scentTypeCache == null) scentTypeCache = BuiltInRegistries.ENTITY_TYPE.get(SCENT_ID);
        return scentTypeCache;
    }

    /** {@code spore:proto} 的 EntityType；未安装 Spore 时返回 null。 */
    public static EntityType<?> getProtoType() {
        if (protoTypeCache == null) protoTypeCache = BuiltInRegistries.ENTITY_TYPE.get(PROTO_ID);
        return protoTypeCache;
    }

    // ==================== 判定 ====================

    public static boolean isSporeEntity(LivingEntity entity) {
        return entity != null && isSporeType(entity.getType());
    }

    /** 与 {@link #isSporeEntity} 等价，但直接接收 EntityType，便于在谓词里复用。 */
    public static boolean isSporeType(EntityType<?> type) {
        if (type == null) return false;
        Boolean cached = SPORE_TYPE_CACHE.get(type);
        if (cached != null) return cached;
        ResourceLocation id = getEntityId(type);
        boolean spore = id != null && SPORE_NAMESPACE.equals(id.getNamespace());
        SPORE_TYPE_CACHE.put(type, spore);
        return spore;
    }

    /** 是否为 {@code spore:proto}。 */
    public static boolean isProto(LivingEntity entity) {
        return entity != null && PROTO_ID.equals(getEntityId(entity.getType()));
    }

    /** 是否为 {@code spore:mound}。 */
    public static boolean isMound(LivingEntity entity) {
        return entity != null && MOUND_ID.equals(getEntityId(entity.getType()));
    }

    /** 是否为 {@code spore:scent}。 */
    public static boolean isScent(LivingEntity entity) {
        return entity != null && SCENT_ID.equals(getEntityId(entity.getType()));
    }

    // ★判断是否为 LargePool
    public static boolean isLargePool(LivingEntity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null) return false;
        // Proto 不是 LargePool
        if (PROTO_ID.equals(id)) return false;
        return largePoolSet().contains(id.toString());
    }

    // ★判断是否为 Boss
    public static boolean isBoss(LivingEntity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null) return false;
        return bossPoolSet().contains(id.toString());
    }

    public static int getInfectedLevel(LivingEntity entity) {
        ResourceLocation regName = getEntityId(entity);
        if (regName == null) return 0;
        // 原始实现还检查了 registry.containsKey(regName)：由 getKey 反查得到的 ID
        // 必然存在于注册表中，该检查恒为真，故省略（结果不变）。
        String id = regName.toString();

        if (smallPoolSet().contains(id)) return 0;
        if (mediumPoolSet().contains(id)) return 1;
        if (largePoolSet().contains(id)) return 2;
        if (bossPoolSet().contains(id)) return 3;
        return 0;
    }
}
