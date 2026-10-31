package com.maha_fish.sporesrp.network;

import com.maha_fish.sporesrp.data.DifficultyManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncEvolutionDataPacket(
        int points,
        int phase,
        String difficulty,
        int cooldownSeconds,
        int currentThreshold,
        int nextThreshold
) implements CustomPacketPayload {
    public static final Type<SyncEvolutionDataPacket> TYPE = new Type<>(
            ResourceLocation.parse("sporesrp:sync_evolution_data")
    );

    public static final StreamCodec<ByteBuf, SyncEvolutionDataPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SyncEvolutionDataPacket::points,
            ByteBufCodecs.INT, SyncEvolutionDataPacket::phase,
            ByteBufCodecs.STRING_UTF8, SyncEvolutionDataPacket::difficulty,
            ByteBufCodecs.INT, SyncEvolutionDataPacket::cooldownSeconds,
            ByteBufCodecs.INT, SyncEvolutionDataPacket::currentThreshold,
            ByteBufCodecs.INT, SyncEvolutionDataPacket::nextThreshold,
            SyncEvolutionDataPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // 客户端处理
    public static void handleClient(SyncEvolutionDataPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientDataHolder.setEvolutionData(
                    packet.points,
                    packet.phase,
                    packet.difficulty,
                    packet.cooldownSeconds,
                    packet.currentThreshold,
                    packet.nextThreshold
            );
        });
    }

    // 服务端处理（本例中不处理服务端接收）
    public static void handleServer(SyncEvolutionDataPacket packet, IPayloadContext ctx) {
        // 服务端不处理此包
    }

    // ========== 客户端数据持有者 ==========
    public static class ClientDataHolder {
        private static int points = 0;
        private static int phase = 0;
        private static String difficulty = "SIMPLE";
        private static int cooldownSeconds = -1;
        private static long receiveWorldTime = 0;
        private static int currentThreshold = 0;
        private static int nextThreshold = 800;
        private static boolean hudEnabled = true;

        public static void setEvolutionData(int pts, int ph, String diff, int cdSec,
                                            int currentThr, int nextThr) {
            // 服务端是唯一权威：无条件接受它给的冷却秒数，
            // 并以本次接收时刻为基准重新开始本地倒数。
            // （旧实现「只接受更小的秒数」，会把诱饵的加法冷却全部丢掉。）
            points = pts;
            phase = ph;
            difficulty = diff;
            currentThreshold = currentThr;
            nextThreshold = nextThr;
            cooldownSeconds = cdSec;
            receiveWorldTime = (Minecraft.getInstance().level != null)
                    ? Minecraft.getInstance().level.getGameTime() : 0;
        }

        public static int getPoints() { return points; }
        public static int getPhase() { return phase; }
        public static String getDifficulty() { return difficulty; }

        public static int getCooldownSeconds() {
            if (cooldownSeconds < 0) return -1;
            if (Minecraft.getInstance().level == null) {
                return cooldownSeconds;
            }
            long now = Minecraft.getInstance().level.getGameTime();
            long elapsedTicks = now - receiveWorldTime;
            long elapsedSeconds = elapsedTicks / 20;
            int remaining = cooldownSeconds - (int) elapsedSeconds;
            return Math.max(0, remaining);
        }

        public static int getCurrentThreshold() { return currentThreshold; }
        public static int getNextThreshold() { return nextThreshold; }
        public static boolean isHudEnabled() { return hudEnabled; }
        public static void setHudEnabled(boolean enabled) { hudEnabled = enabled; }
    }
}