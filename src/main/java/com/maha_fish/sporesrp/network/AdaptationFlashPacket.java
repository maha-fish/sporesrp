package com.maha_fish.sporesrp.network;

import com.maha_fish.sporesrp.Config;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 适应状态闪光包（服务端 -> 客户端）。
 *
 * <p>对应原版 SRP 的伤害适应视觉反馈：
 * <ul>
 *   <li>{@link #KIND_ADAPTING} —— 本次打击产生了适应 → 全身绿光</li>
 *   <li>{@link #KIND_FULL} —— 该伤害类型已达完全适应 → 全身紫光</li>
 * </ul>
 *
 * <p>只发「一次受击」的瞬时状态，不做持久同步。
 * 多段实体（噬袭魔环节等）不单独发包：客户端渲染时把环节解析回父体，复用父体的闪光状态。
 */
public record AdaptationFlashPacket(int entityId, int kind, int durationTicks)
        implements CustomPacketPayload {

    public static final int KIND_ADAPTING = 1;
    public static final int KIND_FULL = 2;

    public static final Type<AdaptationFlashPacket> TYPE = new Type<>(
            ResourceLocation.parse("sporesrp:adaptation_flash"));

    public static final StreamCodec<ByteBuf, AdaptationFlashPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AdaptationFlashPacket::entityId,
            ByteBufCodecs.VAR_INT, AdaptationFlashPacket::kind,
            ByteBufCodecs.VAR_INT, AdaptationFlashPacket::durationTicks,
            AdaptationFlashPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(AdaptationFlashPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientFlashState.flash(packet.entityId, packet.kind, packet.durationTicks));
    }

    public static void handleServer(AdaptationFlashPacket packet, IPayloadContext ctx) {
        // 服务端不处理
    }

    /** 客户端闪光状态（只有客户端收到包时才会加载）。 */
    public static class ClientFlashState {

        private static final Map<Integer, long[]> FLASHES = new HashMap<>();

        private ClientFlashState() {
        }

        public static void flash(int entityId, int kind, int durationTicks) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return;
            int duration = durationTicks <= 0
                    ? Config.COMMON.adaptationFlashDurationTicks.get()
                    : durationTicks;
            FLASHES.put(entityId, new long[]{kind, level.getGameTime() + duration});
        }

        /** @return 0 = 无闪光；KIND_ADAPTING / KIND_FULL */
        public static int kindOf(int entityId) {
            long[] entry = FLASHES.get(entityId);
            if (entry == null) return 0;
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) {
                FLASHES.clear();
                return 0;
            }
            if (level.getGameTime() > entry[1]) {
                FLASHES.remove(entityId);
                return 0;
            }
            return (int) entry[0];
        }

        public static void clear() {
            FLASHES.clear();
        }
    }
}