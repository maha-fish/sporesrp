package com.maha_fish.sporesrp.enchantment;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, SporeSRP.MOD_ID);

    public static final RegistryObject<Enchantment> FUNGAL_BANE =
            ENCHANTMENTS.register("fungal_bane", () -> new FungalBaneEnchantment(
                    Enchantment.Rarity.UNCOMMON,
                    EnchantmentCategory.WEAPON,
                    EquipmentSlot.MAINHAND
            ));

    public static final RegistryObject<Enchantment> FROST_ASPECT =
            ENCHANTMENTS.register("frost_aspect", () -> new FrostAspectEnchantment(
                    Enchantment.Rarity.UNCOMMON,
                    EnchantmentCategory.WEAPON,
                    EquipmentSlot.MAINHAND
            ));

    public static final RegistryObject<Enchantment> FROST_SHOT =
            ENCHANTMENTS.register("frost_shot", () -> new FrostShotEnchantment(
                    Enchantment.Rarity.UNCOMMON,
                    EnchantmentCategory.BOW,
                    EquipmentSlot.MAINHAND
            ));

    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }
}