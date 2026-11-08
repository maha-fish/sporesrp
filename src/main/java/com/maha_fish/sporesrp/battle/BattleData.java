package com.maha_fish.sporesrp.battle;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

public class BattleData {
    private boolean active = false;
    private BattlePhase currentPhase = BattlePhase.PHASE_1;
    private int phaseTimer = 0;
    private boolean timerPaused = false;
    private int totalSpawned = 0;
    private BlockPos beaconPos;
    private BlockPos corePos;
    private long startTime = 0;
    private int moonPhase = 0;

    private ServerLevel level;

    private int lastMinute = -1;
    private int noPlayerTimer = 0;
    private boolean noPlayerTimerActive = false;
    private static final int NO_PLAYER_TIMEOUT_SECONDS = 30;

    private boolean musicIntroPlaying = false;
    private long musicStartTick = 0;

    // ==================== Getters / Setters ====================

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public BattlePhase getCurrentPhase() { return currentPhase; }
    public void setCurrentPhase(BattlePhase phase) { this.currentPhase = phase; }

    public int getPhaseTimer() { return phaseTimer; }
    public void setPhaseTimer(int ticks) { this.phaseTimer = ticks; }
    public void tickPhaseTimer() {
        if (!timerPaused && active) {
            phaseTimer += 20;
        }
    }

    public boolean isTimerPaused() { return timerPaused; }
    public void setTimerPaused(boolean paused) { this.timerPaused = paused; }

    public int getTotalSpawned() { return totalSpawned; }
    public void setTotalSpawned(int count) { this.totalSpawned = count; }
    public void addSpawned(int count) { this.totalSpawned += count; }

    public BlockPos getBeaconPos() { return beaconPos; }
    public void setBeaconPos(BlockPos pos) { this.beaconPos = pos; }

    public BlockPos getCorePos() { return corePos; }
    public void setCorePos(BlockPos pos) { this.corePos = pos; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long time) { this.startTime = time; }

    public int getMoonPhase() { return moonPhase; }
    public void setMoonPhase(int phase) { this.moonPhase = phase; }

    public ServerLevel getLevel() { return level; }
    public void setLevel(ServerLevel level) { this.level = level; }

    public int getLastMinute() { return lastMinute; }
    public void setLastMinute(int lastMinute) { this.lastMinute = lastMinute; }

    public int getNoPlayerTimer() { return noPlayerTimer; }
    public void setNoPlayerTimer(int seconds) { this.noPlayerTimer = seconds; }
    public void resetNoPlayerTimer() { this.noPlayerTimer = 0; }

    public boolean isNoPlayerTimerActive() { return noPlayerTimerActive; }
    public void setNoPlayerTimerActive(boolean active) { this.noPlayerTimerActive = active; }

    public static int getNoPlayerTimeoutSeconds() { return NO_PLAYER_TIMEOUT_SECONDS; }

    public boolean isMusicIntroPlaying() { return musicIntroPlaying; }
    public void setMusicIntroPlaying(boolean playing) { this.musicIntroPlaying = playing; }

    public long getMusicStartTick() { return musicStartTick; }
    public void setMusicStartTick(long tick) { this.musicStartTick = tick; }

    // ==================== 辅助方法 ====================

    public float getPhaseProgress() {
        if (currentPhase.isCompleted()) return 1.0f;
        int duration = currentPhase.getDurationTicks();
        return Math.min(1.0f, (float) phaseTimer / duration);
    }

    public int getRemainingNoPlayerSeconds() {
        return Math.max(0, NO_PLAYER_TIMEOUT_SECONDS - noPlayerTimer);
    }

    public boolean isNoPlayerTimeout() {
        return noPlayerTimerActive && noPlayerTimer >= NO_PLAYER_TIMEOUT_SECONDS;
    }

    // ==================== NBT 序列化 ====================

    // ==================== 战斗扩展状态 ====================

    /** 开战前的天气快照。 */
    private BattleWeather.Snapshot weatherSnapshot;
    /** 已应用过天气（避免重复释放）。 */
    private boolean weatherApplied;
    /** 本场战斗的刷怪规划（锚点 + 临时平台）。 */
    private BattleSpawnPlanner.Plan spawnPlan;
    /** 阶段1 生成的战斗菌囊（用于避免被阶段下限抬级）。 */
    private final List<Integer> battleMoundIds = new ArrayList<>();
    /** 阶段4 的灾厄轮转顺序。 */
    private final List<String> calamityOrder = new ArrayList<>();
    private int calamityIndex;
    /** 当前阶段是否已经执行过入场逻辑。 */
    private boolean phaseEntered;
    /** 下一次落雷的 tick。 */
    private long nextLightningTick;

    public BattleWeather.Snapshot getWeatherSnapshot() { return weatherSnapshot; }
    public void setWeatherSnapshot(BattleWeather.Snapshot snapshot) { this.weatherSnapshot = snapshot; }
    public boolean isWeatherApplied() { return weatherApplied; }
    public void setWeatherApplied(boolean applied) { this.weatherApplied = applied; }
    public BattleSpawnPlanner.Plan getSpawnPlan() { return spawnPlan; }
    public void setSpawnPlan(BattleSpawnPlanner.Plan plan) { this.spawnPlan = plan; }
    public List<Integer> getBattleMoundIds() { return battleMoundIds; }
    public void addBattleMoundId(int id) { battleMoundIds.add(id); }
    public List<String> getCalamityOrder() { return calamityOrder; }
    public int getCalamityIndex() { return calamityIndex; }
    public void setCalamityIndex(int index) { this.calamityIndex = index; }
    public boolean isPhaseEntered() { return phaseEntered; }
    public void setPhaseEntered(boolean entered) { this.phaseEntered = entered; }
    public long getNextLightningTick() { return nextLightningTick; }
    public void setNextLightningTick(long tick) { this.nextLightningTick = tick; }

    /** 取下一个灾厄（轮转到底后重新打乱循环）。 */
    public String nextCalamity(java.util.Random random) {
        if (calamityOrder.isEmpty()) return null;
        if (calamityIndex >= calamityOrder.size()) {
            calamityIndex = 0;
            java.util.Collections.shuffle(calamityOrder, random);
        }
        return calamityOrder.get(calamityIndex++);
    }
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        tag.putInt("phase", currentPhase.ordinal());
        tag.putInt("phaseTimer", phaseTimer);
        tag.putBoolean("timerPaused", timerPaused);
        tag.putInt("totalSpawned", totalSpawned);
        if (beaconPos != null) tag.putLong("beaconPos", beaconPos.asLong());
        if (corePos != null) tag.putLong("corePos", corePos.asLong());
        tag.putLong("startTime", startTime);
        tag.putInt("moonPhase", moonPhase);
        tag.putInt("lastMinute", lastMinute);
        tag.putInt("noPlayerTimer", noPlayerTimer);
        tag.putBoolean("noPlayerTimerActive", noPlayerTimerActive);
        tag.putBoolean("musicIntroPlaying", musicIntroPlaying);
        tag.putLong("musicStartTick", musicStartTick);
        return tag;
    }

    public static BattleData deserializeNBT(CompoundTag tag) {
        BattleData data = new BattleData();
        data.active = tag.getBoolean("active");
        data.currentPhase = BattlePhase.values()[tag.getInt("phase")];
        data.phaseTimer = tag.getInt("phaseTimer");
        data.timerPaused = tag.getBoolean("timerPaused");
        data.totalSpawned = tag.getInt("totalSpawned");
        if (tag.contains("beaconPos")) data.beaconPos = BlockPos.of(tag.getLong("beaconPos"));
        if (tag.contains("corePos")) data.corePos = BlockPos.of(tag.getLong("corePos"));
        data.startTime = tag.getLong("startTime");
        data.moonPhase = tag.getInt("moonPhase");
        data.lastMinute = tag.getInt("lastMinute");
        data.noPlayerTimer = tag.getInt("noPlayerTimer");
        data.noPlayerTimerActive = tag.getBoolean("noPlayerTimerActive");
        data.musicIntroPlaying = tag.getBoolean("musicIntroPlaying");
        data.musicStartTick = tag.getLong("musicStartTick");

        return data;
    }


}