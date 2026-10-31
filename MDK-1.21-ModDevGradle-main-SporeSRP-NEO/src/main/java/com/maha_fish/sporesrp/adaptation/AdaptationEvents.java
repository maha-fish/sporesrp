package com.maha_fish.sporesrp.adaptation;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class AdaptationEvents {

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {


    if (event.getEntity().level().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;
        if (!Config.COMMON.enableAdaptation.get()) return;
        LivingEntity entity = event.getEntity();
        if (!SporeEntityHelper.isSporeEntity(entity)) return;

        ResourceLocation frostbiteId = ResourceLocation.parse("spore:frostbite");
        MobEffect frostbiteEffect = BuiltInRegistries.MOB_EFFECT.get(frostbiteId);
        if (frostbiteEffect != null && entity.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(frostbiteEffect))) {
            return;
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

        // LivingDamageEvent 使用 getOriginalDamage() 获取原始伤害
        DamageSource source = event.getContainer().getSource();
        String damageType = classifyDamage(source);
        if (damageType == null) return;

        IAdaptation adaptation = entity.getData(Adaptations.ADAPTATION);
        if (adaptation == null) return;

        float increment = Config.COMMON.reductionPerHit.get().floatValue();
        adaptation.addReduction(damageType, increment);
        float current = adaptation.getReduction(damageType);
        float effective = Math.min(current, maxReduction);

        // LivingDamageEvent 使用 setNewDamage() 设置新伤害
        event.getContainer().setNewDamage(event.getContainer().getNewDamage() * (1 - effective));
    }

    private String classifyDamage(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return "fire";
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return "explosion";
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return "lightning";
        String msgId = source.getMsgId();
        if (msgId.equals("indirectMagic") || msgId.equals("magic") ||
                msgId.equals("thorns") || msgId.equals("sonic_boom")) {
            return "magic";
        }
        return "physical";
    }
}