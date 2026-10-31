package com.maha_fish.sporesrp.block;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;


public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SporeSRP.MOD_ID);

    public static final RegistryObject<Block> WORLD_PURIFICATION_CORE =
            BLOCKS.register("world_purification_core",
                    () -> new Block(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(15.0f, 1200.0f) // 硬度50，抗爆1200（同黑曜石）
                            .lightLevel(state -> 5) // 微弱光，亮度值 0~15，5 为低亮度
                            .requiresCorrectToolForDrops()
                    ));

    /** 诱饵方块：单方块，等级走方块状态 {@code tier}（1..8）。 */
    public static final RegistryObject<Block> DECOY =
            BLOCKS.register("decoy",
                    () -> new DecoyBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BROWN)
                            .strength(2.0f, 6.0f)
                    ));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

}