package com.maha_fish.sporesrp.network;

import com.maha_fish.sporesrp.data.DifficultyManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 演化数据同步包。
 *
 * <p>冷却不传「剩余秒数」而是传**结束的绝对世界时间 tick**：
 * 客户端与服务端共用同一个世界时间，因此客户端直接用本地 gameTime 相减即可，
 * 不受网络延迟和本地 tick 抖动影响，也无需周期性重发。
 */
public record SyncEvolutionDataPacket(
        int points,
        int phase,
        String difficulty,
        /** 冷却结束的绝对世界时间 tick；0 表示无冷却。 */
        long cooldownEndTick,
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
            ByteBufCodecs.VAR_LONG, SyncEvolutionDataPacket::cooldownEndTick,
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
                    packet.cooldownEndTick,
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
        /** 冷却结束的绝对世界时间 tick；0 表示无冷却。 */
        private static long cooldownEndTick = 0;
        private static int currentThreshold = 0;
        private static int nextThreshold = 800;
        private static boolean hudEnabled = true;

        public static void setEvolutionData(int pts, int ph, String diff, long endTick,
                                            int currentThr, int nextThr) {
            // 服务端是唯一权威：直接记录冷却结束的绝对世界时间 tick。
            // 客户端与服务端共用同一个世界时间，因此这里不需要「接收时刻」做基准，
            // 也就没有网络延迟与本地 tick 抖动带来的累积误差。
            points = pts;
            phase = ph;
            difficulty = diff;
            currentThreshold = currentThr;
            nextThreshold = nextThr;
            cooldownEndTick = endTick;
        }

        public static int getPoints() { return points; }
        public static int getPhase() { return phase; }
        public static String getDifficulty() { return difficulty; }

        /**
         * 剩余冷却秒数。仅当 HUD 绘制时调用（每帧一次），
         * 只做一次取时间 + 一次减法，无分配、无状态变更。
         *
         * @return 剩余秒数；0 表示已就绪；-1 表示当前无法判断（未进入世界）
         */
        public static int getCooldownSeconds() {
            if (cooldownEndTick <= 0) return 0;
            var level = Minecraft.getInstance().level;
            if (level == null) return -1; // 还没进世界，先不显示
            long remainingTicks = cooldownEndTick - level.getGameTime();
            if (remainingTicks <= 0) return 0;
            return (int) ((remainingTicks + 19) / 20);
        }

        public static int getCurrentThreshold() { return currentThreshold; }
        public static int getNextThreshold() { return nextThreshold; }
        public static boolean isHudEnabled() { return hudEnabled; }
        public static void setHudEnabled(boolean enabled) { hudEnabled = enabled; }
    }
}
