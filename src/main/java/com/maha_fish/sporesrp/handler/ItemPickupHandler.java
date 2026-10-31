package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.item.ModItems;
import com.maha_fish.sporesrp.util.AdvancementHelper;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ItemPickupHandler {

    /** 每次容器变化都重新 parse 一次 ResourceLocation 没必要，提为常量。 */
    private static final ResourceLocation CORE_OBTAIN_ID =
            ResourceLocation.parse(SporeSRP.MOD_ID + ":core_obtain");

    // ========== 拾取事件 ==========
    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemEntity().getItem();

        // 检查世界净化核心
        if (stack.getItem() == ModItems.WORLD_PURIFICATION_CORE.get()) {
            AdvancementTriggerHelper.onCoreObtain(player);
        }

        // 检查心核
        if (stack.getItem() == ModItems.MIND_CORE.get()) {
            AdvancementTriggerHelper.onMindCoreObtain(player);
        }
    }

    // ========== 合成事件 ==========
    @SubscribeEvent
    public void onCrafting(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack result = event.getCrafting();

        // 检查世界净化核心
        if (result.getItem() == ModItems.WORLD_PURIFICATION_CORE.get()) {
            AdvancementTriggerHelper.onCoreObtain(player);
        }

        // 检查心核
        if (result.getItem() == ModItems.MIND_CORE.get()) {
            AdvancementTriggerHelper.onMindCoreObtain(player);
        }
    }

    // ========== 容器变化事件（保留作为后备） ==========
    @SubscribeEvent
    public void onInventoryChanged(PlayerContainerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 检查是否已获得成就，避免重复扫描
        ResourceLocation coreId = CORE_OBTAIN_ID;
        if (AdvancementHelper.hasAdvancement(player, coreId)) return;

        // 扫描玩家背包
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.getItem() == ModItems.WORLD_PURIFICATION_CORE.get()) {
                AdvancementTriggerHelper.onCoreObtain(player);
                break;
            }
        }
    }
}