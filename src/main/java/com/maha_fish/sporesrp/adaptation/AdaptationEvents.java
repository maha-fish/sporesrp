package com.maha_fish.sporesrp.adaptation;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class AdaptationEvents {

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!Config.COMMON.enableAdaptation.get()) return;

        LivingEntity entity = event.getEntity();
        if (!SporeEntityHelper.isSporeEntity(entity)) return;

        // 检查是否拥有 spore:frostbite 效果，若有则适应性系统失效
        ResourceLocation frostbiteId = new ResourceLocation("spore", "frostbite");
        var frostbiteEffect = BuiltInRegistries.MOB_EFFECT.get(frostbiteId);
        if (frostbiteEffect != null && entity.hasEffect(frostbiteEffect)) {
            return; // 直接跳过适应性处理
        }

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        WorldEvolutionData data = WorldEvolutionData.get(serverLevel);
        int phase = data.getEvolutionPhase();
        int maxPhase = Config.COMMON.getMaxPhase();
        if (maxPhase <= 0) return;

        int level = SporeEntityHelper.getInfectedLevel(entity);
        double baseImmunity = Config.COMMON.baseImmunity.get().get(level);
        double finalMax = Config.COMMON.finalMaxReduction.get().get(level);

        float maxReduction = (float) (baseImmunity + (finalMax - baseImmunity) * phase / maxPhase);

        String damageType = classifyDamage(event.getSource());
        if (damageType == null) return;

        entity.getCapability(AdaptationCapability.ADAPTATION).ifPresent(adaptation -> {
            float increment = Config.COMMON.reductionPerHit.get().floatValue();
            adaptation.addReduction(damageType, increment);
            float current = adaptation.getReduction(damageType);
            float effective = Math.min(current, maxReduction);
            event.setAmount(event.getAmount() * (1 - effective));
        });
    }

    private String classifyDamage(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return "fire";
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return "explosion";
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return "lightning";
        String msgId = source.getMsgId();
        if (msgId.equals("indirectMagic") || msgId.equals("magic") || msgId.equals("thorns") || msgId.equals("sonic_boom")) {
            return "magic";
        }
        return "physical";
    }
}