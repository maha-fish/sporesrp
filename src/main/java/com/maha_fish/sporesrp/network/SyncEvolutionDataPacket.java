package com.maha_fish.sporesrp.network;

import com.maha_fish.sporesrp.data.DifficultyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncEvolutionDataPacket {
    private final int points;
    private final int phase;
    private final String difficulty;
    /** 冷却结束的**绝对世界时间 tick**；0 表示无冷却。 */
    private final long cooldownEndTick;
    private final int currentThreshold;
    private final int nextThreshold;

    public SyncEvolutionDataPacket(int points, int phase, DifficultyManager.Difficulty difficulty,
                                   long cooldownEndTick, int currentThreshold, int nextThreshold) {
        this.points = points;
        this.phase = phase;
        this.difficulty = difficulty.name();
        this.cooldownEndTick = cooldownEndTick;
        this.currentThreshold = currentThreshold;
        this.nextThreshold = nextThreshold;
    }

    public SyncEvolutionDataPacket(FriendlyByteBuf buf) {
        this.points = buf.readInt();
        this.phase = buf.readInt();
        this.difficulty = buf.readUtf();
        this.cooldownEndTick = buf.readLong();
        this.currentThreshold = buf.readInt();
        this.nextThreshold = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(points);
        buf.writeInt(phase);
        buf.writeUtf(difficulty);
        buf.writeLong(cooldownEndTick);
        buf.writeInt(currentThreshold);
        buf.writeInt(nextThreshold);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientDataHolder.setEvolutionData(points, phase, difficulty, cooldownEndTick,
                    currentThreshold, nextThreshold);
        });
        ctx.get().setPacketHandled(true);
    }

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