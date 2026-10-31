package com.maha_fish.sporesrp.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 诱饵各等级共用的方块物品。
 *
 * <p>8 个等级是 8 个独立物品（靠 {@code tier} 字段区分），都放置同一个诱饵方块；
 * 显示名按等级取 {@code item.sporesrp.decoy_N}，所以创造栏里能一眼看出强弱。
 */
public class DecoyItem extends BlockItem {

    private final int tier;

    public DecoyItem(Block block, Properties properties, int tier) {
        super(block, properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.sporesrp.decoy_" + tier);
    }
}
