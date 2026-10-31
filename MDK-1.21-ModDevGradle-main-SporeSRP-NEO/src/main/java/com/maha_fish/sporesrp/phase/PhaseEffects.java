package com.maha_fish.sporesrp.phase;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

public class PhaseEffects {
    private static final ResourceLocation HEALTH_MOD_ID = ResourceLocation.parse("sporesrp:health_boost");
    private static final ResourceLocation ATTACK_MOD_ID = ResourceLocation.parse("sporesrp:attack_boost");
    private static final ResourceLocation SPEED_MOD_ID = ResourceLocation.parse("sporesrp:speed_boost");
    private static final ResourceLocation ARMOR_MOD_ID = ResourceLocation.parse("sporesrp:armor_boost");
    private static final ResourceLocation KNOCKBACK_MOD_ID = ResourceLocation.parse("sporesrp:knockback_boost");

    private static boolean eternalNightEnabled = false;
    private static final long MIDNIGHT_TIME = 18000;
    private static final long NIGHT_START = 13000;
    private static final long NIGHT_END = 23000;

    private static int cachedPhase = -1;
    private static final long PHASE_CHECK_INTERVAL = 10;
    private static long lastPhaseCheckTick = 0;

    private static Component lastUpgradeMessage = null;
    private static Component lastAtmosphereMessage = null;

    public static void onPhaseChange(int oldPhase, int newPhase, ServerLevel level) {
        cachedPhase = newPhase;

        Component upgradeMsg = Component.translatable("sporesrp.phase.upgrade", oldPhase, newPhase);
        Component atmosphere = Component.translatable("sporesrp.phase." + newPhase);

        lastUpgradeMessage = upgradeMsg;
        lastAtmosphereMessage = atmosphere;

        level.getServer().getPlayerList().broadcastSystemMessage(upgradeMsg, false);
        level.getServer().getPlayerList().broadcastSystemMessage(atmosphere, false);

        refreshAllLevels(level.getServer());

        if (Config.COMMON.eternalNightAfterPhase6.get() && newPhase >= 6) {
            eternalNightEnabled = true;
            setEternalNight(level.getServer());
        } else if (newPhase < 6) {
            eternalNightEnabled = false;
        }

        if (newPhase >= 6 && oldPhase < 6) {
            AdvancementTriggerHelper.onInfectionSpread(level);
        }
        AdvancementTriggerHelper.onPhaseChange(level, newPhase);

        // 阶段变化一定动了点数/冷却，统一在这里同步一次，
        // 避免以后新增的调用点又忘记发同步包
        com.maha_fish.sporesrp.data.WorldEvolutionData.get(level).syncToClients(level);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Config.COMMON_SPEC.isLoaded()) {
            return;
        }

        if (eternalNightEnabled) {
            setEternalNight(event.getServer());
        }

        long currentTick = event.getServer().getTickCount();
        if (currentTick - lastPhaseCheckTick < PHASE_CHECK_INTERVAL) return;
        lastPhaseCheckTick = currentTick;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            WorldEvolutionData data = WorldEvolutionData.get(level);
            int currentPhase = data.getEvolutionPhase();

            if (currentPhase != cachedPhase) {
                int oldPhase = cachedPhase;
                cachedPhase = currentPhase;

                Component upgradeMsg = Component.translatable("sporesrp.phase.upgrade", oldPhase, currentPhase);
                Component atmosphere = Component.translatable("sporesrp.phase." + currentPhase);

                lastUpgradeMessage = upgradeMsg;
                lastAtmosphereMessage = atmosphere;

                event.getServer().getPlayerList().broadcastSystemMessage(upgradeMsg, false);
                event.getServer().getPlayerList().broadcastSystemMessage(atmosphere, false);

                // 全局数据变化 → 所有维度都要刷新，不能只刷新首个检测到的维度
                refreshAllLevels(event.getServer());

                if (Config.COMMON.eternalNightAfterPhase6.get() && currentPhase >= 6) {
                    eternalNightEnabled = true;
                    setEternalNight(event.getServer());
                } else if (currentPhase < 6) {
                    eternalNightEnabled = false;
                }

                SporeSRP.LOGGER.info("Phase change detected by active check: {} -> {}", oldPhase, currentPhase);
                break;
            }
        }
    }

    private static void setEternalNight(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;
        long dayTime = overworld.getDayTime();
        if (dayTime < NIGHT_START || dayTime > NIGHT_END) {
            overworld.setDayTime(MIDNIGHT_TIME);
        }
    }

    /** 对所有已加载维度刷新孢子生物属性（演化数据是全局的）。 */
    public static void refreshAllLevels(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            refreshAllInfectedAttributes(level);
        }
    }

    public static void refreshAllInfectedAttributes(ServerLevel level) {
        WorldEvolutionData data = WorldEvolutionData.get(level);
        int phase = data.getEvolutionPhase();
        double diffMultiplier = data.getDifficulty().getMultiplier();

        double healthMult = (Double) Config.COMMON.healthMultipliers.get().get(phase);
        double attackMult = (Double) Config.COMMON.attackMultipliers.get().get(phase);
        double speedBonus = (Double) Config.COMMON.speedBonuses.get().get(phase);
        int armorBonus = (Integer) Config.COMMON.armorBonuses.get().get(phase);
        double knockbackResist = (Double) Config.COMMON.knockbackResist.get().get(phase);

        int sporeCount = 0;

        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof LivingEntity living && SporeEntityHelper.isSporeEntity(living)) {
                sporeCount++;
                applyAttributesDirect(living, healthMult, attackMult, speedBonus,
                        armorBonus, knockbackResist, diffMultiplier, true);
            }
        }

        if (sporeCount > 500) {
            SporeSRP.LOGGER.warn("Refreshed attributes for {} spore entities in {}, may cause lag!",
                    sporeCount, level.dimension().location());
        }
    }

    public static void applyAttributesToNewEntity(LivingEntity entity, ServerLevel level) {
        WorldEvolutionData data = WorldEvolutionData.get(level);
        int phase = data.getEvolutionPhase();
        double diffMultiplier = data.getDifficulty().getMultiplier();

        double healthMult = (Double) Config.COMMON.healthMultipliers.get().get(phase);
        double attackMult = (Double) Config.COMMON.attackMultipliers.get().get(phase);
        double speedBonus = (Double) Config.COMMON.speedBonuses.get().get(phase);
        int armorBonus = (Integer) Config.COMMON.armorBonuses.get().get(phase);
        double knockbackResist = (Double) Config.COMMON.knockbackResist.get().get(phase);

        applyAttributesDirect(entity, healthMult, attackMult, speedBonus,
                armorBonus, knockbackResist, diffMultiplier, false);
    }

    // ========== 核心：属性应用 ==========
    private static void applyAttributesDirect(LivingEntity entity,
                                              double healthMult,
                                              double attackMult,
                                              double speedBonus,
                                              int armorBonus,
                                              double knockbackResist,
                                              double diffMultiplier,
                                              boolean preserveHealthPercent) {
        // 获取实体的基础属性值（通过 AttributeInstance，若不存在则使用默认值）
        AttributeInstance healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        double baseHealth = (healthAttr != null) ? healthAttr.getBaseValue() : 20.0;

        AttributeInstance attackAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        double baseAttack = (attackAttr != null) ? attackAttr.getBaseValue() : 0.0;

        AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        double baseSpeed = (speedAttr != null) ? speedAttr.getBaseValue() : 0.0;

        AttributeInstance armorAttr = entity.getAttribute(Attributes.ARMOR);
        int baseArmor = (armorAttr != null) ? (int) armorAttr.getBaseValue() : 0;

        AttributeInstance knockbackAttr = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        double baseKnockback = (knockbackAttr != null) ? knockbackAttr.getBaseValue() : 0.0;

        // 计算最终属性值
        double finalHealth = baseHealth + (baseHealth * (healthMult - 1) * diffMultiplier);
        double finalAttack = baseAttack + (baseAttack * (attackMult - 1) * diffMultiplier);
        double finalSpeed = baseSpeed + (speedBonus * diffMultiplier);
        int finalArmor = (int) (baseArmor + (armorBonus * diffMultiplier));
        double finalKnockback = baseKnockback + (knockbackResist * diffMultiplier);

        // ---- 生命值 ----
        if (healthAttr != null) {
            double oldMax = entity.getMaxHealth();
            double oldHealth = entity.getHealth();

            healthAttr.removeModifier(HEALTH_MOD_ID);
            double healthDiff = finalHealth - baseHealth;
            if (Math.abs(healthDiff) > 0.001) {
                healthAttr.addPermanentModifier(new AttributeModifier(
                        HEALTH_MOD_ID, healthDiff, AttributeModifier.Operation.ADD_VALUE
                ));
            }

            if (preserveHealthPercent) {
                if (oldMax > 0) {
                    double percent = oldHealth / oldMax;
                    double newHealth = finalHealth * percent;
                    entity.setHealth((float) Math.min(newHealth, finalHealth));
                } else {
                    entity.setHealth((float) finalHealth);
                }
            } else {
                entity.setHealth((float) finalHealth);
            }
        }

        // ---- 攻击力 ----
        if (attackAttr != null) {
            attackAttr.removeModifier(ATTACK_MOD_ID);
            double attackDiff = finalAttack - baseAttack;
            if (Math.abs(attackDiff) > 0.001) {
                attackAttr.addPermanentModifier(new AttributeModifier(
                        ATTACK_MOD_ID, attackDiff, AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }

        // ---- 移速 ----
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MOD_ID);
            double speedDiff = finalSpeed - baseSpeed;
            if (Math.abs(speedDiff) > 0.001) {
                speedAttr.addPermanentModifier(new AttributeModifier(
                        SPEED_MOD_ID, speedDiff, AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }

        // ---- 护甲 ----
        if (armorAttr != null) {
            armorAttr.removeModifier(ARMOR_MOD_ID);
            int armorDiff = finalArmor - baseArmor;
            if (armorDiff != 0) {
                armorAttr.addPermanentModifier(new AttributeModifier(
                        ARMOR_MOD_ID, armorDiff, AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }

        // ---- 击退抗性 ----
        if (knockbackAttr != null) {
            knockbackAttr.removeModifier(KNOCKBACK_MOD_ID);
            double knockbackDiff = finalKnockback - baseKnockback;
            if (Math.abs(knockbackDiff) > 0.001) {
                knockbackAttr.addPermanentModifier(new AttributeModifier(
                        KNOCKBACK_MOD_ID, knockbackDiff, AttributeModifier.Operation.ADD_VALUE
                ));
            }
        }
    }

    private static boolean isSporeEntity(LivingEntity entity) {
        ResourceLocation regName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return regName != null && "spore".equals(regName.getNamespace());
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;

        DamageSource source = event.getContainer().getSource();
        Entity directEntity = source.getDirectEntity();
        if (!(directEntity instanceof net.minecraft.world.entity.projectile.Projectile)) return;

        Entity owner = source.getEntity();
        if (!(owner instanceof LivingEntity attacker)) return;

        if (!isSporeEntity(attacker)) return;

        if (!(attacker.level() instanceof ServerLevel level)) return;
        WorldEvolutionData data = WorldEvolutionData.get(level);
        int phase = data.getEvolutionPhase();
        List<? extends Double> multipliers = Config.COMMON.attackMultipliers.get();
        double phaseMultiplier = (phase < multipliers.size()) ? multipliers.get(phase) : 1.0;
        double diffMultiplier = data.getDifficulty().getMultiplier();
        double finalMultiplier = 1.0 + (phaseMultiplier - 1) * diffMultiplier;

        float newAmount = event.getContainer().getNewDamage() * (float) finalMultiplier;
        event.getContainer().setNewDamage(newAmount);
    }

    @SubscribeEvent
    public static void onPlayerTrySleep(CanPlayerSleepEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        if (phase >= 6) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("sporesrp.sleep.prevent"), true);
            if (player instanceof ServerPlayer serverPlayer) {
                AdvancementTriggerHelper.onSleepFail(serverPlayer);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerSleeping(CanContinueSleepingEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;  // 不是玩家则忽略
        }
        if (player.level().isClientSide) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        if (phase >= 6) {
            event.setContinueSleeping(false);
            player.displayClientMessage(Component.translatable("sporesrp.sleep.prevent"), true);
        }
    }
}