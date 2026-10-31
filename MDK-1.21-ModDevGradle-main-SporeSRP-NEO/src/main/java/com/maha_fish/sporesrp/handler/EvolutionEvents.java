package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.phase.PhaseEffects;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

public class EvolutionEvents {

    @SubscribeEvent
    public void onSporeKill(LivingDeathEvent event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;var source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity killer)) return;
        ResourceLocation killerId = BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType());
        if (killerId == null || !"spore".equals(killerId.getNamespace())) return;

        LivingEntity victim = event.getEntity();
        ResourceLocation victimId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        if (victimId != null && "spore".equals(victimId.getNamespace())) return;

        if (victim.level() instanceof ServerLevel serverLevel) {
            long currentTick = serverLevel.getServer().getTickCount();
            WorldEvolutionData data = WorldEvolutionData.get(serverLevel);
            int phase = data.getEvolutionPhase();

            // ---- 应用阶段倍率 ----
            double basePoints = victim.getMaxHealth() * Config.COMMON.killPointMultiplier.get();
            List<? extends Double> multipliers = Config.COMMON.phasePointMultipliers.get();
            double phaseMultiplier = (phase < multipliers.size()) ? multipliers.get(phase) : 1.0;
            int points = (int) Math.round(basePoints * phaseMultiplier);

            int oldPhase = data.getEvolutionPhase();
            data.addEvolutionPoints(points, currentTick);
            data.syncToClients(serverLevel);
            int newPhase = data.getEvolutionPhase();
            if (newPhase != oldPhase) {
                PhaseEffects.onPhaseChange(oldPhase, newPhase, serverLevel);
            }
        }
    }

    @SubscribeEvent
    public void onSporeDeath(LivingDeathEvent event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;LivingEntity victim = event.getEntity();
        ResourceLocation victimId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        if (victimId == null || !"spore".equals(victimId.getNamespace())) return;
        if (!(victim.level() instanceof ServerLevel serverLevel)) return;

        // ★ 击杀者必须是玩家
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        // ★ 记录击杀并触发进度
        String poolType = getPoolType(victim);
        AdvancementTriggerHelper.onKill(player, poolType);

        long currentTick = serverLevel.getServer().getTickCount();
        WorldEvolutionData data = WorldEvolutionData.get(serverLevel);
        int phase = data.getEvolutionPhase();
        double penaltyDouble = 0;
        String entityKey = victimId.toString();

        if ("spore:proto".equals(entityKey)) {
            double basePenalty = Config.COMMON.protoDeathPenalty.get();
            // 计算阶段倍率（从阶段6开始）
            double phaseMultiplier = 1.0;
            if (phase >= 6) {
                phaseMultiplier = 1.0 + (phase - 5) * 0.2;
            }
            // 如果是完整心智，额外乘5
            if (FullHivemindHandler.isFullHivemind(victim)) {
                penaltyDouble = basePenalty * phaseMultiplier * 5.0;
            } else {
                penaltyDouble = basePenalty * phaseMultiplier;
            }
        } else if ("spore:mound".equals(entityKey)) {
            penaltyDouble = Config.COMMON.moundDeathPenalty.get();
        } else {
            int level = SporeEntityHelper.getInfectedLevel(victim);
            switch (level) {
                case 0 -> penaltyDouble = Config.COMMON.smallDeathPenalty.get();
                case 1 -> penaltyDouble = Config.COMMON.mediumDeathPenalty.get();
                case 2 -> penaltyDouble = Config.COMMON.largeDeathPenalty.get();
                case 3 -> penaltyDouble = Config.COMMON.bossDeathPenalty.get();
                default -> penaltyDouble = 0;
            }
        }

        if (penaltyDouble != 0) {
            int penalty = (int) Math.round(penaltyDouble);
            data.addEvolutionPoints(-penalty, currentTick);
            data.syncToClients(serverLevel);
            SporeSRP.LOGGER.debug("Spore death penalty: -{} points for {}", penalty, entityKey);
        }
    }
    private String getPoolType(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id == null) return "unknown";
        String name = id.toString();
        if ("spore:proto".equals(name)) return "proto";
        if (SporeEntityHelper.getSmallPool().contains(name)) return "small";
        if (SporeEntityHelper.getMediumPool().contains(name)) return "medium";
        if (SporeEntityHelper.getLargePool().contains(name)) return "large";
        if (SporeEntityHelper.getBossPool().contains(name)) return "boss";
        return "unknown";
    }
}