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
    private final int cooldownSeconds;
    private final int currentThreshold;
    private final int nextThreshold;

    public SyncEvolutionDataPacket(int points, int phase, DifficultyManager.Difficulty difficulty,
                                   int cooldownSeconds, int currentThreshold, int nextThreshold) {
        this.points = points;
        this.phase = phase;
        this.difficulty = difficulty.name();
        this.cooldownSeconds = cooldownSeconds;
        this.currentThreshold = currentThreshold;
        this.nextThreshold = nextThreshold;
    }

    public SyncEvolutionDataPacket(FriendlyByteBuf buf) {
        this.points = buf.readInt();
        this.phase = buf.readInt();
        this.difficulty = buf.readUtf();
        this.cooldownSeconds = buf.readInt();
        this.currentThreshold = buf.readInt();
        this.nextThreshold = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(points);
        buf.writeInt(phase);
        buf.writeUtf(difficulty);
        buf.writeInt(cooldownSeconds);
        buf.writeInt(currentThreshold);
        buf.writeInt(nextThreshold);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientDataHolder.setEvolutionData(points, phase, difficulty, cooldownSeconds,
                    currentThreshold, nextThreshold);
        });
        ctx.get().setPacketHandled(true);
    }

    public static class ClientDataHolder {
        private static int points = 0;
        private static int phase = 0;
        private static String difficulty = "SIMPLE";
        private static int cooldownSeconds = -1;
        private static long receiveWorldTime = 0; // 使用客户端世界时间（tick）
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
                return cooldownSeconds; // 安全兜底，不递减
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