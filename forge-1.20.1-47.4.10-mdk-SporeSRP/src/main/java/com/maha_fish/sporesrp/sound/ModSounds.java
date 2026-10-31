package com.maha_fish.sporesrp.sound;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SporeSRP.MOD_ID);

    // ---- Proto 致命反击音效 ----
    public static final RegistryObject<SoundEvent> PROTO_RESURRECTION =
            SOUND_EVENTS.register("proto_resurrection",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(SporeSRP.MOD_ID, "proto_resurrection")
                    ));

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}