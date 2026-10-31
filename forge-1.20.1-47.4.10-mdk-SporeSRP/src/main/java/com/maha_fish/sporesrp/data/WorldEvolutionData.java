package com.maha_fish.sporesrp.data;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.network.SyncEvolutionDataPacket;
import com.maha_fish.sporesrp.phase.PhaseEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WorldEvolutionData extends SavedData {
    private static final String DATA_NAME = SporeSRP.MOD_ID + "_world_data";
    private int evolutionPoints = 0;
    private int evolutionPhase = 0;
    private long nextUpgradeAvailableTick = 0;
    private DifficultyManager.Difficulty difficulty = DifficultyManager.Difficulty.SIMPLE;
    private final Set<Integer> unlockedPhases = new HashSet<>();
    private int pendingCooldownRemaining = -1; // 用于加载时暂存剩余秒数

    // ★ 修改点：新增净化锁定标志
    private boolean purified = false;

    // ========== 完整心智 ==========
    private String fullHivemindUuid = "";

    // ========== 袭击值系统 ==========
    private double assaultValue = 0.0;
    private long lastAssaultIncrementTick = 0;
    private boolean assaultTriggered = false;
    private long assaultCooldownEndTick = 0;

    // ========== 点数冷却（与「阶段提升冷却」合并为同一个计时器） ==========
    /**
     * 冷却结束时刻。语义：**冷却中无法获得演化点数**。
     *
     * <p>阶段提升与诱饵激活都写同一个 {@link #nextUpgradeAvailableTick}，
     * 且采用**加法**累加：已过期的从现在重新计时，未过期的在剩余时间上继续加。
     */

    public boolean isPointGainBlocked(long currentTick) { return currentTick < nextUpgradeAvailableTick; }

    /** 按「加法」延长点数冷却：已过期则从现在算起，未过期则在剩余上累加。 */
    public void adjustPointCooldown(long currentTick, int seconds) {
        if (seconds == 0) return;
        long base = Math.max(this.nextUpgradeAvailableTick, currentTick);
        this.nextUpgradeAvailableTick = Math.max(currentTick, base + seconds * 20L);
        setDirty();
    }
    /**
     * 饵堆生效：按**中心**诱饵的等级扣演化点数（允许降阶段），并叠加点数冷却。
     *
     * <p>顺序很重要：{@code updatePhase} 的降阶分支会把冷却清零，所以冷却必须在它之后叠加。
     */
    public void applyDecoyPoints(int points, int cooldownSeconds, long currentTick) {
        if (purified) return;
        this.evolutionPoints = Math.max(0, this.evolutionPoints - Math.max(0, points));
        updatePhase(currentTick);
        adjustPointCooldown(currentTick, cooldownSeconds);
        setDirty();
    }

    /** 单个诱饵生效：只叠加演化时间冷却，不改动演化点数。 */
    public void applyDecoyCooldown(int cooldownSeconds, long currentTick) {
        if (purified) return;
        adjustPointCooldown(currentTick, cooldownSeconds);
        setDirty();
    }

    // ========== 基础 Getter/Setter ==========
    public int getEvolutionPoints() { return evolutionPoints; }
    public int getEvolutionPhase() { return evolutionPhase; }
    public long getNextUpgradeAvailableTick() { return nextUpgradeAvailableTick; }
    public DifficultyManager.Difficulty getDifficulty() { return difficulty; }

    public void setDifficulty(DifficultyManager.Difficulty difficulty) {
        this.difficulty = difficulty;
        setDirty();
    }

    // 净化相关方法
    public boolean isPurified() { return purified; }

    public void setPurified(boolean purified) {
        this.purified = purified;
        if (purified) {
            // 净化时强制清零
            this.evolutionPoints = 0;
            this.evolutionPhase = 0;
            this.nextUpgradeAvailableTick = 0;
            this.unlockedPhases.clear();
            this.unlockedPhases.add(0);
        }
        setDirty();
    }

    // ========== 完整心智 API ==========
    public UUID getFullHivemindUuid() {
        if (fullHivemindUuid.isEmpty()) return null;
        try {
            return UUID.fromString(fullHivemindUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setFullHivemindUuid(UUID uuid) {
        this.fullHivemindUuid = uuid != null ? uuid.toString() : "";
        setDirty();
    }

    public boolean hasFullHivemind() {
        return !fullHivemindUuid.isEmpty();
    }

    // ========== 袭击值 API ==========
    public double getAssaultValue() { return assaultValue; }

    public void setAssaultValue(double value) {
        double max = 100.0;
        this.assaultValue = Math.max(0, Math.min(value, max));
        setDirty();
    }

    public void addAssaultValue(double amount) {
        setAssaultValue(this.assaultValue + amount);
    }

    public long getLastAssaultIncrementTick() { return lastAssaultIncrementTick; }

    public void setLastAssaultIncrementTick(long tick) {
        this.lastAssaultIncrementTick = tick;
        setDirty();
    }

    public boolean isAssaultTriggered() { return assaultTriggered; }

    public void setAssaultTriggered(boolean triggered) {
        this.assaultTriggered = triggered;
        setDirty();
    }

    public long getAssaultCooldownEndTick() { return assaultCooldownEndTick; }

    public void setAssaultCooldownEndTick(long tick) {
        this.assaultCooldownEndTick = tick;
        setDirty();
    }

    public boolean isAssaultOnCooldown(long currentTick) {
        return currentTick < assaultCooldownEndTick;
    }

    // ========== 演化点数 API ==========
    // ★ 修改点：setEvolutionPoints 添加净化检查
    public void setEvolutionPoints(int points, long currentTick) {
        if (purified) {
            // 净化后不允许修改点数，强制保持为0
            this.evolutionPoints = 0;
            this.evolutionPhase = 0;
            setDirty();
            return;
        }
        this.evolutionPoints = Math.max(0, points);
        setDirty();
        updatePhase(currentTick);
    }

    // ★ 修改点：addEvolutionPoints 添加净化检查
    public void addEvolutionPoints(int amount, long currentTick) {
        if (purified) {
            return; // 净化后忽略所有加点
        }
        if (amount == 0) {
            return;
        }
        // 正数：受诱饵的「点数冷却」限制
        if (amount > 0) {
            if (isPointGainBlocked(currentTick)) {
                return;
            }
            setEvolutionPoints(this.evolutionPoints + amount, currentTick);
            return;
        }
        // 负数：击杀只扣点数，**不降阶段**（SRP 语义：压阶段只能靠诱饵）
        this.evolutionPoints = Math.max(0, this.evolutionPoints + amount);
        setDirty();
    }

    // ★ 修改点：forceSetPhase 添加净化检查
    public void forceSetPhase(int targetPhase, long currentTick) {
        // 净化后只允许设置为0
        if (purified && targetPhase != 0) {
            SporeSRP.LOGGER.warn("Purified world: forceSetPhase to {} rejected (only 0 allowed)", targetPhase);
            return;
        }
        // 如果净化且targetPhase为0，执行完整清零
        if (purified && targetPhase == 0) {
            this.evolutionPoints = 0;
            this.evolutionPhase = 0;
            this.nextUpgradeAvailableTick = 0;
            this.unlockedPhases.clear();
            this.unlockedPhases.add(0);
            setDirty();
            SporeSRP.LOGGER.info("Purified world: phase reset to 0");
            return;
        }

        var thresholds = Config.COMMON.phaseThresholds.get();
        int maxPhase = Config.COMMON.getMaxPhase();
        if (targetPhase < 0 || targetPhase > maxPhase) return;

        int requiredPoints = (Integer) thresholds.get(targetPhase);
        this.evolutionPoints = requiredPoints;
        this.nextUpgradeAvailableTick = 0;
        this.evolutionPhase = targetPhase;

        // 解锁从0到targetPhase的所有阶段
        for (int i = 0; i <= targetPhase; i++) {
            unlockedPhases.add(i);
        }
        setDirty();

        SporeSRP.LOGGER.info("Force set phase to {} (points: {})", targetPhase, requiredPoints);
    }

    // ========== 阶段更新逻辑 ==========
    // ★ 修改点：updatePhase 添加净化检查
    /** 该阶段对应的冷却秒数（越界按 0 处理）。 */
    private int cooldownForPhase(int phase) {
        var cooldowns = Config.COMMON.phaseUpgradeCooldownSeconds.get();
        return (phase >= 0 && phase < cooldowns.size()) ? cooldowns.get(phase) : 0;
    }

    private void updatePhase(long currentTick) {
        if (purified) {
            this.evolutionPhase = 0;
            setDirty();
            return;
        }

        var thresholds = Config.COMMON.phaseThresholds.get();
        int maxPhase = Config.COMMON.getMaxPhase();
        int theoreticalPhase = 0;
        for (int i = thresholds.size() - 1; i >= 0; i--) {
            if (evolutionPoints >= (Integer) thresholds.get(i)) {
                theoreticalPhase = i;
                break;
            }
        }
        if (theoreticalPhase > maxPhase) theoreticalPhase = maxPhase;

        // 降级处理
        // 降阶同样走「加法」：每降一级就按离开的那个阶段加一次冷却，不再清零
        if (theoreticalPhase < evolutionPhase) {
            while (evolutionPhase > theoreticalPhase) {
                adjustPointCooldown(currentTick, cooldownForPhase(evolutionPhase));
                evolutionPhase--;
            }
            setDirty();
            return;
        }

        // 升级循环（可一次性提升多个阶段）
        while (theoreticalPhase > evolutionPhase) {
            int targetNextPhase = evolutionPhase + 1;
            if (targetNextPhase > maxPhase) break;

            if (unlockedPhases.contains(targetNextPhase)) {
                // 已解锁，直接升级，继续循环
                evolutionPhase = targetNextPhase;
                setDirty();
                // 继续循环，不返回
            } else {
                // 升级并解锁；同时按「加法」延长点数冷却
                var cooldowns = Config.COMMON.phaseUpgradeCooldownSeconds.get();
                int cooldownSeconds = (evolutionPhase < cooldowns.size()) ? cooldowns.get(evolutionPhase) : 0;

                evolutionPhase = targetNextPhase;
                unlockedPhases.add(targetNextPhase);
                adjustPointCooldown(currentTick, cooldownSeconds);
                setDirty();

                // 已升一级，本次跳出；下一次靠点数继续推进
                break;
            }
        }
    }

    // ========== NBT 持久化 ==========
    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("evolutionPoints", evolutionPoints);
        tag.putInt("evolutionPhase", evolutionPhase);
        tag.putLong("nextUpgradeTick", nextUpgradeAvailableTick);
        tag.putInt("difficulty", difficulty.ordinal());
        tag.putString("fullHivemindUuid", fullHivemindUuid);
        tag.putDouble("assaultValue", assaultValue);
        tag.putLong("lastAssaultIncrementTick", lastAssaultIncrementTick);
        tag.putBoolean("assaultTriggered", assaultTriggered);
        tag.putLong("assaultCooldownEndTick", assaultCooldownEndTick);

        // ★ 修改点：保存 purified 标志
        tag.putBoolean("purified", purified);

        // 保存已解锁阶段
        int[] unlockedArray = unlockedPhases.stream().mapToInt(i -> i).toArray();
        tag.putIntArray("unlockedPhases", unlockedArray);

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            long now = server.getTickCount();
            int remaining = 0;
            if (nextUpgradeAvailableTick > now) {
                long diffTicks = nextUpgradeAvailableTick - now;
                remaining = (int) Math.ceil(diffTicks / 20.0);
            }
            tag.putInt("cooldownRemaining", remaining);
        }

        return tag;
    }

    public static WorldEvolutionData load(CompoundTag tag) {
        WorldEvolutionData data = new WorldEvolutionData();
        data.evolutionPoints = tag.getInt("evolutionPoints");
        data.evolutionPhase = tag.getInt("evolutionPhase");
        data.nextUpgradeAvailableTick = tag.getLong("nextUpgradeTick");
        data.difficulty = DifficultyManager.Difficulty.fromOrdinal(tag.getInt("difficulty"));
        data.fullHivemindUuid = tag.getString("fullHivemindUuid");
        data.assaultValue = tag.getDouble("assaultValue");
        data.lastAssaultIncrementTick = tag.getLong("lastAssaultIncrementTick");
        data.assaultTriggered = tag.getBoolean("assaultTriggered");
        data.assaultCooldownEndTick = tag.getLong("assaultCooldownEndTick");

        // ★ 修改点：读取 purified 标志
        data.purified = tag.getBoolean("purified");
        if (data.purified) {
            // 确保加载后强制清零
            data.evolutionPoints = 0;
            data.evolutionPhase = 0;
            data.nextUpgradeAvailableTick = 0;
            data.unlockedPhases.clear();
            data.unlockedPhases.add(0);
        }

        // 加载已解锁阶段
        int[] unlockedArray = tag.getIntArray("unlockedPhases");
        data.unlockedPhases.clear();
        for (int phase : unlockedArray) {
            data.unlockedPhases.add(phase);
        }
        // 保证阶段0一定解锁
        data.unlockedPhases.add(0);

        if (tag.contains("cooldownRemaining")) {
            int remaining = tag.getInt("cooldownRemaining");
            if (remaining > 0) {
                data.pendingCooldownRemaining = remaining;
            }
        }

        return data;
    }

    // get() 在多个每 tick 路径、每次受击、每次实体加入时都会被调用。
    // SavedData 实例在整个服务器生命周期内不变，因此这里按服务器实例缓存，
    // 省掉每次调用的 getDataStorage() 查表；服务器重启后 server 实例变化会自动重建。
    /** 由 {@link #get(ServerLevel)} 绑定的世界，供内嵌同步使用。 */
    private transient ServerLevel boundLevel;
    /** 有改动尚未同步给客户端。 */
    private transient boolean syncPending;

    /**
     * 任何改动都会调到这里 —— 顺手把「待同步」标记打上，
     * 于是所有写入路径（点数、冷却、阶段）都自动带上同步，无需调用点记得发。
     */
    @Override
    public void setDirty() {
        super.setDirty();
        this.syncPending = true;
    }

    /** 把积攒的同步发出去（每个 tick 由 SporeEntityCache 的 tick 钩子调一次）。 */
    public static void flushPendingSync() {
        WorldEvolutionData data = cachedData;
        if (data != null) data.flushSync();
    }

    private void flushSync() {
        if (!this.syncPending) return;
        ServerLevel level = this.boundLevel;
        if (level == null) return;
        MinecraftServer server = level.getServer();
        if (server == null || !server.isRunning()) return;
        this.syncPending = false;
        syncToClients(level);
    }

    private static MinecraftServer cachedServer;
    private static WorldEvolutionData cachedData;

    /** 服务器停止时释放缓存，避免持有已关闭服务器的强引用。 */
    public static void resetCache() {
        cachedServer = null;
        cachedData = null;
    }

    public static WorldEvolutionData get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        WorldEvolutionData data = cachedData;
        if (data == null || cachedServer != server) {
            ServerLevel overworld = server.overworld();
            data = overworld.getDataStorage().computeIfAbsent(
                    WorldEvolutionData::load,
                    WorldEvolutionData::new,
                    DATA_NAME
            );
            cachedServer = server;
            cachedData = data;
        }

        // 绑定世界，供内嵌同步使用
        data.boundLevel = level;

        // 恢复 pending 冷却（如果存在）
        if (data.pendingCooldownRemaining > 0) {
            long now = server.getTickCount();
            data.nextUpgradeAvailableTick = now + data.pendingCooldownRemaining * 20L;
            data.pendingCooldownRemaining = -1;
            data.setDirty();
        }

        long currentTick = server.getTickCount();
        data.updatePhase(currentTick);
        return data;
    }

    public WorldEvolutionData() {
        unlockedPhases.add(0); // 阶段0默认已解锁
    }

    public void syncToClients(ServerLevel level) {
        if (level.isClientSide()) return;
        int cooldownSeconds = getCooldownSeconds(level);
        int currentThr = getCurrentThreshold();
        int nextThr = getNextThreshold();
        SyncEvolutionDataPacket packet = new SyncEvolutionDataPacket(
                this.evolutionPoints,
                this.evolutionPhase,
                this.difficulty,
                cooldownSeconds,
                currentThr,
                nextThr
        );
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            SporeSRP.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public void syncToPlayer(ServerPlayer player) {
        int cooldownSeconds = getCooldownSeconds(player.serverLevel());
        int currentThr = getCurrentThreshold();
        int nextThr = getNextThreshold();
        SyncEvolutionDataPacket packet = new SyncEvolutionDataPacket(
                this.evolutionPoints,
                this.evolutionPhase,
                this.difficulty,
                cooldownSeconds,
                currentThr,
                nextThr
        );
        SporeSRP.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private int getCooldownSeconds(ServerLevel level) {
        // 满阶段也要照实返回：诱饵在满阶段仍能生效，HUD 不能因此不显示倒计时
        long now = level.getServer().getTickCount();
        long nextTick = this.nextUpgradeAvailableTick;
        if (now >= nextTick) {
            return 0; // 冷却已就绪
        }
        long diffTicks = nextTick - now;
        return (int) ((diffTicks + 19) / 20); // 向上取整
    }


    private int getCurrentThreshold() {
        var thresholds = Config.COMMON.phaseThresholds.get();
        if (evolutionPhase >= thresholds.size()) return 0;
        return (Integer) thresholds.get(evolutionPhase);
    }

    private int getNextThreshold() {
        var thresholds = Config.COMMON.phaseThresholds.get();
        int maxPhase = Config.COMMON.getMaxPhase();
        if (evolutionPhase >= maxPhase) return -1;
        return (Integer) thresholds.get(evolutionPhase + 1);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            WorldEvolutionData data = WorldEvolutionData.get(level);
            long currentTick = level.getServer().getTickCount();
            int oldPhase = data.getEvolutionPhase();
            data.updatePhase(currentTick);
            int newPhase = data.getEvolutionPhase();
            if (newPhase != oldPhase) {
                // 触发阶段变化事件（广播、属性刷新）
                PhaseEffects.onPhaseChange(oldPhase, newPhase, level);
                // 同步到客户端（更新 HUD）
                data.syncToClients(level);
            }
        }
    }
}