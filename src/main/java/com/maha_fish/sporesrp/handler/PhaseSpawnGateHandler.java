package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import com.maha_fish.sporesrp.util.SporeSpawnHelper;
import com.maha_fish.sporesrp.util.SporeTierHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 阶段化的刷怪管控（对齐 SRP 演化阶段的刷怪效果）。
 *
 * <p>只作用于**天然生成**（{@code NATURAL} / {@code CHUNK_GENERATION}）：
 * <ul>
 *   <li>{@code sporesrp:infected_biome} 内**完全不干预**（刷怪表独立，等级/密度都不限）；</li>
 *   <li>我方召唤/转化使用的 {@code SPAWNER} / {@code CONVERSION} 也不受影响。</li>
 * </ul>
 *
 * <p>本类负责（仅群系外）：
 * <ol>
 *   <li><b>允许等级</b> {@code spawnTiersByPhase}；</li>
 *   <li><b>刷怪量曲线</b> {@code spawnRateByPhase} + {@code spawnExtraByPhase}；</li>
 *   <li><b>非孢子生物封禁</b> {@code nonSporeSpawnBlockPhase}。</li>
 * </ol>
 *
 * <p>「阶段解锁无视阳光生成」由 {@code SpawnRulesMixin} 直接改原版判定实现。
 */
public final class PhaseSpawnGateHandler {

    private static final ResourceKey<Biome> INFECTED_BIOME = ResourceKey.create(
            Registries.BIOME, ResourceLocation.tryParse("sporesrp:infected_biome"));

    private static final Random RANDOM = new Random();
    /** FinalizeSpawn 里不能安全地立刻再生成实体，先排队到 tick 里执行。 */
    private static final ConcurrentLinkedQueue<Runnable> PENDING_SPAWNS = new ConcurrentLinkedQueue<>();

    private PhaseSpawnGateHandler() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());

        // 感染群系：刷怪表独立，本类完全不干预（不限等级、不限密度）
        if (isInfectedBiome(level, pos)) return;

        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        Entity entity = event.getEntity();

        if (entity instanceof LivingEntity living && SporeEntityHelper.isSporeEntity(living)) {
            SporeTierHelper.Tier tier = SporeTierHelper.tierOf(living);
            if (tier == null) return; // 无等级的功能性实体（孢气等）不管

            // ① 阶段允许等级
            Set<SporeTierHelper.Tier> allowed = allowedTiers(phase);
            if (!allowed.contains(tier)) {
                event.setSpawnCancelled(true);
                return;
            }

            // ② 刷怪量曲线：保留概率
            double rate = byPhase(Config.COMMON.spawnRateByPhase.get(), phase, 1.0);
            if (rate < 1.0 && level.random.nextDouble() > rate) {
                event.setSpawnCancelled(true);
                return;
            }

            // ③ 额外追加：高阶段从「允许等级」里随机抽一只，实现等级提升
            double extra = byPhase(Config.COMMON.spawnExtraByPhase.get(), phase, 0.0);
            if (extra > 0.0 && level.random.nextDouble() < extra) {
                EntityType<?> extraType = randomAllowedType(allowed);
                if (extraType != null) {
                    BlockPos extraPos = living.blockPosition();
                    PENDING_SPAWNS.add(() -> SporeSpawnHelper.spawnFinalized(level, extraType, extraPos));
                }
            }
            return;
        }

        // ④ 非孢子生物：阶段达到阈值后停止天然生成
        int blockPhase = Config.COMMON.nonSporeSpawnBlockPhase.get();
        if (blockPhase > 0 && phase >= blockPhase) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * 「阶段解锁无视阳光生成」——走 NeoForge 的 {@link MobSpawnEvent.SpawnPlacementCheck}。
     *
     * <p>这是原版 {@code SpawnPlacements.checkSpawnRules} 的 NeoForge 挂钩点，
     * 在**所有**刷怪谓词（含 Spore 自己的 {@code Infected.checkMonsterInfectedRules}）失败之后触发。
     *
     * <p>放行条件：默认结果 false、孢子命名空间、天然生成、非和平；
     * 感染群系无条件放行，其它群系需要阶段 ≥ {@code sunlightIgnorePhase}。
     */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getDefaultResult()) return; // 原判定已通过
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) return;

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType());
        if (typeId == null || !"spore".equals(typeId.getNamespace())) return;

        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;

        BlockPos pos = event.getPos();
        if (!isInfectedBiome(level, pos)) {
            int threshold = Config.COMMON.sunlightIgnorePhase.get();
            if (threshold < 0) return;
            if (WorldEvolutionData.get(level).getEvolutionPhase() < threshold) return;
        }
        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
    }
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
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

    /** 从允许的等级里随机抽一个实体类型（用于「额外追加」）。 */
    private static EntityType<?> randomAllowedType(Set<SporeTierHelper.Tier> allowed) {
        List<SporeTierHelper.Tier> tiers = new ArrayList<>(allowed);
        if (tiers.isEmpty()) return null;
        SporeTierHelper.Tier tier = tiers.get(RANDOM.nextInt(tiers.size()));

        Set<String> pool;
        switch (tier) {
            case SMALL: pool = SporeEntityHelper.smallPoolSet(); break;
            case MEDIUM: pool = SporeEntityHelper.mediumPoolSet(); break;
            case LARGE: pool = SporeEntityHelper.largePoolSet(); break;
            case BOSS: pool = SporeEntityHelper.bossPoolSet(); break;
            default: return null;
        }
        if (pool == null || pool.isEmpty()) return null;

        List<String> ids = new ArrayList<>(pool);
        ResourceLocation location = ResourceLocation.tryParse(ids.get(RANDOM.nextInt(ids.size())));
        return location == null ? null : BuiltInRegistries.ENTITY_TYPE.get(location);
    }

    private static double byPhase(List<? extends Double> list, int phase, double fallback) {
        if (list == null || list.isEmpty()) return fallback;
        int index = Math.max(0, Math.min(phase, list.size() - 1));
        Double value = list.get(index);
        return value == null ? fallback : Math.max(0.0, Math.min(1.0, value));
    }
}