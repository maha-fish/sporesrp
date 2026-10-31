package com.maha_fish.sporesrp.block;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * 诱饵方块。
 *
 * <p>右键检查（同一水平面内的 5×5 正方形，中心就是被右键的方块）：
 * <b>四个角</b>（±2, 0, ±2）上是否都有<b>等级相同</b>的诱饵。
 * <ul>
 *   <li>四角齐全 → 饵堆：按中心等级扣演化点数、叠加冷却、<b>吸引周围真菌生物仇恨</b>，
 *       若世界存在 Proto 则<b>必定被其中一只发现</b>；随后 5 个方块全部消失。</li>
 *   <li>否则 → 单个诱饵：只叠加演化时间冷却，不扣点数；随后该方块消失。</li>
 * </ul>
 * 完成后统一同步 HUD 的演化点数与剩余倒计时。
 */
public class DecoyBlock extends Block {

    /** 1..8，与 {@link DecoyTiers} 对应。 */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", DecoyTiers.MIN, DecoyTiers.MAX);

    /** 5×5 正方形的半边长：偏移 ±2。 */
    public static final int CORNER_OFFSET = 2;

    /** 四个角的水平偏移。 */
    private static final int[][] CORNERS = {
            {-CORNER_OFFSET, -CORNER_OFFSET},
            {-CORNER_OFFSET, CORNER_OFFSET},
            {CORNER_OFFSET, -CORNER_OFFSET},
            {CORNER_OFFSET, CORNER_OFFSET}
    };

    public DecoyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TIER, DecoyTiers.MIN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(TIER, DecoyTiers.tierOf(context.getItemInHand()));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 挖掉后掉落对应等级的诱饵物品。 */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(DecoyTiers.item(state.getValue(TIER))));
    }

    // ==================== 右键 ====================

    /** 手持物品右键。潜行时交给正常放置，避免摆诱饵阵时右键被吃掉。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        activate(state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    /** 空手右键。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        activate(state, level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void activate(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        int tier = state.getValue(TIER);
        boolean pile = hasFourMatchingCorners(level, pos, tier);

        WorldEvolutionData data = WorldEvolutionData.get(serverLevel);

        if (pile) {
            applyPileEffect(serverLevel, player, pos, tier, data);
            // 5 个方块全部消失
            for (int[] c : CORNERS) {
                level.removeBlock(pos.offset(c[0], 0, c[1]), false);
            }
            level.removeBlock(pos, false);
        } else {
            applySingleEffect(serverLevel, player, pos, tier, data);
            // 只消失自己
            level.removeBlock(pos, false);
        }

        // 完成后同步 HUD 的剩余倒计时与演化点数
        data.syncToClients(serverLevel);
    }

    /**
     * 检查同一水平面内 5×5 正方形的四个角，是否都有**等级相同**的诱饵。
     */
    private static boolean hasFourMatchingCorners(Level level, BlockPos center, int tier) {
        for (int[] c : CORNERS) {
            BlockState corner = level.getBlockState(center.offset(c[0], 0, c[1]));
            if (!corner.is(ModBlocks.DECOY.get())) return false;
            if (corner.getValue(TIER) != tier) return false;
        }
        return true;
    }

    // ==================== 效果 ====================

    /**
     * 饵堆逻辑：四角齐全时触发。
     *
     * <p>按<b>中心</b>诱饵的等级扣演化点数、叠加冷却，并把周围真菌生物的仇恨拉到玩家身上；
     * 只要世界里有 Proto 存活，就必定有一只被强制注意到玩家。
     */
    private static void applyPileEffect(ServerLevel level, Player player, BlockPos pos, int tier,
                                        WorldEvolutionData data) {
        int points = DecoyTiers.points(tier);
        int cooldown = DecoyTiers.cooldownSeconds(tier);

        data.applyDecoyPoints(points, cooldown, level.getGameTime());

        player.displayClientMessage(
                Component.translatable("sporesrp.decoy.hint.pile", points, cooldown)
                        .withStyle(ChatFormatting.GREEN), true);

        int lured = lureFungalCreatures(level, player);
        if (lured > 0) {
            player.displayClientMessage(
                    Component.translatable("sporesrp.decoy.hint.aggro", lured)
                            .withStyle(ChatFormatting.RED), true);
        }

        if (Config.COMMON.decoyProvokeProto.get() && provokeProto(level, player)) {
            player.displayClientMessage(
                    Component.translatable("sporesrp.decoy.hint.proto")
                            .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    /** 单个诱饵逻辑：四角不齐时触发，只叠加演化时间冷却，不扣点数。 */
    private static void applySingleEffect(ServerLevel level, Player player, BlockPos pos, int tier,
                                          WorldEvolutionData data) {
        int cooldown = DecoyTiers.cooldownSeconds(tier);
        data.applyDecoyCooldown(cooldown, level.getGameTime());

        player.displayClientMessage(
                Component.translatable("sporesrp.decoy.hint.single", cooldown)
                        .withStyle(ChatFormatting.YELLOW), true);
    }

    /**
     * 把半径内所有真菌生物的仇恨拉到玩家身上。
     *
     * @return 被吸引的数量
     */
    private static int lureFungalCreatures(ServerLevel level, Player player) {
        int radius = Config.COMMON.decoyAggroRadius.get();
        if (radius <= 0) return 0;

        AABB box = player.getBoundingBox().inflate(radius);
        int lured = 0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && SporeEntityHelper.isSporeEntity(e))) {
            if (entity instanceof Mob mob) {
                mob.setTarget(player);
                lured++;
            }
        }
        return lured;
    }

    /**
     * 世界存在 Proto 时，强制让其中一只注意到玩家（优先同维度最近的）。
     *
     * @return 是否成功锁定到一只 Proto
     */
    private static boolean provokeProto(ServerLevel level, Player player) {
        MinecraftServer server = level.getServer();
        if (server == null) return false;

        Mob chosen = null;
        double bestSameDim = Double.MAX_VALUE;
        Mob fallback = null;

        for (ServerLevel other : server.getAllLevels()) {
            for (LivingEntity proto : SporeEntityCache.getAliveProtos(other)) {
                if (!proto.isAlive() || !(proto instanceof Mob mob)) continue;

                if (other == level) {
                    double dist = proto.distanceToSqr(player);
                    if (dist < bestSameDim) {
                        bestSameDim = dist;
                        chosen = mob;
                    }
                } else if (fallback == null) {
                    fallback = mob;
                }
            }
        }

        Mob target = (chosen != null) ? chosen : fallback;
        if (target == null) return false;

        target.setTarget(player);
        return true;
    }
}
