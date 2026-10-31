package com.maha_fish.sporesrp.client;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.handler.FullHivemindHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "sporesrp", value = Dist.CLIENT)
public class FullHivemindRenderHandler {

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        // 判断是否为完整心智
        if (FullHivemindHandler.isFullHivemind(entity)) {
            // 将模型放大为原来的 2 倍
            event.getPoseStack().scale(2.0f, 2.0f, 2.0f);
        }
    }
}