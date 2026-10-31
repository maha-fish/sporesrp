package com.maha_fish.sporesrp.adaptation;

import net.minecraft.nbt.CompoundTag;
import java.util.HashMap;
import java.util.Map;

public class Adaptation implements IAdaptation {
    private final Map<String, Float> reductionMap = new HashMap<>();

    @Override
    public void addReduction(String damageType, float amount) {
        float newVal = reductionMap.getOrDefault(damageType, 0f) + amount;
        reductionMap.put(damageType, Math.min(newVal, 1.0f));
    }

    @Override
    public float getReduction(String damageType) {
        return reductionMap.getOrDefault(damageType, 0f);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Float> entry : reductionMap.entrySet()) {
            tag.putFloat(entry.getKey(), entry.getValue());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        reductionMap.clear();
        for (String key : tag.getAllKeys()) {
            reductionMap.put(key, tag.getFloat(key));
        }
    }
}