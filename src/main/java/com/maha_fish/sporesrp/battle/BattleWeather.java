package com.maha_fish.sporesrp.battle;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗期间的天气与闪电。
 *
 * <p>天气是**维度级**状态，因此开战时先把原始值快照进 {@link BattleData}，
 * 结束时再还原；同维度存在多场战斗时用引用计数，只有最后一场结束才真正还原。
 */
public final class BattleWeather {

    /** 天气快照。 */
    public record Snapshot(int clearTime, int rainTime, boolean raining, boolean thundering) {
    }

    /** 每个维度当前有多少场战斗在强制雷雨。 */
    private static final Map<ResourceKey<Level>, Integer> ACTIVE = new ConcurrentHashMap<>();

    private BattleWeather() {
    }

    public static Snapshot snapshot(ServerLevel level) {
        // 天气计时器在 ServerLevelData 上（LevelData 接口本身没有这几个 getter）
        net.minecraft.world.level.storage.ServerLevelData data =
                (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
        return new Snapshot(data.getClearWeatherTime(), data.getRainTime(),
                level.isRaining(), level.isThundering());
    }

    /** 开战：保存快照并转为雷雨。 */
    public static void begin(ServerLevel level, BattleData data, int estimatedTicks) {
        data.setWeatherSnapshot(snapshot(level));
        ACTIVE.merge(level.dimension(), 1, Integer::sum);
        if (Config.COMMON.battleThunderstorm.get()) {
            level.setWeatherParameters(0, Math.max(1200, estimatedTicks), true, true);
        }
    }

    /** 战斗中周期性断言：防止玩家用床/指令改天气。 */
    public static void assertThunder(ServerLevel level) {
        if (!Config.COMMON.battleThunderstorm.get()) return;
        if (!level.isRaining() || !level.isThundering()) {
            level.setWeatherParameters(0, 1200, true, true);
        }
    }

    /** 结束：最后一场战斗才还原原始天气。 */
    public static void release(ServerLevel level, BattleData data) {
        data.setWeatherApplied(true);
        Integer count = ACTIVE.get(level.dimension());
        if (count != null && count > 1) {
            ACTIVE.put(level.dimension(), count - 1);
            return;
        }
        ACTIVE.remove(level.dimension());
        Snapshot s = data.getWeatherSnapshot();
        if (s == null) return;
        level.setWeatherParameters(s.clearTime(), s.rainTime(), s.raining(), s.thundering());
    }

    /**
     * 在竞技场环内随机落一道闪电。
     *
     * <p>落点避开核心/信标安全半径，且最多重掷 6 次。
     */
    public static void strike(ServerLevel level, BlockPos center, BlockPos corePos) {
        if (!Config.COMMON.battleLightningEnabled.get()) return;

        int min = Config.COMMON.battleLightningRadiusMin.get();
        int max = Math.max(min + 1, Config.COMMON.battleLightningRadiusMax.get());
        int safe = Config.COMMON.battleLightningSafeRadius.get();
        double safeSq = (double) safe * safe;

        RandomSource rnd = level.random;
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            int dist = min + rnd.nextInt(max - min + 1);
            int x = center.getX() + (int) (Math.cos(angle) * dist);
            int z = center.getZ() + (int) (Math.sin(angle) * dist);

            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));

            if (corePos != null && surface.distSqr(corePos) < safeSq) continue;
            if (surface.distSqr(center) < safeSq) continue;

            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) return;
            bolt.moveTo(surface.getX() + 0.5, surface.getY(), surface.getZ() + 0.5);
            level.addFreshEntity(bolt);
            return;
        }
        SporeSRP.LOGGER.debug("Battle lightning: no valid strike position near {}", center);
    }
}