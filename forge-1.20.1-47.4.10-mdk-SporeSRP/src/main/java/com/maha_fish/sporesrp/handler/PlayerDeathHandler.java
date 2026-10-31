package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PlayerDeathHandler {

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Player)) return;

        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AdvancementTriggerHelper.onDeath(player);

        var source = event.getSource().getEntity();
        if (!(source instanceof LivingEntity killer)) return;
        ResourceLocation killerId = BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType());
        if (killerId == null || !"spore".equals(killerId.getNamespace())) return;

        if (victim.level() instanceof ServerLevel serverLevel) {
            // 修正实体ID：spore:gastgaber 而不是 gastgeber
            ResourceLocation gastgeberId = ResourceLocation.of("spore:gastgaber", ':');
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(gastgeberId)) {
                SporeSRP.LOGGER.error("Entity {} not found in registry! Cannot spawn on player death.", gastgeberId);
                return;
            }
            EntityType<?> gastgeberType = BuiltInRegistries.ENTITY_TYPE.get(gastgeberId);
            var gastgeber = gastgeberType.create(serverLevel);
            if (gastgeber != null) {
                gastgeber.setPos(victim.getX(), victim.getY(), victim.getZ());
                serverLevel.addFreshEntity(gastgeber);
                serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("sporesrp.death.spawn_gastgeber", victim.getName().getString()),
                        false
                );
            }
        }
    }
}