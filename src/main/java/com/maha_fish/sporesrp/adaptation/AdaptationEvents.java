package com.maha_fish.sporesrp.adaptation;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.network.AdaptationFlashPacket;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 伤害适应系统（对齐 SRP / mcmod「伤害适应性」）。
 *
 * <p>模型：每次受击有**概率**产生 1 次适应；实际减伤 = 适应次数 × 每次减免。
 * 次数上限与是否免疫由等级配置决定；产生概率、每次减免、可适应类型数上限随演化阶段提高。
 */
public class AdaptationEvents {


    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;
        if (!Config.COMMON.enableAdaptation.get()) return;

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (!SporeEntityHelper.isSporeEntity(entity)) return;

        ResourceLocation frostbiteId = ResourceLocation.parse("spore:frostbite");
        MobEffect frostbite = BuiltInRegistries.MOB_EFFECT.get(frostbiteId);
        if (frostbite != null && entity.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(frostbite))) {
            return;
        }

        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        int phase = WorldEvolutionData.get(serverLevel).getEvolutionPhase();
        int tier = SporeEntityHelper.getInfectedLevel(entity);
        AdaptationParams.TierParams params = AdaptationParams.tier(tier);
        if (params == null) return;

        double multiplier = AdaptationParams.phaseMultiplier(phase);
        float probability = (float) Math.min(1.0, params.baseProbability * multiplier);
        float perHit = (float) Math.min(0.50, params.basePerHit * multiplier);
        int maxTypes = Math.max(1, (int) Math.round(params.baseMaxTypes * multiplier));
        float maxTotalReduction = (float) Math.min(1.0, Math.max(0.0, Config.COMMON.adaptationMaxTotalReduction.get()));

        String key = DamageKeyResolver.resolve(event.getContainer().getSource());
        IAdaptation adaptation = entity.getData(Adaptations.ADAPTATION);
        if (adaptation == null) return;

        int count = adaptation.getAdaptation(key);
        // 已完全适应该伤害来源：此后每次受击都算「完全适应」
        boolean fullyAdapted = count >= params.maxCount;

        // ---- 1) 应用当前减伤 ----
        if (params.immune && fullyAdapted) {
            event.getContainer().setNewDamage(0.0F);
        } else {
            float effective = Math.min(count * perHit, maxTotalReduction);
            event.getContainer().setNewDamage(event.getContainer().getNewDamage() * (1.0F - effective));
        }

        // ---- 2) 尝试产生新的适应（已达上限则不再累积）----
        boolean produced = false;
        if (!fullyAdapted) {
            boolean knownType = count > 0;
            boolean roomForNewType = knownType || adaptation.getAdaptedTypeCount() < maxTypes;
            if (roomForNewType && entity.getRandom().nextFloat() < probability) {
                adaptation.addAdaptation(key, 1);
                count++;
                produced = true;
                fullyAdapted = count >= params.maxCount;
            }
        }

        // ---- 3) 视觉反馈 ----
        // 完全适应 → 紫（每次受击都显示）；本次新产生适应 → 绿
        if (fullyAdapted) {
            AdaptationFlash.trigger(entity, AdaptationFlashPacket.KIND_FULL);
        } else if (produced) {
            AdaptationFlash.trigger(entity, AdaptationFlashPacket.KIND_ADAPTING);
        }
    }
}