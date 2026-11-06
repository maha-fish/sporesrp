package com.maha_fish.sporesrp.adaptation;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

/** {@link IAdaptation} 的默认实现：key -> 适应次数。 */
public class Adaptation implements IAdaptation {

    private static final String TAG_COUNTS = "counts";

    private final Map<String, Integer> counts = new HashMap<>();

    @Override
    public void addAdaptation(String key, int delta) {
        if (key == null || delta == 0) return;
        int next = counts.getOrDefault(key, 0) + delta;
        if (next <= 0) {
            counts.remove(key);
        } else {
            counts.put(key, next);
        }
    }

    @Override
    public int getAdaptation(String key) {
        return key == null ? 0 : counts.getOrDefault(key, 0);
    }

    @Override
    public int getAdaptedTypeCount() {
        return counts.size();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag root = new CompoundTag();
        CompoundTag inner = new CompoundTag();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            inner.putInt(entry.getKey(), entry.getValue());
        }
        root.put(TAG_COUNTS, inner);
        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        counts.clear();
        if (nbt == null) return;
        CompoundTag inner = nbt.getCompound(TAG_COUNTS);
        for (String key : inner.getAllKeys()) {
            int value = inner.getInt(key);
            if (value > 0) counts.put(key, value);
        }
    }
}