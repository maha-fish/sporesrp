package com.maha_fish.sporesrp.battle;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.block.ModBlocks;
import com.maha_fish.sporesrp.data.DifficultyManager;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.LogicalSidedProvider;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PurificationBattle {

    private static PurificationBattle INSTANCE;
    private final Map<DimensionPos, BattleData> activeBattles = new ConcurrentHashMap<>();
    private final Map<DimensionPos, Long> musicLoopTickMap = new ConcurrentHashMap<>(); // 下次播放 loop 的 tick
    private int tickCounter = 0;

    // 怪物生成距离（20-40 格）
    private static final int SPAWN_MIN_DIST = 20;
    private static final int SPAWN_MAX_DIST = 40;

    // ★★★ 音乐配置 ★★★
    private static final String MUSIC_INTRO = "spore:insolence_intro";
    private static final String MUSIC_LOOP = "spore:insolence";
    private static final int INTRO_DURATION_TICKS = 12 * 20;  // 12 秒


    // 备用怪物池（当配置池为空时使用）
    private static final List<String> FALLBACK_POOL_PHASE1 = Arrays.asList(
            "spore:inf_human", "spore:inf_villager", "spore:knight", "spore:busser"
    );
    private static final List<String> FALLBACK_POOL_PHASE2 = Arrays.asList(
            "spore:inquisitor", "spore:brot"
    );
    private static final List<String> FALLBACK_POOL_PHASE3 = Arrays.asList(
            "spore:knight", "spore:busser", "spore:inquisitor", "spore:brot"
    );
    private static final List<String> FALLBACK_POOL_PHASE4 = Arrays.asList(
            "spore:sieger", "spore:howitzer"
    );

    // ==================== 维度复合键 ====================

    public static class DimensionPos {
        public final ResourceKey<Level> dimension;
        public final BlockPos pos;

        public DimensionPos(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DimensionPos that = (DimensionPos) o;
            return Objects.equals(dimension, that.dimension) && Objects.equals(pos, that.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, pos);
        }
    }

    // ==================== 单例 ====================

    private PurificationBattle() {}

    public static PurificationBattle getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PurificationBattle();
        }
        return INSTANCE;
    }

    // ==================== 物品栏提示 ====================

    private static final int ACTION_BAR_RANGE = 64; // 只发送给 64 格内的玩家

    private void sendActionBar(ServerLevel level, Component message, BlockPos center) {
        if (level == null || center == null) return;
        double rangeSq = ACTION_BAR_RANGE * ACTION_BAR_RANGE;
        Vec3 centerVec = Vec3.atCenterOf(center); // ★ 转换 BlockPos → Vec3
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(centerVec) <= rangeSq)) {
            player.displayClientMessage(message, true);
        }
    }

    // ==================== 音乐控制 ====================

    /**
     * 播放指定音乐（通过 /playsound 命令，单次播放）
     */
    private void playMusic(ServerLevel level, String soundId, float volume, float pitch) {
        if (soundId == null || soundId.trim().isEmpty()) {
            SporeSRP.LOGGER.debug("Music ID is empty, skipping");
            return;
        }
        String command = String.format("playsound %s music @a ~ ~ ~ %f %f 1", soundId, volume, pitch);
        var source = level.getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
        SporeSRP.LOGGER.info("Playing music: {}", soundId);
    }

    /**
     * 停止所有音乐（通过 /stopsound 命令）
     */
    private void stopMusic(ServerLevel level) {
        String command = "stopsound @a music";
        var source = level.getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
        SporeSRP.LOGGER.info("Music stopped");
    }

    /**
     * 播放开场音乐
     */
    private void playIntroMusic(ServerLevel level, BattleData data) {
        // 先停止所有音乐
        stopMusic(level);

        // 播放 intro（不循环，只播放一次）
        playMusic(level, MUSIC_INTRO, 1.0f, 1.0f);

        // 记录状态
        data.setMusicIntroPlaying(true);
        data.setMusicStartTick(level.getServer().getTickCount());

        SporeSRP.LOGGER.info("Intro music started: {} ({} seconds)", MUSIC_INTRO, INTRO_DURATION_TICKS / 20);
    }


    /**
     * 切换到循环音乐
     */
    private void switchToLoopMusic(ServerLevel level, BattleData data) {
        if (!data.isMusicIntroPlaying()) return;  // 已经切换过了

        // 停止 intro
        stopMusic(level);

        // 播放 loop（循环播放）
        playMusic(level, MUSIC_LOOP, 1.0f, 1.0f);

        // 更新状态
        data.setMusicIntroPlaying(false);

        SporeSRP.LOGGER.info("Switched to loop music: {}", MUSIC_LOOP);
    }

    // ==================== 强制命令 ====================

    public boolean forceStartBattle(ServerLevel level, ServerPlayer player, int phase) {
        BlockPos center = player.blockPosition();
        BlockPos beaconPos = center;
        BlockPos corePos = center.above(3);
        if (!level.getBlockState(corePos).isAir()) {
            corePos = center.above(5);
        }
        level.setBlock(beaconPos, Blocks.BEACON.defaultBlockState(), 3);
        level.setBlock(corePos, ModBlocks.WORLD_PURIFICATION_CORE.get().defaultBlockState(), 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = beaconPos.offset(dx, -1, dz);
                if (level.getBlockState(pos).isAir()) {
                    level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), 3);
                }
            }
        }
        return startBattle(level, beaconPos, corePos, phase);
    }

    public boolean forceEndBattle(ServerLevel level, BlockPos beaconPos) {
        // 如果传入了具体坐标，按坐标查找
        if (beaconPos != null) {
            DimensionPos key = new DimensionPos(level.dimension(), beaconPos);
            BattleData data = activeBattles.get(key);
            if (data != null && data.isActive()) {
                endBattle(level, beaconPos, false);
                return true;
            }
        }

        // 否则自动查找最近的战斗
        Map.Entry<DimensionPos, BattleData> nearest = findNearestBattle(level, level.getSharedSpawnPos());
        if (nearest != null) {
            DimensionPos key = nearest.getKey();
            endBattle(level, key.pos, false);
            SporeSRP.LOGGER.info("Force ended battle at {} in {}", key.pos, level.dimension());
            return true;
        }
        return false;
    }

    public boolean forceSetPhase(ServerLevel level, BlockPos beaconPos, int phaseIndex) {
        BattleData data = null;
        BlockPos actualPos = null;

        // 如果传入了具体坐标，按坐标查找
        if (beaconPos != null) {
            DimensionPos key = new DimensionPos(level.dimension(), beaconPos);
            data = activeBattles.get(key);
            if (data != null && data.isActive()) {
                actualPos = beaconPos;
            }
        }

        // 如果没找到，自动查找最近的战斗
        if (data == null || !data.isActive()) {
            Map.Entry<DimensionPos, BattleData> nearest = findNearestBattle(level, level.getSharedSpawnPos());
            if (nearest != null) {
                data = nearest.getValue();
                actualPos = nearest.getKey().pos;
            }
        }

        if (data == null || !data.isActive()) return false;

        BattlePhase targetPhase;
        switch (phaseIndex) {
            case 1: targetPhase = BattlePhase.PHASE_1; break;
            case 2: targetPhase = BattlePhase.PHASE_2; break;
            case 3: targetPhase = BattlePhase.PHASE_3; break;
            case 4: targetPhase = BattlePhase.PHASE_4; break;
            default: return false;
        }

        BattleSceneManager.clearAllMobs(level, actualPos, 200);
        data.setCurrentPhase(targetPhase);
        data.setPhaseTimer(0);
        data.setLastMinute(-1);
        data.setTotalSpawned(0);

        // ★ 修改：getDisplayName() 现在返回 Component，可直接用于 translatable
        Component msg = Component.translatable("sporesrp.battle.debug.phase_set", targetPhase.getDisplayName());
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        BlockPos centerForBar = actualPos != null ? actualPos : level.getSharedSpawnPos();
        sendActionBar(level, msg, centerForBar);

        // ★ 修改：日志使用 getDisplayNameString() 或 getTranslationKey()
        SporeSRP.LOGGER.info("Force set phase to {} at {}", targetPhase.getTranslationKey(), actualPos);
        return true;
    }

    // ==================== 战斗控制 ====================

    public boolean startBattle(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        return startBattle(level, beaconPos, corePos, 1);
    }

    public boolean startBattle(ServerLevel level, BlockPos beaconPos, BlockPos corePos, int startPhase) {
        DimensionPos key = new DimensionPos(level.dimension(), beaconPos);

        if (activeBattles.containsKey(key)) return false;
        for (BattleData data : activeBattles.values()) {
            if (data.isActive()) return false;
        }

        BattleData data = new BattleData();
        data.setActive(true);
        data.setBeaconPos(beaconPos);
        data.setCorePos(corePos);
        data.setLevel(level);
        data.setStartTime(level.getServer().getTickCount());
        data.setTimerPaused(false);
        data.setLastMinute(-1);
        data.setNoPlayerTimerActive(false);
        data.setNoPlayerTimer(0);


        BattlePhase phase;
        switch (startPhase) {
            case 1: phase = BattlePhase.PHASE_1; break;
            case 2: phase = BattlePhase.PHASE_2; break;
            case 3: phase = BattlePhase.PHASE_3; break;
            case 4: phase = BattlePhase.PHASE_4; break;
            default: phase = BattlePhase.PHASE_1;
        }
        data.setCurrentPhase(phase);

        activeBattles.put(key, data);

        BattleSceneManager.generateArena(level, beaconPos, corePos);

        Component msg = Component.translatable("sporesrp.battle.start");
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        sendActionBar(level, msg, beaconPos);

        playIntroMusic(level, data);

        AdvancementTriggerHelper.onBattleStart(level);

        SporeSRP.LOGGER.info("World Purification Battle started at {} in {}", beaconPos, level.dimension());
        return true;
    }

    public void endBattle(ServerLevel level, BlockPos beaconPos, boolean victory) {
        DimensionPos key = new DimensionPos(level.dimension(), beaconPos);
        BattleData data = activeBattles.remove(key);
        if (data == null) return;

        data.setActive(false);

        // 移除音乐循环记录
        musicLoopTickMap.remove(key);

        // 停止音乐
        stopMusic(level);

        if (victory) {
            onVictory(level, data);
        } else {
            onDefeat(level, data);
        }
    }

    public BattleData getBattle(ServerLevel level, BlockPos beaconPos) {
        DimensionPos key = new DimensionPos(level.dimension(), beaconPos);
        return activeBattles.get(key);
    }

    public boolean isBattleActive(ServerLevel level, BlockPos beaconPos) {
        DimensionPos key = new DimensionPos(level.dimension(), beaconPos);
        BattleData data = activeBattles.get(key);
        return data != null && data.isActive();
    }

    public boolean isAnyBattleActive() {
        for (BattleData data : activeBattles.values()) {
            if (data.isActive()) return true;
        }
        return false;
    }

    public Collection<BattleData> getAllActiveBattles() {
        return activeBattles.values();
    }

    // ==================== 胜利 / 失败 ====================

    private void onVictory(ServerLevel level, BattleData data) {
        // 1. 清除战斗场地内的生物
        BattleSceneManager.clearAllMobs(level, data.getBeaconPos(), 200);

        // 2. 净化世界生物群系
        BattleSceneManager.purifyWorld(level);

        // 3. 设置净化标志为 true（顺带一次性清扫全维度孢子生物）
        WorldEvolutionData evolutionData = WorldEvolutionData.get(level);
        long currentTick = level.getServer().getTickCount();
        com.maha_fish.sporesrp.handler.PurifiedWorldHandler.applyPurification(level, true);
        evolutionData.forceSetPhase(0, currentTick);
        evolutionData.syncToClients(level);

        // 4. 移除世界净化核心
        level.setBlock(data.getCorePos(), Blocks.AIR.defaultBlockState(), 3);

        // 5. 清除所有 spore 生物
        clearAllSporeEntities(level);

        // 6. 发送胜利消息
        Component msg = Component.translatable("sporesrp.battle.victory");
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        sendActionBar(level, msg, data.getBeaconPos());

        // 7. 核心爆炸特效
        BattleSceneManager.coreExplosion(level, data.getCorePos());

        boolean isHard = false; // 根据实际难度判断，可从数据中获取
        // 假设 data 中有难度字段，或从 WorldEvolutionData 获取
        WorldEvolutionData evo = WorldEvolutionData.get(level);
        DifficultyManager.Difficulty diff = evo.getDifficulty();
        if (diff == DifficultyManager.Difficulty.HARD || diff == DifficultyManager.Difficulty.NIGHTMARE) {
            isHard = true;
        }
        AdvancementTriggerHelper.onBattleVictory(level, isHard);
        SporeSRP.LOGGER.info("World Purification Battle VICTORY - Purified flag set to true");
    }

    /**
     * 清除世界中所有命名空间为 spore 的生物
     */
    private void clearAllSporeEntities(ServerLevel level) {
        AABB worldBox = new AABB(
                level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
        );
        List<LivingEntity> allSpore = level.getEntitiesOfClass(LivingEntity.class, worldBox, e -> {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            return id != null && "spore".equals(id.getNamespace());
        });
        int count = allSpore.size();
        for (LivingEntity e : allSpore) {
            e.discard();
        }
        SporeSRP.LOGGER.info("Cleared {} spore entities from world", count);
    }

    private void onDefeat(ServerLevel level, BattleData data) {
        data.setActive(false);
        BattleSceneManager.clearAllMobs(level, data.getBeaconPos(), 200);

        Component msg = Component.translatable("sporesrp.battle.defeat");
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        sendActionBar(level, msg, data.getBeaconPos());

        AdvancementTriggerHelper.onBattleDefeat(level);

        SporeSRP.LOGGER.info("World Purification Battle DEFEAT at {}", data.getBeaconPos());
    }

    // ==================== 守点检查 ====================

    private boolean hasPlayerInArena(ServerLevel level, BlockPos center) {
        int radius = Config.COMMON.playerCheckRadius.get();
        int radiusSq = radius * radius;
        for (ServerPlayer player : level.getPlayers(p -> true)) {
            if (player.isSpectator()) continue;
            double distSq = player.distanceToSqr(center.getX(), center.getY(), center.getZ());
            if (distSq <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    // ==================== Tick 处理 ====================

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter % 20 != 0) return; // 每秒执行一次

        long currentTick = event.getServer().getTickCount();

        for (Map.Entry<DimensionPos, BattleData> entry : activeBattles.entrySet()) {
            DimensionPos key = entry.getKey();         // ★ 定义 key
            BattleData data = entry.getValue();        // ★ 定义 data
            if (!data.isActive()) continue;

            ServerLevel level = data.getLevel();
            if (level == null) continue;

            BlockPos beaconPos = data.getBeaconPos();
            BlockPos corePos = data.getCorePos();

            // ★ 修复场地
            BattleSceneManager.repairArena(level, beaconPos, corePos);

            // ★ 检查信标和核心是否存在
            if (!BattleSceneManager.checkCoreAndBeaconExist(level, beaconPos, corePos)) {
                Component msg = Component.translatable("sporesrp.battle.core_or_beacon_destroyed");
                level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                sendActionBar(level, msg, beaconPos);
                endBattle(level, beaconPos, false);
                continue;
            }


            // ★★★ 音乐切换检测（intro 结束后切换到 loop） ★★★
            if (data.isMusicIntroPlaying()) {
                long elapsedTicks = level.getServer().getTickCount() - data.getMusicStartTick();

                int remainingTicks = (int) (INTRO_DURATION_TICKS - elapsedTicks);
                if (remainingTicks > 0) {
                    int remainingSeconds = (remainingTicks + 19) / 20;
                    Component msg = Component.translatable("sporesrp.battle.preparation", remainingSeconds);
                    sendActionBar(level, msg, beaconPos);
                } else {
                    Component msg = Component.translatable("sporesrp.battle.preparation.ready");
                    sendActionBar(level, msg, beaconPos);
                }

                if (elapsedTicks >= INTRO_DURATION_TICKS) {
                    switchToLoopMusic(level, data);
                    // ★ 设置 240 秒后重播 loop
                    musicLoopTickMap.put(key, level.getServer().getTickCount() + 240 * 20L);
                }

                continue;
            }
            // ★ 检查是否需要重播 loop（每 240 秒）
            if (!data.isMusicIntroPlaying()) {
                Long nextLoopTick = musicLoopTickMap.get(key);
                if (nextLoopTick != null && currentTick >= nextLoopTick) {
                    playMusic(level, MUSIC_LOOP, 1.0f, 1.0f);
                    musicLoopTickMap.put(key, currentTick + 240 * 20L);
                }
            }

            // ---- 玩家守点检测 ----
            boolean hasPlayer = hasPlayerInArena(level, beaconPos);

            if (!hasPlayer) {
                if (!data.isNoPlayerTimerActive()) {
                    data.setNoPlayerTimerActive(true);
                    data.setNoPlayerTimer(0);
                    data.setTimerPaused(true);
                    Component msg = Component.translatable("sporesrp.battle.no_player_start",
                            BattleData.getNoPlayerTimeoutSeconds());
                    level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                    sendActionBar(level, msg, beaconPos);
                    SporeSRP.LOGGER.info("No player timer started, {} seconds remaining",
                            BattleData.getNoPlayerTimeoutSeconds());
                }

                data.setNoPlayerTimer(data.getNoPlayerTimer() + 1);
                int remaining = data.getRemainingNoPlayerSeconds();

                if (remaining > 0 && remaining % 5 == 0) {
                    Component msg = Component.translatable("sporesrp.battle.no_player_remaining", remaining);
                    sendActionBar(level, msg, beaconPos);
                }

                if (data.isNoPlayerTimeout()) {
                    Component msg = Component.translatable("sporesrp.battle.no_player_timeout");
                    level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                    sendActionBar(level, msg, beaconPos);
                    endBattle(level, beaconPos, false);
                    continue;
                }

                continue;
            }

            if (data.isNoPlayerTimerActive()) {
                data.setNoPlayerTimerActive(false);
                data.resetNoPlayerTimer();
                data.setTimerPaused(false);
                Component msg = Component.translatable("sporesrp.battle.no_player_reset");
                level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                sendActionBar(level, msg, beaconPos);
                SporeSRP.LOGGER.info("No player timer reset");
            }

            if (data.isTimerPaused()) {
                data.setTimerPaused(false);
                Component msg = Component.translatable("sporesrp.battle.timer_resume");
                level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                sendActionBar(level, msg, beaconPos);
            }

            int currentMoonPhase = (int) (level.getDayTime() / 24000L) % 8;
            if (currentMoonPhase != data.getMoonPhase()) {
                data.setMoonPhase(currentMoonPhase);
            }

            data.tickPhaseTimer();

            if (data.isActive()) {
                sendBattleStatus(level, data, beaconPos);
            }

            BattlePhase current = data.getCurrentPhase();
            int duration = current.getDurationTicks();

            if (data.getPhaseTimer() >= duration && !current.isCompleted()) {
                BattleSceneManager.clearAllMobs(level, beaconPos, 200);

                BattlePhase nextPhase = current.next();
                data.setCurrentPhase(nextPhase);
                data.setPhaseTimer(0);
                data.setLastMinute(-1);
                data.setTotalSpawned(0);

                if (nextPhase.isCompleted()) {
                    endBattle(level, beaconPos, true);
                    return;
                }

                Component msg = Component.translatable("sporesrp.battle.phase", nextPhase.getDisplayName());
                level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                sendActionBar(level, msg, beaconPos);
            }

            spawnWave(level, data, beaconPos);
        }
    }

    // ==================== 怪物生成 ====================

    private void spawnWave(ServerLevel level, BattleData data, BlockPos center) {
        BattlePhase phase = data.getCurrentPhase();
        if (phase.isCompleted()) return;

        int currentMinute = data.getPhaseTimer() / 1200;
        int lastMinute = data.getLastMinute();

        if (currentMinute > lastMinute && data.getPhaseTimer() > 0) {
            data.setLastMinute(currentMinute);

            BattleDifficulty difficulty = new BattleDifficulty(level, center);
            int baseSpawns = 4 + difficulty.getExtraSpawnsPerMinute();
            int count = Math.min(baseSpawns + difficulty.getExtraSpawnsPerMinute() * currentMinute, 30);

            SporeSRP.LOGGER.info("Spawning wave {}: {} monsters for phase {}", currentMinute, count, phase.name());
            spawnMonsters(level, center, phase, count);
            data.addSpawned(count);
        }
    }

    private void spawnMonsters(ServerLevel level, BlockPos center, BattlePhase phase, int count) {
        List<String> pool = getPoolForPhase(phase);
        if (pool.isEmpty()) {
            SporeSRP.LOGGER.warn("Monster pool is empty for phase {}, no monsters spawned!", phase.name());
            return;
        }

        SporeSRP.LOGGER.info("Spawning {} monsters for phase {} from pool (size {})", count, phase.name(), pool.size());

        Random rand = new Random();

        for (int i = 0; i < count; i++) {
            double angle = rand.nextDouble() * 2 * Math.PI;
            int distance = SPAWN_MIN_DIST + rand.nextInt(SPAWN_MAX_DIST - SPAWN_MIN_DIST + 1);

            int x = center.getX() + (int) (Math.cos(angle) * distance);
            int z = center.getZ() + (int) (Math.sin(angle) * distance);

            int y = center.getY() + 1;

            boolean found = false;
            for (int offset = 0; offset >= -10; offset--) {
                BlockPos checkPos = new BlockPos(x, y + offset, z);
                if (level.getBlockState(checkPos.below()).isSolid() &&
                        level.getBlockState(checkPos).isAir() &&
                        !level.getBlockState(checkPos.above()).isSolid()) {
                    y = y + offset;
                    found = true;
                    break;
                }
            }

            if (!found) {
                for (int offset = 1; offset <= 5; offset++) {
                    BlockPos checkPos = new BlockPos(x, y + offset, z);
                    if (level.getBlockState(checkPos.below()).isSolid() &&
                            level.getBlockState(checkPos).isAir() &&
                            !level.getBlockState(checkPos.above()).isSolid()) {
                        y = y + offset;
                        found = true;
                        break;
                    }
                }
            }

            if (!found || y <= level.getMinBuildHeight()) {
                SporeSRP.LOGGER.warn("Failed to find valid spawn position at ({}, {}, {}), skipping", x, y, z);
                continue;
            }

            BlockPos spawnPos = new BlockPos(x, y, z);

            String entityName = pool.get(rand.nextInt(pool.size()));
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(entityName));
            if (type == null) {
                SporeSRP.LOGGER.warn("Entity type '{}' not found, skipping", entityName);
                continue;
            }

            LivingEntity entity = (LivingEntity) type.create(level);
            if (entity == null) continue;

            entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            entity.refreshDimensions();
            level.addFreshEntity(entity);

            if (entity instanceof Mob mob) {
                Player target = null;
                double nearestDistSq = Double.MAX_VALUE;

                for (ServerPlayer player : level.getPlayers(p -> true)) {
                    if (player.isSpectator()) continue;
                    double distSq = player.distanceToSqr(entity);
                    if (distSq < nearestDistSq && distSq <= 80 * 80) {
                        nearestDistSq = distSq;
                        target = player;
                    }
                }

                if (target != null) {
                    mob.setTarget(target);
                    mob.getNavigation().moveTo(target, 1.5);
                    mob.setPersistenceRequired();
                    mob.setAggressive(true);
                } else {
                    mob.getNavigation().moveTo(center.getX(), center.getY(), center.getZ(), 1.2);
                }
            }
        }
    }

    // ==================== 获取怪物池 ====================

    private List<String> getPoolForPhase(BattlePhase phase) {
        List<String> organoids = new ArrayList<>(SporeEntityHelper.getOrganoids());
        List<String> pool = new ArrayList<>();

        switch (phase) {
            case PHASE_1:
                pool.addAll(SporeEntityHelper.getSmallPool());
                pool.addAll(SporeEntityHelper.getMediumPool());
                break;
            case PHASE_2:
                pool.addAll(SporeEntityHelper.getLargePool());
                pool.addAll(SporeEntityHelper.getBossPool());
                break;
            case PHASE_3:
                pool.addAll(SporeEntityHelper.getMediumPool());
                pool.addAll(SporeEntityHelper.getLargePool());
                break;
            case PHASE_4:
                pool.addAll(SporeEntityHelper.getBossPool());
                pool.addAll(SporeEntityHelper.getLargePool());
                break;
            default:
                return pool;
        }

        pool.removeAll(organoids);

        if (pool.isEmpty()) {
            SporeSRP.LOGGER.warn("Pool is empty for phase {}, using fallback pool", phase.name());
            switch (phase) {
                case PHASE_1: pool.addAll(FALLBACK_POOL_PHASE1); break;
                case PHASE_2: pool.addAll(FALLBACK_POOL_PHASE2); break;
                case PHASE_3: pool.addAll(FALLBACK_POOL_PHASE3); break;
                case PHASE_4: pool.addAll(FALLBACK_POOL_PHASE4); break;
                default: pool.addAll(FALLBACK_POOL_PHASE1);
            }
        }

        return pool;
    }

    // ==================== 战斗状态显示 ====================

    private void sendBattleStatus(ServerLevel level, BattleData data, BlockPos center) {
        if (level == null || data == null) return;

        if (data.isMusicIntroPlaying()) {
            long elapsed = level.getServer().getTickCount() - data.getMusicStartTick();
            int remainingTicks = (int) (INTRO_DURATION_TICKS - elapsed);
            if (remainingTicks > 0) {
                int seconds = (remainingTicks + 19) / 20;
                Component msg = Component.translatable("sporesrp.battle.hud.preparation", seconds);
                sendActionBar(level, msg, center);
            } else {
                Component msg = Component.translatable("sporesrp.battle.hud.preparation.ready");
                sendActionBar(level, msg, center);
            }
            return;
        }

        BattlePhase phase = data.getCurrentPhase();
        if (phase.isCompleted()) return;

        int totalTime = phase.getDurationTicks() / 20;
        int currentTime = data.getPhaseTimer() / 20;
        int remaining = Math.max(0, totalTime - currentTime);

        String timeStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
        String totalStr = String.format("%02d:%02d", totalTime / 60, totalTime % 60);

        // ★ 修改：phaseName 改为 Component
        Component phaseName = phase.getDisplayName();
        int phaseNum = phase.ordinal() + 1;

        float progress = (float) currentTime / totalTime;
        int percent = (int) (progress * 100);

        if (data.isNoPlayerTimerActive()) {
            int remainingNoPlayer = data.getRemainingNoPlayerSeconds();
            Component msg = Component.translatable("sporesrp.battle.hud.status_no_player",
                    remainingNoPlayer, phaseNum, phaseName, percent + "%", timeStr, totalStr);
            sendActionBar(level, msg, center);
        } else {
            Component msg = Component.translatable("sporesrp.battle.hud.status",
                    phaseNum, phaseName, percent + "%", timeStr, totalStr);
            sendActionBar(level, msg, center);
        }
    }

    // ==================== 清理 ====================

    public void clearBattles() {
        activeBattles.clear();
        musicLoopTickMap.clear();
        MinecraftServer server = (MinecraftServer) LogicalSidedProvider.WORKQUEUE.get(LogicalSide.SERVER);
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                stopMusic(level);
            }
        }
    }

    /**
     * 查找玩家所在维度最近的活跃战斗
     */
    private Map.Entry<DimensionPos, BattleData> findNearestBattle(ServerLevel level, BlockPos playerPos) {
        Map.Entry<DimensionPos, BattleData> nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Map.Entry<DimensionPos, BattleData> entry : activeBattles.entrySet()) {
            DimensionPos key = entry.getKey();
            BattleData data = entry.getValue();
            if (!data.isActive()) continue;
            if (!key.dimension.equals(level.dimension())) continue;
            double distSq = key.pos.distSqr(playerPos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = entry;
            }
        }
        return nearest;
    }
}