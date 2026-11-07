package com.maha_fish.sporesrp.client;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.network.AdaptationFlashPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伤害适应的体色蒙版 —— 挂载到原模组每个生物渲染器上的 {@link RenderLayer}。
 *
 * <p>做法：在 {@code EntityRenderersEvent.AddLayers} 阶段给所有 {@code spore:*} 生物的
 * {@link LivingEntityRenderer} 添加本层。层是在**原模型绘制完成后、同一套 pose 变换内**被调用的，
 * 因此用同一张贴图、同样的模型再画一遍即可得到「原贴图上盖一层半透明蒙版」的效果，
 * 位置与朝向天然与原模型完全一致（不会错位、不会颠倒）。
 *
 * <p>颜色语义：
 * <ul>
 *   <li><b>绿光</b>：本次打击让它产生了新的适应；</li>
 *   <li><b>紫光</b>：该伤害来源已<b>完全适应</b>（次数达上限），此后每次受击都显示紫色。</li>
 * </ul>
 *
 * <p>多段实体：环节是独立实体、有各自渲染器，这里把环节解析回父体复用父体的闪光状态，
 * 因此头部与所有环节同时变色。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AdaptationTintLayer extends RenderLayer<LivingEntity, EntityModel<LivingEntity>> {

    private static final Map<Class<?>, Method> PARENT_INT_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> NO_PARENT = ConcurrentHashMap.newKeySet();

    public AdaptationTintLayer(RenderLayerParent<LivingEntity, EntityModel<LivingEntity>> parent) {
        super(parent);
    }

    /** 给所有 {@code spore:*} 生物的渲染器挂上本层。 */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null || !"spore".equals(id.getNamespace())) continue;
            EntityRenderer<?> renderer;
            try {
                renderer = event.getRenderer((EntityType) type);
            } catch (Throwable ignored) {
                continue; // 非生物渲染器
            }
            if (renderer instanceof LivingEntityRenderer livingRenderer) {
                livingRenderer.addLayer(new AdaptationTintLayer(livingRenderer));
            }
        }
        SporeSRP.LOGGER.info("Adaptation tint layers attached to spore entity renderers");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (!Config.COMMON.adaptationVisualEnabled.get()) return;

        int kind = AdaptationFlashPacket.ClientFlashState.kindOf(visualRootId(entity));
        if (kind == 0) return;

        float[] color = kind == AdaptationFlashPacket.KIND_FULL
                ? parseColor(Config.COMMON.adaptationFullColor.get(), 0.72F, 0.30F, 1.0F)
                : parseColor(Config.COMMON.adaptationAdaptingColor.get(), 0.25F, 1.00F, 0.35F);
        float alpha = Config.COMMON.adaptationFlashAlpha.get().floatValue();

        ResourceLocation texture = this.getTextureLocation(entity);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(texture));
        int argb = ((int) (alpha * 255.0F) << 24)
                | ((int) (color[0] * 255.0F) << 16)
                | ((int) (color[1] * 255.0F) << 8)
                | (int) (color[2] * 255.0F);
        this.getParentModel().renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, argb);
    }

    // ==================== 多段实体：解析到父体 ====================

    private static int visualRootId(LivingEntity entity) {
        Entity current = entity;
        for (int depth = 0; depth < 4; depth++) {
            Entity parent = resolveParent(current);
            if (parent == null || parent == current) break;
            current = parent;
        }
        return current.getId();
    }

    private static Entity resolveParent(Entity entity) {
        Class<?> cls = entity.getClass();
        Method intMethod = PARENT_INT_CACHE.get(cls);
        if (intMethod == null) {
            if (NO_PARENT.contains(cls)) return null;
            intMethod = findMethod(cls, "getParentIntId");
            if (intMethod == null) {
                NO_PARENT.add(cls);
                return null;
            }
            PARENT_INT_CACHE.put(cls, intMethod);
        }
        try {
            Object value = intMethod.invoke(entity);
            if (value instanceof Integer id && id >= 0) {
                return entity.level().getEntity(id);
            }
        } catch (Throwable ignored) {
            // 反射失败按「无父体」处理
        }
        return null;
    }

    private static Method findMethod(Class<?> cls, String name) {
        try {
            Method method = cls.getMethod(name);
            return method.getParameterCount() == 0 ? method : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static float[] parseColor(String raw, float defR, float defG, float defB) {
        if (raw != null) {
            String[] parts = raw.split(":");
            if (parts.length == 3) {
                try {
                    return new float[]{
                            clamp(Float.parseFloat(parts[0].trim())),
                            clamp(Float.parseFloat(parts[1].trim())),
                            clamp(Float.parseFloat(parts[2].trim()))
                    };
                } catch (NumberFormatException ignored) {
                    // 用默认值
                }
            }
        }
        return new float[]{defR, defG, defB};
    }

    private static float clamp(float v) {
        if (v < 0.0F) return 0.0F;
        return Math.min(v, 1.0F);
    }
}