package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSurvivalHandler {

    /**
     * 每个维度最近一次已执行「感染扩散」检查的游戏刻。
     * 同一游戏刻内多个玩家同时满足 {@code tickCount % 40 == 0} 时，
     * hasAliveProto + onInfectionSpread 这份与玩家无关的工作只做一次。
     * 触发时刻与原实现完全相同（仍由各玩家的 40 tick 条件驱动），
     * 且 onInfectionSpread 本身是全局一次性逻辑，重复调用本就无副作用。
     */
    private static final Map<ResourceKey<Level>, Long> LAST_INFECTION_CHECK_TICK = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.isDeadOrDying()) return;

        // ★ 每 20 tick 更新一次生存时间（保持不变）
        if (player.tickCount % 20 == 0) {
            AdvancementTriggerHelper.tickSurvival(player);
        }
        if (player.tickCount % 40 == 0) {
            ServerLevel level = (ServerLevel) player.level();
            long gameTick = level.getGameTime();
            Long last = LAST_INFECTION_CHECK_TICK.get(level.dimension());
            if (last != null && last.longValue() == gameTick) {
                return; // 本刻已检查过该维度
            }
            LAST_INFECTION_CHECK_TICK.put(level.dimension(), gameTick);
            boolean hasProto = SporeEntityCache.hasAliveProto(level);
            if (hasProto) {
                AdvancementTriggerHelper.onInfectionSpread(level);
            }
        }
    }
}