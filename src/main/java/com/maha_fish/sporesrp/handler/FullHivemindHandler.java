package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.item.ModItems;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.CasingBuilder;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FullHivemindHandler {

    private static final UUID HEALTH_MOD_UUID = UUID.fromString("f2345678-9abc-def0-1234-56789abcdef0");
    private static final UUID ARMOR_MOD_UUID = UUID.fromString("f3456789-abcd-ef01-2345-6789abcdef01");
    private static final UUID FOLLOW_MOD_UUID = UUID.fromString("f456789a-bcde-f012-3456-789abcdef012");

    private static final Object CONVERSION_LOCK = new Object();

    private static final String HIVEMIND_TAG = "IsFullHivemind";
    private static final String MINING_DATA_TAG = "FullHivemindMiningData";
    // 挖掘常量
    private static final int GROUND_MINING_COOLDOWN = 40;        // 2秒
    private static final int GROUND_MINING_RADIUS = 2;           // 5x5
    private static final int MAX_MINING_Y = -10;
    private static final float MAX_HARDNESS = 50.0f;
    private static final int SPHERE_RADIUS = 50;
    private static final int BLOCKS_PER_TICK = 50;
    private static final int SPHERE_COOLDOWN_TICKS = 40;         // 2秒
    private static final int CASING_COOLDOWN_TICKS = 100;        // 5秒
    private static final int CONVERSION_CHECK_INTERVAL = 30 * 20; // 30秒

    private int tickCounter = 0;

    /** 复用同一个 Random（转换时随机挑选 proto），避免每次转换都新建实例。 */
    private static final Random RANDOM = new Random();

    public static final Map<UUID, MiningData> MINING_DATA_MAP = new ConcurrentHashMap<>();


    public static class MiningData {
        public final UUID entityUuid;
        public int groundMiningY;
        public int groundCooldown;
        public boolean groundMiningDone;
        public boolean sphereMiningStarted;
        public boolean sphereMiningDone;
        public List<BlockPos> sphereQueue;
        public int sphereIndex;
        public int sphereCooldown;
        public long lastItemClearTick;
        public BlockPos lockedPosition;
        public boolean positionLocked;
        public int casingCooldown;
        // ---- watcher 相关 ----
        public int watcherCount;
        public final Set<UUID> watcherUuids = ConcurrentHashMap.newKeySet();

        public MiningData(UUID uuid, int startY) {
            this.entityUuid = uuid;
            this.groundMiningY = startY;
            this.groundCooldown = 0;
            this.groundMiningDone = false;
            this.sphereMiningStarted = false;
            this.sphereMiningDone = false;
            this.sphereQueue = new ArrayList<>();
            this.sphereIndex = 0;
            this.sphereCooldown = 0;
            this.lastItemClearTick = 0;
            this.lockedPosition = null;
            this.positionLocked = false;
            this.casingCooldown = 0;
            // 刚转化时先按 4 个守望者计（4 座塔），spawnWatchers 之后会用实际扫描结果覆盖
            this.watcherCount = 4;
        }
    }

    // ========== 持久化辅助 ==========

    public static void saveMiningData(LivingEntity entity, MiningData data) {
        CompoundTag tag = entity.getPersistentData();
        tag.putBoolean("FH_GroundDone", data.groundMiningDone);
        tag.putInt("FH_GroundY", data.groundMiningY);
        tag.putInt("FH_GroundCooldown", data.groundCooldown);
        tag.putBoolean("FH_SphereDone", data.sphereMiningDone);
        tag.putBoolean("FH_SphereStarted", data.sphereMiningStarted);
        tag.putInt("FH_SphereIndex", data.sphereIndex);
        tag.putInt("FH_CasingCooldown", data.casingCooldown);
        if (data.lockedPosition != null) {
            tag.putLong("FH_LockedPos", data.lockedPosition.asLong());
        }
        tag.putBoolean("FH_PositionLocked", data.positionLocked);
        tag.putLong("FH_LastClear", data.lastItemClearTick);
        String[] watcherStrings = data.watcherUuids.stream()
                .map(UUID::toString)
                .toArray(String[]::new);
        tag.putString("FH_WatcherList", String.join(",", watcherStrings));
        tag.putInt("FH_WatcherCount", data.watcherCount);
    }

    private static MiningData restoreFromNBT(LivingEntity entity, UUID uuid) {
        CompoundTag tag = entity.getPersistentData();
        if (!tag.contains("FH_GroundDone")) return null;

        int startY = tag.getInt("FH_GroundY");
        MiningData data = new MiningData(uuid, startY);
        data.groundMiningDone = tag.getBoolean("FH_GroundDone");
        data.groundMiningY = tag.getInt("FH_GroundY");
        data.groundCooldown = tag.getInt("FH_GroundCooldown");
        data.sphereMiningDone = tag.getBoolean("FH_SphereDone");
        data.sphereMiningStarted = tag.getBoolean("FH_SphereStarted");
        data.sphereIndex = tag.getInt("FH_SphereIndex");
        data.casingCooldown = tag.getInt("FH_CasingCooldown");
        data.lastItemClearTick = tag.getLong("FH_LastClear");
        if (tag.contains("FH_LockedPos")) {
            data.lockedPosition = BlockPos.of(tag.getLong("FH_LockedPos"));
            data.positionLocked = tag.getBoolean("FH_PositionLocked");
        }
        data.watcherCount = tag.getInt("FH_WatcherCount");

        data.watcherUuids.clear();
        if (tag.contains("FH_WatcherList")) {
            String listStr = tag.getString("FH_WatcherList");
            if (!listStr.isEmpty()) {
                String[] parts = listStr.split(",");
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        try {
                            data.watcherUuids.add(UUID.fromString(part));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
        }
            data.watcherCount = data.watcherUuids.size();

        if (data.sphereMiningStarted && (data.sphereQueue == null || data.sphereQueue.isEmpty())) {
            // 如果已经挖了一部分但队列为空，重新生成并跳过已挖掘的
            data.sphereQueue = generateSphereQueue(entity.blockPosition(), SPHERE_RADIUS);
            // 如果 sphereIndex 大于 0，说明已经挖了一部分，截断队列
            if (data.sphereIndex > 0 && data.sphereIndex < data.sphereQueue.size()) {
                data.sphereQueue = data.sphereQueue.subList(data.sphereIndex, data.sphereQueue.size());
                data.sphereIndex = 0; // 重置索引，因为队列已经截断
            }
        }

        if (data.sphereMiningStarted && (data.sphereQueue == null || data.sphereQueue.isEmpty())) {
            data.sphereQueue = generateSphereQueue(entity.blockPosition(), SPHERE_RADIUS);
        }
        return data;
    }

    // ========== 转化方法 ==========
    private static void transformToFullHivemind(LivingEntity proto, ServerLevel level) {
        proto.setCustomName(Component.literal("§4§lFull Hivemind"));
        proto.setCustomNameVisible(true);
        CompoundTag tag = proto.getPersistentData();
        tag.putBoolean(HIVEMIND_TAG, true);
        applyFullHivemindAttributes(proto, level);
        proto.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 4, false, true));

        BlockPos pos = proto.blockPosition();
        int startY = pos.getY() - 1;
        MiningData data = new MiningData(proto.getUUID(), startY);
        MINING_DATA_MAP.put(proto.getUUID(), data);
        saveMiningData(proto, data);

        Component message = Component.translatable("sporesrp.fullhivemind.spawn");
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);

        FullHivemindWatcherHandler.spawnWatchers(level, pos, proto.getUUID());

        saveMiningData(proto, data);

        AdvancementTriggerHelper.onHivemindBorn(level);

        SporeSRP.LOGGER.info("Proto {} transformed into Full Hivemind at {}", proto.getUUID(), pos);
    }

    public static void applyFullHivemindAttributes(LivingEntity entity, ServerLevel level) {
        if (entity == null) return;
        double healthMultiplier = Config.COMMON.fullHivemindHealthMultiplier.get();
        int armorBonus = Config.COMMON.fullHivemindArmorBonus.get();

        // 使用 ResourceLocation 作为标识符
        ResourceLocation healthModId = ResourceLocation.parse("sporesrp:full_hivemind_health");
        ResourceLocation armorModId = ResourceLocation.parse("sporesrp:full_hivemind_armor");
        ResourceLocation followModId = ResourceLocation.parse("sporesrp:full_hivemind_follow");

        AttributeInstance healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double baseHealth = healthAttr.getBaseValue();
            double newHealth = baseHealth * healthMultiplier;
            healthAttr.removeModifier(healthModId);
            if (Math.abs(newHealth - baseHealth) > 0.001) {
                healthAttr.addPermanentModifier(new AttributeModifier(
                        healthModId, newHealth - baseHealth, AttributeModifier.Operation.ADD_VALUE
                ));
            }
            entity.setHealth(entity.getMaxHealth());
        }

        AttributeInstance armorAttr = entity.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(armorModId);
            if (armorBonus != 0) {
                armorAttr.addPermanentModifier(new AttributeModifier(
                        armorModId, armorBonus, AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }

        AttributeInstance followAttr = entity.getAttribute(Attributes.FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.removeModifier(followModId);
            followAttr.addPermanentModifier(new AttributeModifier(
                    followModId, 256.0, AttributeModifier.Operation.ADD_VALUE
            ));
        }

        entity.refreshDimensions();
        SporeSRP.LOGGER.debug("Applied Full Hivemind attributes to {}", entity.getUUID());
    }

    public static boolean isFullHivemind(LivingEntity entity) {
        if (entity == null) return false;
        CompoundTag tag = entity.getPersistentData();
        // 优先使用 NBT 标记
        if (tag.contains(HIVEMIND_TAG) && tag.getBoolean(HIVEMIND_TAG)) {
            return true;
        }
        // ★ 回退：检查自定义名称（确保客户端也能识别）
        Component name = entity.getCustomName();
        if (name != null) {
            String nameStr = name.getString();
            if (nameStr.contains("Full Hivemind")) {
                // 服务端发现名称匹配但标记缺失时，补充标记
                if (!entity.level().isClientSide) {
                    tag.putBoolean(HIVEMIND_TAG, true);
                }
                return true;
            }
        }
        return false;
    }

    public static boolean convertToFullHivemind(LivingEntity proto, ServerLevel level) {
        if (proto == null || !proto.isAlive()) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(proto.getType());
        if (id == null || !"spore:proto".equals(id.toString())) return false;
        if (isFullHivemind(proto)) return false;
        if (hasAnyAliveFullHivemind(level.getServer())) return false;

        WorldEvolutionData data = WorldEvolutionData.get(level);
        synchronized (CONVERSION_LOCK) {
            if (hasAnyAliveFullHivemind(level.getServer())) return false;
            transformToFullHivemind(proto, level);
            data.setFullHivemindUuid(proto.getUUID());
            SporeSRP.LOGGER.info("Command: Full Hivemind created from Proto {}", proto.getUUID());
            return true;
        }
    }

    private static boolean hasAnyAliveFullHivemind(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            WorldEvolutionData data = WorldEvolutionData.get(level);
            if (data.hasFullHivemind()) {
                UUID uuid = data.getFullHivemindUuid();
                if (uuid != null) {
                    LivingEntity entity = (LivingEntity) level.getEntity(uuid);
                    if (entity != null && entity.isAlive() && isFullHivemind(entity)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isGroundMining(UUID uuid) {
        MiningData data = MINING_DATA_MAP.get(uuid);
        return data != null && !data.groundMiningDone;
    }

    // ========== 强制完成命令 ==========
    public static void finishMiningAndBuildCasing(ServerLevel level, UUID uuid) {
        MiningData data = MINING_DATA_MAP.get(uuid);
        if (data == null) {
            SporeSRP.LOGGER.warn("No mining data found for entity {}", uuid);
            return;
        }
        data.groundMiningDone = true;
        data.sphereMiningDone = true;
        data.sphereMiningStarted = true;
        data.sphereIndex = data.sphereQueue != null ? data.sphereQueue.size() : 0;
        LivingEntity entity = (LivingEntity) level.getEntity(uuid);
        if (entity != null && entity.isAlive()) {
            if (data.sphereQueue == null || data.sphereQueue.isEmpty()) {
                data.sphereQueue = generateSphereQueue(entity.blockPosition(), SPHERE_RADIUS);
            }
            for (BlockPos pos : data.sphereQueue) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                mineBlock(level, pos);
            }
            data.sphereIndex = data.sphereQueue.size();

            buildCasingsOnce(level, entity.blockPosition());
            SporeSRP.LOGGER.info("Forced casing build for Full Hivemind {}", uuid);
        }
    }

    private static void buildCasingsOnce(ServerLevel level, BlockPos center) {
        CasingBuilder.buildCasing(level, center, 16, 1);
        CasingBuilder.buildCasing(level, center, 32, 2);
        CasingBuilder.buildCasing(level, center, 48, 3);
    }

    // ========== 事件监听 ==========
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;

        processMining(event);

        for (ServerLevel lv : event.getServer().getAllLevels()) {
            CasingBuilder.tick(lv, CasingBuilder.DEFAULT_BUDGET_PER_TICK);
        }

        tickCounter++;
        if (tickCounter < CONVERSION_CHECK_INTERVAL) return;
        tickCounter = 0;

        MinecraftServer server = event.getServer();
        if (hasAnyAliveFullHivemind(server)) return;

        for (ServerLevel level : server.getAllLevels()) {
            WorldEvolutionData data = WorldEvolutionData.get(level);
            if (data.hasFullHivemind()) continue;

            int phase = data.getEvolutionPhase();
            int minProtoCount = Config.COMMON.fullHivemindMinProtoCount.get();
            int minPhase = Config.COMMON.fullHivemindMinPhase.get();

            if (phase < minPhase) continue;

            // ---- 使用缓存替代 AABB 全服扫描 ----
            List<LivingEntity> protos = SporeEntityCache.getAliveProtos(level);

            if (protos.size() < minProtoCount) continue;
            if (hasAnyAliveFullHivemind(server)) break;

            synchronized (CONVERSION_LOCK) {
                if (hasAnyAliveFullHivemind(server)) break;
                LivingEntity selected = protos.get(RANDOM.nextInt(protos.size()));
                transformToFullHivemind(selected, level);
                data.setFullHivemindUuid(selected.getUUID());
                SporeSRP.LOGGER.info("Full Hivemind created from Proto {} at {}", selected.getUUID(), selected.blockPosition());
            }
        }
    }


    // ========== 挖掘系统 ==========

    private void processMining(ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        long currentTick = server.getTickCount();
        Iterator<Map.Entry<UUID, MiningData>> it = MINING_DATA_MAP.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, MiningData> entry = it.next();
            UUID uuid = entry.getKey();
            MiningData data = entry.getValue();

            LivingEntity entity = null;
            ServerLevel level = null;
            for (ServerLevel lv : server.getAllLevels()) {
                if (lv.getEntity(uuid) instanceof LivingEntity e && e.isAlive()) {
                    entity = e;
                    level = lv;
                    break;
                }
            }

            if (entity == null || level == null) {
                it.remove();
                SporeSRP.LOGGER.debug("Removed mining data for dead/missing entity {}", uuid);
                continue;
            }

            // 尝试从 NBT 恢复
            if (data == null) {
                data = restoreFromNBT(entity, uuid);
                if (data != null) {
                    MINING_DATA_MAP.put(uuid, data);
                    saveMiningData(entity, data);
                    SporeSRP.LOGGER.info("Restored MiningData from NBT for Full Hivemind {}", uuid);
                } else {
                    continue;
                }
            }

            // 位置锁定
            if (data.positionLocked && data.lockedPosition != null) {
                entity.setPos(data.lockedPosition.getX() + 0.5, data.lockedPosition.getY(), data.lockedPosition.getZ() + 0.5);
                entity.setDeltaMovement(0, 0, 0);
                entity.hurtMarked = true;
            }

            // 清理掉落物
            if (currentTick - data.lastItemClearTick >= 1200) {
                clearItems(level, entity.blockPosition(), 50);
                data.lastItemClearTick = currentTick;
            }

            // 地面挖掘
            if (!data.groundMiningDone) {
                data.groundCooldown--;
                if (data.groundCooldown <= 0) {
                    data.groundCooldown = GROUND_MINING_COOLDOWN;
                    boolean hitHard = mineGroundLayer(level, entity.blockPosition(), data.groundMiningY);
                    data.groundMiningY--;

                    if (data.groundMiningY < MAX_MINING_Y || hitHard) {
                        data.groundMiningDone = true;
                        data.lockedPosition = entity.blockPosition();
                        data.positionLocked = true;
                        saveMiningData(entity, data);
                        SporeSRP.LOGGER.info("Full Hivemind {} ground mining finished at Y={}, locked at {}",
                                uuid, data.groundMiningY + 1, data.lockedPosition);
                    }
                }
                continue;
            }

            // 球体挖掘
            if (!data.sphereMiningStarted) {
                entity.setNoGravity(true);
                data.sphereMiningStarted = true;
                if (data.sphereQueue == null || data.sphereQueue.isEmpty()) {
                    data.sphereQueue = generateSphereQueue(entity.blockPosition(), SPHERE_RADIUS);
                    data.sphereIndex = 0;
                }
                saveMiningData(entity, data);
                SporeSRP.LOGGER.info("Full Hivemind {} starting sphere mining with {} blocks", uuid, data.sphereQueue.size());
            }

            if (!data.sphereMiningDone) {
                if (data.sphereCooldown > 0) {
                    data.sphereCooldown--;
                    continue;
                }

                int end = Math.min(data.sphereIndex + BLOCKS_PER_TICK * 3, data.sphereQueue.size());
                for (int i = data.sphereIndex; i < end; i++) {
                    BlockPos pos = data.sphereQueue.get(i);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) continue;
                    mineBlock(level, pos);
                }
                data.sphereIndex = end;
                saveMiningData(entity, data);
                data.sphereCooldown = SPHERE_COOLDOWN_TICKS;

                if (data.sphereIndex >= data.sphereQueue.size()) {
                    data.sphereMiningDone = true;
                    saveMiningData(entity, data);
                    SporeSRP.LOGGER.info("Full Hivemind {} sphere mining completed", uuid);
                }
            }

            // 穹顶建造
            if (data.sphereMiningDone) {
                data.casingCooldown--;
                if (data.casingCooldown <= 0) {
                    data.casingCooldown = CASING_COOLDOWN_TICKS;
                    buildCasings(level, entity.blockPosition());
                    saveMiningData(entity, data);
                    SporeSRP.LOGGER.debug("Full Hivemind {} building casings at {}", uuid, entity.blockPosition());
                }
            }
        }
    }

    private static void buildCasings(ServerLevel level, BlockPos center) {
        CasingBuilder.buildCasing(level, center, 16, 1);
        CasingBuilder.buildCasing(level, center, 32, 2);
        CasingBuilder.buildCasing(level, center, 48, 3);
    }

    private boolean mineGroundLayer(ServerLevel level, BlockPos center, int y) {
        boolean hitHard = false;
        for (int dx = -GROUND_MINING_RADIUS; dx <= GROUND_MINING_RADIUS; dx++) {
            for (int dz = -GROUND_MINING_RADIUS; dz <= GROUND_MINING_RADIUS; dz++) {
                BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                if (!mineBlock(level, pos)) hitHard = true;
            }
        }
        return hitHard;
    }

    private static boolean mineBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;
        Block block = state.getBlock();
        if (block == Blocks.BEDROCK) return false;
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness > MAX_HARDNESS) return false;
        level.removeBlock(pos, false);
        return true;
    }

    public static List<BlockPos> generateSphereQueue(BlockPos center, int radius) {
        List<BlockPos> list = new ArrayList<>();
        int r2 = radius * radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        list.add(center.offset(dx, dy, dz));
                    }
                }
            }
        }
        list.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return list;
    }

    @SubscribeEvent
    public void onFullHivemindDeath(LivingDeathEvent event) {
        if (!Config.COMMON_SPEC.isLoaded()) return;
        LivingEntity entity = event.getEntity();
        if (!isFullHivemind(entity)) return;
        if (entity.level().isClientSide()) return;

        UUID uuid = entity.getUUID();
        MINING_DATA_MAP.remove(uuid);

        if (entity.level() instanceof ServerLevel serverLevel) {
            CasingBuilder.clearTasksAt(serverLevel.dimension(), entity.blockPosition());
        }

        SporeSRP.LOGGER.debug("Removed MiningData for Full Hivemind {}", uuid);

        ServerLevel level = (ServerLevel) entity.level();

        WorldEvolutionData data = WorldEvolutionData.get(level);
        if (entity.getUUID().equals(data.getFullHivemindUuid())) {
            data.setFullHivemindUuid(null);
            SporeSRP.LOGGER.info("Full Hivemind {} died, UUID cleared", entity.getUUID());
        }

        ItemStack stack = new ItemStack(ModItems.MIND_CORE.get());
        ItemEntity itemEntity = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack);
        itemEntity.setGlowingTag(true);
        itemEntity.setNoGravity(true);
        itemEntity.setDeltaMovement(0, 0, 0);
        itemEntity.setPickUpDelay(20);

        level.addFreshEntity(itemEntity);

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            AdvancementTriggerHelper.onHivemindKill(player);
        }
        SporeSRP.LOGGER.info("Full Hivemind dropped Mind Core at {}", entity.blockPosition());
    }

    private void clearItems(ServerLevel level, BlockPos center, int radius) {
        AABB area = new AABB(center).inflate(radius);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area,
                e -> e.isAlive() && e.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= radius * radius);
        if (!items.isEmpty()) {
            for (ItemEntity item : items) {
                item.discard();
            }
            SporeSRP.LOGGER.debug("Cleared {} item entities around {}", items.size(), center);
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!isFullHivemind(entity)) return;

        UUID uuid = entity.getUUID();
        if (!MINING_DATA_MAP.containsKey(uuid)) {
            MiningData data = restoreFromNBT(entity, uuid);
            if (data != null) {
                MINING_DATA_MAP.put(uuid, data);
                SporeSRP.LOGGER.info("Restored MiningData from NBT on entity join: {}", uuid);
            }
        }
    }

    public static void clearMiningData() {
        MINING_DATA_MAP.clear();
    }
}