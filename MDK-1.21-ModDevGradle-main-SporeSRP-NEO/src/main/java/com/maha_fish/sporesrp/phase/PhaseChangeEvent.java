package com.maha_fish.sporesrp.phase;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

public class PhaseChangeEvent extends Event {
    private final int oldPhase;
    private final int newPhase;
    private final ServerLevel level;

    public PhaseChangeEvent(int oldPhase, int newPhase, ServerLevel level) {
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
        this.level = level;
        NeoForge.EVENT_BUS.post(new PhaseChangeEvent(oldPhase, newPhase, level));
    }

    public int getOldPhase() { return oldPhase; }
    public int getNewPhase() { return newPhase; }
    public ServerLevel getLevel() { return level; }
}