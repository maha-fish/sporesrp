package com.maha_fish.sporesrp.block;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, SporeSRP.MOD_ID);

    public static final Supplier<Block> WORLD_PURIFICATION_CORE =
            BLOCKS.register("world_purification_core",
                    () -> new Block(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(15.0f, 1200.0f)
                            .lightLevel(state -> 5)
                            .requiresCorrectToolForDrops()
                    ));

    /** 诱饵方块：单方块，等级走方块状态 {@code tier}（1..8）。 */
    public static final Supplier<Block> DECOY =
            BLOCKS.register("decoy",
                    () -> new DecoyBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BROWN)
                            .strength(2.0f, 6.0f)
                    ));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}