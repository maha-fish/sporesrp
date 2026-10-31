package com.maha_fish.sporesrp.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleHUDPacket {
    private final boolean enabled;

    public ToggleHUDPacket(boolean enabled) { this.enabled = enabled; }
    public ToggleHUDPacket(FriendlyByteBuf buf) { this.enabled = buf.readBoolean(); }
    public void encode(FriendlyByteBuf buf) { buf.writeBoolean(enabled); }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            SyncEvolutionDataPacket.ClientDataHolder.setHudEnabled(enabled);
        });
        ctx.get().setPacketHandled(true);
    }
}