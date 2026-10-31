package com.maha_fish.sporesrp.item;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.block.DecoyTiers;
import com.maha_fish.sporesrp.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SporeSRP.MOD_ID);

    public static final RegistryObject<Item> MIND_CORE =
            ITEMS.register("mind_core", MindCoreItem::new);

    /**
     * 诱饵 1–8 级：**8 个独立物品**，都放置同一个 {@code sporeSRP:decoy} 方块。
     * 拆成独立物品是为了让配方只需要一个普通物品 ID（不必在结果里写 NBT）。
     */
    @SuppressWarnings("unchecked")
    private static final RegistryObject<Item>[] DECOYS = new RegistryObject[DecoyTiers.MAX + 1];

    static {
        for (int tier = DecoyTiers.MIN; tier <= DecoyTiers.MAX; tier++) {
            final int t = tier;
            DECOYS[tier] = ITEMS.register("decoy_" + tier,
                    () -> new DecoyItem(ModBlocks.DECOY.get(), new Item.Properties(), t));
        }
    }

    /** 取该等级的诱饵物品；越界按 1 级处理。 */
    public static Item itemForTier(int tier) {
        int t = Math.max(DecoyTiers.MIN, Math.min(DecoyTiers.MAX, tier));
        return DECOYS[t].get();
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
    public static final RegistryObject<Item> WORLD_PURIFICATION_CORE =
            ITEMS.register("world_purification_core",
                    () -> new BlockItem(ModBlocks.WORLD_PURIFICATION_CORE.get(),
                            new Item.Properties().stacksTo(64)));
}