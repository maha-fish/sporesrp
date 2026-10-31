package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.SpawnPositionHelper;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 完整心智技能系统
 * 使用独立配置，与普通 Proto 分开
 */
public class FullHivemindSkillsHandler {

    /** 固定的 spore 实体 ID，避免每次生成 / 判定时重复构造 ResourceLocation。 */
    private static final ResourceLocation DELUSIONER_ID = new ResourceLocation("spore", "delusioner");
    private static final ResourceLocation RECONSTRUCTOR_ID = new ResourceLocation("spore", "reconstructor");
    private static final ResourceLocation VERVA_ID = new ResourceLocation("spore", "verva");

    // ★ 保留本地缓存的 Set（不在 SporeEntityCache 管理范围内）
    // 按维度存储，避免跨维度误删
    private static final Map<ResourceKey<Level>, Set<UUID>> DELUSIONER_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<UUID>> RECONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private static Set<UUID> getDelusionerSet(ServerLevel level) {
        return DELUSIONER_CACHE.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
    }

    private static Set<UUID> getReconstructorSet(ServerLevel level) {
        return RECONSTRUCTOR_CACHE.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
    }

    // ★ 技能数据 Map（按全局 UUID，因为完整心智全局唯一，暂不按维度）
    public static final Map<UUID, FullHivemindSkillData> skillDataMap = new ConcurrentHashMap<>();

    private int tickCounter = 0;
    private static final int SKILL_INTERVAL = 4; // 每 4 tick 执行一次

    private static class FullHivemindSkillData {
        final UUID hivemindUuid;
        long skill1Cooldown;
        long skill2Cooldown;
        long skill3Cooldown;
        long skill4Cooldown;
        long skill5Cooldown;
        long lastCheckTime;

        FullHivemindSkillData(UUID uuid, long currentTick) {
            this.hivemindUuid = uuid;
            this.skill1Cooldown = currentTick;
            this.skill2Cooldown = currentTick;
            this.skill3Cooldown = currentTick;
            this.skill4Cooldown = currentTick;
            this.skill5Cooldown = currentTick;
            this.lastCheckTime = currentTick;
        }
    }

    // ========== 配置读取 ==========
    private static int getSkill1Cooldown() { return Config.COMMON.fullHivemindSkill1Cooldown.get(); }
    private static int getSkill1MoveMinDist() { return Config.COMMON.fullHivemindSkill1MoveMinDist.get(); }
    private static int getSkill1MoveMaxDist() { return Config.COMMON.fullHivemindSkill1MoveMaxDist.get(); }
    private static int getSkill2CheckInterval() { return Config.COMMON.fullHivemindSkill2CheckInterval.get(); }
    private static int getSkill2Cooldown() { return Config.COMMON.fullHivemindSkill2Cooldown.get(); }
    private static int getSkill2SpawnRange() { return Config.COMMON.fullHivemindSkill2SpawnRange.get(); }
    private static int getLargePoolThreshold() { return Config.COMMON.fullHivemindSkillLargePoolThreshold.get(); }
    private static int getSkill3Cooldown() { return Config.COMMON.fullHivemindSkill3Cooldown.get(); }
    private static int getSkill3SpawnCountMin() { return Config.COMMON.fullHivemindSkill3SpawnCountMin.get(); }
    private static int getSkill3SpawnCountMax() { return Config.COMMON.fullHivemindSkill3SpawnCountMax.get(); }
    private static int getSkill4Cooldown() { return Config.COMMON.fullHivemindSkill4Cooldown.get(); }
    private static int getSkill4SpawnRange() { return Config.COMMON.fullHivemindSkill4SpawnRange.get(); }
    private static int getSkill4CheckRange() { return Config.COMMON.fullHivemindSkill4CheckRange.get(); }
    private static int getSkill5Cooldown() { return Config.COMMON.fullHivemindSkill5Cooldown.get(); }
    private static int getSkill5SpawnRange() { return Config.COMMON.fullHivemindSkill5SpawnRange.get(); }
    private static int getSkill5BossCheckRange() { return Config.COMMON.fullHivemindSkill5BossCheckRange.get(); }
    private static int getSkill5SpawnCount() { return Config.COMMON.fullHivemindSkill5SpawnCount.get(); }

    private static final long CLEANUP_INTERVAL = 6000;

    // ========== 辅助方法 ==========
    private static boolean isFullHivemind(LivingEntity entity) {
        if (entity == null) return false;
        // 缓存版注册名判定，等价于 getKey(...).toString().equals("spore:proto")
        return SporeEntityHelper.isProto(entity) && FullHivemindHandler.isFullHivemind(entity);
    }

    private static boolean isLargePoolEntity(LivingEntity entity) {
        // isLargePool 内部：注册名为空 -> false；proto -> false；其余查 largePoolSet()
        return SporeEntityHelper.isLargePool(entity);
    }

    private static boolean isDelusioner(LivingEntity entity) {
        return DELUSIONER_ID.equals(SporeEntityHelper.getEntityId(entity));
    }

    private static boolean isBoss(LivingEntity entity) {
        // 缓存版 Boss 池 Set 查询，成员判定结果与 List.contains 一致
        return SporeEntityHelper.isBoss(entity);
    }

    private static boolean isReconstructor(LivingEntity entity) {
        return RECONSTRUCTOR_ID.equals(SporeEntityHelper.getEntityId(entity));
    }

    private static LivingEntity getTarget(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            return mob.getTarget();
        }
        return null;
    }

    // ========== 缓存维护（仅本地缓存） ==========
    private void updateCacheOnJoin(LivingEntity entity, ServerLevel level) {
        // ★ largePool 和 boss 不再本地维护，由 SporeEntityCache 统一管理
        if (isDelusioner(entity)) {
            getDelusionerSet(level).add(entity.getUUID());
        }
        if (isReconstructor(entity)) {
            getReconstructorSet(level).add(entity.getUUID());
        }
    }

    private void updateCacheOnDeath(LivingEntity entity, ServerLevel level) {
        if (isDelusioner(entity)) {
            Set<UUID> set = getDelusionerSet(level);
            set.remove(entity.getUUID());
            if (set.isEmpty()) {
                DELUSIONER_CACHE.remove(level.dimension());
            }
        }
        if (isReconstructor(entity)) {
            Set<UUID> set = getReconstructorSet(level);
            set.remove(entity.getUUID());
            if (set.isEmpty()) {
                RECONSTRUCTOR_CACHE.remove(level.dimension());
            }
        }
    }

    private void cleanStaleCache(ServerLevel level) {
        // ★ 遍历所有维度，只有全部找不到才删除
        Iterator<Map.Entry<UUID, FullHivemindSkillData>> it = skillDataMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, FullHivemindSkillData> entry = it.next();
            UUID uuid = entry.getKey();
            boolean found = false;
            for (ServerLevel lv : level.getServer().getAllLevels()) {
                LivingEntity e = (LivingEntity) lv.getEntity(uuid);
                if (e != null && e.isAlive()) { found = true; break; }
            }
            if (!found) it.remove();
        }

        // ★ 只清理当前维度的缓存（因为它们是按维度存储的）
        Set<UUID> delusionerSet = DELUSIONER_CACHE.get(level.dimension());
        if (delusionerSet != null) {
            cleanCacheSet(delusionerSet, level);
            if (delusionerSet.isEmpty()) DELUSIONER_CACHE.remove(level.dimension());
        }
        Set<UUID> reconstructorSet = RECONSTRUCTOR_CACHE.get(level.dimension());
        if (reconstructorSet != null) {
            cleanCacheSet(reconstructorSet, level);
            if (reconstructorSet.isEmpty()) RECONSTRUCTOR_CACHE.remove(level.dimension());
        }
    }

    private void cleanCacheSet(Set<UUID> cache, ServerLevel level) {
        Iterator<UUID> it = cache.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            LivingEntity entity = (LivingEntity) level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                it.remove();
            }
        }
        // 如果 Set 为空，可选择移除维度条目（但由调用方决定）
    }

    // ========== 实体生成 ==========
    // ... 以下 spawnEntity, spawnVerva, spawnDelusioner, spawnReconstructor 保持不变 ...

    private static void spawnEntity(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (type == null) return;
        LivingEntity entity = (LivingEntity) type.create(level);
        if (entity == null) return;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    private static void spawnVerva(ServerLevel level, BlockPos pos) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(VERVA_ID);
        if (type == null) return;
        LivingEntity entity = (LivingEntity) type.create(level);
        if (entity == null) return;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    private static void spawnDelusioner(ServerLevel level, BlockPos pos) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(DELUSIONER_ID);
        if (type == null) return;
        LivingEntity entity = (LivingEntity) type.create(level);
        if (entity == null) return;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    private static void spawnReconstructor(ServerLevel level, BlockPos pos) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(RECONSTRUCTOR_ID);
        if (type == null) return;
        LivingEntity entity = (LivingEntity) type.create(level);
        if (entity == null) return;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    private static BlockPos getRandomPosAround(BlockPos center, int minDist, int maxDist) {
        int dx = (int) (Math.random() * (maxDist - minDist) + minDist);
        int dz = (int) (Math.random() * (maxDist - minDist) + minDist);
        if (Math.random() < 0.5) dx = -dx;
        if (Math.random() < 0.5) dz = -dz;
        return new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
    }

    // ★ 正确版本：返回固体方块顶部高度
    private static int getSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY() + 1;
    }

    // ★ ==================== countInRange / existsInRange 重载 ====================

    // 重载1：接受 Set<UUID>（用于 delusionerCache / reconstructorCache）
    private static long countInRange(ServerLevel level, Set<UUID> cache, BlockPos center, int range) {
        int radiusSq = range * range;
        long count = 0;
        Iterator<UUID> it = cache.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            LivingEntity entity = (LivingEntity) level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            if (entity.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    private static boolean existsInRange(ServerLevel level, Set<UUID> cache, BlockPos center, int range) {
        int radiusSq = range * range;
        Iterator<UUID> it = cache.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            LivingEntity entity = (LivingEntity) level.getEntity(uuid);
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            if (entity.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    // ★ 重载2：接受 List<LivingEntity>（用于 SporeEntityCache 返回的列表）
    private static long countInRange(ServerLevel level, List<LivingEntity> entities, BlockPos center, int range) {
        int radiusSq = range * range;
        long count = 0;
        for (LivingEntity entity : entities) {
            if (entity != null && entity.isAlive()) {
                if (entity.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= radiusSq) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean existsInRange(ServerLevel level, List<LivingEntity> entities, BlockPos center, int range) {
        int radiusSq = range * range;
        for (LivingEntity entity : entities) {
            if (entity != null && entity.isAlive()) {
                if (entity.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= radiusSq) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========== 技能实现 ==========

    // applySkill1 保持不变...

    private static void applySkill1(ServerLevel level, LivingEntity hivemind, LivingEntity target, FullHivemindSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill1Cooldown < getSkill1Cooldown()) return;

        BlockPos targetPos = target.blockPosition();
        AABB range = new AABB(targetPos).inflate(30);
        List<LivingEntity> mounds = level.getEntitiesOfClass(LivingEntity.class, range, e -> {
            // 缓存版判定，替代每个候选实体一次 getKey + toString
            if (!SporeEntityHelper.isMound(e) || e == hivemind) return false;
            if (e.hasCustomName()) {
                Component name = e.getCustomName();
                if (name != null && name.getString().contains("watcher")) {
                    return false;
                }
            }
            return true;
        });
        if (mounds.isEmpty()) return;

        // 移动距离配置在循环外读一次，判定/取值结果不变
        int moveMinDist = getSkill1MoveMinDist();
        int moveMaxDist = getSkill1MoveMaxDist();
        for (LivingEntity mound : mounds) {
            BlockPos newPos = getRandomPosAround(targetPos, moveMinDist, moveMaxDist);
            int y = getSurfaceHeight(level, newPos.getX(), newPos.getZ());
            if (y > level.getMinBuildHeight()) {
                mound.setPos(newPos.getX() + 0.5, y, newPos.getZ() + 0.5);
            }
        }
        data.skill1Cooldown = currentTick;
        SporeSRP.LOGGER.debug("Full Hivemind used skill1: moved {} mounds", mounds.size());
    }

    // applySkill2 保持不变（已经使用统一缓存）
    private static void applySkill2(ServerLevel level, LivingEntity hivemind, FullHivemindSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.lastCheckTime < getSkill2CheckInterval()) return;
        data.lastCheckTime = currentTick;

        BlockPos pos = hivemind.blockPosition();

        // ★ 使用统一缓存
        long largeCount = countInRange(level, SporeEntityCache.getAliveLargePools(level), pos, 160);
        if (largeCount >= getLargePoolThreshold()) return;

        if (currentTick - data.skill2Cooldown < getSkill2Cooldown()) return;

        int minDist = 10;
        int maxDist = getSkill2SpawnRange();
        BlockPos spawnPos = SpawnPositionHelper.findSpawnPos(level, hivemind, minDist, maxDist);
        if (spawnPos != null) {
            summonEntityByCommand(level, "spore:verva", spawnPos);
            data.skill2Cooldown = currentTick;
            SporeSRP.LOGGER.debug("Full Hivemind used skill2: spawned verva at {}", spawnPos);
        } else {
            SporeSRP.LOGGER.debug("Full Hivemind skill2 failed: no valid spawn position");
        }
    }

    // applySkill3 保持不变（已经使用统一缓存）
    private static void applySkill3(ServerLevel level, LivingEntity hivemind, LivingEntity target, FullHivemindSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill3Cooldown < getSkill3Cooldown()) return;

        BlockPos targetPos = target.blockPosition();

        // ★ 使用统一缓存
        boolean hasLarge = existsInRange(level, SporeEntityCache.getAliveLargePools(level), targetPos, 32);
        if (hasLarge) return;

        long largeCount = countInRange(level, SporeEntityCache.getAliveLargePools(level), hivemind.blockPosition(), 160);
        if (largeCount >= getLargePoolThreshold()) return;

        int count = getSkill3SpawnCountMin() + (int) (Math.random() * (getSkill3SpawnCountMax() - getSkill3SpawnCountMin() + 1));

        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = SpawnPositionHelper.findSpawnPos(level, hivemind, 10, 32);
            if (spawnPos != null) {
                summonEntityByCommand(level, "spore:verva", spawnPos);
            }
        }

        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = SpawnPositionHelper.findSpawnPos(level, target, 10, 32);
            if (spawnPos != null) {
                summonEntityByCommand(level, "spore:verva", spawnPos);
            }
        }

        data.skill3Cooldown = currentTick;
        SporeSRP.LOGGER.debug("Full Hivemind used skill3: attempted to spawn vervas");
    }

    // ★ applySkill4：修正 delusioner 缓存为按维度
    private static void applySkill4(ServerLevel level, LivingEntity hivemind, LivingEntity target, FullHivemindSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill4Cooldown < getSkill4Cooldown()) return;

        BlockPos targetPos = target.blockPosition();

        // ★ delusionerCache 按维度获取
        boolean hasDelusioner = existsInRange(level, getDelusionerSet(level), targetPos, getSkill4CheckRange());
        if (hasDelusioner) return;

        // ★ 使用统一缓存
        long largeCount = countInRange(level, SporeEntityCache.getAliveLargePools(level), hivemind.blockPosition(), 160);
        if (largeCount < getLargePoolThreshold()) return;

        BlockPos spawnPos = SpawnPositionHelper.findSpawnPos(level, target, 5, getSkill4SpawnRange());
        if (spawnPos != null) {
            spawnDelusioner(level, spawnPos);
            data.skill4Cooldown = currentTick;
            SporeSRP.LOGGER.debug("Full Hivemind used skill4: spawned delusioner at {}", spawnPos);
        } else {
            SporeSRP.LOGGER.debug("Full Hivemind skill4 failed: no valid spawn position");
        }
    }

    // ★ applySkill5：修正 reconstructor 缓存为按维度
    private static void applySkill5(ServerLevel level, LivingEntity hivemind, FullHivemindSkillData data) {
        if (FullHivemindHandler.isGroundMining(hivemind.getUUID())) {
            return;
        }

        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill5Cooldown < getSkill5Cooldown()) return;

        BlockPos center = hivemind.blockPosition();

        // ★ 使用统一缓存
        boolean hasBoss = existsInRange(level, SporeEntityCache.getAliveBosses(level), center, getSkill5BossCheckRange());
        if (hasBoss) return;

        // ★ reconstructorCache 按维度获取
        boolean hasReconstructor = existsInRange(level, getReconstructorSet(level), center, 16);
        if (hasReconstructor) return;

        int count = getSkill5SpawnCount();
        int maxRadius = getSkill5SpawnRange();

        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = scanForSurface(level, center, maxRadius);
            if (spawnPos != null) {
                spawnReconstructor(level, spawnPos);
            } else {
                SporeSRP.LOGGER.debug("Full Hivemind skill5: no valid surface within {} blocks", maxRadius);
                break;
            }
        }

        data.skill5Cooldown = currentTick;
        SporeSRP.LOGGER.debug("Full Hivemind used skill5: attempted to spawn reconstructors");
    }

    // ========== 事件监听 ==========

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // ★ 更新本地缓存（delusioner / reconstructor），传入 level
        updateCacheOnJoin(entity, level);

        if (isFullHivemind(entity)) {
            UUID uuid = entity.getUUID();
            long currentTick = entity.level().getServer().getTickCount();
            skillDataMap.put(uuid, new FullHivemindSkillData(uuid, currentTick));
            SporeSRP.LOGGER.debug("Full Hivemind {} added to skill system", uuid);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;

        // ★ 更新本地缓存，传入 level
        updateCacheOnDeath(entity, level);

        if (isFullHivemind(entity)) {
            skillDataMap.remove(entity.getUUID());
            SporeSRP.LOGGER.debug("Full Hivemind {} removed from skill system", entity.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < SKILL_INTERVAL) return;
        tickCounter = 0;

        long currentTick = event.getServer().getTickCount();

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (FullHivemindSkillData data : skillDataMap.values()) {
                LivingEntity hivemind = (LivingEntity) level.getEntity(data.hivemindUuid);
                if (hivemind == null || !hivemind.isAlive()) continue;

                LivingEntity target = getTarget(hivemind);

                applySkill2(level, hivemind, data);
                if (target != null) {
                    applySkill1(level, hivemind, target, data);
                    applySkill3(level, hivemind, target, data);
                    applySkill4(level, hivemind, target, data);
                }
                applySkill5(level, hivemind, data);
            }

            // 定期清理过期缓存
            if (currentTick % CLEANUP_INTERVAL == 0) {
                cleanStaleCache(level);
            }
        }
    }

    // ========== 工具方法 ==========

    private static void summonEntityByCommand(ServerLevel level, String entityId, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        String command = String.format("summon %s %.2f %.2f %.2f", entityId, x, y, z);
        var source = level.getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static BlockPos scanForSurface(ServerLevel level, BlockPos center, int maxRadius) {
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) {
                            continue;
                        }
                        BlockPos checkPos = center.offset(dx, dy, dz);
                        if (isValidSpawnSurface(level, checkPos)) {
                            return checkPos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isValidSpawnSurface(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) return false;
        if (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced()) return false;
        return level.getBlockState(pos.below()).isSolid();
    }

    // ========== 调试 ==========

    public static int getSkillDataMapSize() {
        return skillDataMap.size();
    }
}