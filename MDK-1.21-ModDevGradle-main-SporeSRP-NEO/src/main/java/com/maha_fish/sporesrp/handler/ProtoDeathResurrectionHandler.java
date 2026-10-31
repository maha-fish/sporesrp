package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProtoDeathResurrectionHandler {

    private static final ConcurrentHashMap<UUID, Boolean> triggeredProtos = new ConcurrentHashMap<>();
    private static final int CLEANUP_INTERVAL = 6000; // 5分钟
    private int cleanupTimer = 0;


    private static boolean isEnabled() {
        return Config.COMMON.protoResurrectionEnabled.get();
    }

    private static int getCheckRadius() {
        return Config.COMMON.protoResurrectionCheckRadius.get();
    }

    private static int getLargePoolThreshold() {
        return Config.COMMON.protoResurrectionLargePoolThreshold.get();
    }

    private static float getHealthPercent() {
        return Config.COMMON.protoResurrectionHealthPercent.get().floatValue();
    }

    private static int getResistanceDuration() {
        return Config.COMMON.protoResurrectionResistanceDuration.get() * 20;
    }

    private static int getResistanceAmplifier() {
        return Config.COMMON.protoResurrectionResistanceAmplifier.get();
    }

    private static double getMoveSpeedMultiplier() {
        return Config.COMMON.protoResurrectionMoveSpeedMultiplier.get();
    }

    private static double getPushRadius() {
        return Config.COMMON.protoResurrectionPushRadius.get();
    }

    private static double getPushStrength() {
        return Config.COMMON.protoResurrectionPushStrength.get();
    }

    private static String getSoundId() {
        return Config.COMMON.protoResurrectionSoundId.get();
    }

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {


    if (event.getEntity().level().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;if (!isEnabled()) return;
        if (event.getEntity().level().isClientSide) return;

        LivingEntity entity = event.getEntity();

        if (!SporeEntityHelper.isProto(entity)) return;

        float currentHealth = entity.getHealth();
        float damage = event.getContainer().getNewDamage();
        if (damage < currentHealth) {
            return;
        }

        UUID protoUuid = entity.getUUID();

        if (triggeredProtos.containsKey(protoUuid)) {
            return;
        }


        BlockPos protoPos = entity.blockPosition();
        double protoX = entity.getX();
        double protoY = entity.getY();
        double protoZ = entity.getZ();

        if (!(entity.level() instanceof ServerLevel level)) return;

        // ---- 检测周围 LargePool 和 Boss ----
        int checkRadius = getCheckRadius();
        AABB checkBox = new AABB(protoPos).inflate(checkRadius);
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(LivingEntity.class, checkBox,
                e -> e != entity && e.isAlive());

        long largePoolCount = 0;
        boolean hasBoss = false;

        Set<String> largePoolList = SporeEntityHelper.largePoolSet();
        Set<String> bossPoolList = SporeEntityHelper.bossPoolSet();

        ArrayList<LivingEntity> targetsToRecall = new ArrayList<>();

        for (LivingEntity nearby : nearbyEntities) {
            ResourceLocation id = SporeEntityHelper.getEntityId(nearby.getType());
            if (id == null) continue;
            String name = id.toString();

            boolean isLargePool = largePoolList.contains(name);
            boolean isBoss = bossPoolList.contains(name);

            if (isLargePool || isBoss) {
                targetsToRecall.add(nearby);
                if (isLargePool) largePoolCount++;
                if (isBoss) hasBoss = true;
            }

            if (largePoolCount >= getLargePoolThreshold() && hasBoss) {
                break;
            }
        }

        boolean shouldTrigger = (largePoolCount >= getLargePoolThreshold()) || hasBoss;

        if (!shouldTrigger) {
            SporeSRP.LOGGER.debug("Proto {} conditions not met: largePool={}, hasBoss={}",
                    protoUuid, largePoolCount, hasBoss);
            return;
        }

        triggeredProtos.put(protoUuid, true);

        // ---- 阻止伤害 ----
        event.getContainer().setNewDamage(0f);

        // ---- 1. 召回生物 ----
        double speedMultiplier = getMoveSpeedMultiplier();
        for (LivingEntity target : targetsToRecall) {
            if (target instanceof Mob mob) {
                mob.getNavigation().moveTo(protoX, protoY, protoZ, speedMultiplier);
            } else {
                target.teleportTo(protoX, protoY, protoZ);
            }
        }

        // ---- 2. 推开非真菌生物（修正版） ----
        double pushRadius = getPushRadius();
        double pushStrength = getPushStrength();
        AABB pushBox = new AABB(protoPos).inflate(pushRadius);

        List<LivingEntity> pushTargets = level.getEntitiesOfClass(LivingEntity.class, pushBox,
                e -> e != entity && e.isAlive());

        for (LivingEntity target : pushTargets) {
            if (SporeEntityHelper.isSporeEntity(target)) continue;

            // 检查距离，太近的不推（防止计算异常）
            double dx = target.getX() - protoX;
            double dz = target.getZ() - protoZ;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (dist < 0.1) continue;

            // 归一化方向向量
            double normDx = dx / dist;
            double normDz = dz / dist;

            // ---- 直接设置速度 ----
            double horizontalSpeed = pushStrength * 1.5;  // 水平速度 = 力度 × 1.5
            double verticalSpeed = 0.6 + pushStrength * 0.2; // 竖直速度

            target.setDeltaMovement(normDx * horizontalSpeed, verticalSpeed, normDz * horizontalSpeed);
            target.hurtMarked = true;

            // 仅在 DEBUG 开启时构建日志参数（getKey + String.format 本身有开销）
            if (SporeSRP.LOGGER.isDebugEnabled()) {
                SporeSRP.LOGGER.debug("Pushed non-fungal entity {} away from Proto (dist={}, speed={})",
                        SporeEntityHelper.getEntityId(target),
                        String.format("%.1f", dist),
                        String.format("%.1f", horizontalSpeed));
            }
        }

        // ---- 3. 播放音效 ----
        String soundIdStr = getSoundId();
        if (soundIdStr != null && !soundIdStr.trim().isEmpty()) {
            ResourceLocation soundLoc = ResourceLocation.tryParse(soundIdStr.trim());
            if (soundLoc != null) {
                SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
                if (soundEvent != null) {
                    level.playSound(null, protoX, protoY, protoZ, soundEvent, SoundSource.HOSTILE, 4.0f, 0.8f);
                }
            }
        }

        // ---- 4. 恢复血量 ----
        float maxHealth = entity.getMaxHealth();
        float newHealth = maxHealth * getHealthPercent();
        entity.setHealth(newHealth);

        // ---- 5. 施加抗性提升 ----
        entity.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                getResistanceDuration(),
                getResistanceAmplifier(),
                false,
                true
        ));

        // ---- 6. 广播消息 ----
        Component message = Component.translatable("sporesrp.proto.resurrection.message");
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }

        SporeSRP.LOGGER.info("Proto {} triggered resurrection! Recalling {} largePool, {} boss entities, pushed {} non-fungal entities",
                protoUuid, largePoolCount, hasBoss ? 1 : 0, pushTargets.size());
    }

    public static void resetTriggers() {
        triggeredProtos.clear();
        SporeSRP.LOGGER.info("Proto death resurrection triggers reset");
    }

    public static int getTriggeredCount() {
        return triggeredProtos.size();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {


        
    if (!Config.COMMON_SPEC.isLoaded()) return;cleanupTimer++;
        if (cleanupTimer < CLEANUP_INTERVAL) return;
        cleanupTimer = 0;

        Iterator<Map.Entry<UUID, Boolean>> it = triggeredProtos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Boolean> entry = it.next();
            UUID uuid = entry.getKey();
            boolean alive = false;
            for (ServerLevel level : event.getServer().getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof LivingEntity && entity.isAlive()) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                it.remove();
                SporeSRP.LOGGER.debug("Cleaned up triggeredProto entry for dead entity {}", uuid);
            }
        }
    }

}