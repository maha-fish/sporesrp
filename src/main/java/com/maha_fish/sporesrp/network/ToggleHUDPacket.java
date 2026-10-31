package com.maha_fish.sporesrp.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleHUDPacket(boolean enabled) implements CustomPacketPayload {
    public static final Type<ToggleHUDPacket> TYPE = new Type<>(
            ResourceLocation.parse("sporesrp:toggle_hud")
    );

    public static final StreamCodec<ByteBuf, ToggleHUDPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ToggleHUDPacket::enabled,
            ToggleHUDPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 客户端处理
    public static void handleClient(ToggleHUDPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SyncEvolutionDataPacket.ClientDataHolder.setHudEnabled(packet.enabled);
        });
    }

    // 服务端处理（本例中不处理服务端接收）
    public static void handleServer(ToggleHUDPacket packet, IPayloadContext ctx) {
        // 服务端不处理此包
    }
}