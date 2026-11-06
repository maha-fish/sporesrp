package com.maha_fish.sporesrp.adaptation;

import com.maha_fish.sporesrp.Config;

import java.util.List;

/**
 * 适应参数表（按生物等级）。
 *
 * <p>配置 {@code adaptation.tiers} 每项格式：{@code 等级:产生概率:每次减免:最大次数:是否免疫}
 *
 * <p>随演化阶段增强：{@code 系数 = 1 + phaseGrowthPerStage × 阶段}，
 * 作用于**产生概率**、**每次减免**、**可适应类型数上限**。
 * 次数上限不缩放，因此总减伤会随阶段提高（即「阶段越高越难打」）。
 */
public final class AdaptationParams {

    /** 单个等级的参数。 */
    public static final class TierParams {
        public final float baseProbability;
        public final float basePerHit;
        public final int maxCount;
        public final boolean immune;
        public final int baseMaxTypes;

        TierParams(float baseProbability, float basePerHit, int maxCount, boolean immune, int baseMaxTypes) {
            this.baseProbability = baseProbability;
            this.basePerHit = basePerHit;
            this.maxCount = Math.max(1, maxCount);
            this.immune = immune;
            this.baseMaxTypes = Math.max(0, baseMaxTypes);
        }
    }

    private static volatile TierParams[] snapshot;
    private static volatile List<? extends String> snapTiers;
    private static volatile List<? extends Integer> snapTypes;

    private AdaptationParams() {
    }

    /** 阶段系数：1.0 起，每阶段 +phaseGrowth。 */
    public static double phaseMultiplier(int phase) {
        double growth = Config.COMMON.adaptationPhaseGrowth.get();
        return 1.0 + growth * Math.max(0, phase);
    }

    /** @return 该等级的参数；越界返回 {@code null} */
    public static TierParams tier(int index) {
        TierParams[] params = snapshot();
        if (params == null || index < 0 || index >= params.length) return null;
        return params[index];
    }

    private static TierParams[] snapshot() {
        List<? extends String> tiers = Config.COMMON.adaptationTiers.get();
        List<? extends Integer> types = Config.COMMON.adaptationMaxTypes.get();

        TierParams[] current = snapshot;
        if (current != null && same(tiers, snapTiers) && same(types, snapTypes)) return current;

        synchronized (AdaptationParams.class) {
            current = snapshot;
            if (current != null && same(tiers, snapTiers) && same(types, snapTypes)) return current;

            int size = tiers == null ? 0 : tiers.size();
            TierParams[] built = new TierParams[size];
            for (int i = 0; i < size; i++) {
                built[i] = parse(tiers.get(i), i, types);
            }
            snapTiers = tiers;
            snapTypes = types;
            snapshot = built;
            return built;
        }
    }

    private static TierParams parse(String raw, int index, List<? extends Integer> types) {
        int maxTypes = (types != null && index < types.size() && types.get(index) != null)
                ? types.get(index) : 0;
        if (raw == null) return new TierParams(0F, 0F, 1, false, maxTypes);

        String[] parts = raw.split(":");
        if (parts.length < 4) return new TierParams(0F, 0F, 1, false, maxTypes);
        try {
            float probability = clamp(Float.parseFloat(parts[1].trim()), 0F, 1F);
            float perHit = clamp(Float.parseFloat(parts[2].trim()), 0F, 1F);
            int maxCount = Math.max(1, Integer.parseInt(parts[3].trim()));
            boolean immune = parts.length >= 5 && Boolean.parseBoolean(parts[4].trim());
            return new TierParams(probability, perHit, maxCount, immune, maxTypes);
        } catch (NumberFormatException e) {
            // 配置写错时该等级不产生适应，不影响其它等级
            return new TierParams(0F, 0F, 1, false, maxTypes);
        }
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static boolean same(List<?> a, List<?> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}