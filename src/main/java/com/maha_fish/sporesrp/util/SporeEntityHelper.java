package com.maha_fish.sporesrp.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spore 生物识别工具。
 *
 * <p>刷怪池**不再由配置名单维护**，而是由 {@link SporeTierHelper} 按 Spore 的继承体系推导，
 * 因此 Spore 新增生物会自动归位，不需要改配置。
 *
 * <p>性能：注册表查询（{@code BuiltInRegistries.ENTITY_TYPE.getKey}）结果按 EntityType 永久缓存；
 * 刷怪池快照由 {@link SporeTierHelper} 缓存，仅在等级标注配置变化时重建。
 */
public final class SporeEntityHelper {

    private SporeEntityHelper() {
    }

    /** spore 命名空间下的固定实体 ID，避免重复构造 ResourceLocation。 */
    private static final ResourceLocation PROTO_ID = ResourceLocation.tryParse("spore:proto");
    private static final ResourceLocation MOUND_ID = ResourceLocation.tryParse("spore:mound");
    private static final ResourceLocation SCENT_ID = ResourceLocation.tryParse("spore:scent");

    private static final String SPORE_NAMESPACE = "spore";

    /** EntityType -> 是否属于 spore 命名空间。 */
    private static final ConcurrentHashMap<EntityType<?>, Boolean> SPORE_TYPE_CACHE =
            new ConcurrentHashMap<>();
    /** EntityType -> 注册名。注册表冻结后注册名不会变化，可永久缓存。 */
    private static final ConcurrentHashMap<EntityType<?>, ResourceLocation> ID_CACHE =
            new ConcurrentHashMap<>();

    // ==================== 各等级刷怪池（按 Spore 继承体系推导） ====================

    /** 小等级生物池（BasicInfected 等）。含 "spore:" 前缀。 */
    public static Set<String> smallPoolSet() { return SporeTierHelper.poolSnapshot().pool(SporeTierHelper.Tier.SMALL); }
    /** 中等级生物池（EvolvedInfected + 标注为 medium 的类器官与无等级生物）。 */
    public static Set<String> mediumPoolSet() { return SporeTierHelper.poolSnapshot().pool(SporeTierHelper.Tier.MEDIUM); }
    /** 大等级生物池（Hyper / Experiment + 标注为 large 的类器官）。 */
    public static Set<String> largePoolSet() { return SporeTierHelper.poolSnapshot().pool(SporeTierHelper.Tier.LARGE); }
    /** Boss 生物池（Calamity）。 */
    public static Set<String> bossPoolSet() { return SporeTierHelper.poolSnapshot().pool(SporeTierHelper.Tier.BOSS); }
    /** 类器官集合（Organoid 基类的全部子类）。 */
    public static Set<String> organoidSet() { return SporeTierHelper.poolSnapshot().organoids(); }

    public static List<String> getSmallPool() { return new ArrayList<>(smallPoolSet()); }
    public static List<String> getMediumPool() { return new ArrayList<>(mediumPoolSet()); }
    public static List<String> getLargePool() { return new ArrayList<>(largePoolSet()); }
    public static List<String> getBossPool() { return new ArrayList<>(bossPoolSet()); }
    public static List<String> getOrganoids() { return new ArrayList<>(organoidSet()); }

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

    /** 是否为 Large 等级（类器官 proto 虽在 large 池，但不算 Large 级生物）。 */
    public static boolean isLargePool(LivingEntity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null) return false;
        if (PROTO_ID.equals(id)) return false;
        return largePoolSet().contains(id.toString());
    }

    /** 是否为 Boss 等级。 */
    public static boolean isBoss(LivingEntity entity) {
        ResourceLocation id = getEntityId(entity);
        if (id == null) return false;
        return bossPoolSet().contains(id.toString());
    }

    /** 0 = 小，1 = 中，2 = 大，3 = Boss；未知返回 0。 */
    public static int getInfectedLevel(LivingEntity entity) {
        ResourceLocation regName = getEntityId(entity);
        if (regName == null) return 0;
        String id = regName.toString();

        if (smallPoolSet().contains(id)) return 0;
        if (mediumPoolSet().contains(id)) return 1;
        if (largePoolSet().contains(id)) return 2;
        if (bossPoolSet().contains(id)) return 3;
        return 0;
    }

    /** 空列表常量，供调用方在无 Spore 时复用。 */
    public static List<String> emptyPool() {
        return Collections.emptyList();
    }
}
