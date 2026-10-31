package com.maha_fish.sporesrp.handler;

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
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CDUHandler {

    private static final ResourceLocation CDU_BLOCK_ID = new ResourceLocation("spore", "cdu");

    /**
     * {@code spore:cdu} 方块实例的惰性缓存。
     *
     * <p>原实现在每次右键 / 破坏时都做 {@code BuiltInRegistries.BLOCK.getKey(...).toString()}
     * 再与字符串比较。方块注册表在注册完成后不再变化，因此把目标方块解析一次后直接做
     * 引用比较即可，判定结果完全相同（未安装 Spore 时始终不匹配）。
     */
    private static Block cduBlock;

    private static Block getCduBlock() {
        if (cduBlock == null) {
            Block resolved = BuiltInRegistries.BLOCK.get(CDU_BLOCK_ID);
            // get() 对未注册 ID 会返回默认方块，必须再比对注册名才是精确匹配
            if (resolved != null && CDU_BLOCK_ID.equals(BuiltInRegistries.BLOCK.getKey(resolved))) {
                cduBlock = resolved;
            }
        }
        return cduBlock;
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player)) return;
        Player player = (Player) entity;
        Level level = player.level();
        if (level.isClientSide()) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block targetCdu = getCduBlock();
        if (targetCdu == null || state.getBlock() != targetCdu) return;

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
        Player player = event.getPlayer();
        if (player == null) return;
        Level level = player.level();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        Block targetCdu = getCduBlock();
        if (targetCdu == null || state.getBlock() != targetCdu) return;

        CDUCommandHandler.stopPurification(serverLevel, pos);
    }
}