package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * 演化阶段的「世界惩罚」门控。
 *
 * <p>阶段越高，世界越不适宜正常生存 —— 这是 SRP 的核心压迫感来源：
 * 玩家不只是被更强的怪追着打，而是**生存本身在被逐步剥夺**。
 *
 * <p>所有阈值与概率都进 {@code Config}，默认值按 SRP：
 * <ul>
 *   <li>6 阶段起：作物生长受阻（10% → 30% → 60% → 100%）、无法钓鱼</li>
 *   <li>8 阶段起：孢子生物死亡不掉经验</li>
 *   <li>9 阶段起：非孢子生物自然回血减半</li>
 * </ul>
 */
public class PhaseGateHandler {

    private static final RandomSource RANDOM = RandomSource.create();

    private static int phaseOf(ServerLevel level) {
        return WorldEvolutionData.get(level).getEvolutionPhase();
    }

    // ==================== 6 阶段起：作物生长受阻 ====================

    @SubscribeEvent
    public void onCropGrow(CropGrowEvent.Pre event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;
        if (!Config.COMMON.enablePhaseGate.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        int phase = phaseOf(level);
        var chances = Config.COMMON.cropGrowthBlockChance.get();
        if (phase < 0 || phase >= chances.size()) return;

        Double chance = chances.get(phase);
        if (chance == null || chance <= 0.0) return;
        if (chance >= 1.0 || RANDOM.nextDouble() < chance) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    // ==================== 6 阶段起：无法钓鱼 ====================

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;
        if (!Config.COMMON.enablePhaseGate.get()) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (phaseOf(level) < Config.COMMON.fishingBlockPhase.get()) return;
        event.setCanceled(true);
    }

    // ==================== 8 阶段起：孢子生物不掉经验 ====================

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;
        if (!Config.COMMON.enablePhaseGate.get()) return;
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (!SporeEntityHelper.isSporeEntity(entity)) return;
        if (phaseOf(level) < Config.COMMON.noXpPhase.get()) return;
        event.setDroppedExperience(0);
    }

    // ==================== 9 阶段起：非孢子生物回血减半 ====================

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;
        if (!Config.COMMON.enablePhaseGate.get()) return;
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (SporeEntityHelper.isSporeEntity(entity)) return;
        if (phaseOf(level) < Config.COMMON.healPenaltyPhase.get()) return;

        double factor = Config.COMMON.healPenaltyFactor.get();
        if (factor >= 1.0) return;
        event.setAmount((float) (event.getAmount() * factor));
    }
}
