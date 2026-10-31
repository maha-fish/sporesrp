package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.EntityRemoval;
import com.maha_fish.sporesrp.phase.PhaseEffects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.maha_fish.sporesrp.util.SporeEntityHelper;

import java.util.List;
import java.util.Random;
import java.util.Set;

public class InfectionConversionHandler {
    private static final Random RANDOM = new Random();

    public static boolean tryConvert(LivingEntity victim, LivingEntity attacker, ServerLevel serverLevel, double currentHpPercent) {
        ResourceLocation attackerId = SporeEntityHelper.getEntityId(attacker);
        if (attackerId == null || !"spore".equals(attackerId.getNamespace())) return false;

        ResourceLocation victimId = SporeEntityHelper.getEntityId(victim);
        if (victimId != null && "spore".equals(victimId.getNamespace())) return false;

        long currentTick = serverLevel.getServer().getTickCount();
        WorldEvolutionData data = WorldEvolutionData.get(serverLevel);
        int phase = data.getEvolutionPhase();

        double thresholdPercent = Config.COMMON.conversionHpThresholdFactor.get() * phase;
        if (currentHpPercent > thresholdPercent) return false;

        double conversionChance = Config.COMMON.conversionBaseFactor.get() * phase;
        double maxChance = Config.COMMON.conversionMaxChance.get();
        if (conversionChance > maxChance) conversionChance = maxChance;
        if (RANDOM.nextDouble() >= conversionChance) return false;

        double maxHp = victim.getMaxHealth();
        double smallMax = Config.COMMON.conversionSmallMaxHp.get();
        double mediumMax = Config.COMMON.conversionMediumMaxHp.get();
        double largeMax = Config.COMMON.conversionLargeMaxHp.get();

        List<? extends String> pool;
        if (maxHp < smallMax) pool = SporeEntityHelper.getSmallPool();
        else if (maxHp < mediumMax) pool = SporeEntityHelper.getMediumPool();
        else if (maxHp <= largeMax) pool = SporeEntityHelper.getLargePool();
        else pool = SporeEntityHelper.getBossPool();

        if (pool.isEmpty()) return false;

        // organoidSet() 是配置列表的缓存 HashSet 视图，成员判定结果与 List.contains 完全一致
        Set<String> organoidIds = SporeEntityHelper.organoidSet();
        int maxAttempts = Config.COMMON.conversionMaxAttempts.get();

        EntityType<?> spawnType = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String typeName = pool.get(RANDOM.nextInt(pool.size()));
            if (organoidIds.contains(typeName)) continue;
            ResourceLocation spawnKey = ResourceLocation.tryParse(typeName);
            if (spawnKey == null) continue;
            spawnType = BuiltInRegistries.ENTITY_TYPE.get(spawnKey);
            if (spawnType != null) break;
        }

        if (spawnType == null) {
            SporeSRP.LOGGER.warn("Conversion failed: no valid entity found in pool after {} attempts", maxAttempts);
            return false;
        }

        LivingEntity newMob = (LivingEntity) spawnType.create(serverLevel);
        if (newMob == null) return false;
        newMob.setPos(victim.getX(), victim.getY(), victim.getZ());
        serverLevel.addFreshEntity(newMob);

        List<? extends Double> multipliers = Config.COMMON.phasePointMultipliers.get();
        double phaseMultiplier = (phase < multipliers.size()) ? multipliers.get(phase) : 1.0;
        double basePoints = victim.getMaxHealth() * Config.COMMON.killPointMultiplier.get();
        int points = (int) Math.round(basePoints * phaseMultiplier);

        int oldPhase = data.getEvolutionPhase();
        data.addEvolutionPoints(points, currentTick);
        int newPhase = data.getEvolutionPhase();
        if (newPhase != oldPhase) {
            PhaseEffects.onPhaseChange(oldPhase, newPhase, serverLevel);
        }
        // 转化会加点、也可能顺带升阶并延长冷却 —— 必须同步 HUD，否则倒计时不动
        data.syncToClients(serverLevel);
        return true;
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!Config.COMMON.enableConversion.get()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;
        if (victim instanceof Player) return;

        double currentHpPercent = victim.getHealth() / victim.getMaxHealth();
        boolean success = tryConvert(victim, attacker, serverLevel, currentHpPercent);
        if (success) {
            EntityRemoval.removeUnsafe(victim);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!Config.COMMON.enableConversion.get()) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (victim instanceof Player) return;

        var source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity attacker)) return;
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;

        boolean success = tryConvert(victim, attacker, serverLevel, 0.0);
        if (success) {
            EntityRemoval.removeUnsafe(victim);
        }
    }
}