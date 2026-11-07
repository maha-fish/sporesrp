package com.maha_fish.sporesrp.client;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.network.SyncEvolutionDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

/**
 * 演化阶段的环境氛围效果（纯客户端）。
 *
 * <ul>
 *   <li><b>迷雾</b>：阶段越高，雾的终点越近（能见度越低）；</li>
 *   <li><b>雾色</b>：按阶段向暗红混合，越晚越红；</li>
 *   <li><b>孢子</b>：达到配置阶段后，玩家周围持续飘散孢子粒子。</li>
 * </ul>
 *
 * <p>所有参数都在 {@code Config.COMMON} 的 {@code environment} 段，可整段关闭。
 * 阶段数值取自同步包（客户端已有），因此本类不产生任何网络与服务器开销。
 *
 * <p>注意：{@code RenderFog} 必须 {@code setCanceled(true)} 才会让自定义距离生效 ——
 * Forge 侧的分发逻辑是 {@code if (EVENT_BUS.post(event))}，post 返回的就是「是否被取消」。
 */
public class EnvironmentEffectsHandler {

    /** 粒子的亚 tick 累积器，避免每 tick 取整导致密度偏低。 */
    private static double particleCarry;

    private EnvironmentEffectsHandler() {
    }

    /** 孢子感染群系。 */
    private static final ResourceKey<Biome> INFECTED_BIOME = ResourceKey.create(
            Registries.BIOME, ResourceLocation.tryParse("sporesrp:infected_biome"));

    /** 玩家当前是否站在孢子感染群系内。 */
    private static boolean inInfectedBiome() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        return mc.level.getBiome(mc.player.blockPosition())
                .unwrapKey()
                .map(key -> key.equals(INFECTED_BIOME))
                .orElse(false);
    }
    private static int phase() {
        return SyncEvolutionDataPacket.ClientDataHolder.getPhase();
    }

    /** 取按阶段列表的值；越界或未配置返回 fallback。 */
    private static double byPhase(List<? extends Double> list, int phase, double fallback) {
        if (phase < 0 || phase >= list.size()) return fallback;
        Double v = list.get(phase);
        return v == null ? fallback : v;
    }

    // ==================== 雾距离 ====================

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!Config.COMMON.environmentEnabled.get()) return;

        // 群系浓雾优先于阶段雾：进入感染群系后强制近距离雾
        if (Config.COMMON.environmentBiomeFogEnabled.get() && inInfectedBiome()) {
            float near = Config.COMMON.environmentBiomeFogNear.get().floatValue();
            float far = Config.COMMON.environmentBiomeFogFar.get().floatValue();
            if (near >= far) near = far * 0.5F;
            event.setNearPlaneDistance(near);
            event.setFarPlaneDistance(far);
            event.setCanceled(true);
            return;
        }

        if (!Config.COMMON.environmentFogEnabled.get()) return;

        int phase = phase();
        // far <= 0 表示该阶段不改雾（保持原版/其他模组的行为）
        double far = byPhase(Config.COMMON.environmentFogFarByPhase.get(), phase, 0.0);
        if (far <= 0.0) return;

        double near = Math.max(0.0, byPhase(Config.COMMON.environmentFogNearByPhase.get(), phase, 0.0));
        if (near >= far) near = far * 0.5;

        event.setNearPlaneDistance((float) near);
        event.setFarPlaneDistance((float) far);
        event.setCanceled(true); // 必须取消，否则原版会用自己的距离覆盖
    }

    // ==================== 雾色 ====================

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!Config.COMMON.environmentEnabled.get()) return;
        if (!Config.COMMON.environmentFogEnabled.get()) return;


        double t = byPhase(Config.COMMON.environmentFogTintByPhase.get(), phase(), 0.0);
        if (t <= 0.0) return;
        if (t > 1.0) t = 1.0;

        double tr = Config.COMMON.environmentFogTintRed.get();
        double tg = Config.COMMON.environmentFogTintGreen.get();
        double tb = Config.COMMON.environmentFogTintBlue.get();

        event.setRed((float) (event.getRed() + (tr - event.getRed()) * t));
        event.setGreen((float) (event.getGreen() + (tg - event.getGreen()) * t));
        event.setBlue((float) (event.getBlue() + (tb - event.getBlue()) * t));
    }

    // ==================== 孢子粒子 ====================

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Config.COMMON.environmentEnabled.get()) return;
        if (!Config.COMMON.environmentParticlesEnabled.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;

        if (phase() < Config.COMMON.environmentParticlePhase.get()) {
            particleCarry = 0.0;
            return;
        }

        particleCarry += Config.COMMON.environmentParticlesPerSecond.get() / 20.0;
        int count = (int) particleCarry;
        if (count <= 0) return;
        particleCarry -= count;

        var random = mc.level.random;
        for (int i = 0; i < count; i++) {
            double dx = (random.nextDouble() - 0.5) * 8.0;
            double dy = random.nextDouble() * 4.0 - 1.0;
            double dz = (random.nextDouble() - 0.5) * 8.0;
            mc.level.addParticle(ParticleTypes.CRIMSON_SPORE,
                    mc.player.getX() + dx,
                    mc.player.getY() + dy,
                    mc.player.getZ() + dz,
                    0.0, 0.01, 0.0);
        }
    }
}
