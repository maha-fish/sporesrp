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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 伤害适应系统（对齐 SRP / mcmod「伤害适应性」）。
 *
 * <p>模型：每次受击有**概率**产生 1 次适应；实际减伤 = 适应次数 × 每次减免。
 * 次数上限与是否免疫由等级配置决定；产生概率、每次减免、可适应类型数上限随演化阶段提高。
 *
 * <p>适应来源 key 见 {@link DamageKeyResolver}（玩家按主手物品、生物按实体类型、其它按伤害类型）。
 */
public class AdaptationEvents {


    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!Config.COMMON.enableAdaptation.get()) return;

        LivingEntity entity = event.getEntity();
        if (!SporeEntityHelper.isSporeEntity(entity)) return;

        // spore:frostbite 效果存在时，适应性系统失效
        ResourceLocation frostbiteId = ResourceLocation.tryParse("spore:frostbite");
        if (frostbiteId != null) {
            MobEffect frostbite = BuiltInRegistries.MOB_EFFECT.get(frostbiteId);
            if (frostbite != null && entity.hasEffect(frostbite)) return;
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

        String key = DamageKeyResolver.resolve(event.getSource());

        IAdaptation adaptation = entity.getCapability(AdaptationCapability.ADAPTATION).orElse(null);
        if (adaptation == null) return;

        int count = adaptation.getAdaptation(key);

        // ---- 1) 应用当前减伤 ----
        if (params.immune && count >= params.maxCount) {
            event.setAmount(0.0F);
        } else {
            float effective = Math.min(count * perHit, maxTotalReduction);
            event.setAmount(event.getAmount() * (1.0F - effective));
        }

        // ---- 2) 尝试产生新的适应 ----
        boolean produced = false;
        boolean knownType = count > 0;
        if (!knownType && adaptation.getAdaptedTypeCount() >= maxTypes) {
            // 可适应类型数已满：不再学习新来源（已有来源仍可继续累积）
        } else if (entity.getRandom().nextFloat() < probability) {
            adaptation.addAdaptation(key, 1);
            count++;
            produced = true;
        }

        // ---- 3) 视觉反馈 ----
        if (produced) {
            AdaptationFlash.trigger(entity, count >= params.maxCount
                    ? AdaptationFlashPacket.KIND_FULL
                    : AdaptationFlashPacket.KIND_ADAPTING);
        }
    }
}