package com.maha_fish.sporesrp.battle;

import net.minecraft.network.chat.Component;

public enum BattlePhase {
    PHASE_1("sporesrp.battle.phase.1", 6 * 60 * 20),      // 6分钟
    PHASE_2("sporesrp.battle.phase.2", 6 * 60 * 20),      // 6分钟
    PHASE_3("sporesrp.battle.phase.3", 288 * 20),         // 288秒 = 4分48秒
    PHASE_4("sporesrp.battle.phase.4", 96 * 20),          // 96秒 = 1分36秒
    COMPLETED("sporesrp.battle.phase.completed", 0);

    private final String translationKey;
    private final int durationTicks;

    BattlePhase(String translationKey, int durationTicks) {
        this.translationKey = translationKey;
        this.durationTicks = durationTicks;
    }

    public String getTranslationKey() { return translationKey; }
    public int getDurationTicks() { return durationTicks; }

    /** 返回可本地化的显示名 */
    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    /** 返回翻译后的字符串（用于日志、指令等需要 String 的场景） */
    public String getDisplayNameString() {
        return getDisplayName().getString();
    }

    public BattlePhase next() {
        BattlePhase[] values = values();
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal >= values.length - 1) return COMPLETED;
        return values[nextOrdinal];
    }

    public boolean isCompleted() { return this == COMPLETED; }

    public int getPhaseNumber() {
        return ordinal() + 1;
    }
}