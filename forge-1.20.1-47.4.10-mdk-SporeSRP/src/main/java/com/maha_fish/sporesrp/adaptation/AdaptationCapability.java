package com.maha_fish.sporesrp.adaptation;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdaptationCapability {
    public static final Capability<IAdaptation> ADAPTATION = CapabilityManager.get(new CapabilityToken<>() {});

    public static ICapabilityProvider createProvider(IAdaptation adaptation) {
        return new Provider(adaptation);
    }

    private static class Provider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final IAdaptation adaptation;
        private final LazyOptional<IAdaptation> holder;

        public Provider(IAdaptation adaptation) {
            this.adaptation = adaptation;
            this.holder = LazyOptional.of(() -> adaptation);
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ADAPTATION ? holder.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return adaptation.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            adaptation.deserializeNBT(nbt);
        }
    }
}