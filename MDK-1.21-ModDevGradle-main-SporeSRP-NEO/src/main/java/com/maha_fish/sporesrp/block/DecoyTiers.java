package com.maha_fish.sporesrp.block;

import com.maha_fish.sporesrp.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 诱饵 1–8 级的数值表，数据来源：SRP（mcmod.cn 饵堆 / 诱饵条目）。
 *
 * <p>等级由「物品本身」区分：`sporesrp:decoy_1` … `sporesrp:decoy_8` 是 8 个独立物品，
 * 都放置同一个 {@link DecoyBlock}，方块状态 {@code tier} 由放置的物品决定。
 *
 * <p>机制（SRP V1.9.13 之后）：
 * <ul>
 *   <li><b>单个诱饵</b> —— 只重置/延长演化时间冷却，<b>不扣点数</b>；</li>
 *   <li><b>饵堆</b> —— 按<b>中心</b>诱饵的等级扣演化点数。</li>
 * </ul>
 */
public final class DecoyTiers {

    public static final int MIN = 1;
    public static final int MAX = 8;

    /** 演化点数降低量；索引 0 占位。 */
    private static final int[] POINTS = {
            0, 10, 20, 50, 500, 4_000, 80_000, 350_000, 6_250_000
    };

    /** 演化时间冷却（秒）；索引 0 占位。 */
    private static final int[] COOLDOWN_SECONDS = {
            0, 10, 20, 50, 250, 300, 600, 600, 1_200
    };

    private DecoyTiers() {
    }

    public static int clamp(int tier) {
        return Math.max(MIN, Math.min(MAX, tier));
    }

    /** 该等级能降低的演化点数。 */
    public static int points(int tier) {
        return POINTS[clamp(tier)];
    }

    /** 该等级附加的演化时间冷却（秒）。 */
    public static int cooldownSeconds(int tier) {
        return COOLDOWN_SECONDS[clamp(tier)];
    }

    /** 该等级对应的物品；越界按 1 级处理。 */
    public static Item item(int tier) {
        return ModItems.itemForTier(clamp(tier));
    }

    /** 由物品栈反查等级；不是诱饵时按 1 级处理。 */
    public static int tierOf(ItemStack stack) {
        for (int t = MIN; t <= MAX; t++) {
            if (stack.is(ModItems.itemForTier(t))) return t;
        }
        return MIN;
    }
}
