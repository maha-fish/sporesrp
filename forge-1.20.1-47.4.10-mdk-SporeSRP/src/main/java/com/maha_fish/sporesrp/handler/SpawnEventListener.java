package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.phase.PhaseEffects;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpawnEventListener {

    private static final Random RANDOM = new Random();
    private static final Queue<LivingEntity> pendingRemovals = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        // 只处理 spore 命名空间的生物（走缓存，避免每次加入都反查注册表）
        if (!SporeEntityHelper.isSporeType(living.getType())) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        String entityId = SporeEntityHelper.getEntityId(living.getType()).toString();

        // 1. 净化检查（最高优先级）—— 走每维度缓存的布尔值，
        //    不再对每只入场生物调 WorldEvolutionData.get()（那会连带跑 updatePhase）
        if (PurifiedWorldHandler.isPurified(serverLevel)) {
            pendingRemovals.add(living);
            return; // 阻止后续逻辑
        }

        // 2. scent 特殊处理（仅在完整心智附近时悬浮）
        if ("spore:scent".equals(entityId)) {
            AABB box = living.getBoundingBox().inflate(1);
            List<LivingEntity> nearby = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive()
                            && FullHivemindHandler.isFullHivemind(e)
                            && e.distanceToSqr(living) <= 1.0);
            if (!nearby.isEmpty()) {
                living.setNoGravity(true);
                living.setDeltaMovement(0, 0, 0);
            }
        }

        // 3. 应用阶段属性加成（仅孢子生物）
        PhaseEffects.applyAttributesToNewEntity(living, serverLevel);

        // 4. Gastgaber Builder 变种转化
        if ("spore:gastgaber".equals(entityId)) {
            double chance = Config.COMMON.gastgaberBuilderSpawnChance.get();
            if (RANDOM.nextDouble() < chance) {
                GastgaberBuilderHandler.markAsBuilder(living);
            }
        }
    }

    /** 服务器停止时清空队列，避免静态队列跨存档持有旧世界实体引用。 */
    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        pendingRemovals.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        int processed = 0;
        while (!pendingRemovals.isEmpty() && processed < 50) {
            LivingEntity entity = pendingRemovals.poll();
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
            processed++;
        }
    }

    /**
     * 清空移除队列（用于调试或重启时清理）
     */
    public static void clearPendingRemovals() {
        pendingRemovals.clear();
    }
}