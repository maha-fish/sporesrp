package com.maha_fish.sporesrp.battle;

import com.maha_fish.sporesrp.data.WorldEvolutionData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

public class BattleDifficulty {

    private final ServerLevel level;
    private final BlockPos center;
    private final int playerCount;
    private final boolean keepInventory;
    private final int evolutionPhase;
    private final double difficultyMultiplier;

    public BattleDifficulty(ServerLevel level, BlockPos center) {
        this.level = level;
        this.center = center;

        this.playerCount = level.getPlayers(p ->
                p.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= 16 * 16
        ).size();

        this.keepInventory = level.getServer().getWorldData().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        this.evolutionPhase = WorldEvolutionData.get(level).getEvolutionPhase();
        this.difficultyMultiplier = WorldEvolutionData.get(level).getDifficulty().getMultiplier();
    }

    public int getBaseDifficulty() {
        return Math.max(1, playerCount / 4);
    }

    public int getModifiedDifficulty() {
        int base = getBaseDifficulty();
        int bonus = keepInventory ? 2 : 0;
        return Math.max(1, base + bonus);
    }

    public int getFinalDifficulty() {
        return (int) (getModifiedDifficulty() * difficultyMultiplier);
    }

    public int getExtraSpawnsPerMinute() {
        return (int) (getFinalDifficulty() * 0.5 + 1);
    }

    public int getPlayerCount() { return playerCount; }
    public int getEvolutionPhase() { return evolutionPhase; }
    public double getDifficultyMultiplier() { return difficultyMultiplier; }
}