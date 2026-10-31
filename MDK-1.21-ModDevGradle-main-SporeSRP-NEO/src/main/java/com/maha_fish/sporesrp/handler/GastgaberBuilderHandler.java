package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gastgaber Builder 变种系统
 * - 转化概率、吞噬范围、点数等均可配置
 * - Builder 列表由 SporeEntityCache 统一管理
 * - Builder 不能吞噬 Builder
 * - 呼叫间隔 15 秒
 * - 从配置列表中随机选择结构生成
 */
public class GastgaberBuilderHandler {

    public static final String BUILDER_TAG = "IsGastgaberBuilder";
    public static final String POINTS_TAG = "GastgaberBuilderPoints";
    public static final String COOLDOWN_TAG = "GastgaberBuilderCooldown";

    /** spore:gastgaber 的注册名，避免每个 Builder 每 10 tick 重建字符串。 */
    private static final ResourceLocation GASTGABER_ID = ResourceLocation.parse("spore:gastgaber");

    // ========== 从配置读取 ==========
    private static double getSpawnChance() {
        return Config.COMMON.gastgaberBuilderSpawnChance.get();
    }

    private static double getCollisionRadius() {
        return Config.COMMON.gastgaberBuilderCollisionRadius.get();
    }

    private static int getMaxPoints() {
        return Config.COMMON.gastgaberBuilderMaxPoints.get();
    }

    private static int getSmallPoints() {
        return Config.COMMON.gastgaberBuilderSmallPoints.get();
    }

    private static int getMediumPoints() {
        return Config.COMMON.gastgaberBuilderMediumPoints.get();
    }

    private static int getLargePoints() {
        return Config.COMMON.gastgaberBuilderLargePoints.get();
    }

    private static int getCallInterval() {
        return Config.COMMON.gastgaberBuilderCallInterval.get() * 20;
    }

    private static int getCallRadius() {
        return Config.COMMON.gastgaberBuilderCallRadius.get();
    }

    private static int getCooldownTicks() {
        return Config.COMMON.gastgaberBuilderCooldownSeconds.get() * 20;
    }

    // ========== 本地缓存（仅 Builder 自身的状态数据） ==========
    // ★ builderCallCooldown 保留：每个 Builder 的呼叫冷却时间（属于状态数据，不是存在性缓存）
    private static final ConcurrentHashMap<UUID, Long> builderCallCooldown = new ConcurrentHashMap<>();

    // ========== 辅助方法 ==========

    public static boolean isBuilder(LivingEntity entity) {
        if (entity == null) return false;
        CompoundTag persistentData = entity.getPersistentData();
        return persistentData.contains(BUILDER_TAG) && persistentData.getBoolean(BUILDER_TAG);
    }

    public static int getPoints(LivingEntity entity) {
        if (entity == null) return 0;
        CompoundTag persistentData = entity.getPersistentData();
        return persistentData.getInt(POINTS_TAG);
    }

    public static void setPoints(LivingEntity entity, int points) {
        if (entity == null) return;
        int maxPoints = getMaxPoints();
        CompoundTag persistentData = entity.getPersistentData();
        persistentData.putInt(POINTS_TAG, Math.max(0, Math.min(points, maxPoints)));
    }

    public static long getCooldown(LivingEntity entity) {
        if (entity == null) return 0;
        CompoundTag persistentData = entity.getPersistentData();
        return persistentData.getLong(COOLDOWN_TAG);
    }

    public static void setCooldown(LivingEntity entity, long currentTick) {
        if (entity == null) return;
        CompoundTag persistentData = entity.getPersistentData();
        persistentData.putLong(COOLDOWN_TAG, currentTick + getCooldownTicks());
    }

    public static void markAsBuilder(LivingEntity entity) {
        if (entity == null) return;
        CompoundTag persistentData = entity.getPersistentData();
        persistentData.putBoolean(BUILDER_TAG, true);
        persistentData.putInt(POINTS_TAG, 0);
        persistentData.putLong(COOLDOWN_TAG, 0);
        entity.setCustomName(Component.literal("builder"));
        entity.setCustomNameVisible(true);
        SporeSRP.LOGGER.info("Gastgaber {} marked as Builder", entity.getUUID());

        // ★ 不再手动加入 builderCache，SporeEntityCache 会在定时扫描时通过 isBuilder() 自动发现
    }

    public static boolean isFungalEntity(LivingEntity entity) {
        if (entity == null) return false;
        return SporeEntityHelper.isSporeEntity(entity);
    }

    private static boolean isBossOrOrganoid(LivingEntity entity) {
        ResourceLocation id = SporeEntityHelper.getEntityId(entity);
        if (id == null) return false;
        String name = id.toString();
        return SporeEntityHelper.bossPoolSet().contains(name) ||
                SporeEntityHelper.organoidSet().contains(name);
    }

    // ========== 吞噬逻辑 ==========

    private static void consumeEntity(LivingEntity builder, LivingEntity target, long currentTick) {
        // ---- 不能吞噬 Builder ----
        if (isBuilder(target)) return;

        long cooldownEnd = getCooldown(builder);
        if (currentTick < cooldownEnd) return;

        if (isBossOrOrganoid(target)) return;

        int level = SporeEntityHelper.getInfectedLevel(target);
        int gainedPoints;
        switch (level) {
            case 0: gainedPoints = getSmallPoints(); break;
            case 1: gainedPoints = getMediumPoints(); break;
            case 2: gainedPoints = getLargePoints(); break;
            default: return;
        }

        int currentPoints = getPoints(builder);
        int maxPoints = getMaxPoints();
        int newPoints = Math.min(currentPoints + gainedPoints, maxPoints);
        setPoints(builder, newPoints);

        target.discard();

        SporeSRP.LOGGER.debug("Builder {} consumed {} (level {}), points: {} → {}",
                builder.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()),
                level, currentPoints, newPoints);

        if (newPoints >= maxPoints) {
            triggerStructure(builder);
        }
    }

    // ========== 事件监听 ==========

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        // ★ 不需要手动加入缓存，SporeEntityCache 会自动发现
        // 但如果是 Builder，初始化其冷却状态（如果需要的话）
        // 注意：builderCallCooldown 是运行时状态，不需要在这里初始化
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        if (isBuilder(entity)) {
            // ★ 从本地状态缓存中移除
            builderCallCooldown.remove(entity.getUUID());
            SporeSRP.LOGGER.debug("Builder {} removed from state cache", entity.getUUID());
        }
        // ★ 注意：存在性缓存由 SporeEntityCache 的定时清理负责
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {



    if (!Config.COMMON_SPEC.isLoaded()) return;long currentTick = event.getServer().getTickCount();
        if (currentTick % 10 != 0) return;

        double collisionRadius = getCollisionRadius();
        int callInterval = getCallInterval();
        int callRadius = getCallRadius();
        // 谓词内每个候选实体都会读两次配置列表，这里每 tick 只取一次（内容完全相同）
        Set<String> bossPool = SporeEntityHelper.bossPoolSet();
        Set<String> organoids = SporeEntityHelper.organoidSet();

        for (ServerLevel level : event.getServer().getAllLevels()) {
            // ★ 使用统一缓存获取所有 Builder
            List<LivingEntity> builders = SporeEntityCache.getAliveBuilders(level);
            if (builders.isEmpty()) continue;

            for (LivingEntity builder : builders) {
                // 验证 Builder 是否有效
                if (builder == null || !builder.isAlive()) continue;

                ResourceLocation id = SporeEntityHelper.getEntityId(builder);
                if (!GASTGABER_ID.equals(id)) continue;

                // ---- 1. 碰撞吞噬检测 ----
                AABB collisionBox = builder.getBoundingBox().inflate(collisionRadius);
                double radiusSq = collisionRadius * collisionRadius;
                List<LivingEntity> nearbyTargets = level.getEntitiesOfClass(LivingEntity.class, collisionBox,
                        e -> {
                            if (e == builder || !e.isAlive()) return false;
                            if (!SporeEntityHelper.isSporeEntity(e)) return false;
                            ResourceLocation targetId = SporeEntityHelper.getEntityId(e);
                            if (targetId != null) {
                                String targetName = targetId.toString();
                                if (bossPool.contains(targetName) || organoids.contains(targetName)) return false;
                            }
                            return e.distanceToSqr(builder) <= radiusSq;
                        });

                for (LivingEntity target : nearbyTargets) {
                    consumeEntity(builder, target, currentTick);
                    if (!builder.isAlive()) break;
                }

                // ---- 2. 呼叫逻辑 ----
                long cooldownEnd = getCooldown(builder);
                if (currentTick < cooldownEnd) continue;

                UUID uuid = builder.getUUID();
                Long lastCallTick = builderCallCooldown.get(uuid);
                if (lastCallTick == null || currentTick - lastCallTick >= callInterval) {
                    callNearbyFungal(builder, level, callRadius);
                    builderCallCooldown.put(uuid, currentTick);
                }
            }
        }
    }

    // ========== 呼叫逻辑 ==========

    private void callNearbyFungal(LivingEntity builder, ServerLevel level, int radius) {
        BlockPos center = builder.blockPosition();
        AABB callBox = new AABB(center).inflate(radius);

        Set<String> bossPool = SporeEntityHelper.bossPoolSet();
        Set<String> organoids = SporeEntityHelper.organoidSet();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, callBox,
                e -> {
                    if (e == builder || !e.isAlive()) return false;
                    if (!SporeEntityHelper.isSporeEntity(e)) return false;
                    ResourceLocation targetId = SporeEntityHelper.getEntityId(e);
                    if (targetId != null) {
                        String targetName = targetId.toString();
                        if (bossPool.contains(targetName) || organoids.contains(targetName)) return false;
                    }
                    if (!(e instanceof Mob mob)) return false;
                    return mob.getNavigation().isDone();
                });

        if (targets.isEmpty()) return;

        for (LivingEntity target : targets) {
            if (target instanceof Mob mob) {
                mob.getNavigation().moveTo(
                        builder.getX(),
                        builder.getY(),
                        builder.getZ(),
                        1.2
                );
            }
        }
    }

    // ========== 结构生成（从配置列表随机选择） ==========

    private static void triggerStructure(LivingEntity builder) {
        ServerLevel level = (ServerLevel) builder.level();
        BlockPos pos = builder.blockPosition();
        long currentTick = level.getServer().getTickCount();

        // 重置点数
        setPoints(builder, 0);
        // 设置冷却
        setCooldown(builder, currentTick);

        // ---- 从配置中读取结构列表 ----
        List<? extends String> structureNames = Config.COMMON.gastgaberBuilderStructures.get();
        if (structureNames == null || structureNames.isEmpty()) {
            SporeSRP.LOGGER.warn("No builder structures configured, cannot generate structure");
            return;
        }

        // ---- 随机选择一个结构 ----
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
        String selectedName = structureNames.get(random.nextInt(structureNames.size()));
        ResourceLocation structureLocation = ResourceLocation.parse("sporesrp" + ":" + selectedName);

        // ---- 加载结构模板 ----
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.get(structureLocation).orElse(null);

        if (template == null) {
            SporeSRP.LOGGER.error("Failed to load structure: {}, check if the file exists at assets/sporesrp/structures/{}.nbt",
                    structureLocation, selectedName);
            return;
        }

        // ---- 设置放置参数 ----
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        // ---- 计算放置位置（Builder 脚下） ----
        BlockPos placePos = pos.below();

        // ---- 放置结构 ----
        template.placeInWorld(level, placePos, placePos, settings, random, 2);

        SporeSRP.LOGGER.info("Builder {} placed structure: {} at {}", builder.getUUID(), selectedName, placePos);
    }

    // ========== 调试 ==========

    public static void resetBuilder(LivingEntity entity) {
        if (entity == null) return;
        CompoundTag persistentData = entity.getPersistentData();
        persistentData.remove(BUILDER_TAG);
        persistentData.remove(POINTS_TAG);
        persistentData.remove(COOLDOWN_TAG);
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);
        builderCallCooldown.remove(entity.getUUID());

        // ★ 不再需要手动从 builderCache 移除，SporeEntityCache 会在下次扫描时自动处理
        SporeSRP.LOGGER.info("Builder {} reset", entity.getUUID());
    }

    public static int getBuilderCount(ServerLevel level) {
        if (level == null) return 0;
        // ★ 使用统一缓存获取数量
        return SporeEntityCache.getAliveBuilderCount(level);
    }

    public static void clearCache() {
        builderCallCooldown.clear();
        SporeSRP.LOGGER.info("Builder state cache cleared");
    }
}