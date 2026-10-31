package com.maha_fish.sporesrp.command;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.battle.BattleData;
import com.maha_fish.sporesrp.battle.PurificationBattle;
import com.maha_fish.sporesrp.block.ModBlocks;
import com.maha_fish.sporesrp.data.DifficultyManager;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.handler.FullHivemindAssaultHandler;
import com.maha_fish.sporesrp.handler.FullHivemindHandler;
import com.maha_fish.sporesrp.handler.MoundLevelSystem;
import com.maha_fish.sporesrp.network.ToggleHUDPacket;
import com.maha_fish.sporesrp.phase.PhaseEffects;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class SporeCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sporesrp")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                return 0;
                            }
                            var level = player.serverLevel();
                            WorldEvolutionData data = WorldEvolutionData.get(level);
                            int points = data.getEvolutionPoints();
                            int phase = data.getEvolutionPhase();
                            int maxPhase = Config.COMMON.getMaxPhase();
                            String diff = data.getDifficulty().getDisplayName();

                            // 基础信息
                            source.sendSuccess(() -> Component.translatable("sporesrp.command.info", points, phase, maxPhase, diff), false);

                            // ---- 倒计时/升级状态 ----
                            if (phase >= maxPhase) {
                                source.sendSuccess(() -> Component.translatable("sporesrp.command.info.max_phase"), false);
                            } else {
                                var thresholds = Config.COMMON.phaseThresholds.get();
                                int nextThreshold = (Integer) thresholds.get(phase + 1);
                                if (points < nextThreshold) {
                                    int need = nextThreshold - points;
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.info.points_insufficient", need), false);
                                } else {
                                    long currentTick = level.getServer().getTickCount();
                                    long nextAvailable = data.getNextUpgradeAvailableTick();
                                    if (currentTick >= nextAvailable) {
                                        source.sendSuccess(() -> Component.translatable("sporesrp.command.info.cooldown_ready"), false);
                                    } else {
                                        long remainingTicks = nextAvailable - currentTick;
                                        long remainingSeconds = (remainingTicks + 19) / 20; // 向上取整
                                        source.sendSuccess(() -> Component.translatable("sporesrp.command.info.cooldown_remaining", remainingSeconds), false);
                                    }
                                }
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("cache")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                return 0;
                            }
                            ServerLevel level = player.serverLevel();

                            // 原始缓存 Map 大小
                            int protoSize = SporeEntityCache.PROTO_CACHE.size();
                            int moundSize = SporeEntityCache.MOUND_CACHE.size();
                            int largePoolSize = SporeEntityCache.LARGE_POOL_CACHE.size();
                            int bossSize = SporeEntityCache.BOSS_CACHE.size();
                            int builderSize = SporeEntityCache.BUILDER_CACHE.size();

                            // 预计算列表大小（存活）
                            int aliveProtos = SporeEntityCache.getAliveProtoCount(level);
                            int aliveMounds = SporeEntityCache.getAliveMoundCount(level);
                            int aliveLargePools = SporeEntityCache.getAliveLargePoolCount(level);
                            int aliveBosses = SporeEntityCache.getAliveBossCount(level);
                            int aliveBuilders = SporeEntityCache.getAliveBuilderCount(level);

                            source.sendSuccess(() -> Component.translatable("sporesrp.command.cache.header"), false);

                            source.sendSuccess(() -> Component.translatable("sporesrp.command.cache.proto",
                                    protoSize, aliveProtos), false);
                            source.sendSuccess(() -> Component.translatable("sporesrp.command.cache.mound",
                                    moundSize, aliveMounds), false);
                            source.sendSuccess(() -> Component.translatable("sporesrp.command.cache.large_pool",
                                    largePoolSize, aliveLargePools), false);
                            source.sendSuccess(() -> Component.translatable("sporesrp.command.cache.boss",
                                    bossSize, aliveBosses), false);
                            source.sendSuccess(() -> Component.translatable("sporesrp.command.cache.builder",
                                    builderSize, aliveBuilders), false);

                            source.sendSuccess(() -> Component.translatable("sporesrp.command.cache.footer"), false);
                            return 1;
                        })
                )
                .then(Commands.literal("addpoints")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    var level = player.serverLevel();
                                    long currentTick = level.getServer().getTickCount();
                                    WorldEvolutionData data = WorldEvolutionData.get(level);
                                    int oldPhase = data.getEvolutionPhase();
                                    data.addEvolutionPoints(amount, currentTick);
                                    data.syncToClients(level);
                                    int newPhase = data.getEvolutionPhase();
                                    if (newPhase != oldPhase) {
                                        PhaseEffects.onPhaseChange(oldPhase, newPhase, level);
                                    }
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.addpoints.success", amount), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("setpoints")
                        .then(Commands.argument("points", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int points = IntegerArgumentType.getInteger(ctx, "points");
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    var level = player.serverLevel();
                                    long currentTick = level.getServer().getTickCount();
                                    WorldEvolutionData data = WorldEvolutionData.get(level);
                                    int oldPhase = data.getEvolutionPhase();
                                    data.setEvolutionPoints(points, currentTick);
                                    data.syncToClients(level);
                                    int newPhase = data.getEvolutionPhase();
                                    if (newPhase != oldPhase) {
                                        PhaseEffects.onPhaseChange(oldPhase, newPhase, level);
                                    }
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.setpoints.success", points), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("addcooldown")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(-86400, 86400))
                                .executes(ctx -> {
                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    var level = player.serverLevel();
                                    long currentTick = level.getServer().getTickCount();
                                    WorldEvolutionData data = WorldEvolutionData.get(level);
                                    data.adjustPointCooldown(currentTick, seconds);
                                    data.syncToClients(level);
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.addcooldown.success", seconds), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("setphase")
                        .then(Commands.argument("phase", IntegerArgumentType.integer(0, 10))
                                .executes(ctx -> {
                                    int targetPhase = IntegerArgumentType.getInteger(ctx, "phase");
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    var level = player.serverLevel();
                                    long currentTick = level.getServer().getTickCount();
                                    WorldEvolutionData data = WorldEvolutionData.get(level);
                                    int oldPhase = data.getEvolutionPhase();
                                    var thresholds = Config.COMMON.phaseThresholds.get();
                                    if (targetPhase >= thresholds.size()) {
                                        source.sendFailure(Component.translatable("sporesrp.command.setphase.invalid", targetPhase, thresholds.size() - 1));
                                        return 0;
                                    }
                                    data.forceSetPhase(targetPhase, currentTick);
                                    data.syncToClients(level);
                                    int newPhase = data.getEvolutionPhase();
                                    if (newPhase != oldPhase) {
                                        PhaseEffects.onPhaseChange(oldPhase, newPhase, level);
                                    }
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.setphase.success", targetPhase, thresholds.get(targetPhase)), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("difficulty")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (DifficultyManager.Difficulty d : DifficultyManager.Difficulty.values()) {
                                        builder.suggest(d.name().toLowerCase());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String modeName = StringArgumentType.getString(ctx, "mode");
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    DifficultyManager.Difficulty target = DifficultyManager.Difficulty.fromName(modeName);
                                    if (target == null) {
                                        source.sendFailure(Component.translatable("sporesrp.command.difficulty.invalid", modeName));
                                        return 0;
                                    }
                                    var level = player.serverLevel();
                                    WorldEvolutionData data = WorldEvolutionData.get(level);
                                    DifficultyManager.Difficulty old = data.getDifficulty();
                                    if (old == target) {
                                        source.sendSuccess(() -> Component.translatable("sporesrp.command.difficulty.already", target.getDisplayName()), false);
                                        return 1;
                                    }
                                    data.setDifficulty(target);
                                    data.syncToClients(level);
                                    PhaseEffects.refreshAllInfectedAttributes(level);
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.difficulty.success",
                                            old.getDisplayName(), target.getDisplayName()), true);
                                    SporeSRP.LOGGER.info("Difficulty changed from {} to {} by {}", old, target, player.getName().getString());
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("mound")
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    LivingEntity target = getTargetMound(player);
                                    if (target == null) {
                                        source.sendFailure(Component.translatable("sporesrp.command.mound.not_found"));
                                        return 0;
                                    }
                                    UUID uuid = target.getUUID();
                                    int level = MoundLevelSystem.getLevel(uuid);
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.mound.info", level), false);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("setlevel")
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> {
                                            int newLevel = IntegerArgumentType.getInteger(ctx, "level");
                                            CommandSourceStack source = ctx.getSource();
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                                return 0;
                                            }
                                            LivingEntity target = getTargetMound(player);
                                            if (target == null) {
                                                source.sendFailure(Component.translatable("sporesrp.command.mound.not_found"));
                                                return 0;
                                            }
                                            UUID uuid = target.getUUID();
                                            int oldLevel = MoundLevelSystem.getLevel(uuid);
                                            MoundLevelSystem.setLevel(uuid, newLevel);
                                            source.sendSuccess(() -> Component.translatable("sporesrp.command.mound.setlevel.success", oldLevel, newLevel), true);
                                            return 1;
                                        })
                                )
                        )
                )
                // ========== 重置配置命令 ==========
                .then(Commands.literal("reset_config")
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();

                            if (!source.hasPermission(2)) {
                                source.sendFailure(Component.translatable("sporesrp.command.reset_config.no_permission"));
                                return 0;
                            }

                            try {
                                Path configDir = Paths.get("config");
                                String[] filesToDelete = {
                                        "sporesrp_mound_modified.marker",
                                        "sporesrp_sporedata_modified.marker",
                                        "sporeconfig.toml",
                                        "sporedata.toml",
                                        "sporesrp-common.toml"
                                };

                                int deletedCount = 0;
                                for (String fileName : filesToDelete) {
                                    Path filePath = configDir.resolve(fileName);
                                    if (Files.exists(filePath)) {
                                        Files.delete(filePath);
                                        deletedCount++;
                                    }
                                }

                                // ---- 修复：创建 final 副本用于 lambda ----
                                final int finalDeletedCount = deletedCount;

                                if (deletedCount > 0) {
                                    source.sendSuccess(() -> Component.translatable(
                                            "sporesrp.command.reset_config.success", finalDeletedCount
                                    ), false);
                                    source.sendSuccess(() -> Component.translatable(
                                            "sporesrp.command.reset_config.restart"
                                    ), false);
                                    SporeSRP.LOGGER.info("Config reset by {} (deleted {} files)",
                                            source.getTextName(), deletedCount);
                                } else {
                                    source.sendSuccess(() -> Component.translatable(
                                            "sporesrp.command.reset_config.no_files"
                                    ), false);
                                }

                                return 1;

                            } catch (IOException e) {
                                SporeSRP.LOGGER.error("Failed to delete files during config reset", e);
                                source.sendFailure(Component.translatable(
                                        "sporesrp.command.reset_config.failed"
                                ));
                                return 0;
                            }
                        })
                )
                // ========== 完整心智命令 ==========
                .then(Commands.literal("fullhivemind")
                        .then(Commands.literal("convert")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    LivingEntity target = getTargetEntity(player);
                                    if (target == null) {
                                        source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.no_target"));
                                        return 0;
                                    }
                                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
                                    if (id == null || !"spore:proto".equals(id.toString())) {
                                        source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.not_proto"));
                                        return 0;
                                    }
                                    ServerLevel level = player.serverLevel();
                                    boolean success = FullHivemindHandler.convertToFullHivemind(target, level);
                                    if (success) {
                                        source.sendSuccess(() -> Component.translatable("sporesrp.command.fullhivemind.convert.success"), true);
                                        return 1;
                                    } else {
                                        source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.convert.failed"));
                                        return 0;
                                    }
                                })
                        )
                        .then(Commands.literal("assault")
                                .then(Commands.literal("start")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    builder.suggest("air");
                                                    builder.suggest("ground");
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    String type = StringArgumentType.getString(ctx, "type");
                                                    CommandSourceStack source = ctx.getSource();
                                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                                        return 0;
                                                    }
                                                    ServerLevel level = player.serverLevel();
                                                    boolean success = FullHivemindAssaultHandler.startAssaultCommand(level, player, type);
                                                    if (success) {
                                                        source.sendSuccess(() -> Component.translatable("sporesrp.command.fullhivemind.assault.start", type), true);
                                                        return 1;
                                                    } else {
                                                        source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.assault.start.failed"));
                                                        return 0;
                                                    }
                                                })
                                        )
                                        .executes(ctx -> {
                                            CommandSourceStack source = ctx.getSource();
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                                return 0;
                                            }
                                            ServerLevel level = player.serverLevel();
                                            boolean success = FullHivemindAssaultHandler.startAssaultCommand(level, player, "air");
                                            if (success) {
                                                source.sendSuccess(() -> Component.translatable("sporesrp.command.fullhivemind.assault.start", "air"), true);
                                                return 1;
                                            } else {
                                                source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.assault.start.failed"));
                                                return 0;
                                            }
                                        })
                                )
                                .then(Commands.literal("info")
                                        .executes(ctx -> {
                                            CommandSourceStack source = ctx.getSource();
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                                return 0;
                                            }
                                            ServerLevel level = player.serverLevel();
                                            WorldEvolutionData data = WorldEvolutionData.get(level);

                                            // ★ 尝试 1：通过 UUID 查找
                                            UUID hivemindUuid = data.getFullHivemindUuid();
                                            LivingEntity entity = null;
                                            if (hivemindUuid != null) {
                                                entity = (LivingEntity) level.getEntity(hivemindUuid);
                                            }

                                            // ★ 尝试 2（降级方案）：扫描所有维度，查找完整心智
                                            if (entity == null || !entity.isAlive() || !FullHivemindHandler.isFullHivemind(entity)) {
                                                for (ServerLevel lv : level.getServer().getAllLevels()) {
                                                    // ★ 使用三参数版本：Class + AABB + Predicate
                                                    AABB worldBox = new AABB(
                                                            lv.getWorldBorder().getMinX(), lv.getMinBuildHeight(), lv.getWorldBorder().getMinZ(),
                                                            lv.getWorldBorder().getMaxX(), lv.getMaxBuildHeight(), lv.getWorldBorder().getMaxZ()
                                                    );
                                                    List<LivingEntity> candidates = lv.getEntitiesOfClass(
                                                            LivingEntity.class,
                                                            worldBox,
                                                            e -> FullHivemindHandler.isFullHivemind(e)
                                                    );
                                                    if (!candidates.isEmpty()) {
                                                        entity = candidates.get(0);
                                                        data.setFullHivemindUuid(entity.getUUID());
                                                        SporeSRP.LOGGER.info("Auto-recovered Full Hivemind UUID: {}", entity.getUUID());
                                                        break;
                                                    }
                                                }
                                            }

                                            // 如果仍然找不到，报错
                                            if (entity == null) {
                                                source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.no_hivemind"));
                                                return 0;
                                            }

                                            // 获取袭击值
                                            double assaultValue = data.getAssaultValue();
                                            long currentTick = level.getServer().getTickCount();
                                            boolean onCooldown = data.isAssaultOnCooldown(currentTick);

                                            Component msg;
                                            if (onCooldown) {
                                                long cooldownEndTick = data.getAssaultCooldownEndTick();
                                                long remainingSeconds = (cooldownEndTick - currentTick + 19) / 20;
                                                msg = Component.translatable("sporesrp.command.fullhivemind.assault.info.cooldown", assaultValue, remainingSeconds);
                                            } else {
                                                msg = Component.translatable("sporesrp.command.fullhivemind.assault.info.ready", assaultValue);
                                            }
                                            source.sendSuccess(() -> msg, false);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("finishmining")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    ServerLevel level = player.serverLevel();
                                    WorldEvolutionData data = WorldEvolutionData.get(level);
                                    UUID uuid = data.getFullHivemindUuid();
                                    if (uuid == null) {
                                        source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.no_hivemind"));
                                        return 0;
                                    }
                                    // 检查实体是否仍然存在
                                    LivingEntity entity = (LivingEntity) level.getEntity(uuid);
                                    if (entity == null || !entity.isAlive() || !FullHivemindHandler.isFullHivemind(entity)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.fullhivemind.hivemind_not_found"));
                                        return 0;
                                    }

                                    // 强制完成挖掘并建造穹顶
                                    FullHivemindHandler.finishMiningAndBuildCasing(level, uuid);
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.fullhivemind.finishmining.success"), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("hud")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("on");
                                    builder.suggest("off");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String state = StringArgumentType.getString(ctx, "state");
                                    boolean enable = state.equalsIgnoreCase("on");
                                    CommandSourceStack source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    SporeSRP.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                            new ToggleHUDPacket(enable));
                                    source.sendSuccess(() -> Component.translatable("sporesrp.command.hud.toggle", enable ? "ON" : "OFF"), true);
                                    return 1;
                                })
                        )
                )
                // ========== 世界净化之战调试命令 ==========
                .then(Commands.literal("battle")
                        .then(Commands.literal("start")
                                .then(Commands.argument("phase", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> {
                                            int phase = IntegerArgumentType.getInteger(ctx, "phase");
                                            CommandSourceStack source = ctx.getSource();
                                            if (!source.hasPermission(2)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.no_permission"));
                                                return 0;
                                            }
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                                return 0;
                                            }
                                            ServerLevel level = player.serverLevel();
                                            boolean success = PurificationBattle.getInstance().forceStartBattle(level, player, phase);
                                            if (success) {
                                                source.sendSuccess(() -> Component.translatable("sporesrp.command.battle.start.success",
                                                        phase), true);
                                            } else {
                                                source.sendFailure(Component.translatable("sporesrp.command.battle.start.failed"));
                                            }
                                            return success ? 1 : 0;
                                        })
                                )
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    if (!source.hasPermission(2)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.no_permission"));
                                        return 0;
                                    }
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    ServerLevel level = player.serverLevel();
                                    boolean success = PurificationBattle.getInstance().forceStartBattle(level, player, 1);
                                    if (success) {
                                        source.sendSuccess(() -> Component.translatable("sporesrp.command.battle.start.success", 1), true);
                                    } else {
                                        source.sendFailure(Component.translatable("sporesrp.command.battle.start.failed"));
                                    }
                                    return success ? 1 : 0;
                                })
                        )
                        .then(Commands.literal("end")
                                .executes(ctx -> {
                                    CommandSourceStack source = ctx.getSource();
                                    if (!source.hasPermission(2)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.no_permission"));
                                        return 0;
                                    }
                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                        source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                        return 0;
                                    }
                                    ServerLevel level = player.serverLevel();
                                    BlockPos pos = player.blockPosition(); // 获取玩家位置
                                    boolean success = PurificationBattle.getInstance().forceEndBattle(level, pos);
                                    if (success) {
                                        source.sendSuccess(() -> Component.translatable("sporesrp.command.battle.end.success"), true);
                                    } else {
                                        source.sendFailure(Component.translatable("sporesrp.command.battle.end.failed"));
                                    }
                                    return success ? 1 : 0;
                                })
                        )
                        .then(Commands.literal("setphase")
                                .then(Commands.argument("phase", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> {
                                            int phase = IntegerArgumentType.getInteger(ctx, "phase");
                                            CommandSourceStack source = ctx.getSource();
                                            if (!source.hasPermission(2)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.no_permission"));
                                                return 0;
                                            }
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                                return 0;
                                            }
                                            ServerLevel level = player.serverLevel();
                                            BlockPos pos = player.blockPosition();
                                            boolean success = PurificationBattle.getInstance().forceSetPhase(level, pos, phase);
                                            if (success) {
                                                source.sendSuccess(() -> Component.translatable("sporesrp.command.battle.setphase.success", phase), true);
                                            } else {
                                                source.sendFailure(Component.translatable("sporesrp.command.battle.setphase.failed"));
                                            }
                                            return success ? 1 : 0;
                                        })
                                )
                        )
                        // 在 battle 子命令中，在 setphase 之后添加
                        .then(Commands.literal("victoryeffect")
                                .then(Commands.argument("state", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("true");
                                            builder.suggest("false");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String state = StringArgumentType.getString(ctx, "state");
                                            boolean enable = Boolean.parseBoolean(state);
                                            CommandSourceStack source = ctx.getSource();
                                            if (!source.hasPermission(2)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.no_permission"));
                                                return 0;
                                            }
                                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                source.sendFailure(Component.translatable("sporesrp.command.player_only"));
                                                return 0;
                                            }
                                            ServerLevel level = player.serverLevel();
                                            WorldEvolutionData data = WorldEvolutionData.get(level);

                                            // 设置净化状态（变 true 时一次性清扫该维度的孢子生物）
                                            com.maha_fish.sporesrp.handler.PurifiedWorldHandler
                                                    .applyPurification(level, enable);
                                            data.syncToClients(level);

                                            if (enable) {
                                                // 如果设为 true，立即执行完整净化（清除生物+移除核心）
                                                // 注意：需要找到当前战斗的核心位置，或者扫描所有净化核心
                                                // 简化方案：扫描世界中存在的净化核心并移除
                                                BlockPos corePos = findPurificationCore(level);
                                                if (corePos != null) {
                                                    level.setBlock(corePos, Blocks.AIR.defaultBlockState(), 3);
                                                }
                                                clearAllSporeEntities(level); // 需要将此方法设为 public static
                                                source.sendSuccess(() -> Component.translatable("sporesrp.command.battle.victoryeffect.activated"), true);
                                            } else {
                                                source.sendSuccess(() -> Component.translatable("sporesrp.command.battle.victoryeffect.deactivated"), true);
                                            }

                                            return 1;
                                        })
                                )
                        )
                )


        );
    }

    private static LivingEntity getTargetMound(ServerPlayer player) {
        double reachDistance = 6.0;
        HitResult hitResult = player.pick(reachDistance, 1.0F, false);
        if (hitResult instanceof EntityHitResult entityHit) {
            LivingEntity entity = (LivingEntity) entityHit.getEntity();
            if (entity != player && isMound(entity)) {
                return entity;
            }
        }
        AABB searchArea = player.getBoundingBox().inflate(reachDistance);
        return player.level().getEntitiesOfClass(LivingEntity.class, searchArea, e -> e != player && isMound(e))
                .stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }
    // 添加辅助方法：获取玩家准星指向的实体
    private static LivingEntity getTargetEntity(ServerPlayer player) {
        double reachDistance = 64.0;
        HitResult hitResult = player.pick(reachDistance, 1.0F, false);
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            if (entity instanceof LivingEntity living) {
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
                if (id != null && "spore:proto".equals(id.toString())) {
                    return living;
                }
            }
        }
        // 如果射线未命中 Proto，再使用锥形扫描
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getLookAngle();
        AABB aabb = new AABB(player.blockPosition()).inflate(reachDistance);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, aabb,
                e -> {
                    if (e == player || !e.isAlive()) return false;
                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
                    if (id == null || !"spore:proto".equals(id.toString())) return false;
                    Vec3 toEntity = e.getEyePosition(1.0F).subtract(eyePos).normalize();
                    double dot = lookVec.dot(toEntity);
                    return dot > 0.866; // cos(30°)
                });
        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
            return candidates.get(0);
        }
        return null;
    }

    private static boolean isMound(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("spore:mound");
    }
    private static BlockPos findPurificationCore(ServerLevel level) {
        // 扫描世界中存在的净化核心（性能开销可接受，仅命令调用时执行）
        for (int x = -100; x <= 100; x += 10) {
            for (int z = -100; z <= 100; z += 10) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y += 10) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(ModBlocks.WORLD_PURIFICATION_CORE.get())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static void clearAllSporeEntities(ServerLevel level) {
        // 复用 PurificationBattle 中的方法，或者在此处独立实现
        AABB worldBox = new AABB(
                level.getWorldBorder().getMinX(), level.getMinBuildHeight(), level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(), level.getMaxBuildHeight(), level.getWorldBorder().getMaxZ()
        );
        List<LivingEntity> allSpore = level.getEntitiesOfClass(LivingEntity.class, worldBox, e -> {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            return id != null && "spore".equals(id.getNamespace());
        });
        for (LivingEntity e : allSpore) {
            e.discard();
        }
    }
}