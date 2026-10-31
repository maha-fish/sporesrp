package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
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
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.maha_fish.sporesrp.util.SporeEntityHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProtoSkillsHandler {

    /** 固定的 spore 实体 ID，避免每次生成 / 判定时重复构造 ResourceLocation。 */
    private static final ResourceLocation DELUSIONER_ID = new ResourceLocation("spore", "delusioner");
    private static final ResourceLocation RECONSTRUCTOR_ID = new ResourceLocation("spore", "reconstructor");

    // ==================== Proto 技能数据 ====================
    private static class ProtoSkillData {
        final UUID protoUuid;
        long skill1Cooldown;
        long skill2Cooldown;
        long skill3Cooldown;
        long skill4Cooldown;
        long skill5Cooldown;
        long lastCheckTime;

        ProtoSkillData(UUID protoUuid, long currentTick) {
            this.protoUuid = protoUuid;
            this.skill1Cooldown = currentTick;
            this.skill2Cooldown = currentTick;
            this.skill3Cooldown = currentTick;
            this.skill4Cooldown = currentTick;
            this.skill5Cooldown = currentTick;
            this.lastCheckTime = currentTick;
        }
    }

    public static final Map<UUID, ProtoSkillData> skillDataMap = new ConcurrentHashMap<>();

    // ★ 新增：delusioner 和 reconstructor 本地缓存（保留本地维护）
    private static final Map<ResourceKey<Level>, Set<UUID>> DELUSIONER_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<UUID>> RECONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private static Set<UUID> getDelusionerSet(ServerLevel level) {
        return DELUSIONER_CACHE.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
    }

    private static Set<UUID> getReconstructorSet(ServerLevel level) {
        return RECONSTRUCTOR_CACHE.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet());
    }

    // ==================== 配置读取 ====================
    private static boolean isEnabled() {
        return Config.COMMON.protoSkillsEnabled.get();
    }

    // 技能1
    private static int getSkill1CooldownTicks() {
        return Config.COMMON.protoSkill1CooldownTicks.get();
    }

    private static int getSkill1MoveMinDist() {
        return Config.COMMON.protoSkill1MoveMinDist.get();
    }

    private static int getSkill1MoveMaxDist() {
        return Config.COMMON.protoSkill1MoveMaxDist.get();
    }

    // 技能2
    private static int getSkill2CheckIntervalTicks() {
        return Config.COMMON.protoSkill2CheckIntervalTicks.get();
    }

    private static int getSkill2CooldownTicks() {
        return Config.COMMON.protoSkill2CooldownTicks.get();
    }

    private static int getSkill2SpawnRange() {
        return Config.COMMON.protoSkill2SpawnRange.get();
    }

    private static int getLargePoolThreshold() {
        return Config.COMMON.protoSkillLargePoolThreshold.get();
    }

    // 技能3
    private static int getSkill3CooldownTicks() {
        return Config.COMMON.protoSkill3CooldownTicks.get();
    }

    private static int getSkill3SpawnCountMin() {
        return Config.COMMON.protoSkill3SpawnCountMin.get();
    }

    private static int getSkill3SpawnCountMax() {
        return Config.COMMON.protoSkill3SpawnCountMax.get();
    }

    // 技能4
    private static int getSkill4CooldownTicks() {
        return Config.COMMON.protoSkill4CooldownTicks.get();
    }

    private static int getSkill4SpawnRange() {
        return Config.COMMON.protoSkill4SpawnRange.get();
    }

    private static int getSkill4CheckRange() {
        return Config.COMMON.protoSkill4CheckRange.get();
    }

    // 技能5
    private static int getSkill5CooldownTicks() {
        return Config.COMMON.protoSkill5CooldownTicks.get();
    }

    private static int getSkill5SpawnRange() {
        return Config.COMMON.protoSkill5SpawnRange.get();
    }

    private static int getSkill5BossCheckRange() {
        return Config.COMMON.protoSkill5BossCheckRange.get();
    }

    private static int getSkill5SpawnCount() {
        return Config.COMMON.protoSkill5SpawnCount.get();
    }

    private static final long CLEANUP_INTERVAL = 6000; // 5分钟

    // ==================== 辅助方法 ====================
    private static boolean isProto(LivingEntity entity) {
        // 缓存版注册名判定（结果与 ForgeRegistries 查询 + 字符串比较一致）
        return SporeEntityHelper.isProto(entity);
    }

    private static boolean isMound(LivingEntity entity) {
        return SporeEntityHelper.isMound(entity);
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


    private static LivingEntity getTarget(LivingEntity proto) {
        if (proto instanceof Mob mob) {
            return mob.getTarget();
        }
        return null;
    }

    // ==================== 实体生成（保育体使用命令，其他直接创建） ====================
    private static void spawnEntity(ServerLevel level, BlockPos pos, EntityType<?> type) {
        if (type == null) return;
        LivingEntity entity = (LivingEntity) type.create(level);
        if (entity == null) return;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    // 保育体：使用命令生成
    private static void spawnBiomassLump(ServerLevel level, BlockPos pos) {
        String command = String.format("summon spore:verva %.1f %.1f %.1f",
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        CommandSourceStack source = new CommandSourceStack(
                CommandSource.NULL,
                new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
                Vec2.ZERO,
                level,
                4,
                "",
                Component.literal(""),
                level.getServer(),
                null
        ).withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static void spawnDelusioner(ServerLevel level, BlockPos pos) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(DELUSIONER_ID);
        spawnEntity(level, pos, type);
    }

    private static void spawnReconstructor(ServerLevel level, BlockPos pos) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(RECONSTRUCTOR_ID);
        spawnEntity(level, pos, type);
    }

    // ==================== 辅助方法 ====================
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

    // 统计范围内（半径）的特定缓存中实体数量
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

    // 检查范围内是否存在特定缓存中的实体
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

    // ==================== Set<UUID> 版本重载（用于 delusionerCache / reconstructorCache） ====================

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

    // ==================== 技能实现 ====================

    // 技能1：移动 mound
    private static void applySkill1(ServerLevel level, LivingEntity proto, LivingEntity target, ProtoSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill1Cooldown < getSkill1CooldownTicks()) return;

        BlockPos targetPos = target.blockPosition();
        AABB range = new AABB(targetPos).inflate(15);
        List<LivingEntity> mounds = level.getEntitiesOfClass(LivingEntity.class, range, e -> {
            // 缓存版判定，替代每个候选实体一次 getKey + toString
            if (!SporeEntityHelper.isMound(e) || e == proto) return false;
            // 排除名称包含 "watcher" 的 mound
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
        SporeSRP.LOGGER.debug("Proto used skill1: moved {} mounds", mounds.size());
    }

    // 技能2：生成保育体（当 largepool < 7）
    private static void applySkill2(ServerLevel level, LivingEntity proto, ProtoSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.lastCheckTime < getSkill2CheckIntervalTicks()) return;
        data.lastCheckTime = currentTick;

        BlockPos protoPos = proto.blockPosition();
        int range = getSkill2SpawnRange();
        long largeCount = countInRange(level, SporeEntityCache.getAliveLargePools(level), protoPos, 128);
        if (largeCount >= getLargePoolThreshold()) return;

        if (currentTick - data.skill2Cooldown < getSkill2CooldownTicks()) return;
        BlockPos spawnPos = getRandomPosAround(protoPos, 10, range);
        int y = getSurfaceHeight(level, spawnPos.getX(), spawnPos.getZ());
        if (y > level.getMinBuildHeight()) {
            spawnBiomassLump(level, new BlockPos(spawnPos.getX(), y, spawnPos.getZ()));
            data.skill2Cooldown = currentTick;
            SporeSRP.LOGGER.debug("Proto {} used skill2: summoned biomass lump", proto.getUUID());
        }
    }

    // 技能3：在 proto 和目标附近生成保育体（当 largepool < 7 且目标附近无 largepool）
    private static void applySkill3(ServerLevel level, LivingEntity proto, LivingEntity target, ProtoSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill3Cooldown < getSkill3CooldownTicks()) return;

        BlockPos protoPos = proto.blockPosition();
        BlockPos targetPos = target.blockPosition();
        int range = 32;
        boolean hasLarge = existsInRange(level, SporeEntityCache.getAliveLargePools(level), targetPos, range);
        if (hasLarge) return;

        long largeCount = countInRange(level, SporeEntityCache.getAliveLargePools(level), protoPos, 128);
        if (largeCount >= getLargePoolThreshold()) return;

        int count = getSkill3SpawnCountMin() + (int) (Math.random() * (getSkill3SpawnCountMax() - getSkill3SpawnCountMin() + 1));
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = getRandomPosAround(protoPos, 10, 32);
            int y = getSurfaceHeight(level, spawnPos.getX(), spawnPos.getZ());
            if (y > level.getMinBuildHeight()) {
                spawnBiomassLump(level, new BlockPos(spawnPos.getX(), y, spawnPos.getZ()));
            }
        }
        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = getRandomPosAround(targetPos, 10, 32);
            int y = getSurfaceHeight(level, spawnPos.getX(), spawnPos.getZ());
            if (y > level.getMinBuildHeight()) {
                spawnBiomassLump(level, new BlockPos(spawnPos.getX(), y, spawnPos.getZ()));
            }
        }
        data.skill3Cooldown = currentTick;
        SporeSRP.LOGGER.debug("Proto {} used skill3: spawned {} biomass lumps around proto and target", proto.getUUID(), count * 2);
    }

    // 技能4：生成 delusioner（当 largepool >= 7 且目标周围64格无 delusioner）
    private static void applySkill4(ServerLevel level, LivingEntity proto, LivingEntity target, ProtoSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill4Cooldown < getSkill4CooldownTicks()) return;

        BlockPos targetPos = target.blockPosition();
        boolean hasDelusioner = existsInRange(level, getDelusionerSet(level), targetPos, getSkill4CheckRange());
        if (hasDelusioner) return;

        long largeCount = countInRange(level, SporeEntityCache.getAliveLargePools(level), proto.blockPosition(), 128);
        if (largeCount < getLargePoolThreshold()) return;

        BlockPos spawnPos = getRandomPosAround(targetPos, 5, getSkill4SpawnRange());
        int y = getSurfaceHeight(level, spawnPos.getX(), spawnPos.getZ());
        if (y > level.getMinBuildHeight()) {
            spawnDelusioner(level, new BlockPos(spawnPos.getX(), y, spawnPos.getZ()));
            data.skill4Cooldown = currentTick;
            SporeSRP.LOGGER.debug("Proto {} used skill4: spawned delusioner", proto.getUUID());
        }
    }

    // 技能5：生成 reconstructor（当周围128格内无 boss，且16格内无 reconstructor）
    private static void applySkill5(ServerLevel level, LivingEntity proto, ProtoSkillData data) {
        long currentTick = level.getServer().getTickCount();
        if (currentTick - data.skill5Cooldown < getSkill5CooldownTicks()) return;

        BlockPos center = proto.blockPosition();
        boolean hasBoss = existsInRange(level, SporeEntityCache.getAliveBosses(level), center, getSkill5BossCheckRange());
        if (hasBoss) return;

        boolean hasReconstructor = existsInRange(level, getReconstructorSet(level), center, 16);
        if (hasReconstructor) return;

        int count = getSkill5SpawnCount();
        int maxRadius = getSkill5SpawnRange(); // 复用配置项

        for (int i = 0; i < count; i++) {
            BlockPos spawnPos = scanForSurface(level, center, maxRadius);
            if (spawnPos != null) {
                spawnReconstructor(level, spawnPos);
            } else {
                SporeSRP.LOGGER.debug("Proto skill5: no valid surface within {} blocks", maxRadius);
                break;
            }
        }

        data.skill5Cooldown = currentTick;
        SporeSRP.LOGGER.debug("Proto used skill5: attempted to spawn reconstructors");
    }

    // ==================== 事件监听 ====================
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!isEnabled()) return;
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();
        if (isProto(entity)) {
            UUID uuid = entity.getUUID();
            long currentTick = entity.level().getServer().getTickCount();
            skillDataMap.put(uuid, new ProtoSkillData(uuid, currentTick));
            SporeSRP.LOGGER.debug("Proto {} added to skill system", uuid);
        } else if (isDelusioner(entity)) {
            getDelusionerSet((ServerLevel) event.getLevel()).add(entity.getUUID());
        } else if (isReconstructor(entity)) {
            getReconstructorSet((ServerLevel) event.getLevel()).add(entity.getUUID());
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!isEnabled()) return;
        LivingEntity entity = event.getEntity();
        if (isProto(entity)) {
            skillDataMap.remove(entity.getUUID());
            SporeSRP.LOGGER.debug("Proto {} removed from skill system", entity.getUUID());
        } else if (isDelusioner(entity)) {
            ServerLevel level = (ServerLevel) entity.level();
            Set<UUID> set = getDelusionerSet(level);
            set.remove(entity.getUUID());
            if (set.isEmpty()) {
                DELUSIONER_CACHE.remove(level.dimension());
            }
        } else if (isReconstructor(entity)) {
            ServerLevel level = (ServerLevel) entity.level();
            Set<UUID> set = getReconstructorSet(level);
            set.remove(entity.getUUID());
            if (set.isEmpty()) {
                RECONSTRUCTOR_CACHE.remove(level.dimension());
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;

        // 每 2 tick 执行一次技能逻辑
        if (event.getServer().getTickCount() % 2 != 0) return;

        long currentTick = event.getServer().getTickCount();

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ProtoSkillData data : skillDataMap.values()) {
                LivingEntity proto = (LivingEntity) level.getEntity(data.protoUuid);
                if (proto == null || !proto.isAlive()) continue;

                LivingEntity target = getTarget(proto);
                applySkill2(level, proto, data);
                if (target != null) {
                    applySkill1(level, proto, target, data);
                    applySkill3(level, proto, target, data);
                    applySkill4(level, proto, target, data);
                }
                applySkill5(level, proto, data);
            }
            if (currentTick % CLEANUP_INTERVAL == 0) {
                // 只清理 delusioner 和 reconstructor 本地缓存
                cleanLocalCache(level);
            }
        }
    }
    public static BlockPos scanForSurface(ServerLevel level, BlockPos center, int maxRadius) {
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

    public static boolean isValidSpawnSurface(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) return false;
        if (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced()) return false;
        return level.getBlockState(pos.below()).isSolid();
    }
    private void cleanLocalCache(ServerLevel level) {
        Iterator<Map.Entry<UUID, ProtoSkillData>> skillIt = skillDataMap.entrySet().iterator();
        while (skillIt.hasNext()) {
            Map.Entry<UUID, ProtoSkillData> entry = skillIt.next();
            UUID uuid = entry.getKey();
            boolean found = false;
            for (ServerLevel lv : level.getServer().getAllLevels()) {
                LivingEntity e = (LivingEntity) lv.getEntity(uuid);
                if (e != null && e.isAlive()) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                skillIt.remove();
            }
        }

        // ★ 只清理当前维度的缓存，跨维度不误删
        Set<UUID> delusionerSet = DELUSIONER_CACHE.get(level.dimension());
        if (delusionerSet != null) {
            Iterator<UUID> it = delusionerSet.iterator();
            while (it.hasNext()) {
                LivingEntity e = (LivingEntity) level.getEntity(it.next());
                if (e == null || !e.isAlive()) it.remove();
            }
            if (delusionerSet.isEmpty()) DELUSIONER_CACHE.remove(level.dimension());
        }
        Set<UUID> reconstructorSet = RECONSTRUCTOR_CACHE.get(level.dimension());
        if (reconstructorSet != null) {
            Iterator<UUID> it = reconstructorSet.iterator();
            while (it.hasNext()) {
                LivingEntity e = (LivingEntity) level.getEntity(it.next());
                if (e == null || !e.isAlive()) it.remove();
            }
            if (reconstructorSet.isEmpty()) RECONSTRUCTOR_CACHE.remove(level.dimension());
        }
    }
}