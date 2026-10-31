package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MoundHandler {
    private static final Random RANDOM = new Random();
    private static int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!Config.COMMON.enableMoundSystem.get()) return;
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < Config.COMMON.moundSpawnIntervalTicks.get()) return;
        tickCounter = 0;

        // 没有玩家则直接跳过
        if (event.getServer().getPlayerList().getPlayerCount() == 0) return;

        trySpawnMounds(event.getServer().getAllLevels());
    }

    private void trySpawnMounds(Iterable<ServerLevel> levels) {
        // 获取配置
        int maxGlobal = Config.COMMON.moundSpawnMaxPerTick.get();
        if (maxGlobal <= 0) return;
        int radius = Config.COMMON.moundSpawnRadius.get();
        double baseChance = Config.COMMON.moundSpawnBaseChance.get();
        double phaseBonus = Config.COMMON.moundSpawnPhaseBonus.get();

        // 菌囊实体类型只解析一次（原实现每次尝试都构造 ResourceLocation 并查注册表）
        EntityType<?> moundType = SporeEntityHelper.getMoundType();

        // 为每个玩家收集其周围的孢子实体
        for (ServerLevel level : levels) {
            List<ServerPlayer> players = level.getPlayers(pl -> true);
            if (players.isEmpty()) continue;

            // 收集所有潜在候选（孢子实体）的集合，但只考虑玩家半径内的
            // 使用 Set 去重，因为同一实体可能被多个玩家覆盖
            Set<LivingEntity> candidates = new HashSet<>();
            for (ServerPlayer player : players) {
                AABB playerBox = player.getBoundingBox().inflate(radius);
                // 直接获取该范围内的孢子实体（只生成在玩家附近）
                List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class, playerBox,
                        e -> e.isAlive() && SporeEntityHelper.isSporeEntity(e));
                candidates.addAll(nearby);
            }

            if (candidates.isEmpty()) continue;

            // 阶段在整个循环内不会变化，原实现在循环内重复取同一对象
            WorldEvolutionData data = WorldEvolutionData.get(level);
            int phase = data.getEvolutionPhase();

            // 随机打乱后处理
            List<LivingEntity> shuffled = new ArrayList<>(candidates);
            Collections.shuffle(shuffled, RANDOM);
            int spawned = 0;

            for (LivingEntity entity : shuffled) {
                if (spawned >= maxGlobal) break;

                if (phase <= 0) continue;

                double chance = baseChance + phaseBonus * phase;
                if (chance > 0.5) chance = 0.5;
                if (RANDOM.nextDouble() > chance) continue;

                // 检查周围是否已有菌囊
                // 原实现取范围内所有 LivingEntity 再逐个查注册名；直接按菌囊类型过滤，
                // 匹配集合完全相同，但不必再遍历/查询无关实体。
                AABB box = entity.getBoundingBox().inflate(radius);
                boolean hasMound = !level.getEntitiesOfClass(LivingEntity.class, box,
                        SporeEntityHelper::isMound).isEmpty();
                if (hasMound) continue;

                if (moundType == null) continue;

                // 尝试生成
                for (int attempt = 0; attempt < 10; attempt++) {
                    int dx = RANDOM.nextInt(radius * 2 + 1) - radius;
                    int dz = RANDOM.nextInt(radius * 2 + 1) - radius;
                    int dy = RANDOM.nextInt(5) - 2;
                    BlockPos spawnPos = entity.blockPosition().offset(dx, dy, dz);
                    if (!level.isLoaded(spawnPos)) continue;
                    if (level.getBlockState(spawnPos.below()).isSolid() && level.getBlockState(spawnPos).isAir()) {
                        var mound = moundType.create(level);
                        if (mound != null) {
                            mound.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                            level.addFreshEntity(mound);
                            spawned++;
                            break;
                        }
                    }
                }
            }
        }
    }
}
