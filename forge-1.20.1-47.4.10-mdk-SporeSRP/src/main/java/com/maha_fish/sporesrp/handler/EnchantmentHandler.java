package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.enchantment.ModEnchantments;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class EnchantmentHandler {

    private static MobEffect frostbiteEffect = null;
    /** 警告只输出一次（避免每次命中都刷日志）；效果仍会继续尝试查找，不做 null 永久缓存。 */
    private static boolean frostbiteWarned = false;
    private static final ResourceLocation FROSTBITE_ID = new ResourceLocation("spore", "frostbite");

    private static MobEffect getFrostbiteEffect() {
        if (frostbiteEffect == null) {
            frostbiteEffect = BuiltInRegistries.MOB_EFFECT.get(FROSTBITE_ID);
            if (frostbiteEffect == null && !frostbiteWarned) {
                frostbiteWarned = true;
                SporeSRP.LOGGER.warn("spore:frostbite effect not found! Frost enchantments will not work.");
            }
        }
        return frostbiteEffect;
    }

    // ========== 真菌杀手 + 冰冻附加 ==========
    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        // 获取攻击者
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        if (attacker instanceof ServerPlayer player) {
            // 缓存版注册名查询，且 toString() 只算一次（原来每次命中要算两次）
            ResourceLocation targetId = SporeEntityHelper.getEntityId(target);
            if (targetId != null) {
                String targetKey = targetId.toString();
                if ("spore:proto".equals(targetKey)) {
                    AdvancementTriggerHelper.onEncounterProto(player);
                }
                if ("spore:gastgaber".equals(targetKey)) {
                    AdvancementTriggerHelper.onEncounterBuilder(player);
                }
            }
        }

        ItemStack weapon = attacker.getMainHandItem();

        // ---- 真菌杀手：对孢子生物增伤（无视护甲） ----
        if (SporeEntityHelper.isSporeEntity(target)) {
            int baneLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.FUNGAL_BANE.get(), weapon);
            if (baneLevel > 0) {
                // 每级 +2.5 伤害，直接加在护甲减免后的伤害上（无视护甲）
                float extraDamage = 2.5f * baneLevel;
                event.setAmount(event.getAmount() + extraDamage);
            }
        }

        // ---- 冰冻附加：施加冻伤 ----
        int frostLevel = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.FROST_ASPECT.get(), weapon);
        if (frostLevel > 0) {
            applyFrostEffect(target, frostLevel);
        }
    }

    // ========== 冰矢（远程） ==========
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        // 通过投射物获取世界
        if (event.getProjectile().level().isClientSide) return;
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;

        HitResult hitResult = event.getRayTraceResult();

        if (shooter instanceof ServerPlayer player) {
            if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
                // 缓存版注册名查询，且 toString() 只算一次
                ResourceLocation targetId = SporeEntityHelper.getEntityId(target);
                if (targetId != null) {
                    String targetKey = targetId.toString();
                    if ("spore:proto".equals(targetKey)) {
                        AdvancementTriggerHelper.onEncounterProto(player);
                    }
                    if ("spore:gastgaber".equals(targetKey)) {
                        AdvancementTriggerHelper.onEncounterBuilder(player);
                    }
                }
            }
        }

        // 检查弓箭手的弓/弩是否有冰矢附魔
        ItemStack bow = shooter.getMainHandItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.FROST_SHOT.get(), bow);
        if (level <= 0) {
            bow = shooter.getOffhandItem();
            level = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.FROST_SHOT.get(), bow);
            if (level <= 0) {
                return;
            }
        }

        // 检查是否击中了实体
        if (!(hitResult instanceof EntityHitResult entityHit)) return;

        if (entityHit.getEntity() instanceof LivingEntity target) {
            applyFrostEffect(target, level);
        }
    }

    // ========== 通用：施加冻伤效果 ==========
    private static void applyFrostEffect(LivingEntity target, int level) {
        MobEffect effect = getFrostbiteEffect();
        if (effect == null) return;

        // 持续时间: 4 秒 + 每级 2 秒
        int duration = (4 + 2 * level) * 20; // 转为 tick

        // 效果等级: 每两级 +1 (I级: 1-2级, II级: 3-4级, III级: 5级)
        int amplifier = (level + 1) / 2 - 1;
        if (amplifier < 0) amplifier = 0;

        target.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true));
    }
}