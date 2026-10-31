package com.maha_fish.sporesrp.data;

public class DifficultyManager {
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
                if (d.name().equalsIgnoreCase(name)) {
                    return d;
                }
            }
            return null;
        }

        public static Difficulty fromOrdinal(int ordinal) {
            Difficulty[] values = values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
            return SIMPLE;
        }

        public String getDisplayName() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }
}