package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.phase.PhaseChangeEvent;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class TrackingSystemHandler {
    private static MobEffect markerEffect = null;

    private static MobEffect getMarkerEffect() {
        if (markerEffect == null) {
            markerEffect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse("spore:marker"));
            if (markerEffect == null) {
                SporeSRP.LOGGER.warn("spore:marker effect not found! Tracking system disabled.");
            }
        }
        return markerEffect;
    }

    public static void applyMarkerToEntity(LivingEntity entity, int amplifier) {
        MobEffect effect = getMarkerEffect();
        if (effect == null || amplifier <= 0) return;
        // 持续时间使用 Integer.MAX_VALUE 近似无限，不显示粒子
        entity.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect), Integer.MAX_VALUE, amplifier, false, false));
    }

    public static void clearMarker(LivingEntity entity) {
        MobEffect effect = getMarkerEffect();
        if (effect != null) {
            entity.removeEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect));
        }
    }

    public static void updateAllMarkers(ServerLevel level, int phase) {
        if (!Config.COMMON.enableTracking.get()) return;
        int amplifier = Math.min(phase, Config.COMMON.maxMarkerAmplifier.get());
        level.getEntities().getAll().forEach(entity -> {
            if (entity instanceof LivingEntity living && SporeEntityHelper.isSporeEntity(living)) {
                if (amplifier <= 0) {
                    clearMarker(living);
                } else {
                    applyMarkerToEntity(living, amplifier);
                }
            }
        });
    }

    @SubscribeEvent
    public void onPhaseChange(PhaseChangeEvent event) {

    if (!Config.COMMON_SPEC.isLoaded()) return;if (!Config.COMMON.enableTracking.get()) return;
        ServerLevel level = event.getLevel();
        int newPhase = event.getNewPhase();
        updateAllMarkers(level, newPhase);
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {


    if (event.getLevel().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;if (!Config.COMMON.enableTracking.get()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!SporeEntityHelper.isSporeEntity(living)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        int phase = WorldEvolutionData.get(serverLevel).getEvolutionPhase();
        int amplifier = Math.min(phase, Config.COMMON.maxMarkerAmplifier.get());
        if (amplifier > 0) {
            applyMarkerToEntity(living, amplifier);
        }
    }
}