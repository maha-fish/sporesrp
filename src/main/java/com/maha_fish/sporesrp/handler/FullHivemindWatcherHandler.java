package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FullHivemindWatcherHandler {

    private static final int TOWER_DISTANCE = 128;
    private static final int EFFECT_INTERVAL = 20; // 每秒
    private int tickCounter = 0;

    // ★ 不再需要扫描冷却相关字段

    // ==================== 守望者死亡监听 ====================
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isWatcher(entity)) return;
        UUID watcherUuid = entity.getUUID();

        // ★ 遍历所有完整心智的 MiningData，移除该 watcher
        for (Map.Entry<UUID, FullHivemindHandler.MiningData> entry : FullHivemindHandler.MINING_DATA_MAP.entrySet()) {
            FullHivemindHandler.MiningData data = entry.getValue();
            if (data.watcherUuids.remove(watcherUuid)) {
                data.watcherCount = Math.max(0, data.watcherCount - 1);
                SporeSRP.LOGGER.debug("Watcher {} died, count for {} reduced to {}", watcherUuid, entry.getKey(), data.watcherCount);
                break;
            }
        }
    }

    // ==================== 受伤事件（验证存活并施加减伤） ====================
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        LivingEntity target = event.getEntity();
        if (!FullHivemindHandler.isFullHivemind(target)) return;

        UUID hivemindUuid = target.getUUID();
        FullHivemindHandler.MiningData data = FullHivemindHandler.MINING_DATA_MAP.get(hivemindUuid);
        if (data == null) return;

        // ★ 首先清理当前已死亡的 watcher（不依赖扫描，直接检查）
        ServerLevel level = (ServerLevel) target.level();
        boolean watcherStateChanged = cleanupDeadWatchers(data, level);

        // 只在守望者状态真正变化时落盘，避免每次受击都写一遍 NBT
        if (watcherStateChanged) {
            FullHivemindHandler.saveMiningData(target, data);
        }

        // 如果有 watcher 存活，则阻止伤害并施加效果
        if (data.watcherCount > 0) {
            // 提示攻击者
            if (event.getSource().getEntity() instanceof Player attacker) {
                attacker.displayClientMessage(
                        Component.translatable("sporesrp.fullhivemind.attack.prevented"),
                        true
                );
            }
            // 取消伤害
            event.setCanceled(true);
            // 施加抗性和治疗（原有逻辑）
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, true));
            target.heal(4.0f);
        }
        // 如果 watcherCount == 0，则正常受伤（不处理）
    }

    // ==================== 每秒效果（基于计数） ====================
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < EFFECT_INTERVAL) return;
        tickCounter = 0;

        for (Map.Entry<UUID, FullHivemindHandler.MiningData> entry : FullHivemindHandler.MINING_DATA_MAP.entrySet()) {
            UUID hivemindUuid = entry.getKey();
            FullHivemindHandler.MiningData data = entry.getValue();
            if (data.watcherCount <= 0) continue;

            // 获取完整心智实体
            LivingEntity hivemind = null;
            for (ServerLevel lv : event.getServer().getAllLevels()) {
                LivingEntity e = (LivingEntity) lv.getEntity(hivemindUuid);
                if (e != null && e.isAlive()) {
                    hivemind = e;
                    break;
                }
            }
            if (hivemind == null) continue;

            // ★ 每秒也清理一次死亡的 watcher（防御性）
            ServerLevel level = (ServerLevel) hivemind.level();
            cleanupDeadWatchers(data, level);

            // 如果仍有 watcher，施加效果
            if (data.watcherCount > 0) {
                hivemind.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 4, false, true));
                hivemind.heal(4.0f);
            }
        }
    }

    // ==================== 工具方法 ====================

    private boolean isWatcher(LivingEntity entity) {
        if (!SporeEntityHelper.isMound(entity)) return false;
        if (!entity.hasCustomName()) return false;
        Component name = entity.getCustomName();
        return name != null && name.getString().contains("watcher");
    }

    /**
     * ★ 清理已死亡的 watcher，并更新计数。
     *
     * <p>注意：区块未加载时 {@code level.getEntity(uuid)} 返回 {@code null}，这**不代表**守望者已死。
     * 此时必须保留条目并继续计入，否则守望者仍然存活、只是暂时没被加载时，
     * {@code watcherCount} 会被清成 0，完整心智就收不到减伤加成了。
     * 只有「能取到实体且确实不再存活」才移除并扣减。
     *
     * @return 是否存在变化（供调用方决定是否需要落盘）
     */
    private boolean cleanupDeadWatchers(FullHivemindHandler.MiningData data, ServerLevel level) {
        if (data == null || level == null) return false;
        Iterator<UUID> it = data.watcherUuids.iterator();
        int removed = 0;
        while (it.hasNext()) {
            UUID uuid = it.next();
            LivingEntity entity = (LivingEntity) level.getEntity(uuid);
            if (entity != null && !entity.isAlive()) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            data.watcherCount = Math.max(0, data.watcherCount - removed);
            SporeSRP.LOGGER.debug("Cleanup removed {} dead watchers, remaining: {}", removed, data.watcherCount);
            return true;
        }
        return false;
    }

    // ==================== 结构生成（初始化守望者） ====================

    public static void spawnWatchers(ServerLevel level, BlockPos center, UUID hivemindUuid) {
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.get(new ResourceLocation("sporesrp", "tower")).orElse(null);
        if (template == null) {
            SporeSRP.LOGGER.warn("Failed to load tower structure, cannot spawn watchers.");
            return;
        }

        BlockPos[] positions = {
                center.offset(TOWER_DISTANCE, 0, 0),
                center.offset(-TOWER_DISTANCE, 0, 0),
                center.offset(0, 0, TOWER_DISTANCE),
                center.offset(0, 0, -TOWER_DISTANCE)
        };

        for (BlockPos pos : positions) {
            int surfaceY = getSurfaceHeight(level, pos.getX(), pos.getZ());
            if (surfaceY == level.getMinBuildHeight()) continue;
            BlockPos placePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
            StructurePlaceSettings settings = new StructurePlaceSettings();
            template.placeInWorld(level, placePos, placePos, settings, net.minecraft.util.RandomSource.create(), 2);
        }

        // ★ 放置后，扫描所有 watcher（仅一次，然后加入 MiningData）
        net.minecraft.world.entity.EntityType<?> moundType = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation("spore", "mound"));
        if (moundType == null) return;

        // 只扫描刚生成的位置附近（范围 TOWER_DISTANCE*2 足够）
        List<LivingEntity> watchers = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(center).inflate(TOWER_DISTANCE * 2),
                e -> e.getType() == moundType
                        && e.hasCustomName()
                        && e.getCustomName() != null
                        && e.getCustomName().getString().contains("watcher"));

        FullHivemindHandler.MiningData data = FullHivemindHandler.MINING_DATA_MAP.get(hivemindUuid);
        if (data != null) {
            data.watcherUuids.clear();
            for (LivingEntity watcher : watchers) {
                data.watcherUuids.add(watcher.getUUID());
            }
            data.watcherCount = data.watcherUuids.size();
            LivingEntity hivemind = (LivingEntity) level.getEntity(hivemindUuid);
            if (hivemind != null) {
                FullHivemindHandler.saveMiningData(hivemind, data);
            }
            SporeSRP.LOGGER.info("Initialized watcher count for {}: {}", hivemindUuid, data.watcherCount);
        }

        SporeSRP.LOGGER.info("Spawned watcher towers around {}", center);
    }

    // ★ 正确版本：返回固体方块顶部高度
    private static int getSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY() - 1;
    }
}