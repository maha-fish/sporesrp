package com.maha_fish.sporesrp.mixin;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「阶段解锁无视阳光生成」——直接改原版刷怪判定，而不是另开刷怪通道。
 *
 * <p>目标：{@code Monster.checkMonsterSpawnRules(...)}。
 * 它在 {@code isDarkEnoughToSpawn} 失败时返回 {@code false}（白天不刷怪）。
 * 我们在返回值处拦截：只要满足
 * <ul>
 *   <li>实体属于 {@code spore} 命名空间；</li>
 *   <li>是 {@code NATURAL} / {@code CHUNK_GENERATION} 的天然生成；</li>
 *   <li>难度不是和平；</li>
 *   <li><b>感染群系无视阶段与昼夜</b>，始终放行；</li>
 *   <li>其它群系需要世界演化阶段 ≥ {@code sunlightIgnorePhase}；</li>
 * </ul>
 * 就把结果改成 {@code true}，让孢子生物在白天也能自然生成。
 *
 * <p>只影响孢子生物，原版怪物（僵尸等）白天仍然不刷。
 */
@Mixin(Monster.class)
public class MonsterSpawnMixin {


    /** 孢子感染群系：**无论阶段、无论昼夜**都要刷怪。 */
    private static final ResourceKey<Biome> SPORESRP_INFECTED_BIOME = ResourceKey.create(
            Registries.BIOME, ResourceLocation.tryParse("sporesrp:infected_biome"));
    /** 每维度阶段缓存：{tick, phase}，避免每次刷怪判定都跑一遍阶段计算。 */
    private static final Map<ResourceKey<net.minecraft.world.level.Level>, long[]> PHASE_CACHE = new ConcurrentHashMap<>();

    @Inject(method = "checkMonsterSpawnRules", at = @At("RETURN"), cancellable = true)
    private static void sporesrp$ignoreSunlight(EntityType<? extends Monster> type,
                                                ServerLevelAccessor accessor,
                                                MobSpawnType spawnType,
                                                BlockPos pos,
                                                RandomSource random,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // 已经可以生成，不用管
        if (spawnType != MobSpawnType.NATURAL && spawnType != MobSpawnType.CHUNK_GENERATION) return;

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (typeId == null || !"spore".equals(typeId.getNamespace())) return;

        if (!(accessor instanceof ServerLevel level)) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;

        // ① 感染群系：无论演化阶段、无论昼夜，一律放行
        if (level.getBiome(pos).unwrapKey()
                .map(key -> SPORESRP_INFECTED_BIOME.equals(key)).orElse(false)) {
            cir.setReturnValue(true);
            return;
        }

        // ② 其它群系：达到阶段阈值后放行
        int threshold = Config.COMMON.sunlightIgnorePhase.get();
        if (threshold < 0) return;
        if (currentPhase(level) < threshold) return;

        cir.setReturnValue(true);
    }

    /** 带缓存的阶段读取（每 20 tick 刷新一次）。 */
    private static int currentPhase(ServerLevel level) {
        long now = level.getGameTime();
        long[] cached = PHASE_CACHE.get(level.dimension());
        if (cached != null && now - cached[0] < 20) return (int) cached[1];

        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        PHASE_CACHE.put(level.dimension(), new long[]{now, phase});
        return phase;
    }
}
