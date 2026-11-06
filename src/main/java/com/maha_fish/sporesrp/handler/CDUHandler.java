package com.maha_fish.sporesrp.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * {@code spore:cdu} 方块的辅助事件。
 *
 * <p>群系净化现在由 {@link CDUCommandHandler} **跟随 CDU 方块自身的燃料脉冲**完成，
 * 不再需要玩家用冰罐右击来启动，因此这里只保留"方块被破坏时停止跟踪"。
 */
public class CDUHandler {

    private static final ResourceLocation CDU_BLOCK_ID = ResourceLocation.tryParse("spore:cdu");

    /** {@code spore:cdu} 方块实例的惰性缓存（注册表冻结后不再变化）。 */
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