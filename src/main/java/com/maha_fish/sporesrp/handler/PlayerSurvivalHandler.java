package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSurvivalHandler {

    /**
     * 每维度上一次执行「proto 存活检测 + 感染扩散成就」的服务器 tick。
     *
     * <p>多个玩家在同一次服务器 tick 内各自触发 40 tick 检查时，工作内容完全重复：
     * {@code hasAliveProto} 只是读取本 tick 的预计算列表，
     * {@code onInfectionSpread} 自身以 {@code infectionSpreadTriggered} 保证幂等。
     * 因此按「维度 + 服务器 tick」去重，效果与逐玩家重复调用一致。
     */
    private static final Map<ResourceKey<Level>, Long> LAST_SPREAD_CHECK_TICK = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {


    if (event.getEntity().level().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isDeadOrDying()) return;

        if (player.tickCount % 20 == 0) {
            AdvancementTriggerHelper.tickSurvival(player);
        }
        if (player.tickCount % 40 == 0) {
            ServerLevel level = (ServerLevel) player.level();
            ResourceKey<Level> dim = level.dimension();
            long serverTick = level.getServer().getTickCount();
            Long lastTick = LAST_SPREAD_CHECK_TICK.get(dim);
            if (lastTick == null || lastTick.longValue() != serverTick) {
                LAST_SPREAD_CHECK_TICK.put(dim, serverTick);
                boolean hasProto = SporeEntityCache.hasAliveProto(level);
                if (hasProto) {
                    AdvancementTriggerHelper.onInfectionSpread(level);
                }
            }
        }
    }
}
