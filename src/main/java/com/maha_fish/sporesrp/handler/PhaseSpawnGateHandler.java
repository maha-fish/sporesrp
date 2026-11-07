package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import com.maha_fish.sporesrp.util.SporeSpawnHelper;
import com.maha_fish.sporesrp.util.SporeTierHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 阶段化的刷怪管控（对齐 SRP 演化阶段的刷怪效果）。
 *
 * <p>只作用于**天然生成**（{@code NATURAL} / {@code CHUNK_GENERATION}）：
 * <ul>
 *   <li>{@code sporesrp:infected_biome} 内的刷怪完全独立，**不干预**；</li>
 *   <li>我方召唤/转化使用的 {@code SPAWNER} / {@code CONVERSION} 也不受影响。</li>
 * </ul>
 *
 * <p>本类负责：
 * <ol>
 *   <li><b>允许等级</b> {@code spawnTiersByPhase}；</li>
 *   <li><b>刷怪量曲线</b> {@code spawnRateByPhase} + {@code spawnExtraByPhase}；</li>
 *   <li><b>非孢子生物封禁</b> {@code nonSporeSpawnBlockPhase}。</li>
 * </ol>
 *
 * <p>「阶段解锁无视阳光生成」由 {@code MonsterSpawnMixin} 直接改原版判定实现。
 */
public final class PhaseSpawnGateHandler {

    private static final ResourceKey<Biome> INFECTED_BIOME = ResourceKey.create(
            Registries.BIOME, ResourceLocation.tryParse("sporesrp:infected_biome"));

    /** FinalizeSpawn 里不能安全地立刻再生成实体，先排队到 tick 里执行。 */
    private static final ConcurrentLinkedQueue<Runnable> PENDING_SPAWNS = new ConcurrentLinkedQueue<>();

    private PhaseSpawnGateHandler() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (isInfectedBiome(level, pos)) return;

        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        Entity entity = event.getEntity();

        if (entity instanceof LivingEntity living && SporeEntityHelper.isSporeEntity(living)) {
            SporeTierHelper.Tier tier = SporeTierHelper.tierOf(living);
            if (tier == null) return;

            if (!allowedTiers(phase).contains(tier)) {
                event.setSpawnCancelled(true);
                return;
            }
            double rate = byPhase(Config.COMMON.spawnRateByPhase.get(), phase, 1.0);
            if (rate < 1.0 && level.random.nextDouble() > rate) {
                event.setSpawnCancelled(true);
                return;
            }
            double extra = byPhase(Config.COMMON.spawnExtraByPhase.get(), phase, 0.0);
            if (extra > 0.0 && level.random.nextDouble() < extra) {
                EntityType<?> extraType = living.getType();
                BlockPos extraPos = living.blockPosition();
                PENDING_SPAWNS.add(() -> SporeSpawnHelper.spawnFinalized(level, extraType, extraPos));
            }
            return;
        }

        int blockPhase = Config.COMMON.nonSporeSpawnBlockPhase.get();
        if (blockPhase > 0 && phase >= blockPhase) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int processed = 0;
        while (!PENDING_SPAWNS.isEmpty() && processed < 20) {
            try {
                PENDING_SPAWNS.poll().run();
                processed++;
            } catch (Exception ignored) {
                // 单次失败不影响其它
            }
        }
    }

    private static boolean isInfectedBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> INFECTED_BIOME.equals(key)).orElse(false);
    }

    private static Set<SporeTierHelper.Tier> allowedTiers(int phase) {
        List<? extends String> list = Config.COMMON.spawnTiersByPhase.get();
        if (list == null || list.isEmpty()) return EnumSet.allOf(SporeTierHelper.Tier.class);
        int index = Math.max(0, Math.min(phase, list.size() - 1));
        String raw = list.get(index);
        if (raw == null || raw.isBlank()) return EnumSet.allOf(SporeTierHelper.Tier.class);

        Set<SporeTierHelper.Tier> result = EnumSet.noneOf(SporeTierHelper.Tier.class);
        for (String token : raw.split(",")) {
            String name = token.trim().toUpperCase();
            if (name.isEmpty()) continue;
            try {
                result.add(SporeTierHelper.Tier.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // 写错的档名忽略
            }
        }
        return result.isEmpty() ? EnumSet.allOf(SporeTierHelper.Tier.class) : result;
    }

    private static double byPhase(List<? extends Double> list, int phase, double fallback) {
        if (list == null || list.isEmpty()) return fallback;
        int index = Math.max(0, Math.min(phase, list.size() - 1));
        Double value = list.get(index);
        return value == null ? fallback : Math.max(0.0, Math.min(1.0, value));
    }
}