package com.maha_fish.sporesrp.adaptation;

import net.minecraft.nbt.CompoundTag;

public interface IAdaptation {
    void addReduction(String damageType, float amount);
    float getReduction(String damageType);
    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag nbt);
}