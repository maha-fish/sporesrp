package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SpawnPositionHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SupportSystemHandler {
    private static final Random RANDOM = new Random();
    private static final ConcurrentHashMap<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final List<String> MINDS = List.of("spore:proto", "spore:hivetumor");
    private static final long CLEANUP_INTERVAL = 6000; // 5 分钟
    private int cleanupTimer = 0;

    // ==================== 过滤后的召唤池缓存 ====================
    // 原实现每次触发支援都用 stream + List.contains 重新过滤一遍配置池；
    // 这里缓存过滤结果（以相关配置列表实例作为失效依据），候选顺序保持完全不变，
    // 因此 RANDOM.nextInt(pool.size()) 取到的实体与原实现一致。
    private static final PoolCache SMALL_POOL = new PoolCache();
    private static final PoolCache MEDIUM_POOL = new PoolCache();
    private static final PoolCache LARGE_POOL = new PoolCache();

    private static final class PoolCache {
        private List<? extends String> source;
        private List<? extends String> organoids;
        private List<? extends String> bosses;
        private List<String> value; // null = 尚未构建

        List<String> of(List<? extends String> src) {
            List<? extends String> org = SporeEntityHelper.getOrganoids();
            List<? extends String> boss = SporeEntityHelper.getBossPool();
            // 先比内容再决定是否重建：配置项可能每次 get() 都返回新实例。
            if (this.value != null
                    && (src == this.source || (src != null && src.equals(this.source)))
                    && (org == this.organoids || (org != null && org.equals(this.organoids)))
                    && (boss == this.bosses || (boss != null && boss.equals(this.bosses)))) {
                this.source = src;
                this.organoids = org;
                this.bosses = boss;
                return this.value;
            }
            this.source = src;
            this.organoids = org;
            this.bosses = boss;

            Set<String> organoidSet = SporeEntityHelper.organoidSet();
            Set<String> bossSet = SporeEntityHelper.bossPoolSet();
            List<String> out = new ArrayList<>(src.size());
            for (String id : src) {
                if (organoidSet.contains(id)) continue;
                if (bossSet.contains(id)) continue;
                out.add(id);
            }
            this.value = out.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(out);
            return this.value;
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!Config.COMMON.enableSupportSystem.get()) return;
        if (event.getEntity().level().isClientSide) return;

        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        if (!(event.getSource().getEntity() instanceof LivingEntity)) return;

        ResourceLocation victimId = SporeEntityHelper.getEntityId(victim);
        if (victimId == null || !"spore".equals(victimId.getNamespace())) return;
        if (SporeEntityHelper.isScent(victim)) return;

        if (!(victim.level() instanceof ServerLevel serverLevel)) return;

        long currentTime = serverLevel.getGameTime();
        UUID victimUUID = victim.getUUID();
        Long cooldownEnd = COOLDOWNS.get(victimUUID);
        if (cooldownEnd != null && currentTime < cooldownEnd) return;

        WorldEvolutionData data = WorldEvolutionData.get(serverLevel);
        int phase = data.getEvolutionPhase();
        int level = SporeEntityHelper.getInfectedLevel(victim);
        double baseChance = Config.COMMON.supportBaseChance.get() + Config.COMMON.supportPhaseIncrement.get() * phase;
        var multipliers = Config.COMMON.supportLevelMultipliers.get();
        double multiplier = (level < multipliers.size()) ? multipliers.get(level) : 1.0;
        double finalChance = baseChance * multiplier;
        if (finalChance > 1.0) finalChance = 1.0;
        if (RANDOM.nextDouble() > finalChance) return;

        int cooldownTicks = Config.COMMON.supportCooldownSeconds.get() * 20;
        COOLDOWNS.put(victimUUID, currentTime + cooldownTicks);

        String victimName = victimId.toString();
        boolean isMind = MINDS.contains(victimName);

        if (isMind) {
            handleMindSupport(victim, level, serverLevel);
        } else {
            handleNormalSupport(victim, level, serverLevel);
        }
    }

    private void handleNormalSupport(LivingEntity victim, int level, ServerLevel world) {
        int lowerLevel = level - 1;
        if (lowerLevel >= 0) {
            summonFromLevel(lowerLevel, world, victim);
        } else {
            summonFromLevel(level, world, victim);
        }

        if (RANDOM.nextDouble() < Config.COMMON.supportMoundChance.get()) summonMound(world, victim);
        if (RANDOM.nextDouble() < Config.COMMON.supportSameLevelChance.get()) summonFromLevel(level, world, victim);
        if (lowerLevel >= 0 && RANDOM.nextDouble() < Config.COMMON.supportSecondLowerChance.get())
            summonFromLevel(lowerLevel, world, victim);
        if (RANDOM.nextDouble() < Config.COMMON.supportScentChance.get()) summonScent(world, victim);
    }

    private void handleMindSupport(LivingEntity victim, int level, ServerLevel world) {
        summonVerva(world, victim);

        if (RANDOM.nextDouble() < Config.COMMON.supportMoundChance.get()) summonMound(world, victim);
        if (RANDOM.nextDouble() < Config.COMMON.supportSameLevelChance.get()) summonVerva(world, victim);
        if (RANDOM.nextDouble() < Config.COMMON.supportSecondLowerChance.get()) summonVerva(world, victim);
        if (RANDOM.nextDouble() < Config.COMMON.supportScentChance.get()) summonScent(world, victim);
    }

    private void summonFromLevel(int targetLevel, ServerLevel world, LivingEntity center) {
        List<String> pool;
        switch (targetLevel) {
            case 0: pool = SMALL_POOL.of(SporeEntityHelper.getSmallPool()); break;
            case 1: pool = MEDIUM_POOL.of(SporeEntityHelper.getMediumPool()); break;
            case 2: pool = LARGE_POOL.of(SporeEntityHelper.getLargePool()); break;
            default: return;
        }
        if (pool.isEmpty()) return;
        String typeName = pool.get(RANDOM.nextInt(pool.size()));
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.of(typeName, ':'));
        if (type == null) return;
        summonEntity(type, world, center);
    }

    private void summonMound(ServerLevel world, LivingEntity center) {
        BlockPos centerPos = center.blockPosition();
        double checkRadius = 15.0;
        if (hasNearbyMound(world, centerPos, checkRadius)) {
            return; // 已有菌囊，不生成
        }
        EntityType<?> moundType = SporeEntityHelper.getMoundType();
        if (moundType != null) summonEntity(moundType, world, center);
    }

    private void summonScent(ServerLevel world, LivingEntity center) {
        BlockPos centerPos = center.blockPosition();
        double checkRadius = 15.0;
        if (hasNearbyScent(world, centerPos, checkRadius)) {
            return; // 已有支援孢气，不生成
        }
        EntityType<?> scentType = SporeEntityHelper.getScentType();
        if (scentType == null) return;

        double minDist = Config.COMMON.supportMinSpawnDistance.get();
        double maxDist = Config.COMMON.supportMaxSpawnDistance.get();

        BlockPos spawnPos = SpawnPositionHelper.findSpawnPos(world, center, minDist, maxDist);
        if (spawnPos == null) return;

        LivingEntity scent = (LivingEntity) scentType.create(world);
        if (scent == null) return;
        scent.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        world.addFreshEntity(scent);
    }

    private void summonVerva(ServerLevel world, LivingEntity center) {
        double minDist = Config.COMMON.supportMinSpawnDistance.get();
        double maxDist = Config.COMMON.supportMaxSpawnDistance.get();

        // 使用 SpawnPositionHelper 查找安全位置
        BlockPos spawnPos = SpawnPositionHelper.findSpawnPos(world, center, minDist, maxDist);
        if (spawnPos == null) return;

        // ---- 修复：将坐标转换为浮点数 ----
        double x = spawnPos.getX() + 0.5;
        double y = spawnPos.getY();
        double z = spawnPos.getZ() + 0.5;

        // 使用命令生成保育体
        String entityId = "spore:verva";
        String command = String.format("summon %s %.2f %.2f %.2f", entityId, x, y, z);

        var source = world.getServer().createCommandSourceStack().withPermission(4).withSuppressedOutput();
        world.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private void summonEntity(EntityType<?> type, ServerLevel world, LivingEntity center) {
        LivingEntity newMob = (LivingEntity) type.create(world);
        if (newMob == null) return;

        double minDist = Config.COMMON.supportMinSpawnDistance.get();
        double maxDist = Config.COMMON.supportMaxSpawnDistance.get();

        // 使用 SpawnPositionHelper 查找安全位置
        BlockPos spawnPos = SpawnPositionHelper.findSpawnPos(world, center, minDist, maxDist);
        if (spawnPos == null) return;

        newMob.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        world.addFreshEntity(newMob);
        int cooldownTicks = Config.COMMON.supportCooldownSeconds.get() * 20;
        COOLDOWNS.put(newMob.getUUID(), world.getGameTime() + cooldownTicks);
    }

    // 原实现扫描范围内全部 LivingEntity 再逐个查注册名；直接按类型过滤，匹配集合相同。
    private static boolean hasNearbyMound(ServerLevel world, BlockPos center, double radius) {
        return !world.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(radius),
                SporeEntityHelper::isMound).isEmpty();
    }

    private static boolean hasNearbyScent(ServerLevel world, BlockPos center, double radius) {
        return !world.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(radius),
                SporeEntityHelper::isScent).isEmpty();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        cleanupTimer++;
        if (cleanupTimer < CLEANUP_INTERVAL) return;
        cleanupTimer = 0;

        long currentTime = event.getServer().overworld().getGameTime();
        COOLDOWNS.entrySet().removeIf(entry -> currentTime >= entry.getValue());
    }
}
