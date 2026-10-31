package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.maha_fish.sporesrp.util.SporeBridge;
import com.maha_fish.sporesrp.util.SporeEntityHelper;

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
     * 否则「我方计时 5 分钟」与「Spore 原生老化」会长期漂移，
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
        return SporeEntityHelper.isMound(e);
    }

    private static boolean isNonSporeEntity(LivingEntity e) {
        if (e instanceof Player) return true;
        return !SporeEntityHelper.isSporeType(e.getType());
    }

    // ==================== 生成实体辅助 ====================
    private static void spawnEntity(ServerLevel level, BlockPos pos, List<? extends String> pool) {
        if (pool.isEmpty()) return;
        Set<String> organoids = SporeEntityHelper.organoidSet();
        for (int attempt = 0; attempt < 10; attempt++) {
            String typeName = pool.get(RANDOM.nextInt(pool.size()));
            if (organoids.contains(typeName)) continue;
            ResourceLocation id = ResourceLocation.tryParse(typeName);
            if (id == null) continue;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            if (type == null) continue;
            // 池里若被配置成非生物实体（物品/船…），原写法会 ClassCastException
            if (!(type.create(level) instanceof LivingEntity entity)) continue;
            entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            level.addFreshEntity(entity);
            return;
        }
    }

    private static void spawnScent(ServerLevel level, BlockPos pos) {
        EntityType<?> type = SporeEntityHelper.getScentType();
        if (type == null) return;
        if (!(type.create(level) instanceof LivingEntity entity)) return;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    // ==================== 事件监听 ====================

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
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
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity living = (LivingEntity) event.getEntity();
        if (!isMound(living)) return;
        removeMound(living.getUUID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ServerLevel overworld = event.getServer().overworld();
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

                // 检查周围是否存在其他非类器官体的真菌生物
                boolean hasOtherFungus = false;
                AABB checkBox = new AABB(center).inflate(range);
                List<LivingEntity> nearbyFungus = level.getEntitiesOfClass(LivingEntity.class, checkBox,
                        e -> e != mound && e.isAlive() && SporeEntityHelper.isSporeEntity(e));
                if (!nearbyFungus.isEmpty()) {
                    // 缓存版类器官体 Set：成员判定结果与 List.contains 完全一致
                    Set<String> organoidIds = SporeEntityHelper.organoidSet();
                    for (LivingEntity e : nearbyFungus) {
                        ResourceLocation id = SporeEntityHelper.getEntityId(e);
                        if (id == null) continue;
                        String name = id.toString();
                        if (!organoidIds.contains(name)) {
                            hasOtherFungus = true;
                            break;
                        }
                    }
                }
                if (hasOtherFungus) {
                    SporeSRP.LOGGER.debug("Mound {} (level {}) skipped support due to nearby non-organoid fungus", uuid, levelNum);
                    continue;
                }

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
}