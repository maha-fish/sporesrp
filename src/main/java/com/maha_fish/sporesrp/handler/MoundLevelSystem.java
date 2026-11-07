package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.SporeSpawnHelper;
import com.maha_fish.sporesrp.util.SporeBridge;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import com.maha_fish.sporesrp.util.SporeTierHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MoundLevelSystem {

    // ==================== NBT 持久化键名 ====================
    private static final String NBT_KEY_LEVEL = "Sporesrp_Mound_Level";
    private static final String NBT_KEY_LAST_UPGRADE = "Sporesrp_Mound_LastUpgrade";

    // ==================== 内存缓存（用于运行时快速访问） ====================
    public static final ConcurrentHashMap<UUID, MoundData> moundDataMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> cooldownMap = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();
    private static Map<Integer, SupportConfig> CONFIG_BY_LEVEL = null;

    private static class MoundData {
        int level;
        long lastLevelUpTick;
        long lastScanTick;
        MoundData(long currentTick) {
            this.level = 1;
            this.lastLevelUpTick = currentTick;
            this.lastScanTick = currentTick;
        }
    }

    private static class SupportConfig {
        int level, range, minSmall, maxSmall, minMedium, maxMedium, cooldownTicks;
        boolean spawnScent;
        SupportConfig(String line) {
            String[] parts = line.split(":");
            if (parts.length != 8) throw new IllegalArgumentException("Invalid support config: " + line);
            this.level = Integer.parseInt(parts[0]);
            this.range = Integer.parseInt(parts[1]);
            this.minSmall = Integer.parseInt(parts[2]);
            this.maxSmall = Integer.parseInt(parts[3]);
            this.minMedium = Integer.parseInt(parts[4]);
            this.maxMedium = Integer.parseInt(parts[5]);
            this.cooldownTicks = Integer.parseInt(parts[6]) * 20;
            this.spawnScent = parts[7].equals("1");
        }
    }

    private static synchronized Map<Integer, SupportConfig> getConfig() {
        if (CONFIG_BY_LEVEL == null) {
            CONFIG_BY_LEVEL = new HashMap<>();
            try {
                for (String line : Config.COMMON.moundSupportConfigs.get()) {
                    SupportConfig cfg = new SupportConfig(line);
                    CONFIG_BY_LEVEL.put(cfg.level, cfg);
                }
            } catch (Exception e) {
                SporeSRP.LOGGER.error("Failed to parse mound support config", e);
            }
        }
        return CONFIG_BY_LEVEL;
    }

    public static void reloadConfig() { CONFIG_BY_LEVEL = null; }

    // ==================== 辅助方法（服务器实例 & 当前Tick） ====================
    private static MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    private static long getCurrentTick() {
        MinecraftServer server = getServer();
        return server != null ? server.getTickCount() : 0;
    }

    // ==================== 写入 NBT（仅当实体已加载时） ====================
    public static void writeLevelToNBT(UUID uuid, int level) {
        MinecraftServer server = getServer();
        if (server == null) return;
        for (ServerLevel levelObj : server.getAllLevels()) {
            LivingEntity entity = (LivingEntity) levelObj.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                CompoundTag tag = entity.getPersistentData();
                tag.putInt(NBT_KEY_LEVEL, Math.max(1, Math.min(level, 4)));
                tag.putLong(NBT_KEY_LAST_UPGRADE, getCurrentTick());
                return;
            }
        }
    }

    // ==================== 核心 API（完全兼容指令） ====================

    public static void addMound(UUID uuid, long currentTick) {
        if (!moundDataMap.containsKey(uuid)) {
            moundDataMap.put(uuid, new MoundData(currentTick));
        }
    }

    public static void removeMound(UUID uuid) {
        moundDataMap.remove(uuid);
        cooldownMap.remove(uuid);
    }

    public static int getLevel(UUID uuid) {
        MoundData data = moundDataMap.get(uuid);
        if (data != null) {
            return data.level;
        }

        // ★ 内存缺失时，尝试从已加载实体的 NBT 恢复（防御性编程）
        MinecraftServer server = getServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                LivingEntity entity = (LivingEntity) level.getEntity(uuid);
                if (entity != null && entity.isAlive()) {
                    CompoundTag tag = entity.getPersistentData();
                    if (tag.contains(NBT_KEY_LEVEL)) {
                        int levelFromNbt = tag.getInt(NBT_KEY_LEVEL);
                        MoundData recovered = new MoundData(getCurrentTick());
                        recovered.level = Math.max(1, Math.min(levelFromNbt, 4));
                        if (tag.contains(NBT_KEY_LAST_UPGRADE)) {
                            recovered.lastLevelUpTick = tag.getLong(NBT_KEY_LAST_UPGRADE);
                        }
                        moundDataMap.put(uuid, recovered);
                        return recovered.level;
                    }
                }
            }
        }
        return 1; // 默认等级
    }

    public static void setLevel(UUID uuid, int newLevel) {
        int clamped = Math.max(1, Math.min(newLevel, 4));

        // 更新内存
        MoundData data = moundDataMap.get(uuid);
        if (data == null) {
            data = new MoundData(getCurrentTick());
            moundDataMap.put(uuid, data);
        }
        data.level = clamped;

        // ★ 尝试写入 NBT（实体可能未加载，此时写入会无效果，等实体加载时由 onEntityJoin 恢复）
        writeLevelToNBT(uuid, clamped);
    }

    /**
     * 等级改为**镜像 Spore 菌囊的真实成长阶段**。
     * 本模组不再自己计时升级，因此原来的 {@code tryLevelUp} 已删除 ——
     * 否则「我方计时」与「Spore 原生老化」会长期漂移，
     * 出现「支援强度按我方等级、抗揍程度按 Spore 阶段」的错配。
     */
    static void syncLevelFromSpore(UUID uuid, LivingEntity mound) {
        MoundData data = moundDataMap.get(uuid);
        if (data == null || mound == null) return;
        int age = SporeBridge.getMoundAge(mound);
        if (age > 0 && age != data.level) {
            data.level = age;
            data.lastLevelUpTick = 0;
        }
    }

    // ==================== 辅助判断 ====================
    private static boolean isMound(LivingEntity e) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return id != null && "spore:mound".equals(id.toString());
    }

    private static boolean isNonSporeEntity(LivingEntity e) {
        if (e instanceof Player) return true;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return id == null || !"spore".equals(id.getNamespace());
    }

    // ==================== 生成实体辅助 ====================
    private static void spawnEntity(ServerLevel level, BlockPos pos, List<? extends String> pool) {
        if (pool.isEmpty()) return;
        List<? extends String> organoids = SporeEntityHelper.getOrganoids();
        for (int attempt = 0; attempt < 10; attempt++) {
            String typeName = pool.get(RANDOM.nextInt(pool.size()));
            if (organoids.contains(typeName)) continue;
            ResourceLocation id = ResourceLocation.tryParse(typeName);
            if (id == null) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) continue;
            LivingEntity summoned = SporeSpawnHelper.spawnFinalized(level, type, pos);

            if (summoned != null) trackSummon(level, summoned);
            return;
        }
    }

    private static void spawnScent(ServerLevel level, BlockPos pos) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("spore:scent"));
        if (type == null) return;
        LivingEntity summoned = SporeSpawnHelper.spawnFinalized(level, type, pos);

        if (summoned != null) trackSummon(level, summoned);
    }

    // ==================== 菌囊支援召唤物：抛出 + 生命周期 ====================
    /**
     * UUID -> {召唤时的 tick, 最后一次有仇恨的 tick}
     *
     * <p>只跟踪被菌囊支援召唤出来的**生物**（{@code Mob}）；
     * 孢气等非生物实体不跟踪。
     */
    private static final Map<UUID, long[]> SUMMONED_MOBS = new ConcurrentHashMap<>();
    private static int summonSweepTimer = 0;
    /** 生命周期检查间隔（tick）。 */
    private static final int SUMMON_SWEEP_INTERVAL = 10;

    /**
     * 召唤后处理：施加「被抛出」的推力，并登记生命周期。
     *
     * <ul>
     *   <li>向上 + 水平面 360° 随机方向的轻微推力；</li>
     *   <li>无仇恨满 {@code summonedNoAggroSeconds} 秒 → 消失；</li>
     *   <li>存在满 {@code summonedLifetimeSeconds} 秒 → 消失；</li>
     *   <li>被召唤的生物不会再次成为支援触发者（本类只按时间+玩家距离触发，天然不会）。</li>
     * </ul>
     */
    private static void trackSummon(ServerLevel level, LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return;
        // 孢气等「无等级」的功能性实体不纳入生命周期，否则会被当成召唤物清掉。
        // ScentEntity 继承 PathfinderMob，单纯判 Mob 会误伤。
        if (SporeTierHelper.tierOf(entity) == null) return;

        double horizontal = Config.COMMON.supportThrowHorizontal.get();
        double upward = Config.COMMON.supportThrowUpward.get();
        if (horizontal > 0.0 || upward > 0.0) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            mob.setDeltaMovement(Math.cos(angle) * horizontal, upward, Math.sin(angle) * horizontal);
            mob.hurtMarked = true; // 让客户端同步速度
        }

        long now = level.getGameTime();
        SUMMONED_MOBS.put(mob.getUUID(), new long[]{now, now});
    }

    /**
     * 该生物是否为菌囊支援召唤出来的。
     *
     * <p>供 {@code SupportSystemHandler} 判断：被召唤出来的生物不应再次触发受击支援，
     * 否则会出现「菌囊召唤 → 召唤物被打 → 再支援 → 再召唤」的连锁。
     */
    public static boolean isSummoned(LivingEntity entity) {
        return entity != null && SUMMONED_MOBS.containsKey(entity.getUUID());
    }
    /** 清理超时的召唤物：无仇恨 10 秒，或存在 100 秒。 */
    private static void sweepSummonedMobs(MinecraftServer server) {
        if (SUMMONED_MOBS.isEmpty() || server == null) return;

        long now = server.overworld().getGameTime();
        long noAggroTicks = (long) Config.COMMON.supportSummonedNoAggroSeconds.get() * 20L;
        long lifetimeTicks = (long) Config.COMMON.supportSummonedLifetimeSeconds.get() * 20L;

        Iterator<Map.Entry<UUID, long[]>> iterator = SUMMONED_MOBS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, long[]> mapEntry = iterator.next();
            long[] state = mapEntry.getValue();

            LivingEntity entity = null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(mapEntry.getKey()) instanceof LivingEntity living) {
                    entity = living;
                    break;
                }
            }
            if (entity == null || !entity.isAlive()) {
                iterator.remove();
                continue;
            }

            if (entity instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
                state[1] = now; // 有仇恨，刷新计时
                continue;
            }

            boolean lifetimeOut = lifetimeTicks > 0 && now - state[0] >= lifetimeTicks;
            boolean noAggroOut = noAggroTicks > 0 && now - state[1] >= noAggroTicks;
            if (lifetimeOut || noAggroOut) {
                entity.discard();
                iterator.remove();
            }
        }
    }
    // ==================== 事件监听 ====================

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        

    if (event.getLevel().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) event.getEntity();
        if (!isMound(living)) return;

        UUID uuid = living.getUUID();
        CompoundTag tag = living.getPersistentData();

        // ★ 关键逻辑：内存优先
        if (moundDataMap.containsKey(uuid)) {
            // 内存中已有数据（可能是指令刚设置的），以内存为准，强制覆写 NBT
            int memLevel = moundDataMap.get(uuid).level;
            tag.putInt(NBT_KEY_LEVEL, Math.max(1, Math.min(memLevel, 4)));
            tag.putLong(NBT_KEY_LAST_UPGRADE, getCurrentTick());
            SporeSRP.LOGGER.debug("Mound {} loaded with memory level {} (overwrote NBT)", uuid, memLevel);
            return;
        }

        // 内存中没有，从 NBT 恢复
        int level = tag.contains(NBT_KEY_LEVEL) ? tag.getInt(NBT_KEY_LEVEL) : 1;
        long lastUpgrade = tag.contains(NBT_KEY_LAST_UPGRADE) ? tag.getLong(NBT_KEY_LAST_UPGRADE) : getCurrentTick();

        MoundData data = new MoundData(getCurrentTick());
        data.level = Math.max(1, Math.min(level, 4));
        data.lastLevelUpTick = lastUpgrade;
        // lastScanTick 保留为当前 Tick，避免加载后立即扫描
        moundDataMap.put(uuid, data);
        SporeSRP.LOGGER.debug("Mound {} loaded from NBT with level {}", uuid, data.level);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        

    if (event.getEntity().level().isClientSide()) return;
    if (event.getEntity().level().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) event.getEntity();
        if (!isMound(living)) return;
        removeMound(living.getUUID());
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {        // ---- 0. 菌囊召唤物生命周期（无仇恨 10 秒 / 存在 100 秒） ----
        if (++summonSweepTimer >= SUMMON_SWEEP_INTERVAL) {
            summonSweepTimer = 0;
            sweepSummonedMobs(ServerLifecycleHooks.getCurrentServer());
        }


    if (!Config.COMMON_SPEC.isLoaded()) return;ServerLevel overworld = event.getServer().overworld();
        if (overworld.getPlayers(player -> true).isEmpty()) return;

        long currentTick = event.getServer().getTickCount();

        // ---- 1. 等级镜像 ----
        // 等级不再由本模组计时提升，改为在下面解析到菌囊实体时同步 Spore 的真实阶段。

        // ---- 2. 支援系统 ----
        Map<Integer, SupportConfig> configs = getConfig();
        int scanInterval = Config.COMMON.moundScanIntervalTicks.get();

        Set<ServerLevel> activeLevels = new HashSet<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            activeLevels.add((ServerLevel) player.level());
        }
        if (activeLevels.isEmpty()) return;

        if (currentTick % 6000 == 0) {
            cooldownMap.entrySet().removeIf(entry -> currentTick - entry.getValue() > 360000);
            cleanStaleData(overworld);  // 用主世界作为入口，内部会遍历所有维度
        }

        for (ServerLevel level : activeLevels) {
            for (UUID uuid : moundDataMap.keySet()) {
                MoundData data = moundDataMap.get(uuid);
                if (data == null) continue;
                if (currentTick - data.lastScanTick < scanInterval) continue;
                data.lastScanTick = currentTick;

                LivingEntity mound = (LivingEntity) level.getEntity(uuid);
                if (mound == null || !mound.isAlive()) continue;

                // 用 Spore 的真实成长阶段刷新镜像，保证支援强度与原生阶段一致
                syncLevelFromSpore(uuid, mound);

                // ---- 阶段节点：强制菌囊不低于该阶段的最低等级 ----
                int phaseFloor = minLevelForPhase(WorldEvolutionData.get(level).getEvolutionPhase());
                if (phaseFloor > getLevel(uuid)) {
                    setLevel(uuid, phaseFloor);
                    SporeSRP.LOGGER.debug("Mound {} forced to level {} by phase floor", uuid, phaseFloor);
                }
                int levelNum = getLevel(uuid);
                SupportConfig cfg = configs.get(levelNum);
                if (cfg == null) continue;

                // 冷却检查
                Long lastTrigger = cooldownMap.get(uuid);
                if (lastTrigger != null) {
                    if (currentTick - lastTrigger < cfg.cooldownTicks) {
                        continue;
                    } else {
                        cooldownMap.remove(uuid);
                    }
                }

                // 检查范围内是否有玩家
                BlockPos center = mound.blockPosition();
                int range = cfg.range;
                boolean hasTarget = false;
                for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(mound) <= range * range)) {
                    hasTarget = true;
                    break;
                }
                if (!hasTarget) continue;

                cooldownMap.put(uuid, currentTick);

                // 触发支援
                if (cfg.spawnScent) {
                    spawnScent(level, center);
                } else {
                    for (int i = 0; i < cfg.maxSmall; i++) {
                        if (i < cfg.minSmall - 1 && RANDOM.nextBoolean()) continue;
                        spawnEntity(level, center, SporeEntityHelper.getSmallPool());
                    }
                    for (int i = 0; i < cfg.maxMedium; i++) {
                        if (i < cfg.minMedium - 1 && RANDOM.nextBoolean()) continue;
                        spawnEntity(level, center, SporeEntityHelper.getMediumPool());
                    }
                }
                SporeSRP.LOGGER.debug("Mound {} (level {}) triggered support", uuid, levelNum);
            }
        }
    }

    // ==================== 清理 API（供 CacheCleaner 调用） ====================
    public static void cleanStaleData(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, MoundData>> it = moundDataMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MoundData> entry = it.next();
            UUID uuid = entry.getKey();

            boolean found = false;
            for (ServerLevel lv : server.getAllLevels()) {
                LivingEntity e = (LivingEntity) lv.getEntity(uuid);
                if (e != null && e.isAlive()) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                it.remove();
                cooldownMap.remove(uuid);
            }
        }
    }

    // ==================== 调试工具 ====================
    public static int getMapSize() {
        return moundDataMap.size();
    }

    /** 当前演化阶段对应的菌囊最低等级（配置索引 = 演化阶段）。 */
    private static int minLevelForPhase(int phase) {
        var list = Config.COMMON.moundMinLevelByPhase.get();
        if (list == null || list.isEmpty()) return 1;
        int index = Math.max(0, Math.min(phase, list.size() - 1));
        Integer value = list.get(index);
        if (value == null) return 1;
        return Math.max(1, Math.min(value, 4));
    }
}