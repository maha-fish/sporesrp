package com.maha_fish.sporesrp.item;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.block.DecoyTiers;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的创造模式物品栏。
 *
 * <p>诱饵只有一「种」物品，但通过 {@code Tier} 标签把 1–8 级各自作为一个条目加进来，
 * 于是创造栏里每级都出现一次，玩家可以直接取到对应等级。
 */
public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SporeSRP.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.sporesrp"))
                    .icon(() -> new ItemStack(ModItems.MIND_CORE.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.MIND_CORE.get());
                        output.accept(ModItems.WORLD_PURIFICATION_CORE.get());
                        for (int tier = DecoyTiers.MIN; tier <= DecoyTiers.MAX; tier++) {
                            output.accept(new ItemStack(ModItems.itemForTier(tier)));
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
