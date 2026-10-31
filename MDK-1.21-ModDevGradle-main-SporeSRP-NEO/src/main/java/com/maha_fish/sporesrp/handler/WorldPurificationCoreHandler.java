package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.battle.PurificationBattle;
import com.maha_fish.sporesrp.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class WorldPurificationCoreHandler {

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {

    if (event.getLevel().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;
        if (!Config.COMMON.enablePurificationBattle.get()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getPlacedBlock().is(Blocks.BEACON)) return;

        Level level = (Level) event.getLevel();
        BlockPos beaconPos = event.getPos();

        // 条件1：3x3 矿物块底座
        int beaconCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos check = beaconPos.offset(dx, -1, dz);
                Block block = level.getBlockState(check).getBlock();
                if (block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK ||
                        block == Blocks.DIAMOND_BLOCK || block == Blocks.EMERALD_BLOCK) {
                    beaconCount++;
                }
            }
        }
        if (beaconCount != 9) {
            player.displayClientMessage(Component.translatable("sporesrp.finalbattle.incomplete_beacon"), true);
            return;
        }

        // 条件2：8格内存在核心方块
        BlockPos corePos = null;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                for (int dy = -8; dy <= 8; dy++) {
                    BlockPos check = beaconPos.offset(dx, dy, dz);
                    if (level.getBlockState(check).is(ModBlocks.WORLD_PURIFICATION_CORE.get())) {
                        corePos = check;
                        break;
                    }
                }
                if (corePos != null) break;
            }
            if (corePos != null) break;
        }
        if (corePos == null) {
            player.displayClientMessage(Component.translatable("sporesrp.finalbattle.no_core"), true);
            return;
        }

        // 条件3：核心上方直通天空
        boolean canSeeSky = true;
        for (int dy = 1; dy < level.getMaxBuildHeight(); dy++) {
            if (!level.getBlockState(corePos.above(dy)).isAir()) {
                canSeeSky = false;
                break;
            }
        }
        if (!canSeeSky) {
            player.displayClientMessage(Component.translatable("sporesrp.finalbattle.no_sky"), true);
            return;
        }

        // 条件4：5x5x7范围内无杂块
        boolean hasMisc = false;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy <= 6; dy++) {
                    BlockPos check = beaconPos.offset(dx, dy, dz);
                    Block block = level.getBlockState(check).getBlock();
                    if (block == Blocks.AIR || block == Blocks.CAVE_AIR) continue;
                    if (check.equals(beaconPos)) continue;
                    if (block == ModBlocks.WORLD_PURIFICATION_CORE.get()) continue;
                    if (check.getY() == beaconPos.getY() - 1) {
                        if (block == Blocks.IRON_BLOCK || block == Blocks.GOLD_BLOCK ||
                                block == Blocks.DIAMOND_BLOCK || block == Blocks.EMERALD_BLOCK) {
                            continue;
                        }
                    }
                    hasMisc = true;
                    break;
                }
                if (hasMisc) break;
            }
            if (hasMisc) break;
        }
        if (hasMisc) {
            player.displayClientMessage(Component.translatable("sporesrp.finalbattle.misc_blocks"), true);
            return;
        }

        // ✅ 所有条件满足，启动战斗
        if (level instanceof ServerLevel serverLevel) {
            boolean success = PurificationBattle.getInstance().startBattle(serverLevel, beaconPos, corePos);
            if (success) {
                player.displayClientMessage(Component.translatable("sporesrp.finalbattle.success"), true);
            } else {
                player.displayClientMessage(Component.translatable("sporesrp.finalbattle.already_active"), true);
            }
        }
    }
}