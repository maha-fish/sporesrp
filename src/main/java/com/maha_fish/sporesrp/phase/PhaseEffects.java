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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.UUID;

public class PhaseEffects {
    private static final UUID HEALTH_MOD_UUID = UUID.fromString("a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d");
    private static final UUID ATTACK_MOD_UUID = UUID.fromString("b2c3d4e5-6f7a-8b9c-0d1e-2f3a4b5c6d7e");
    private static final UUID SPEED_MOD_UUID = UUID.fromString("c3d4e5f6-7a8b-9c0d-1e2f-3a4b5c6d7e8f");
    private static final UUID ARMOR_MOD_UUID = UUID.fromString("d4e5f6a7-8b9c-0d1e-2f3a-4b5c6d7e8f9a");
    private static final UUID KNOCKBACK_MOD_UUID = UUID.fromString("e5f6a7b8-9c0d-1e2f-3a4b-5c6d7e8f9a0b");

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

        // 演化数据是全局的（存于主世界），属性刷新必须覆盖所有维度，
        // 否则下界/末地的孢子生物会一直用旧阶段的属性。
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
        MinecraftForge.EVENT_BUS.post(new PhaseChangeEvent(oldPhase, newPhase, level));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

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

        // ★ 使用传统 for 循环遍历 getAll()，避免 Lambda 和多余的 AABB 检查
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

    // ========== 核心修改：生命值百分比保留 ==========
    private static void applyAttributesDirect(LivingEntity entity,
                                              double healthMult,
                                              double attackMult,
                                              double speedBonus,
                                              int armorBonus,
                                              double knockbackResist,
                                              double diffMultiplier,
                                              boolean preserveHealthPercent) {
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) entity.getType();
        AttributeSupplier supplier = DefaultAttributes.getSupplier(type);
        if (supplier == null) return;

        double baseHealth = getBaseValueSafe(supplier, Attributes.MAX_HEALTH, 20.0);
        double baseAttack = getBaseValueSafe(supplier, Attributes.ATTACK_DAMAGE, 0.0);
        double baseSpeed = getBaseValueSafe(supplier, Attributes.MOVEMENT_SPEED, 0.0);
        int baseArmor = (int) getBaseValueSafe(supplier, Attributes.ARMOR, 0.0);
        double baseKnockback = getBaseValueSafe(supplier, Attributes.KNOCKBACK_RESISTANCE, 0.0);

        double finalHealth = baseHealth + (baseHealth * (healthMult - 1) * diffMultiplier);
        double finalAttack = baseAttack + (baseAttack * (attackMult - 1) * diffMultiplier);
        double finalSpeed = baseSpeed + (speedBonus * diffMultiplier);
        int finalArmor = (int) (baseArmor + (armorBonus * diffMultiplier));
        double finalKnockback = baseKnockback + (knockbackResist * diffMultiplier);

        // ---- 生命值（修复保留百分比） ----
        AttributeInstance healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            // 在添加修饰符之前保存旧的最大生命值
            double oldMax = entity.getMaxHealth();
            double oldHealth = entity.getHealth();

            // 清除旧修饰符并添加新修饰符
            healthAttr.removeModifier(HEALTH_MOD_UUID);
            double healthDiff = finalHealth - baseHealth;
            healthAttr.addPermanentModifier(new AttributeModifier(
                    HEALTH_MOD_UUID, "sporesrp_health_boost",
                    healthDiff, AttributeModifier.Operation.ADDITION
            ));

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
        AttributeInstance attackAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(ATTACK_MOD_UUID);
            double attackDiff = finalAttack - baseAttack;
            attackAttr.addPermanentModifier(new AttributeModifier(
                    ATTACK_MOD_UUID, "sporesrp_attack_boost",
                    attackDiff, AttributeModifier.Operation.ADDITION
            ));
        }

        // ---- 移速 ----
        AttributeInstance speedAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MOD_UUID);
            double speedDiff = finalSpeed - baseSpeed;
            speedAttr.addPermanentModifier(new AttributeModifier(
                    SPEED_MOD_UUID, "sporesrp_speed_boost",
                    speedDiff, AttributeModifier.Operation.ADDITION
            ));
        }

        // ---- 护甲 ----
        AttributeInstance armorAttr = entity.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(ARMOR_MOD_UUID);
            int armorDiff = finalArmor - baseArmor;
            armorAttr.addPermanentModifier(new AttributeModifier(
                    ARMOR_MOD_UUID, "sporesrp_armor_boost",
                    armorDiff, AttributeModifier.Operation.ADDITION
            ));
        }

        // ---- 击退抗性 ----
        AttributeInstance knockbackAttr = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.removeModifier(KNOCKBACK_MOD_UUID);
            double knockbackDiff = finalKnockback - baseKnockback;
            knockbackAttr.addPermanentModifier(new AttributeModifier(
                    KNOCKBACK_MOD_UUID, "sporesrp_knockback_boost",
                    knockbackDiff, AttributeModifier.Operation.ADDITION
            ));
        }
    }

    private static double getBaseValueSafe(AttributeSupplier supplier, Attribute attribute, double fallback) {
        try {
            return supplier.getBaseValue(attribute);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean isSporeEntity(LivingEntity entity) {
        ResourceLocation regName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return regName != null && "spore".equals(regName.getNamespace());
    }

    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        ServerLevel level = (ServerLevel) player.level();
        int phase = WorldEvolutionData.get(level).getEvolutionPhase();
        if (phase >= 6) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.translatable("sporesrp.sleep.prevent"), true);
            if (player instanceof ServerPlayer serverPlayer) {
                AdvancementTriggerHelper.onSleepFail(serverPlayer);
            }
        }
    }
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 只处理服务端
        if (event.getEntity().level().isClientSide()) return;

        DamageSource source = event.getSource();
        // 获取直接伤害实体（投射物）
        Entity directEntity = source.getDirectEntity();
        if (!(directEntity instanceof net.minecraft.world.entity.projectile.Projectile)) return;

        // 获取攻击者（发射者）
        Entity owner = source.getEntity();
        if (!(owner instanceof LivingEntity attacker)) return;

        // 只对孢子生物有效
        if (!isSporeEntity(attacker)) return;

        // 获取当前阶段倍率
        if (!(attacker.level() instanceof ServerLevel level)) return;
        WorldEvolutionData data = WorldEvolutionData.get(level);
        int phase = data.getEvolutionPhase();
        List<? extends Double> multipliers = Config.COMMON.attackMultipliers.get();
        double phaseMultiplier = (phase < multipliers.size()) ? multipliers.get(phase) : 1.0;
        double diffMultiplier = data.getDifficulty().getMultiplier();
        double finalMultiplier = 1.0 + (phaseMultiplier - 1) * diffMultiplier;

        // 应用伤害倍率
        float newAmount = event.getAmount() * (float) finalMultiplier;
        event.setAmount(newAmount);
    }
}