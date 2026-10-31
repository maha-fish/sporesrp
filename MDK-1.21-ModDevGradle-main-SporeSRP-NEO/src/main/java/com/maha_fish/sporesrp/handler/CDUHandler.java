package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class CDUHandler {

    /** spore:cdu 方块对象（惰性解析；每次点击/破坏都做 getKey().toString() 太浪费）。 */
    private static Block cduBlock;

    private static Block getCduBlock() {
        // 未解析到时保持 null 并下次重试（与原行为一致：不匹配即返回），不做永久缓存。
        if (cduBlock == null) {
            cduBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("spore:cdu"));
        }
        return cduBlock;
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;
        Level level = player.level();
        if (level.isClientSide()) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != getCduBlock()) return;

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty()) return;
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!"spore:ice_canister".equals(itemId)) return;

        if (level instanceof ServerLevel serverLevel) {
            CDUCommandHandler.startPurification(serverLevel, pos);
            player.sendSystemMessage(Component.translatable("sporesrp.cdu.purification.start"));
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        

    if (event.getLevel().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;Player player = event.getPlayer();
        if (player == null) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        if (state.getBlock() != getCduBlock()) return;

        CDUCommandHandler.stopPurification(serverLevel, pos);
    }
}