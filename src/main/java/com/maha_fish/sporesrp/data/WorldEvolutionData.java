package com.maha_fish.sporesrp.data;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.network.SyncEvolutionDataPacket;
import com.maha_fish.sporesrp.phase.PhaseEffects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = SporeSRP.MOD_ID)
public class WorldEvolutionData extends SavedData {
    private static final String DATA_NAME = SporeSRP.MOD_ID + "_world_data";
    private int evolutionPoints = 0;
    private int evolutionPhase = 0;
    private long nextUpgradeAvailableTick = 0;
    private DifficultyManager.Difficulty difficulty = DifficultyManager.Difficulty.SIMPLE;
    private final Set<Integer> unlockedPhases = new HashSet<>();
    private int pendingCooldownRemaining = -1;
    private boolean purified = false;
    private String fullHivemindUuid = "";
    private double assaultValue = 0.0;
    private long lastAssaultIncrementTick = 0;
    private boolean assaultTriggered = false;
    private long assaultCooldownEndTick = 0;

    // ========== 诱饵 / 点数冷却 ==========
    /**
     * 到此刻之前不再累计演化点数 —— 对应 SRP 的「演化点数冷却」。
     * 诱饵生效后设置，冷却期间任何来源（击杀、周期、转化）都不加点。
     */
    // ========== 点数冷却（与「阶段提升冷却」合并为同一个计时器） ==========
    /**
     * 冷却结束时刻。语义：**冷却中无法获得演化点数**。
     *
     * <p>阶段提升与诱饵激活都写同一个 {@link #nextUpgradeAvailableTick}，
     * 且采用**加法**累加：已过期的从现在重新计时，未过期的在剩余时间上继续加。
     */

    public boolean isPointGainBlocked(long currentTick) { return currentTick < nextUpgradeAvailableTick; }

    /** 按「加法」延长点数冷却：已过期则从现在算起，未过期则在剩余上累加。 */
    /**
     * 统一时钟：冷却/点数判定一律使用**世界时间**。
     *
     * <p>历史遗留：有的调用点传 {@code level.getGameTime()}（跨存档累积），
     * 而显示侧却用 {@code server.getTickCount()}（uptime，重启归零），
     * 两个时钟一减就会得出上百万 tick 的荒谬倒计时。这里彻底统一。
     */
    private long nowTick() {
        if (this.boundLevel != null) return this.boundLevel.getGameTime();
        MinecraftServer server = cachedServer;
        if (server != null) {
            try { return server.overworld().getGameTime(); } catch (Throwable ignored) { }
        }
        return 0L;
    }

    public void adjustPointCooldown(long currentTick, int seconds) {
        if (seconds == 0) return;
        long now = nowTick();
        long base = Math.max(this.nextUpgradeAvailableTick, now);
        this.nextUpgradeAvailableTick = Math.max(now, base + seconds * 20L);

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
        // 只允许降阶。绝不能调 updatePhase —— 它可能把「落后于点数的阶段」补升一级，
        // 从而顺带把那个阶段的默认冷却也加上，表现为「诱饵把冷却拉回阶段默认值」。
        lowerPhaseOnly();
        adjustPointCooldown(nowTick(), cooldownSeconds);
        setDirty();
    }

    /** 单个诱饵生效：只叠加演化时间冷却，不改动演化点数。 */
    public void applyDecoyCooldown(int cooldownSeconds, long currentTick) {
        if (purified) return;

        adjustPointCooldown(nowTick(), cooldownSeconds);
        setDirty();
    }

    // ========== Getter/Setter ==========
    public int getEvolutionPoints() { return evolutionPoints; }
    public int getEvolutionPhase() { return evolutionPhase; }
    public long getNextUpgradeAvailableTick() { return nextUpgradeAvailableTick; }
    public DifficultyManager.Difficulty getDifficulty() { return difficulty; }

    public void setDifficulty(DifficultyManager.Difficulty difficulty) {
        this.difficulty = difficulty;
        setDirty();
    }

    public boolean isPurified() { return purified; }

    public void setPurified(boolean purified) {
        this.purified = purified;
        if (purified) {
            this.evolutionPoints = 0;
            this.evolutionPhase = 0;
            this.nextUpgradeAvailableTick = 0;
            this.unlockedPhases.clear();
            this.unlockedPhases.add(0);
        }
        setDirty();
    }

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
    public void setEvolutionPoints(int points, long currentTick) {
        if (purified) {
            this.evolutionPoints = 0;
            this.evolutionPhase = 0;
            setDirty();
            return;
        }
        this.evolutionPoints = Math.max(0, points);
        setDirty();
        updatePhase(nowTick());
    }

    public void addEvolutionPoints(int amount, long currentTick) {
        if (purified) return;
        if (amount == 0) return;
        // 正数：受诱饵的「点数冷却」限制
        if (amount > 0) {
            if (isPointGainBlocked(currentTick)) return;
            setEvolutionPoints(this.evolutionPoints + amount, currentTick);
            return;
        }
        // 负数：击杀只扣点数，**不降阶段**（SRP 语义：压阶段只能靠诱饵）
        this.evolutionPoints = Math.max(0, this.evolutionPoints + amount);
        setDirty();
    }

    public void forceSetPhase(int targetPhase, long currentTick) {
        if (purified && targetPhase != 0) {
            SporeSRP.LOGGER.warn("Purified world: forceSetPhase to {} rejected (only 0 allowed)", targetPhase);
            return;
        }
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

        for (int i = 0; i <= targetPhase; i++) {
            unlockedPhases.add(i);
        }
        setDirty();

        SporeSRP.LOGGER.info("Force set phase to {} (points: {})", targetPhase, requiredPoints);
    }


    /** 按当前演化点数推算应有的阶段（越界已夹到 maxPhase）。 */
    private int computeTheoreticalPhase() {
        var thresholds = Config.COMMON.phaseThresholds.get();
        int maxPhase = Config.COMMON.getMaxPhase();
        int theoretical = 0;
        for (int i = thresholds.size() - 1; i >= 0; i--) {
            if (evolutionPoints >= (Integer) thresholds.get(i)) {
                theoretical = i;
                break;
            }
        }
        return Math.min(theoretical, maxPhase);
    }

    /**
     * 只降不升：阶段若高于当前点数对应的阶段，就回退到该阶段。
     *
     * <p>诱饵专用 —— 诱饵的职责是压阶段，绝不能推进阶段（推进阶段会顺带收阶段冷却）。
     */
    private void lowerPhaseOnly() {
        int theoretical = computeTheoreticalPhase();
        if (theoretical < evolutionPhase) {
            evolutionPhase = theoretical;
            setDirty();
        }
    }

    private void updatePhase(long ignoredTick) {
        long currentTick = nowTick();
        if (purified) {
            this.evolutionPhase = 0;
            setDirty();
            return;
        }

        int maxPhase = Config.COMMON.getMaxPhase();
        int theoreticalPhase = computeTheoreticalPhase();

        // 降级处理：只是阶段回退，**不叠加任何冷却** ——
        // 冷却只由「升阶」和「诱饵」各自负责。否则一用诱饵压阶段，
        // 就会把那些早已走完的阶段冷却又加回来。
        if (theoreticalPhase < evolutionPhase) {
            evolutionPhase = theoreticalPhase;
            setDirty();
            return;
        }

        while (theoreticalPhase > evolutionPhase) {
            int targetNextPhase = evolutionPhase + 1;
            if (targetNextPhase > maxPhase) break;

            if (unlockedPhases.contains(targetNextPhase)) {
                evolutionPhase = targetNextPhase;
                setDirty();
            } else {
                // 冷却中不得解锁新阶段（配置项文档：冷却时间内即使点数足够也不能升级）。
                // 少了这道判断，onServerTick 每 tick 调一次 updatePhase 就会连升多级、
                // 每级各叠一次阶段冷却。
                if (currentTick < nextUpgradeAvailableTick) {
                    break;
                }
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("evolutionPoints", evolutionPoints);
        tag.putInt("evolutionPhase", evolutionPhase);
        tag.putLong("nextUpgradeTick", nextUpgradeAvailableTick);
        tag.putInt("difficulty", difficulty.ordinal());
        tag.putString("fullHivemindUuid", fullHivemindUuid);
        tag.putDouble("assaultValue", assaultValue);
        tag.putLong("lastAssaultIncrementTick", lastAssaultIncrementTick);
        tag.putBoolean("assaultTriggered", assaultTriggered);
        tag.putLong("assaultCooldownEndTick", assaultCooldownEndTick);
        tag.putBoolean("purified", purified);

        int[] unlockedArray = unlockedPhases.stream().mapToInt(i -> i).toArray();
        tag.putIntArray("unlockedPhases", unlockedArray);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            long now = server.overworld().getGameTime();
            int remaining = 0;
            if (nextUpgradeAvailableTick > now) {
                long diffTicks = nextUpgradeAvailableTick - now;
                remaining = (int) Math.ceil(diffTicks / 20.0);
            }
            tag.putInt("cooldownRemaining", remaining);
        }

        return tag;
    }

    public static WorldEvolutionData load(CompoundTag tag, HolderLookup.Provider registries) {
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
        data.purified = tag.getBoolean("purified");
        if (data.purified) {
            data.evolutionPoints = 0;
            data.evolutionPhase = 0;
            data.nextUpgradeAvailableTick = 0;
            data.unlockedPhases.clear();
            data.unlockedPhases.add(0);
        }

        int[] unlockedArray = tag.getIntArray("unlockedPhases");
        data.unlockedPhases.clear();
        for (int phase : unlockedArray) {
            data.unlockedPhases.add(phase);
        }
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
    // 原始实现每次都 new 一个 SavedData.Factory 并重新查 DataStorage；
    // 这里把 Factory 提前构建，并按服务器实例缓存已解析的数据对象。
    private static final SavedData.Factory<WorldEvolutionData> FACTORY =
            new SavedData.Factory<>(WorldEvolutionData::new, (tag, reg) -> load(tag, reg), null);

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
            data = overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
            cachedServer = server;
            cachedData = data;
        }

        // 绑定世界，供内嵌同步使用
        data.boundLevel = level;

        if (data.pendingCooldownRemaining > 0) {
            long now = server.overworld().getGameTime();
            data.nextUpgradeAvailableTick = now + data.pendingCooldownRemaining * 20L;
            data.pendingCooldownRemaining = -1;
            data.setDirty();
        }

        long currentTick = server.getTickCount();
        data.updatePhase(currentTick);
        return data;
    }

    public WorldEvolutionData() {
        unlockedPhases.add(0);
    }

    // ========== 网络同步 ==========
    public void syncToClients(ServerLevel level) {
        if (level.isClientSide()) return;
        long cooldownEndTick = this.nextUpgradeAvailableTick;
        int currentThr = getCurrentThreshold();
        int nextThr = getNextThreshold();
        SyncEvolutionDataPacket packet = new SyncEvolutionDataPacket(
                this.evolutionPoints,
                this.evolutionPhase,
                this.difficulty.name(),
                cooldownEndTick,
                currentThr,
                nextThr
        );
        PacketDistributor.sendToAllPlayers(packet);
    }

    public void syncToPlayer(ServerPlayer player) {
        long cooldownEndTick = this.nextUpgradeAvailableTick;
        int currentThr = getCurrentThreshold();
        int nextThr = getNextThreshold();
        SyncEvolutionDataPacket packet = new SyncEvolutionDataPacket(
                this.evolutionPoints,
                this.evolutionPhase,
                this.difficulty.name(),
                cooldownEndTick,
                currentThr,
                nextThr
        );
        PacketDistributor.sendToPlayer(player, packet);
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

    // ========== 服务端 Tick 事件（静态监听） ==========
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            WorldEvolutionData data = WorldEvolutionData.get(level);
            long currentTick = level.getServer().getTickCount();
            int oldPhase = data.getEvolutionPhase();
            data.updatePhase(currentTick);
            int newPhase = data.getEvolutionPhase();
            if (newPhase != oldPhase) {
                PhaseEffects.onPhaseChange(oldPhase, newPhase, level);
                data.syncToClients(level);
            }
        }
    }
}