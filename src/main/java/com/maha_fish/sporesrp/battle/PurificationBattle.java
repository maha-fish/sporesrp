package com.maha_fish.sporesrp.battle;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import java.util.Set;
import net.minecraft.world.level.levelgen.Heightmap;
import com.maha_fish.sporesrp.util.SporeBridge;
import com.maha_fish.sporesrp.util.SporeSpawnHelper;
import com.maha_fish.sporesrp.block.ModBlocks;
import com.maha_fish.sporesrp.data.DifficultyManager;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PurificationBattle {

    private static PurificationBattle INSTANCE;
    private final Map<DimensionPos, BattleData> activeBattles = new ConcurrentHashMap<>();
    private final Map<DimensionPos, Long> musicLoopTickMap = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    private static final int SPAWN_MIN_DIST = 20;
    private static final int SPAWN_MAX_DIST = 40;

    private static final String MUSIC_INTRO = "spore:insolence_intro";
    private static final String MUSIC_LOOP = "spore:insolence";
    private static final int INTRO_DURATION_TICKS = 12 * 20;

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

    private static final int ACTION_BAR_RANGE = 64;

    private void sendActionBar(ServerLevel level, Component message, BlockPos center) {
        if (level == null || center == null) return;
        double rangeSq = ACTION_BAR_RANGE * ACTION_BAR_RANGE;
        Vec3 centerVec = Vec3.atCenterOf(center);
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(centerVec) <= rangeSq)) {
            player.displayClientMessage(message, true);
        }
    }

    // ==================== 音乐控制 ====================

    private void playMusic(ServerLevel level, String soundId, float volume, float pitch) {
        if (soundId == null || soundId.trim().isEmpty()) {
            SporeSRP.LOGGER.debug("Music ID is empty, skipping");
            return;
        }

        // 直接发声音包，不走 /playsound。
        // 原因：命令源取自 createCommandSourceStack()，其位置是**世界出生点**，
        // 于是 `~ ~ ~` 把声音播在出生点，离得远的玩家衰减到听不见 ——
        // 这正是「用指令开战没有 BGM」的原因。
        // 这里改成对每个玩家、以他自身坐标为播放点发送，音量恒为满，等价于全局 BGM。
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        var holderOpt = id == null ? null : BuiltInRegistries.SOUND_EVENT.getHolder(id);
        if (holderOpt == null || holderOpt.isEmpty()) {
            SporeSRP.LOGGER.warn("Unknown sound id, music skipped: {}", soundId);
            return;
        }

        long seed = level.random.nextLong();
        int sent = 0;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSoundPacket(
                    holderOpt.get(), SoundSource.MUSIC,
                    player.getX(), player.getY(), player.getZ(),
                    volume, pitch, seed));
            sent++;
        }
        SporeSRP.LOGGER.info("Playing music: {} ({} players)", soundId, sent);
    }

    private void stopMusic(ServerLevel level) {
        String command = "stopsound @a music";
        var source = level.getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
        SporeSRP.LOGGER.info("Music stopped");
    }

    private void playIntroMusic(ServerLevel level, BattleData data) {
        stopMusic(level);
        playMusic(level, MUSIC_INTRO, 1.0f, 1.0f);
        data.setMusicIntroPlaying(true);
        data.setMusicStartTick(level.getServer().getTickCount());
        SporeSRP.LOGGER.info("Intro music started: {} ({} seconds)", MUSIC_INTRO, INTRO_DURATION_TICKS / 20);
    }

    private void switchToLoopMusic(ServerLevel level, BattleData data) {
        if (!data.isMusicIntroPlaying()) return;
        stopMusic(level);
        playMusic(level, MUSIC_LOOP, 1.0f, 1.0f);
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
        if (beaconPos != null) {
            DimensionPos key = new DimensionPos(level.dimension(), beaconPos);
            BattleData data = activeBattles.get(key);
            if (data != null && data.isActive()) {
                endBattle(level, beaconPos, false);
                return true;
            }
        }

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
        onPhaseEnter(level, data);

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

        if (!checkBattleHeight(level, beaconPos, corePos)) {
            Component msg = Component.translatable("sporesrp.finalbattle.too_low");
            level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
            SporeSRP.LOGGER.warn("Battle start rejected: below sea level at {}", corePos);
            return false;
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

        // 天气、刷怪锚点、阶段入场
        BattleWeather.begin(level, data, 25000);
        data.setSpawnPlan(BattleSpawnPlanner.prepare(level, beaconPos, corePos));
        data.setNextLightningTick(level.getServer().getTickCount()
                + Config.COMMON.battleLightningIntervalTicks.get());
        onPhaseEnter(level, data);

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
        musicLoopTickMap.remove(key);
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
        BattleSceneManager.clearAllMobs(level, data.getBeaconPos(), 200);
        BattleSceneManager.purifyWorld(level);

        WorldEvolutionData evolutionData = WorldEvolutionData.get(level);
        long currentTick = level.getServer().getTickCount();
        com.maha_fish.sporesrp.handler.PurifiedWorldHandler.applyPurification(level, true);
        evolutionData.forceSetPhase(0, currentTick);
        evolutionData.syncToClients(level);

        level.setBlock(data.getCorePos(), Blocks.AIR.defaultBlockState(), 3);
        clearAllSporeEntities(level);

        Component msg = Component.translatable("sporesrp.battle.victory");
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        sendActionBar(level, msg, data.getBeaconPos());

        BattleSceneManager.coreExplosion(level, data.getCorePos());

        boolean isHard = false;
        WorldEvolutionData evo = WorldEvolutionData.get(level);
        DifficultyManager.Difficulty diff = evo.getDifficulty();
        if (diff == DifficultyManager.Difficulty.HARD || diff == DifficultyManager.Difficulty.NIGHTMARE) {
            isHard = true;
        }
        AdvancementTriggerHelper.onBattleVictory(level, isHard);
        SporeSRP.LOGGER.info("World Purification Battle VICTORY - Purified flag set to true");
    }

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
    public void onServerTick(ServerTickEvent.Post event) {

        if (!Config.COMMON_SPEC.isLoaded()) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        long currentTick = event.getServer().getTickCount();

        for (Map.Entry<DimensionPos, BattleData> entry : activeBattles.entrySet()) {
            DimensionPos key = entry.getKey();
            BattleData data = entry.getValue();
            if (!data.isActive()) continue;

            ServerLevel level = data.getLevel();
            if (level == null) continue;

            BlockPos beaconPos = data.getBeaconPos();
            BlockPos corePos = data.getCorePos();

            BattleSceneManager.repairArena(level, beaconPos, corePos);

            if (!BattleSceneManager.checkCoreAndBeaconExist(level, beaconPos, corePos)) {
                Component msg = Component.translatable("sporesrp.battle.core_or_beacon_destroyed");
                level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
                sendActionBar(level, msg, beaconPos);
                endBattle(level, beaconPos, false);
                continue;
            }

            // ---- 天气断言 + 定期落雷 ----
            if (currentTick % 100 == 0) {
                BattleWeather.assertThunder(level);
            }
            if (currentTick >= data.getNextLightningTick()) {
                BattleWeather.strike(level, beaconPos, corePos);
                int baseInterval = Config.COMMON.battleLightningIntervalTicks.get();
                int jitter = Math.max(1, baseInterval * 2 / 5);
                data.setNextLightningTick(currentTick + baseInterval
                        + level.random.nextInt(jitter * 2 + 1) - jitter);
            }            if (data.isMusicIntroPlaying()) {
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
                    musicLoopTickMap.put(key, level.getServer().getTickCount() + 240 * 20L);
                }

                continue;
            }

            if (!data.isMusicIntroPlaying()) {
                Long nextLoopTick = musicLoopTickMap.get(key);
                if (nextLoopTick != null && currentTick >= nextLoopTick) {
                    playMusic(level, MUSIC_LOOP, 1.0f, 1.0f);
                    musicLoopTickMap.put(key, currentTick + 240 * 20L);
                }
            }

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
        onPhaseEnter(level, data);

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

    // ==================== 怪物生成（阶段化） ====================

    /** 每波：按阶段决定组成；阶段4 走灾厄轮转。 */
    private void spawnWave(ServerLevel level, BattleData data, BlockPos center) {
        BattlePhase phase = data.getCurrentPhase();
        if (phase.isCompleted()) return;
        if (data.getSpawnPlan() == null) {
            data.setSpawnPlan(BattleSpawnPlanner.prepare(level, center, data.getCorePos()));
        }

        // 阶段4：按固定间隔出灾厄（每波 2~3 只，轮完重新打乱）
        if (phase == BattlePhase.PHASE_4) {
            int interval = Math.max(20, Config.COMMON.calamityWaveIntervalTicks.get());
            int wave = data.getPhaseTimer() / interval;
            if (wave <= data.getLastMinute()) return;
            data.setLastMinute(wave);
            spawnCalamityWave(level, data);
            return;
        }

        int currentMinute = data.getPhaseTimer() / 1200;
        if (currentMinute <= data.getLastMinute() || data.getPhaseTimer() <= 0) return;
        data.setLastMinute(currentMinute);

        BattleDifficulty difficulty = new BattleDifficulty(level, center);
        int baseSpawns = 4 + difficulty.getExtraSpawnsPerMinute();
        int count = Math.min(baseSpawns + difficulty.getExtraSpawnsPerMinute() * currentMinute, 30);
        // 难度系数加成：每级 +10%，上限 2.5 倍
        double mult = Math.min(2.5, 1.0 + 0.10 * Math.max(0, difficulty.getFinalDifficulty() - 1));
        count = Math.max(1, (int) Math.round(count * mult));

        switch (phase) {
            case PHASE_1 -> {
                // 小 : 中 = 5 : 1
                spawnComposed(level, data, count,
                        sanitize(SporeEntityHelper.getSmallPool()), 5,
                        sanitize(SporeEntityHelper.getMediumPool()), 1);
            }
            case PHASE_2 -> spawnFlyingWave(level, data, count);
            case PHASE_3 -> {
                // 中 : 大 = 3 : 1
                spawnComposed(level, data, count,
                        sanitize(SporeEntityHelper.getMediumPool()), 3,
                        sanitize(SporeEntityHelper.getLargePool()), 1);
            }
            default -> { }
        }
        data.addSpawned(count);
    }

    /** 按权重分层生成（保证数量比例，而不是概率抽取）。 */
    private void spawnComposed(ServerLevel level, BattleData data, int total,
                               List<String> poolA, int weightA, List<String> poolB, int weightB) {
        List<String> a = new ArrayList<>(poolA);
        List<String> b = new ArrayList<>(poolB);
        if (a.isEmpty() && b.isEmpty()) {
            SporeSRP.LOGGER.warn("Battle: both composition pools are empty, wave skipped");
            return;
        }
        if (b.isEmpty()) {
            spawnFromPool(level, data, a, total, false);
            return;
        }
        if (a.isEmpty()) {
            spawnFromPool(level, data, b, total, false);
            return;
        }
        int countA = Math.max(0, (int) Math.round((double) total * weightA / (weightA + weightB)));
        int countB = Math.max(0, total - countA);
        spawnFromPool(level, data, a, countA, false);
        spawnFromPool(level, data, b, countB, false);
    }

    /** 阶段2：飞行生物 + 每波固定数量的 hinderburg。 */
    private void spawnFlyingWave(ServerLevel level, BattleData data, int count) {
        List<String> flying = new ArrayList<>();
        for (String id : Config.COMMON.phase2FlyingPool.get()) {
            if (id != null && !id.isBlank()) flying.add(id.trim());
        }
        if (flying.isEmpty()) {
            SporeSRP.LOGGER.warn("Battle phase 2: flying pool is empty, falling back to large pool");
            flying = sanitize(SporeEntityHelper.getLargePool());
        }
        spawnFromPool(level, data, flying, count, true);

        int hinderburgs = Config.COMMON.phase2HinderburgPerWave.get();
        for (int i = 0; i < hinderburgs; i++) {
            spawnFromPool(level, data, List.of("spore:hinderburg"), 1, true);
        }
    }

    /** 阶段4：每波 2~3 只灾厄，按轮转顺序取，轮完重新开始。 */
    private void spawnCalamityWave(ServerLevel level, BattleData data) {
        int min = Math.max(1, Config.COMMON.calamityPerWaveMin.get());
        int max = Math.max(min, Config.COMMON.calamityPerWaveMax.get());
        int amount = min + level.random.nextInt(max - min + 1);
        for (int i = 0; i < amount; i++) {
            String id = data.nextCalamity(new java.util.Random(level.random.nextLong()));
            if (id == null) return;
            spawnFromPool(level, data, List.of(id), 1, false);
        }
        // 护卫：随难度增加
        BattleDifficulty difficulty = new BattleDifficulty(level, data.getBeaconPos());
        int escorts = Math.min(6, Math.max(0, (int) ((difficulty.getFinalDifficulty() - 1) * 0.75)));
        if (escorts > 0) {
            spawnFromPool(level, data, sanitize(SporeEntityHelper.getMediumPool()), escorts, false);
        }
        data.addSpawned(amount);
    }

    /**
     * 从池中生成指定数量的怪。
     *
     * @param flying true = 空中生成（飞行生物）
     */
    private void spawnFromPool(ServerLevel level, BattleData data, List<String> pool, int count, boolean flying) {
        if (count <= 0 || pool.isEmpty()) return;
        List<String> organoids = new ArrayList<>(SporeEntityHelper.organoidSet());
        Random rand = new Random();

        for (int i = 0; i < count; i++) {
            String entityName = null;
            for (int attempt = 0; attempt < 8; attempt++) {
                String candidate = pool.get(rand.nextInt(pool.size()));
                if (!organoids.contains(candidate)) {
                    entityName = candidate;
                    break;
                }
            }
            if (entityName == null) entityName = pool.get(rand.nextInt(pool.size()));

            ResourceLocation id = ResourceLocation.tryParse(entityName);
            if (id == null) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) continue;

            BlockPos pos = flying
                    ? BattleSpawnPlanner.air(level, data.getSpawnPlan(), level.random)
                    : BattleSpawnPlanner.ground(level, data.getSpawnPlan(), level.random);
            if (pos == null) {
                SporeSRP.LOGGER.warn("Battle: no spawn anchor available, wave partially skipped");
                return;
            }

            LivingEntity entity = SporeSpawnHelper.spawnFinalized(level, type, pos);
            if (!(entity instanceof Mob mob)) continue;

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
            mob.setPersistenceRequired();
            if (target != null) {
                mob.setTarget(target);
                mob.getNavigation().moveTo(target, 1.5);
                mob.setAggressive(true);
            }
        }
    }

    /** 剔除未注册/类器官。 */
    private List<String> sanitize(List<String> raw) {
        List<String> out = new ArrayList<>();
        Set<String> organoids = SporeEntityHelper.organoidSet();
        for (String id : raw) {
            if (id == null || id.isBlank()) continue;
            if (organoids.contains(id)) continue;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || BuiltInRegistries.ENTITY_TYPE.get(rl) == null) continue;
            out.add(id);
        }
        return out;
    }

    // ==================== 阶段入场 ====================

    /** 阶段进入时执行一次：菌囊环 / 灾厄轮转初始化 / 重算锚点。 */
    private void onPhaseEnter(ServerLevel level, BattleData data) {
        BattlePhase phase = data.getCurrentPhase();
        data.setLastMinute(-1);
        data.setSpawnPlan(BattleSpawnPlanner.prepare(level, data.getBeaconPos(), data.getCorePos()));

        switch (phase) {
            case PHASE_1 -> spawnMoundRing(level, data);
            case PHASE_4 -> {
                List<String> bosses = sanitize(SporeEntityHelper.getBossPool());
                data.getCalamityOrder().clear();
                data.getCalamityOrder().addAll(bosses);
                data.setCalamityIndex(0);
                java.util.Collections.shuffle(data.getCalamityOrder(), new java.util.Random(level.random.nextLong()));
                SporeSRP.LOGGER.info("Battle phase 4: {} calamities in rotation", bosses.size());
            }
            default -> { }
        }
    }

    /** 阶段1：场地外圈生成一圈 1 阶菌囊。 */
    private void spawnMoundRing(ServerLevel level, BattleData data) {
        int n = Config.COMMON.moundRingCount.get();
        if (n <= 0) return;
        ResourceLocation moundId = ResourceLocation.tryParse("spore:mound");
        EntityType<?> moundType = moundId == null ? null : BuiltInRegistries.ENTITY_TYPE.get(moundId);
        if (moundType == null) {
            SporeSRP.LOGGER.warn("Battle: spore:mound not found, mound ring skipped");
            return;
        }
        BlockPos center = data.getBeaconPos();
        BlockPos core = data.getCorePos();
        int radius = Config.COMMON.moundRingRadius.get();
        int minCore = Config.COMMON.spawnMinCoreDistance.get();
        double minCoreSq = (double) minCore * minCore;
        int spawned = 0;

        for (int i = 0; i < n; i++) {
            double angle = Math.PI * 2.0 * i / n;
            int x = center.getX() + (int) (Math.cos(angle) * radius);
            int z = center.getZ() + (int) (Math.sin(angle) * radius);
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));
            if (core != null && surface.distSqr(core) < minCoreSq) continue;

            LivingEntity mound = SporeSpawnHelper.spawnFinalized(level, moundType, surface);
            if (mound == null) continue;
            SporeBridge.setMoundAge(mound, 1);
            mound.getPersistentData().putBoolean("sporesrp_battle_mound", true);
            if (mound instanceof Mob mob) mob.setPersistenceRequired();
            data.addBattleMoundId(mound.getId());
            spawned++;
        }
        SporeSRP.LOGGER.info("Battle phase 1: spawned {} level-1 mounds in a ring", spawned);
    }

    // ==================== 战斗高度校验 ====================

    /** 只允许在海平面以上开战；超平坦世界豁免。 */
    private boolean checkBattleHeight(ServerLevel level, BlockPos beaconPos, BlockPos corePos) {
        if (isFlatWorld(level)) return true;
        int configured = Config.COMMON.battleMinY.get();
        int threshold = configured >= 0 ? configured : level.getSeaLevel();
        return corePos.getY() >= threshold && beaconPos.getY() >= threshold - 2;
    }

    private static boolean isFlatWorld(ServerLevel level) {
        return level.getChunkSource().getGenerator()
                instanceof net.minecraft.world.level.levelgen.FlatLevelSource;
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
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                stopMusic(level);
            }
        }
    }

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