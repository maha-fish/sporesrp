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
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        boolean naturalOnly = type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION;
        if (!naturalOnly && !isBlockedExtraType(type)) return;

        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        // 感染群系：刷怪表独立，本类不干预
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

            // ③ 额外追加：每个等级池独立概率，随阶段递增
            for (SporeTierHelper.Tier extraTier : allowed) {
                double extra = byPhase(extraRatios(extraTier), phase, 0.0);
                if (extra <= 0.0 || level.random.nextDouble() >= extra) continue;
                EntityType<?> extraType = randomTypeOfTier(extraTier);
                if (extraType == null) continue;
                BlockPos extraPos = living.blockPosition();
                PENDING_SPAWNS.add(() -> SporeSpawnHelper.spawnFinalized(level, extraType, extraPos));
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
     * 「阶段解锁无视阳光生成」——走 Forge 的 {@link MobSpawnEvent.SpawnPlacementCheck}。
     *
     * <p>这是原版 {@code SpawnPlacements.checkSpawnRules} 的 Forge 挂钩点，
     * 在**所有**刷怪谓词（含 Spore 自己的 {@code Infected.checkMonsterInfectedRules}）失败之后触发，
     * 因此比在 {@code Monster.checkMonsterSpawnRules} 上做 mixin 可靠：
     * 后者会被 {@code isDarkEnoughToSpawn && ...} 的短路直接跳过。
     *
     * <p>放行条件：默认结果 false、孢子命名空间、天然生成、非和平；
     * 感染群系无条件放行，其它群系需要阶段 ≥ {@code sunlightIgnorePhase}。
     */
    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getDefaultResult()) return; // 原判定已通过
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) return;

        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityType());
        if (typeId == null) return;
        // 阶段10：其它模组的自然生成同样拦截
        if (!"spore".equals(typeId.getNamespace())) {
            int blockPhase = Config.COMMON.nonSporeSpawnBlockPhase.get();
            if (blockPhase > 0 && WorldEvolutionData.get(level).getEvolutionPhase() >= blockPhase) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            }
            return;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;

        BlockPos pos = event.getPos();
        boolean inBiome = isInfectedBiome(level, pos);
        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        if (!inBiome) {
            int threshold = Config.COMMON.sunlightIgnorePhase.get();
            if (threshold < 0) {
                return;
            }
            if (phase < threshold) {
                return;
            }
        }
        event.setResult(Event.Result.ALLOW);
    }
    /**
     * 第二个光照关卡：{@code ForgeEventFactory.checkSpawnPosition} → {@code MobSpawnEvent.PositionCheck}。
     *
     * <p>自然生成会经过两道检查：
     * <ol>
     *   <li>{@code SpawnPlacements.checkSpawnRules} —— 由 {@link #onSpawnPlacementCheck} 放行；</li>
     *   <li>{@code Mob.checkSpawnRules(...) && Mob.checkSpawnObstruction(...)} —— 由本方法放行。</li>
     * </ol>
     * 只放行第 1 道时，白天仍然会在第 2 道因为光照被拦下，表现就是「白天只剩不检查光照的灾厄」。
     */
    @SubscribeEvent
    public static void onPositionCheck(net.minecraftforge.event.entity.living.MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL
                && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Entity entity = event.getEntity();
        boolean spore = entity instanceof LivingEntity living && SporeEntityHelper.isSporeEntity(living);
        if (!spore) {
            int blockPhase = Config.COMMON.nonSporeSpawnBlockPhase.get();
            if (blockPhase > 0 && WorldEvolutionData.get(level).getEvolutionPhase() >= blockPhase) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            }
            return;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;

        BlockPos pos = entity.blockPosition();
        boolean inBiome = isInfectedBiome(level, pos);
        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        if (!inBiome) {
            int threshold = Config.COMMON.sunlightIgnorePhase.get();
            if (threshold < 0) return;
            if (phase < threshold) return;
        }
        event.setResult(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
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

    /** 每个等级池的附加概率表。 */
    private static List<? extends Double> extraRatios(SporeTierHelper.Tier tier) {
        return switch (tier) {
            case SMALL -> Config.COMMON.spawnExtraSmallByPhase.get();
            case MEDIUM -> Config.COMMON.spawnExtraMediumByPhase.get();
            case LARGE -> Config.COMMON.spawnExtraLargeByPhase.get();
            case BOSS -> Config.COMMON.spawnExtraBossByPhase.get();
        };
    }

    /** 从指定等级池里随机取一个实体类型。 */
    private static EntityType<?> randomTypeOfTier(SporeTierHelper.Tier tier) {
        Set<String> pool = switch (tier) {
            case SMALL -> SporeEntityHelper.smallPoolSet();
            case MEDIUM -> SporeEntityHelper.mediumPoolSet();
            case LARGE -> SporeEntityHelper.largePoolSet();
            case BOSS -> SporeEntityHelper.bossPoolSet();
        };
        if (pool == null || pool.isEmpty()) return null;
        // 按池过滤：类器官体不参与附加生成
        List<String> ids = new ArrayList<>(pool);
        ids.removeIf(SporeEntityHelper.organoidSet()::contains);
        if (ids.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(ids.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ids.size())));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
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

    /** 非孢子生物在阶段10需要额外拦截的生成来源。 */
    private static boolean isBlockedExtraType(MobSpawnType type) {
        return type == MobSpawnType.SPAWNER
                || type == MobSpawnType.STRUCTURE
                || type == MobSpawnType.PATROL
                || type == MobSpawnType.REINFORCEMENT;
    }
}
