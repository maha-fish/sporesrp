package com.maha_fish.sporesrp.enchantment;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔引用类 - 使用 ResourceKey 引用数据包定义的附魔
 * 附魔实际定义由数据包 JSON 提供（位于 data/sporesrp/enchantment/）
 */
public class ModEnchantments {

    // ✅ 正确写法：使用 ResourceLocation.parse() 或 fromNamespaceAndPath()
    public static final ResourceKey<Enchantment> FUNGAL_BANE =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.parse(SporeSRP.MOD_ID + ":fungal_bane"));

    public static final ResourceKey<Enchantment> FROST_ASPECT =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.parse(SporeSRP.MOD_ID + ":frost_aspect"));

    public static final ResourceKey<Enchantment> FROST_SHOT =
            ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.parse(SporeSRP.MOD_ID + ":frost_shot"));

    // 数据包驱动，无需 Java 注册
    public static void register() {
        // 空方法，保留兼容性
    }
}