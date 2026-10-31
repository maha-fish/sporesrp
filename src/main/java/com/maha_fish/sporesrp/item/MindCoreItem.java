package com.maha_fish.sporesrp.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public class MindCoreItem extends Item {
    public MindCoreItem() {
        super(new Properties()
                .rarity(Rarity.EPIC)
                .stacksTo(64)
        );
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}