package com.maha_fish.sporesrp.adaptation;

import net.minecraft.nbt.CompoundTag;

/**
 * 伤害适应数据（按 SRP 的「次数」模型）。
 *
 * <p>每个 key 记录寄生体对该伤害来源累积的适应次数；
 * 实际减伤 = 次数 × 每次减免，由 {@code AdaptationEvents} 结合等级与演化阶段计算。
 */
public interface IAdaptation {

    /** 给某个来源增加适应次数（可为负，用于重置）。 */
    void addAdaptation(String key, int delta);

    /** 已累积的适应次数。 */
    int getAdaptation(String key);

    /** 已适应的不同来源数量（用于类型数上限判定）。 */
    int getAdaptedTypeCount();

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag nbt);
}