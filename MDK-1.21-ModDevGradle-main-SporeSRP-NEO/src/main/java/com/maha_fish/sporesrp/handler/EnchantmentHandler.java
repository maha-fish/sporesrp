package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.enchantment.ModEnchantments;
import com.maha_fish.sporesrp.util.AdvancementTriggerHelper;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public class EnchantmentHandler {

    // ========== 附魔注册表获取 ==========
    private static final Registry<Enchantment> ENCHANTMENT_REGISTRY;

    static {
        Registry<?> registry = BuiltInRegistries.REGISTRY.get(Registries.ENCHANTMENT.location());
        if (registry == null) {
            SporeSRP.LOGGER.error("Enchantment registry not found!");
            ENCHANTMENT_REGISTRY = null;
        } else {
            @SuppressWarnings("unchecked")
            Registry<Enchantment> casted = (Registry<Enchantment>) registry;
            ENCHANTMENT_REGISTRY = casted;
        }
    }

    // ========== 冻伤效果缓存 ==========
    private static MobEffect frostbiteEffect = null;
    /** 未找到 spore:frostbite 时的告警只发一次（每次命中都查表+WARN 太浪费）。 */
    private static boolean frostbiteWarned = false;

    private static MobEffect getFrostbiteEffect() {
        if (frostbiteEffect == null) {
            frostbiteEffect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.parse("spore:frostbite"));
            if (frostbiteEffect == null) {
                // 不永久缓存 null：效果若稍后才注册仍能被找到；只是告警不再重复。
                if (!frostbiteWarned) {
                    frostbiteWarned = true;
                    SporeSRP.LOGGER.warn("spore:frostbite effect not found!");
                }
            } else {
                frostbiteWarned = false;
            }
        }
        return frostbiteEffect;
    }

    // ========== 检测附魔 ==========
    private static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        ItemEnchantments itemenchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : itemenchantments.entrySet()) {
            ResourceKey<Enchantment> enchantmentKey = entry.getKey().getKey();
            if (enchantmentKey != null && enchantmentKey.equals(key)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    // ========== 近战伤害事件 ==========
    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        

    if (event.getEntity().level().isClientSide()) return;if (!Config.COMMON_SPEC.isLoaded()) return;LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        if (!(event.getContainer().getSource().getEntity() instanceof LivingEntity attacker)) return;

        if (attacker instanceof ServerPlayer player) {
            ResourceLocation targetId = SporeEntityHelper.getEntityId(target);
            if (targetId != null) {
                String targetIdStr = targetId.toString();
                if ("spore:proto".equals(targetIdStr)) {
                    AdvancementTriggerHelper.onEncounterProto(player);
                }
                if ("spore:gastgaber".equals(targetIdStr)) {
                    AdvancementTriggerHelper.onEncounterBuilder(player);
                }
            }
        }

        ItemStack weapon = attacker.getMainHandItem();

        // ---- 真菌杀手 ----
        if (SporeEntityHelper.isSporeEntity(target)) {
            int baneLevel = getEnchantmentLevel(weapon, ModEnchantments.FUNGAL_BANE);
            if (baneLevel > 0) {
                float extraDamage = 2.5f * baneLevel;
                event.getContainer().setNewDamage(event.getContainer().getNewDamage() + extraDamage);
            }
        }

        // ---- 冰冻附加 ----
        int frostLevel = getEnchantmentLevel(weapon, ModEnchantments.FROST_ASPECT);
        if (frostLevel > 0) {
            applyFrostEffect(target, frostLevel);
        }
    }

    // ========== 远程投射物事件 ==========
    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        
    if (!Config.COMMON_SPEC.isLoaded()) return;if (event.getProjectile().level().isClientSide) return;
        if (!(event.getProjectile() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof LivingEntity shooter)) return;

        HitResult hitResult = event.getRayTraceResult();

        if (shooter instanceof ServerPlayer player) {
            if (hitResult instanceof EntityHitResult entityHit &&
                    entityHit.getEntity() instanceof LivingEntity target) {
                ResourceLocation targetId = SporeEntityHelper.getEntityId(target);
                if (targetId != null) {
                    String targetIdStr = targetId.toString();
                    if ("spore:proto".equals(targetIdStr)) {
                        AdvancementTriggerHelper.onEncounterProto(player);
                    }
                    if ("spore:gastgaber".equals(targetIdStr)) {
                        AdvancementTriggerHelper.onEncounterBuilder(player);
                    }
                }
            }
        }

        ItemStack bow = shooter.getMainHandItem();
        int level = getEnchantmentLevel(bow, ModEnchantments.FROST_SHOT);
        if (level <= 0) {
            bow = shooter.getOffhandItem();
            level = getEnchantmentLevel(bow, ModEnchantments.FROST_SHOT);
            if (level <= 0) {
                return;
            }
        }

        if (!(hitResult instanceof EntityHitResult entityHit)) return;

        if (entityHit.getEntity() instanceof LivingEntity target) {
            applyFrostEffect(target, level);
        }
    }

    // ========== 通用：施加冻伤效果 ==========
    private static void applyFrostEffect(LivingEntity target, int level) {
        MobEffect effect = getFrostbiteEffect();
        if (effect == null) return;

        int duration = (4 + 2 * level) * 20;
        int amplifier = (level + 1) / 2 - 1;
        if (amplifier < 0) amplifier = 0;

        target.addEffect(new MobEffectInstance(
                BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect),
                duration,
                amplifier,
                false,
                true
        ));
    }
}