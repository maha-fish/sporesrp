package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class EvolutionPeriodicHandler {
    private int tickCounter = 0;
    private static final int INTERVAL_TICKS = 60 * 20;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter < INTERVAL_TICKS) return;
        tickCounter = 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            // ---- 使用缓存替代 AABB 全服扫描 ----
            List<LivingEntity> protos = SporeEntityCache.getAliveProtos(level);
            List<LivingEntity> mounds = SporeEntityCache.getAliveMounds(level);

            long protoCount = protos.size();
            double moundPointsAccumulator = 0.0;

            List<? extends Double> multipliers = Config.COMMON.moundLevelPointMultipliers.get();
            double baseMoundPoints = Config.COMMON.moundPeriodicPoints.get();

            for (LivingEntity mound : mounds) {
                int levelNum = MoundLevelSystem.getLevel(mound.getUUID());
                if (levelNum < 1) levelNum = 1;
                if (levelNum > 4) levelNum = 4;
                double multiplier = (levelNum <= multipliers.size()) ? multipliers.get(levelNum - 1) : 1.0;
                moundPointsAccumulator += baseMoundPoints * multiplier;
            }

            if (protoCount > 0 || moundPointsAccumulator > 0) {
                long currentTick = event.getServer().getTickCount();
                WorldEvolutionData data = WorldEvolutionData.get(level);
                double totalPointsDouble = protoCount * Config.COMMON.protoPeriodicPoints.get() + moundPointsAccumulator;

                int phase = data.getEvolutionPhase();
                List<? extends Double> phaseMultipliers = Config.COMMON.phasePointMultipliers.get();
                double phaseMultiplier = (phase < phaseMultipliers.size()) ? phaseMultipliers.get(phase) : 1.0;
                totalPointsDouble *= phaseMultiplier;

                int totalPoints = (int) Math.round(totalPointsDouble);
                data.addEvolutionPoints(totalPoints, currentTick);
                data.syncToClients(level);
                SporeSRP.LOGGER.debug("Periodic evolution: +{} points (proto={}, moundPointsSum={}, phaseMult={})",
                        totalPoints, protoCount, moundPointsAccumulator, phaseMultiplier);
            }
        }
    }
}