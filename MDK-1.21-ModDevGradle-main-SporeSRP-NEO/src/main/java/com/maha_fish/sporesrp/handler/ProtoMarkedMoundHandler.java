package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为每个 proto 独立管理标记菌囊，确保其周围 256 格内的标记菌囊数量不低于 5
 * - 标记菌囊会定期移动到新的随机位置
 * - 所有配置均可通过 Config 调整
 * - 不进行空气检测，直接生成在地表
 */
public class ProtoMarkedMoundHandler {

    // 每个 proto 的管理数据
    private static class ProtoData {
        final UUID protoUuid;
        final Set<UUID> markedMounds = new HashSet<>(); // 属于该 proto 的标记菌囊 UUID
        long lastCheckTime;      // 上次检查数量的时间 (tick)
        long lastMoveTime;       // 上次移动标记菌囊的时间 (tick)

        ProtoData(UUID protoUuid, long currentTick) {
            this.protoUuid = protoUuid;
            this.lastCheckTime = currentTick;
            this.lastMoveTime = currentTick;
        }
    }

    public static final Map<UUID, ProtoData> protoDataMap = new ConcurrentHashMap<>();

    // 配置值（从 Config 读取）
    private static int getCheckIntervalTicks() {
        return Config.COMMON.protoMarkedMoundCheckIntervalTicks.get();
    }

    private static int getMoveIntervalTicks() {
        return Config.COMMON.protoMarkedMoundMoveIntervalTicks.get();
    }

    private static int getTargetMarkedCount() {
        return Config.COMMON.protoMarkedMoundTargetCount.get(); // 默认 5
    }

    private static int getMinDistance() {
        return Config.COMMON.protoMarkedMoundMinDistance.get();
    }

    private static int getMaxDistance() {
        return Config.COMMON.protoMarkedMoundMaxDistance.get();
    }

    private static boolean isEnabled() {
        return Config.COMMON.protoMarkedMoundEnabled.get();
    }

    // ==================== 辅助方法 ====================
    private static boolean isProto(LivingEntity entity) {
        return SporeEntityHelper.isProto(entity);
    }

    private static boolean isMarkedMound(LivingEntity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        return persistentData.contains("IsMarkedMound") && persistentData.getBoolean("IsMarkedMound");
    }

    private static UUID getOwnerProtoUuid(LivingEntity entity) {
        CompoundTag persistentData = entity.getPersistentData();
        if (persistentData.contains("OwnerProtoUUID")) {
            try {
                return UUID.fromString(persistentData.getString("OwnerProtoUUID"));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
    private static final long CLEANUP_INTERVAL = 6000;

    private void cleanStaleData(ServerLevel level) {
        // ★ 遍历所有维度查找，只有全部找不到才删除
        Iterator<Map.Entry<UUID, ProtoData>> it = protoDataMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ProtoData> entry = it.next();
            UUID uuid = entry.getKey();
            boolean found = false;
            for (ServerLevel lv : level.getServer().getAllLevels()) {
                LivingEntity e = (LivingEntity) lv.getEntity(uuid);
                if (e != null && e.isAlive()) { found = true; break; }
            }
            if (!found) it.remove();
        }
    }

    // 设置标记菌囊的归属标记
    private static void markAsMarkedMound(LivingEntity mound, UUID ownerProtoUuid) {
        CompoundTag persistentData = mound.getPersistentData();
        persistentData.putBoolean("IsMarkedMound", true);
        persistentData.putString("OwnerProtoUUID", ownerProtoUuid.toString());
    }

    // 生成标记菌囊（直接放在地面，不检测上方空气）
    private static boolean spawnMarkedMound(ServerLevel level, BlockPos pos, UUID ownerProtoUuid) {
        EntityType<?> moundType = SporeEntityHelper.getMoundType();
        if (moundType == null) return false;
        LivingEntity mound = (LivingEntity) moundType.create(level);
        if (mound == null) return false;
        mound.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        markAsMarkedMound(mound, ownerProtoUuid);
        return level.addFreshEntity(mound);
    }

    // 统计指定 proto 周围 256 格内的标记菌囊数量（只统计属于该 proto 的）
    private static int countMarkedMoundsAround(ServerLevel level, BlockPos center, int radius, UUID ownerUuid) {
        AABB aabb = new AABB(center).inflate(radius);
        List<LivingEntity> markedMounds = level.getEntitiesOfClass(LivingEntity.class, aabb, e -> {
            return isMarkedMound(e) && ownerUuid.equals(getOwnerProtoUuid(e));
        });
        return markedMounds.size();
    }


    // ★ 正确版本：返回固体方块顶部高度
    private static int getSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY() + 1;
    }

    // 为指定 proto 生成足够数量的标记菌囊（直到数量达到目标）
    private static void ensureMarkedMoundCount(ServerLevel level, ProtoData data, BlockPos protoPos) {
        int currentCount = countMarkedMoundsAround(level, protoPos, getMaxDistance(), data.protoUuid);
        int target = getTargetMarkedCount();
        if (currentCount >= target) return;

        int needed = target - currentCount;
        int generated = 0;
        int attempts = 0;
        int maxAttempts = needed * 10;
        // 距离配置在重试循环内每次都会读两次，这里提前取出（数值相同）
        int minDistance = getMinDistance();
        int maxDistance = getMaxDistance();
        int distanceRange = maxDistance - minDistance;
        while (generated < needed && attempts < maxAttempts) {
            // 随机位置：距离 protoMinDistance ~ protoMaxDistance 之间
            int dx = (int) (Math.random() * distanceRange + minDistance);
            int dz = (int) (Math.random() * distanceRange + minDistance);
            if (Math.random() < 0.5) dx = -dx;
            if (Math.random() < 0.5) dz = -dz;
            int x = protoPos.getX() + dx;
            int z = protoPos.getZ() + dz;
            int y = getSurfaceHeight(level, x, z);
            if (y > level.getMinBuildHeight()) {
                BlockPos spawnPos = new BlockPos(x, y, z);
                if (spawnMarkedMound(level, spawnPos, data.protoUuid)) {
                    generated++;
                }
            }
            attempts++;
        }
    }

    // 移动标记菌囊到新位置（不检测空气）
    private static void moveMarkedMound(ServerLevel level, LivingEntity mound, BlockPos newPos) {
        mound.setPos(newPos.getX() + 0.5, newPos.getY(), newPos.getZ() + 0.5);
    }

    // 获取随机位置（相对于 proto）
    private static BlockPos getRandomPositionAround(BlockPos center, int minDist, int maxDist) {
        int dx = (int) (Math.random() * (maxDist - minDist) + minDist);
        int dz = (int) (Math.random() * (maxDist - minDist) + minDist);
        if (Math.random() < 0.5) dx = -dx;
        if (Math.random() < 0.5) dz = -dz;
        int x = center.getX() + dx;
        int z = center.getZ() + dz;
        return new BlockPos(x, center.getY(), z);
    }

    // ==================== 事件监听 ====================
    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        

    if (event.getLevel().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;if (!isEnabled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (isProto(entity)) {
            UUID uuid = entity.getUUID();
            long currentTick = level.getServer().getTickCount();
            protoDataMap.put(uuid, new ProtoData(uuid, currentTick));
            SporeSRP.LOGGER.debug("Proto {} added to marked mound system", uuid);
        }
        // 如果是标记菌囊，检查其所属 proto 是否还存在，如果不存在则移除自身
        if (isMarkedMound(entity)) {
            UUID ownerUuid = getOwnerProtoUuid(entity);
            if (ownerUuid != null && !protoDataMap.containsKey(ownerUuid)) {
                entity.discard();
                SporeSRP.LOGGER.debug("Orphaned marked mound removed");
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;if (!isEnabled()) return;
        LivingEntity entity = event.getEntity();
        if (isProto(entity)) {
            protoDataMap.remove(entity.getUUID());
            SporeSRP.LOGGER.debug("Proto {} removed from marked mound system", entity.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {


    if (!Config.COMMON_SPEC.isLoaded()) return;if (!isEnabled()) return;

        if (event.getServer().getTickCount() % 2 != 0) return;

        long currentTick = event.getServer().getTickCount();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ProtoData data : protoDataMap.values()) {
                LivingEntity proto = (LivingEntity) level.getEntity(data.protoUuid);
                if (proto == null || !proto.isAlive()) continue;

                BlockPos protoPos = proto.blockPosition();
                if (currentTick - data.lastCheckTime >= getCheckIntervalTicks()) {
                    data.lastCheckTime = currentTick;
                    ensureMarkedMoundCount(level, data, protoPos);
                }
                if (currentTick - data.lastMoveTime >= getMoveIntervalTicks()) {
                    data.lastMoveTime = currentTick;
                    AABB area = new AABB(protoPos).inflate(getMaxDistance());
                    List<LivingEntity> markedMounds = level.getEntitiesOfClass(LivingEntity.class, area, e ->
                            isMarkedMound(e) && data.protoUuid.equals(getOwnerProtoUuid(e))
                    );
                    for (LivingEntity mound : markedMounds) {
                        BlockPos newPos = getRandomPositionAround(protoPos, getMinDistance(), getMaxDistance());
                        int y = getSurfaceHeight(level, newPos.getX(), newPos.getZ());
                        if (y > level.getMinBuildHeight()) {
                            moveMarkedMound(level, mound, new BlockPos(newPos.getX(), y, newPos.getZ()));
                        }
                    }
                }
            }

            // 定期清理过期 Proto 数据
            if (currentTick % CLEANUP_INTERVAL == 0) {
                cleanStaleData(level);
            }
        }
    }
}