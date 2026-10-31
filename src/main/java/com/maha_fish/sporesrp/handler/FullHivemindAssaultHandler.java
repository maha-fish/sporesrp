package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 简化版完整心智袭击系统
 * - 自动积累袭击值，达到100后检测玩家距离触发袭击
 * - 命令 /sporesrp fullhivemind assault start [type] 可手动触发
 * - 不维护袭击状态，不生成BossBar，不跟踪怪物，生成即结束
 * - 触发后重置袭击值并进入冷却
 */
public class FullHivemindAssaultHandler {

    private static final double MAX_ASSAULT_VALUE = 100.0;
    private static final int ASSAULT_SPAWN_RADIUS_MIN = 30;
    private static final int ASSAULT_SPAWN_RADIUS_MAX = 50;
    private static final int AIR_ASSAULT_HEIGHT_OFFSET = 20;
    private static final int Y_THRESHOLD = 75;

    private int tickCounter = 0;

    /** 复用同一个 Random，避免每次袭击/每只怪都新建实例。 */
    private static final Random RANDOM = new Random();

    // ========== 配置读取 ==========
    private static double getIncrementPerTick() {
        return Config.COMMON.assaultValueIncrementPerTick.get();
    }
    private static int getIncrementInterval() {
        return Config.COMMON.assaultValueIncrementInterval.get();
    }
    private static double getSmallKillPoints() {
        return Config.COMMON.assaultSmallPoolKillPoints.get();
    }
    private static double getMediumKillPoints() {
        return Config.COMMON.assaultMediumPoolKillPoints.get();
    }
    private static double getLargeKillPoints() {
        return Config.COMMON.assaultLargePoolKillPoints.get();
    }
    private static double getBossKillPoints() {
        return Config.COMMON.assaultBossPoolKillPoints.get();
    }
    private static int getTriggerCheckInterval() {
        return Config.COMMON.assaultTriggerCheckInterval.get();
    }
    private static int getDistanceThreshold() {
        return Config.COMMON.assaultDistanceThreshold.get();
    }
    private static int getCooldownTicks() {
        return Config.COMMON.assaultCooldownSeconds.get() * 20;
    }

    // ========== 辅助方法 ==========
    private static boolean isSporeEntity(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && "spore".equals(id.getNamespace());
    }

    private static int getInfectedLevel(LivingEntity entity) {
        return SporeEntityHelper.getInfectedLevel(entity);
    }

    private static LivingEntity getFullHivemind(ServerLevel level) {
        WorldEvolutionData data = WorldEvolutionData.get(level);
        UUID uuid = data.getFullHivemindUuid();
        if (uuid == null) return null;
        LivingEntity entity = (LivingEntity) level.getEntity(uuid);
        if (entity == null || !entity.isAlive()) {
            return null;
        }
        return entity;
    }

    // ========== 事件监听 ==========
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {


    if (event.getEntity().level().isClientSide()) return;
    if (event.getEntity().level().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;
    if (!Config.COMMON_SPEC.isLoaded()) return;
    if (!Config.COMMON_SPEC.isLoaded()) return;if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;

        LivingEntity victim = event.getEntity();
        if (!isSporeEntity(victim)) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;

        LivingEntity hivemind = getFullHivemind(level);
        if (hivemind == null) return;

        WorldEvolutionData data = WorldEvolutionData.get(level);
        if (data.isAssaultOnCooldown(level.getServer().getTickCount())) return;

        int levelNum = getInfectedLevel(victim);
        double points = 0;
        switch (levelNum) {
            case 0: points = getSmallKillPoints(); break;
            case 1: points = getMediumKillPoints(); break;
            case 2: points = getLargeKillPoints(); break;
            case 3: points = getBossKillPoints(); break;
            default: return;
        }
        if (points <= 0) return;
        data.addAssaultValue(points);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {



    if (!Config.COMMON_SPEC.isLoaded()) return;tickCounter++;
        if (tickCounter < 10) return;
        tickCounter = 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            LivingEntity hivemind = getFullHivemind(level);
            if (hivemind == null) continue;

            WorldEvolutionData data = WorldEvolutionData.get(level);
            long currentTick = level.getServer().getTickCount();

            if (data.isAssaultOnCooldown(currentTick)) continue;

            long lastTick = data.getLastAssaultIncrementTick();
            if (lastTick == 0 || currentTick - lastTick >= getIncrementInterval()) {
                if (data.getAssaultValue() < MAX_ASSAULT_VALUE) {
                    data.addAssaultValue(getIncrementPerTick());
                    data.setLastAssaultIncrementTick(currentTick);
                }
            }

            if (data.getAssaultValue() < MAX_ASSAULT_VALUE) continue;

            if (!data.isAssaultTriggered()) {
                data.setAssaultTriggered(true);
                continue;
            }

            long lastCheckTick = data.getLastAssaultIncrementTick();
            if (currentTick - lastCheckTick < getTriggerCheckInterval()) continue;
            data.setLastAssaultIncrementTick(currentTick);

            BlockPos hivemindPos = hivemind.blockPosition();
            ServerPlayer target = null;
            for (ServerPlayer player : level.getPlayers(p -> true)) {
                if (player.isCreative() || player.isSpectator()) continue;
                double dist = player.distanceToSqr(hivemind);
                if (dist >= getDistanceThreshold() * getDistanceThreshold()) {
                    target = player;
                    break;
                }
            }
            if (target == null) continue;

            // 触发袭击
            if (target.getY() > Y_THRESHOLD) {
                triggerAirAssault(level, target);
            } else {
                triggerGroundAssault(level, target);
            }

            data.setAssaultValue(0);
            data.setAssaultTriggered(false);
            data.setAssaultCooldownEndTick(currentTick + getCooldownTicks());
        }
    }

    // ========== 袭击生成逻辑（无状态） ==========
    private static void triggerAirAssault(ServerLevel level, ServerPlayer target) {
        String[] airMobs = {"spore:busser", "spore:scavenger", "spore:gargoyle"};
        int count = 1 + RANDOM.nextInt(2);

        for (String mobName : airMobs) {
            for (int i = 0; i < count; i++) {
                spawnAssaultMob(level, target, mobName, true);
            }
        }
        target.sendSystemMessage(Component.translatable("sporesrp.assault.air.trigger"));
        AdvancementTriggerHelper.onHivemindAssault(level);
    }

    private static void triggerGroundAssault(ServerLevel level, ServerPlayer target) {
        Set<String> organoids = SporeEntityHelper.organoidSet();
        // 过滤池，排除类器官体
        List<String> smallPool = SporeEntityHelper.getSmallPool().stream()
                .filter(name -> !organoids.contains(name))
                .collect(Collectors.toList());
        List<String> mediumPool = SporeEntityHelper.getMediumPool().stream()
                .filter(name -> !organoids.contains(name))
                .collect(Collectors.toList());
        List<String> largePool = SporeEntityHelper.getLargePool().stream()
                .filter(name -> !organoids.contains(name))
                .collect(Collectors.toList());

        // 如果某池为空，回退到原池（但原池可能也包含类器官体，但为了避免空池，可以回退）
        if (smallPool.isEmpty()) smallPool = new ArrayList<>(SporeEntityHelper.getSmallPool());
        if (mediumPool.isEmpty()) mediumPool = new ArrayList<>(SporeEntityHelper.getMediumPool());
        if (largePool.isEmpty()) largePool = new ArrayList<>(SporeEntityHelper.getLargePool());

        Random rand = RANDOM;

        // 空池守卫：仅在池为空（原本会抛 IndexOutOfBounds）时提前退出，非空时抽选结果不变
        if (smallPool.isEmpty()) return;
        for (int i = 0; i < 4; i++) {
            String name = smallPool.get(rand.nextInt(smallPool.size()));
            spawnAssaultMob(level, target, name, false);
        }
        if (mediumPool.isEmpty()) return;
        for (int i = 0; i < 3; i++) {
            String name = mediumPool.get(rand.nextInt(mediumPool.size()));
            spawnAssaultMob(level, target, name, false);
        }
        if (largePool.isEmpty()) return;
        String largeName = largePool.get(rand.nextInt(largePool.size()));
        spawnAssaultMob(level, target, largeName, false);

        AdvancementTriggerHelper.onHivemindAssault(level);
        target.sendSystemMessage(Component.translatable("sporesrp.assault.ground.trigger"));
    }

    private static boolean spawnAssaultMob(ServerLevel level, ServerPlayer target, String entityName, boolean isAir) {
        BlockPos targetPos = target.blockPosition();
        Random rand = RANDOM;

        double angle = rand.nextDouble() * 2 * Math.PI;
        double distance = ASSAULT_SPAWN_RADIUS_MIN + rand.nextDouble() * (ASSAULT_SPAWN_RADIUS_MAX - ASSAULT_SPAWN_RADIUS_MIN);
        int x = (int) (targetPos.getX() + Math.cos(angle) * distance);
        int z = (int) (targetPos.getZ() + Math.sin(angle) * distance);

        int y;
        if (isAir) {
            y = (int) targetPos.getY() + AIR_ASSAULT_HEIGHT_OFFSET + rand.nextInt(10);
        } else {
            y = getSurfaceHeight(level, x, z);
            if (y <= level.getMinBuildHeight()) {
                y = targetPos.getY();
            }
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityName));
        if (type == null) return false;

        LivingEntity mob = (LivingEntity) type.create(level);
        if (mob == null) return false;

        mob.setPos(x + 0.5, y, z + 0.5);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false));

        if (mob instanceof Mob mobEntity) {
            mobEntity.setTarget(target);
        }

        level.addFreshEntity(mob);
        return true;
    }

    // ★ 正确版本：返回固体方块顶部高度
    private static int getSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY() + 1;
    }

    // ========== 命令 API（只保留 start） ==========
    public static boolean startAssaultCommand(ServerLevel level, ServerPlayer target, String type) {
        if (target == null) return false;
        LivingEntity hivemind = getFullHivemind(level);
        if (hivemind == null) return false;

        if ("ground".equalsIgnoreCase(type)) {
            triggerGroundAssault(level, target);
        } else {
            triggerAirAssault(level, target);
        }

        WorldEvolutionData data = WorldEvolutionData.get(level);
        data.setAssaultValue(0);
        data.setAssaultTriggered(false);
        data.setAssaultCooldownEndTick(level.getServer().getTickCount() + getCooldownTicks());
        return true;
    }

    // 无状态，所以以下方法仅保留兼容，返回 false 或 0
    public static boolean isAssaultActive(ServerLevel level) { return false; }
    public static int getAssaultMobCount(ServerLevel level) { return 0; }
    public static String getAssaultType(ServerLevel level) { return ""; }
}