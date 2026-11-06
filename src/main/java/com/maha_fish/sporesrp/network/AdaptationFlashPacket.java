package com.maha_fish.sporesrp.network;

import com.maha_fish.sporesrp.Config;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 适应状态闪光包（服务端 -> 客户端）。
 *
 * <p>对应原版 SRP 的伤害适应视觉反馈：
 * <ul>
 *   <li>{@link #KIND_ADAPTING} —— 本次打击产生了适应 → 全身绿光</li>
 *   <li>{@link #KIND_FULL} —— 该伤害类型已达完全适应 → 全身紫光</li>
 * </ul>
 *
 * <p>只发「一次受击」的瞬时状态，不做持久同步：客户端收到后记录到期 tick，
 * 渲染时按 entityId 查询，过期自动清除。
 *
 * <p>多段实体（噬袭魔环节等）**不单独发包**：客户端渲染时会把环节解析回父体，
 * 直接复用父体的闪光状态，因此所有环节天然同时变色。
 */
public class AdaptationFlashPacket {

    public static final byte KIND_ADAPTING = 1;
    public static final byte KIND_FULL = 2;

    private final int entityId;
    private final byte kind;
    private final int durationTicks;

    public AdaptationFlashPacket(int entityId, byte kind, int durationTicks) {
        this.entityId = entityId;
        this.kind = kind;
        this.durationTicks = durationTicks;
    }

    public AdaptationFlashPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.kind = buf.readByte();
        this.durationTicks = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(kind);
        buf.writeVarInt(durationTicks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientFlashState.flash(entityId, kind, durationTicks));
        ctx.get().setPacketHandled(true);
    }

    /**
     * 客户端闪光状态。
     *
     * <p>独立成嵌套类：只有客户端收到包时才会加载它，
     * 专用服务器不会因此加载任何客户端类（与本模组其它包的做法一致）。
     */
    public static class ClientFlashState {

        private static final Map<Integer, long[]> FLASHES = new HashMap<>();

        private ClientFlashState() {
        }

        public static void flash(int entityId, byte kind, int durationTicks) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return;
            int duration = durationTicks <= 0
                    ? Config.COMMON.adaptationFlashDurationTicks.get()
                    : durationTicks;
            FLASHES.put(entityId, new long[]{kind, level.getGameTime() + duration});
        }

        /** @return 0 = 无闪光；{@link #KIND_ADAPTING} / {@link #KIND_FULL} */
        public static byte kindOf(int entityId) {
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
            return (byte) entry[0];
        }

        /** 退出世界时清空，避免 entityId 在不同世界间串号。 */
        public static void clear() {
            FLASHES.clear();
        }
    }
}
