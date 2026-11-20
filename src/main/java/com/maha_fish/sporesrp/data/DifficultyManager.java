package com.maha_fish.sporesrp.data;

/**
 * 难度体系：两套并行。
 *
 * <ul>
 *   <li>{@link Mode#SPORESRP}：本模组内置难度（SIMPLE/NORMAL/HARD/NIGHTMARE，单倍率）。</li>
 *   <li>{@link Mode#SRP}：Scape and Run: Parasites 原生难度
 *       （EASY/NORMAL/HARD/IMPOSSIBLE；数值取自 SRP Wiki "World Options"）。</li>
 * </ul>
 */
public class DifficultyManager {

    private DifficultyManager() {
    }

    /** 难度模式。 */
    public enum Mode {
        SPORESRP("sporesrp"),
        SRP("srp");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public static Mode fromOrdinal(int ordinal) {
            Mode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SPORESRP;
        }

        public static Mode fromName(String name) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(name) || mode.key.equalsIgnoreCase(name)) return mode;
            }
            return null;
        }
    }

    /** 内置难度：单值倍率。 */
    public enum Difficulty {
        SIMPLE(0.5),
        NORMAL(1.0),
        HARD(1.3),
        NIGHTMARE(1.7);

        private final double multiplier;

        Difficulty(double multiplier) {
            this.multiplier = multiplier;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public static Difficulty fromName(String name) {
            for (Difficulty d : values()) {
                if (d.name().equalsIgnoreCase(name)) return d;
            }
            return null;
        }

        public static Difficulty fromOrdinal(int ordinal) {
            Difficulty[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SIMPLE;
        }

        public String getDisplayName() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }

    /**
     * SRP 原生难度。
     *
     * <p>数值来源（SRP Wiki - World Options）：
     * <ul>
     *   <li>Easy：点数获取减半；击退抗性更低</li>
     *   <li>Normal：默认，无变化</li>
     *   <li>Hard：点数 ×1.5；生命/伤害/护甲 ×4</li>
     *   <li>Impossible：点数 ×10；生命/伤害 ×11，护甲 ×6；极强击退抗性</li>
     * </ul>
     */
    public enum SrpDifficulty {
        EASY(0.5, 1.0, 1.0, 1.0, 0.25),
        NORMAL(1.0, 1.0, 1.0, 1.0, 0.5),
        HARD(1.5, 4.0, 4.0, 4.0, 0.75),
        IMPOSSIBLE(10.0, 11.0, 11.0, 6.0, 1.0);

        private final double pointMultiplier;
        private final double healthMultiplier;
        private final double damageMultiplier;
        private final double armorMultiplier;
        private final double knockbackResistance;

        SrpDifficulty(double pointMultiplier, double healthMultiplier, double damageMultiplier,
                      double armorMultiplier, double knockbackResistance) {
            this.pointMultiplier = pointMultiplier;
            this.healthMultiplier = healthMultiplier;
            this.damageMultiplier = damageMultiplier;
            this.armorMultiplier = armorMultiplier;
            this.knockbackResistance = knockbackResistance;
        }

        public double getPointMultiplier() { return pointMultiplier; }
        public double getHealthMultiplier() { return healthMultiplier; }
        public double getDamageMultiplier() { return damageMultiplier; }
        public double getArmorMultiplier() { return armorMultiplier; }
        public double getKnockbackResistance() { return knockbackResistance; }

        public static SrpDifficulty fromName(String name) {
            for (SrpDifficulty d : values()) {
                if (d.name().equalsIgnoreCase(name)) return d;
            }
            return null;
        }

        public static SrpDifficulty fromOrdinal(int ordinal) {
            SrpDifficulty[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NORMAL;
        }
    }

    /** 当前生效的难度档案：把「模式 + 档位」收敛成一个统一取值入口。 */
    public static final class Profile {
        private final Mode mode;
        private final Difficulty sporeDifficulty;
        private final SrpDifficulty srpDifficulty;

        public Profile(Mode mode, Difficulty sporeDifficulty, SrpDifficulty srpDifficulty) {
            this.mode = mode == null ? Mode.SPORESRP : mode;
            this.sporeDifficulty = sporeDifficulty == null ? Difficulty.SIMPLE : sporeDifficulty;
            this.srpDifficulty = srpDifficulty == null ? SrpDifficulty.NORMAL : srpDifficulty;
        }

        public Mode getMode() { return mode; }
        public Difficulty getSporeDifficulty() { return sporeDifficulty; }
        public SrpDifficulty getSrpDifficulty() { return srpDifficulty; }

        public boolean isSrp() { return mode == Mode.SRP; }

        /** 点数获取倍率。 */
        public double pointMultiplier() {
            return isSrp() ? srpDifficulty.getPointMultiplier() : sporeDifficulty.getMultiplier();
        }

        /** 寄生体生命倍率。 */
        public double healthMultiplier() {
            return isSrp() ? srpDifficulty.getHealthMultiplier() : sporeDifficulty.getMultiplier();
        }

        /** 寄生体伤害倍率。 */
        public double damageMultiplier() {
            return isSrp() ? srpDifficulty.getDamageMultiplier() : sporeDifficulty.getMultiplier();
        }

        /** 寄生体护甲倍率。 */
        public double armorMultiplier() {
            return isSrp() ? srpDifficulty.getArmorMultiplier() : sporeDifficulty.getMultiplier();
        }

        /** 击退抗性（0~1）。 */
        public double knockbackResistance() {
            return isSrp() ? srpDifficulty.getKnockbackResistance() : 0.0D;
        }

        /** HUD/指令用的 lang key：`sporesrp.difficulty.<mode>.<tier>`。 */
        public String translationKey() {
            String tier = isSrp() ? srpDifficulty.name().toLowerCase() : sporeDifficulty.name().toLowerCase();
            return "sporesrp.difficulty." + mode.getKey() + "." + tier;
        }

        public String modeTranslationKey() {
            return "sporesrp.difficulty.mode." + mode.getKey();
        }
    }
}