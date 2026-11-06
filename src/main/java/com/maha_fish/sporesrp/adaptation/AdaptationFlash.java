package com.maha_fish.sporesrp.adaptation;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.network.AdaptationFlashPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

/**
 * 伤害适应的视觉/听觉反馈（服务端触发）。
 *
 * <p>原版 SRP 的表现（mcmod「伤害适应性」）：
 * <ul>
 *   <li>本次产生适应 → 全身绿光 + 红色星状颗粒</li>
 *   <li>该伤害类型完全适应 → 全身紫光 + 金属敲击音</li>
 *   <li>普通受击 → 原版红光（无需处理）</li>
 * </ul>
 *
 * <p>与原版的差异：原版是**概率**产生适应，我们是**每次命中必累积**，
 * 所以默认用 {@code milestone} 模式（每跨过 1/4 进度才闪一次），避免每一下都在闪。
 * 这一点只影响视觉，不影响减伤数值。
 */
public final class AdaptationFlash {

    /** 闪光生效的玩家范围（格）。 */
    private static final double SEND_RADIUS = 64.0;

    private AdaptationFlash() {
    }

    /**
     * 判定并触发一次反馈。
     *
     * @param entity 受击的孢子生物（多段实体只需父体调用一次）
     * @param before 本次受击前的有效减伤（已按上限截断）
     * @param after  本次受击后的有效减伤（已按上限截断）
     * @param max    当前允许的最大减伤
     */
    /**
     * 触发一次反馈。
     *
     * @param kind {@link AdaptationFlashPacket#KIND_ADAPTING}（绿光）或
     *             {@link AdaptationFlashPacket#KIND_FULL}（紫光）
     */
    public static void trigger(LivingEntity entity, int kind) {
        if (!Config.COMMON.adaptationVisualEnabled.get()) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        int duration = Config.COMMON.adaptationFlashDurationTicks.get();
        AdaptationFlashPacket packet = new AdaptationFlashPacket(entity.getId(), (byte) kind, duration);

        double radiusSq = SEND_RADIUS * SEND_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) > radiusSq) continue;
            SporeSRP.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        if (Config.COMMON.adaptationFlashParticles.get()) {
            int count = Config.COMMON.adaptationFlashParticleCount.get();
            if (count > 0) {
                level.sendParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        count, entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.35, entity.getBbWidth() * 0.4,
                        0.05);
            }
        }

        if (kind == AdaptationFlashPacket.KIND_FULL && Config.COMMON.adaptationFullSound.get()) {
            level.playSound(null, entity.blockPosition(), SoundEvents.ANVIL_LAND,
                    SoundSource.HOSTILE, 0.7F, 0.6F);
        }
    }


}
