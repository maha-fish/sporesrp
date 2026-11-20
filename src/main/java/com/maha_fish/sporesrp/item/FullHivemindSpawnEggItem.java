package com.maha_fish.sporesrp.item;

import com.maha_fish.sporesrp.handler.FullHivemindHandler;
import com.maha_fish.sporesrp.util.SporeSpawnHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * 完整心智刷怪蛋。
 *
 * <p>生成 {@code spore:proto}，打上 {@code IsFullHivemind} 标记，
 * 再调用 {@link FullHivemindHandler#applyFullHivemindAttributes} 完成「完整心智」成型。
 */
public class FullHivemindSpawnEggItem extends Item {

    private static final ResourceLocation PROTO_ID = ResourceLocation.tryParse("spore:proto");
    private static final String HIVEMIND_TAG = "IsFullHivemind";

    public FullHivemindSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        // Spore 未安装时静默失败（containsKey 防止 get() 回退成猪）
        if (PROTO_ID == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(PROTO_ID)) {
            return InteractionResult.PASS;
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(PROTO_ID);
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        LivingEntity living = SporeSpawnHelper.spawnFinalized(serverLevel, type, pos);
        if (living == null) return InteractionResult.PASS;

        living.getPersistentData().putBoolean(HIVEMIND_TAG, true);
        living.setCustomName(Component.literal("Full Hivemind"));
        FullHivemindHandler.applyFullHivemindAttributes(living, serverLevel);

        serverLevel.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0F, 0.6F);

        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}