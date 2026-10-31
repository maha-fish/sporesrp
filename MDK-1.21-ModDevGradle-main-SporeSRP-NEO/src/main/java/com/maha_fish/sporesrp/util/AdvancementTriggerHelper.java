package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancementTriggerHelper {

    private static boolean infectionSpreadTriggered = false;

    // ========== 计数器 ==========
    private static final ConcurrentHashMap<UUID, KillCounter> killCounters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> deathCounters = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> survivalTicks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> triggeredOnce = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Long> lastSurvivalCheckTick = new ConcurrentHashMap<>();

    private static class KillCounter {
        int total = 0, small = 0, medium = 0, large = 0, boss = 0, proto = 0;
    }

    private static KillCounter getCounter(ServerPlayer player) {
        return killCounters.computeIfAbsent(player.getUUID(), k -> new KillCounter());
    }

    private static void grant(ServerPlayer player, String id) {
        AdvancementHelper.grant(player, ResourceLocation.parse(SporeSRP.MOD_ID + ":" + id));
    }

    private static void grantToAll(ServerLevel level, String id) {
        AdvancementHelper.grantToAll(level, ResourceLocation.parse(SporeSRP.MOD_ID + ":" + id));
    }

    public static boolean hasTriggered(ServerPlayer player, String id) {
        return triggeredOnce.containsKey(player.getUUID()) &&
                triggeredOnce.get(player.getUUID()).contains(id);
    }

    private static void markTriggered(ServerPlayer player, String id) {
        triggeredOnce.merge(player.getUUID(), id, (old, val) -> old + "," + val);
    }

    // ========== 击杀 ==========
    public static void onKill(ServerPlayer player, String poolType) {
        if (player == null) return;
        KillCounter c = getCounter(player);
        c.total++;
        switch (c.total) {
            case 1: grant(player, "first_kill"); break;
            case 10: grant(player, "kill_10"); break;
            case 50: grant(player, "kill_50"); break;
            case 100: grant(player, "kill_100"); break;
        }
        if ("small".equals(poolType)) {
            c.small++;
            if (c.small == 1) grant(player, "kill_small");
            if (c.small == 10) grant(player, "kill_small_10");
        } else if ("medium".equals(poolType)) {
            c.medium++;
            if (c.medium == 1) grant(player, "kill_medium");
            if (c.medium == 10) grant(player, "kill_medium_10");
        } else if ("large".equals(poolType)) {
            c.large++;
            if (c.large == 1) grant(player, "kill_large");
            if (c.large == 10) grant(player, "kill_large_10");
        } else if ("boss".equals(poolType)) {
            c.boss++;
            if (c.boss == 1) grant(player, "kill_boss");
            if (c.boss == 5) grant(player, "kill_boss_5");
        } else if ("proto".equals(poolType)) {
            c.proto++;
            if (c.proto == 1) grant(player, "kill_proto");
            if (c.proto == 3) grant(player, "kill_proto_3");
        }
    }

    // ========== 死亡 ==========
    public static void onDeath(ServerPlayer player) {
        if (player == null) return;
        int count = deathCounters.merge(player.getUUID(), 1, Integer::sum);
        switch (count) {
            case 1: grant(player, "die_first"); break;
            case 10: grant(player, "die_10"); break;
            case 50: grant(player, "die_50"); break;
        }
        survivalTicks.put(player.getUUID(), 0L);
        lastSurvivalCheckTick.put(player.getUUID(), (long) player.getServer().getTickCount());
    }

    // ========== 生存 ==========
    public static void tickSurvival(ServerPlayer player) {
        if (player == null || player.isDeadOrDying()) return;
        UUID uuid = player.getUUID();
        long now = player.getServer().getTickCount();

        Long last = lastSurvivalCheckTick.get(uuid);
        if (last == null) {
            // 首次记录，只建立基线
            lastSurvivalCheckTick.put(uuid, now);
            return;
        }
        long delta = now - last;
        // 异常差值：玩家刚登录/离线很久/重连/切维度，跳过本次并重置基线
        if (delta <= 0 || delta > 100) {
            lastSurvivalCheckTick.put(uuid, now);
            return;
        }
        lastSurvivalCheckTick.put(uuid, now);

        long ticks = survivalTicks.merge(uuid, delta, Long::sum);
        if (ticks >= 24000 && !hasTriggered(player, "survive_day")) {
            grant(player, "survive_day");
            markTriggered(player, "survive_day");
        }
        if (ticks >= 168000 && !hasTriggered(player, "survive_week")) {
            grant(player, "survive_week");
            markTriggered(player, "survive_week");
        }
        if (ticks >= 720000 && !hasTriggered(player, "survive_month")) {
            grant(player, "survive_month");
            markTriggered(player, "survive_month");
        }
    }

    // ========== 世界状态 ==========
    public static void onPhaseChange(ServerLevel level, int phase) {
        String id = null;
        switch (phase) {
            case 1: id = "phase_1"; break;
            case 3: id = "phase_3"; break;
            case 6: id = "phase_6"; break;
            case 10: id = "phase_10"; break;
        }
        if (id != null) grantToAll(level, id);
    }

    public static void onEncounterProto(ServerPlayer player) {
        if (player == null || hasTriggered(player, "encounter_proto")) return;
        grant(player, "encounter_proto");
        markTriggered(player, "encounter_proto");
    }

    public static void onEncounterBuilder(ServerPlayer player) {
        if (player == null || hasTriggered(player, "encounter_builder")) return;
        grant(player, "encounter_builder");
        markTriggered(player, "encounter_builder");
    }

    public static void onInfectionSpread(ServerLevel level) {
        if (level == null || infectionSpreadTriggered) return;
        infectionSpreadTriggered = true;
        grantToAll(level, "infection_spread");
    }

    public static void onSleepFail(ServerPlayer player) {
        if (player == null || hasTriggered(player, "sleep_fail")) return;
        grant(player, "sleep_fail");
        markTriggered(player, "sleep_fail");
    }

    // ========== 战斗 ==========
    public static void onBattleStart(ServerLevel level) {
        grantToAll(level, "battle_start");
    }

    public static void onBattleVictory(ServerLevel level, boolean isHard) {
        grantToAll(level, "battle_complete");
        if (isHard) grantToAll(level, "battle_hard");
    }

    public static void onBattleDefeat(ServerLevel level) {
        grantToAll(level, "battle_fail");
    }

    // ========== 完整心智 ==========
    public static void onHivemindBorn(ServerLevel level) {
        grantToAll(level, "hivemind_born");
    }

    public static void onHivemindAssault(ServerLevel level) {
        grantToAll(level, "hivemind_assault");
    }

    public static void onHivemindKill(ServerPlayer player) {
        if (player != null) grant(player, "hivemind_kill");
    }

    // ========== 物品 ==========
    public static void onMindCoreObtain(ServerPlayer player) {
        if (player == null) return;
        grant(player, "mind_core_obtain");  // grant 内部已检查 progress.isDone()

        ResourceLocation coreRecipeId = ResourceLocation.parse(SporeSRP.MOD_ID + ":" + "world_purification_core");
        try {
            var recipeManager = player.serverLevel().getRecipeManager();
            var recipeOptional = recipeManager.byKey(coreRecipeId);
            // ★ 1.20.1 中 byKey 直接返回 Optional<Recipe<?>>，不需要 .value()
            recipeOptional.ifPresent(recipe -> {
                player.awardRecipes(Collections.singletonList(recipe));
                SporeSRP.LOGGER.info("Unlocked recipe for {} for player {}", coreRecipeId, player.getName().getString());
            });
            if (recipeOptional.isEmpty()) {
                SporeSRP.LOGGER.warn("Recipe {} not found, cannot unlock", coreRecipeId);
            }
        } catch (Exception e) {
            SporeSRP.LOGGER.warn("Failed to unlock recipe for {}: {}", coreRecipeId, e.getMessage());
        }
    }

    public static void onCoreObtain(ServerPlayer player) {
        if (player == null) return;
        grant(player, "core_obtain");  // grant 内部已检查 progress.isDone()
    }

    public static void clear() {
        killCounters.clear();
        deathCounters.clear();
        survivalTicks.clear();
        triggeredOnce.clear();
        lastSurvivalCheckTick.clear();
        infectionSpreadTriggered = false;
    }
}