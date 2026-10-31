package com.maha_fish.sporesrp.sound;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, SporeSRP.MOD_ID);

    // ✅ 修复：使用 ResourceLocation.parse
    public static final Supplier<SoundEvent> PROTO_RESURRECTION =
            SOUND_EVENTS.register("proto_resurrection",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.parse(SporeSRP.MOD_ID + ":proto_resurrection")
                    ));

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}