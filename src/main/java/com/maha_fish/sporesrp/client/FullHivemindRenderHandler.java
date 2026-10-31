package com.maha_fish.sporesrp.client;

import com.maha_fish.sporesrp.handler.FullHivemindHandler;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = "sporesrp", value = Dist.CLIENT)
public class FullHivemindRenderHandler {

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (FullHivemindHandler.isFullHivemind(entity)) {
            event.getPoseStack().scale(2.0f, 2.0f, 2.0f);
        }
    }
}