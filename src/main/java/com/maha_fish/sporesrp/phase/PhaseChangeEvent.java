package com.maha_fish.sporesrp.phase;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.Event;

public class PhaseChangeEvent extends Event {
    private final int oldPhase;
    private final int newPhase;
    private final ServerLevel level;

    public PhaseChangeEvent(int oldPhase, int newPhase, ServerLevel level) {
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
        this.level = level;
    }

    public int getOldPhase() { return oldPhase; }
    public int getNewPhase() { return newPhase; }
    public ServerLevel getLevel() { return level; }
}