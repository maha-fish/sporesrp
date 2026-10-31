package com.maha_fish.sporesrp.util;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class AdvancementHelper {

    public static void grant(ServerPlayer player, ResourceLocation id) {
        if (player == null) return;
        AdvancementHolder advancement = player.server.getAdvancements().get(id);        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (!progress.isDone()) {
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(advancement, criterion);
            }
        }
    }

    public static void grantToAll(ServerLevel level, ResourceLocation id) {
        if (level == null) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            grant(player, id);
        }
    }

    // ★ 检查是否已获得
    public static boolean hasAdvancement(ServerPlayer player, ResourceLocation id) {
        if (player == null) return false;
        AdvancementHolder advancement = player.server.getAdvancements().get(id);        if (advancement == null) return false;
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        return progress.isDone();
    }
}