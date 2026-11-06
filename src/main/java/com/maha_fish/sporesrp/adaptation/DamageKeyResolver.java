package com.maha_fish.sporesrp.adaptation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把一次伤害归类成「适应来源 key」。
 *
 * <p>规则对齐 SRP（mcmod 伤害适应性，V1.9.6 起）：
 * <ul>
 *   <li>玩家造成的伤害 → 按**主手物品**记录（切武器 = 新来源；同注册名视为同一来源）；</li>
 *   <li>生物造成的伤害 → 按该生物的**实体类型**记录；</li>
 *   <li>非实体伤害 → 按**伤害类型**记录。</li>
 * </ul>
 *
 * <p>投射物（箭、三叉戟等）归到**发射者主手物品**上，这样「副手弓」的适应会累积到主手武器，
 * 与原版描述一致。
 */
public final class DamageKeyResolver {

    private static final Map<Item, String> ITEM_IDS = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, String> TYPE_IDS = new ConcurrentHashMap<>();

    private DamageKeyResolver() {
    }

    /** 解析出适应 key；永不为 null。 */
    public static String resolve(DamageSource source) {
        if (source == null) return "dmg:unknown";

        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();

        // 投射物：按发射者的主手物品归类
        if (direct != null && direct != attacker && direct instanceof Projectile) {
            if (attacker instanceof LivingEntity living) {
                return itemKey(living);
            }
            return "dmg:" + source.getMsgId();
        }

        if (attacker instanceof LivingEntity living) {
            if (living instanceof Player) {
                return itemKey(living);
            }
            return "mob:" + typeId(living.getType());
        }
        return "dmg:" + source.getMsgId();
    }

    private static String itemKey(LivingEntity living) {
        ItemStack stack = living.getMainHandItem();
        if (stack.isEmpty()) return "item:minecraft:air";
        String id = ITEM_IDS.computeIfAbsent(stack.getItem(), item -> {
            ResourceLocation location = BuiltInRegistries.ITEM.getKey(item);
            return location == null ? "minecraft:air" : location.toString();
        });
        return "item:" + id;
    }

    private static String typeId(EntityType<?> type) {
        if (type == null) return "unknown";
        return TYPE_IDS.computeIfAbsent(type, t -> {
            ResourceLocation location = BuiltInRegistries.ENTITY_TYPE.getKey(t);
            return location == null ? "unknown" : location.toString();
        });
    }
}