package com.maha_fish.sporesrp.client;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.network.SyncEvolutionDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.text.DecimalFormat;

@Mod.EventBusSubscriber(modid = "sporesrp", value = Dist.CLIENT)
public class HUDOverlay {

    private static final int FULL_WIDTH = 113;
    private static final int FULL_HEIGHT = 29;
    private static final int BAR_TEXTURE_X = 23;
    private static final int BAR_TEXTURE_Y = 32;
    private static final int TEXTURE_SIZE = 256;

    // ★ 优化1：静态缓存 DecimalFormat，避免每帧创建
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    // ★ 优化2：预加载所有阶段的纹理，避免每帧 new ResourceLocation
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[11];
    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = new ResourceLocation("sporesrp", "textures/gui/overlast/evolutionbar" + i + ".png");
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!SyncEvolutionDataPacket.ClientDataHolder.isHudEnabled()) return;

        int phase = SyncEvolutionDataPacket.ClientDataHolder.getPhase();
        int points = SyncEvolutionDataPacket.ClientDataHolder.getPoints();
        int currentThreshold = SyncEvolutionDataPacket.ClientDataHolder.getCurrentThreshold();
        int nextThreshold = SyncEvolutionDataPacket.ClientDataHolder.getNextThreshold();
        int cooldown = SyncEvolutionDataPacket.ClientDataHolder.getCooldownSeconds();
        String difficulty = SyncEvolutionDataPacket.ClientDataHolder.getDifficulty();

        int progress = progressWidth(phase, points, currentThreshold, nextThreshold);

        // ★ 优化3：直接从预加载数组获取纹理，无对象分配
        ResourceLocation texture = TEXTURES[Math.min(phase, 10)];

        GuiGraphics gui = event.getGuiGraphics();
        int x = Config.COMMON.hudX.get();
        int y = Config.COMMON.hudY.get();

        // 绘制进度条
        if (progress > 0) {
            gui.blit(texture, x - 80 - 10, y + 3,
                    BAR_TEXTURE_X, BAR_TEXTURE_Y, progress, FULL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        }
        gui.blit(texture, x - FULL_WIDTH, y,
                0, 0, FULL_WIDTH, FULL_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        // 点数
        String pointsText = formatPoints(points);
        int centerX = x - FULL_WIDTH - 15;
        int pointsY = y + 13;
        gui.drawCenteredString(mc.font, Component.literal(pointsText), centerX, pointsY, 0xFFFFFF);

        // 难度与冷却
        int infoY = y + FULL_HEIGHT - 2;
        String cooldownText;
        if (cooldown < 0) {
            cooldownText = "";
        } else if (cooldown == 0) {
            cooldownText = Component.translatable("sporesrp.hud.cooldown.ready").getString();
        } else {
            cooldownText = Component.translatable("sporesrp.hud.cooldown", cooldown).getString();
        }
        String difficultyText = Component.translatable("sporesrp.hud.difficulty", difficulty).getString();

        String infoText = cooldownText.isEmpty() ? difficultyText : cooldownText + "  |  " + difficultyText;
        gui.drawString(mc.font, Component.literal(infoText), x - FULL_WIDTH, infoY, 0xAAAAAA);
    }

    private static int progressWidth(int phase, int points, int currentThreshold, int nextThreshold) {
        if (phase >= 10) return 80;
        int span = nextThreshold - currentThreshold;
        if (span <= 0) return 0;
        long progress = (long) (points - currentThreshold) * 80L / span;
        return (int) Math.max(0, Math.min(80, progress));
    }

    // ★ 优化4：使用静态 DecimalFormat，不再每帧创建
    private static String formatPoints(int points) {
        if (points < 0) return "0P";
        double value = points;
        String unit = "P";

        if (points >= 1_000_000_000_000L) {
            value = points / 1_000_000_000_000.0;
            unit = "tP";
        } else if (points >= 1_000_000_000) {
            value = points / 1_000_000_000.0;
            unit = "bP";
        } else if (points >= 1_000_000) {
            value = points / 1_000_000.0;
            unit = "MP";
        } else if (points >= 10_000) {
            value = points / 10_000.0;
            unit = "wP";
        } else if (points >= 1000) {
            value = points / 1000.0;
            unit = "kP";
        }

        return DF.format(value) + unit;
    }
}