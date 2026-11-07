package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PlayerSurvivalHandler {

    /** 孢子感染群系。 */
    private static final ResourceKey<Biome> INFECTED_BIOME = ResourceKey.create(
            Registries.BIOME, ResourceLocation.tryParse("sporesrp:infected_biome"));

    /** 玩家当前是否真正站在孢子感染群系内。 */
    private static boolean isInInfectedBiome(ServerPlayer player, ServerLevel level) {
        return level.getBiome(player.blockPosition())
                .unwrapKey()
                .map(key -> INFECTED_BIOME.equals(key))
                .orElse(false);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.isDeadOrDying()) return;

        // 每 20 tick 更新一次生存时间
        if (player.tickCount % 20 == 0) {
            AdvancementTriggerHelper.tickSurvival(player);
        }

        // 「进入感染群系」进度：玩家真正站在感染群系里才授予（每人一次）
        if (player.tickCount % 40 == 0 && player.level() instanceof ServerLevel level) {
            if (isInInfectedBiome(player, level)) {
                AdvancementTriggerHelper.onEnterInfectedBiome(player);
            }
        }
    }
}