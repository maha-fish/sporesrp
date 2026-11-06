package com.maha_fish.sporesrp.client;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.network.AdaptationFlashPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伤害适应的全身体色反馈（纯客户端渲染）。
 *
 * <p>做法：在 {@code RenderLivingEvent.Pre} 设置着色器调制色，{@code Post} 复位。
 * 这样实体本体、贴图层、装备都会被同一颜色调制，效果等同「全身变色」，
 * 且**不需要 mixin**、不需要访问 Spore 的渲染器内部。
 *
 * <p><b>多段实体</b>：Spore 的环节（噬袭魔环节、利维坦环节、各类断肢）是**独立实体**，
 * 各自触发渲染事件。这里把它们解析回父体，复用父体的闪光状态，因此所有环节同时变色。
 * 只对「独立实体型」环节有效；{@code PartEntity} 型的部件由父体渲染器内部绘制，天然跟随。
 */
public class AdaptationFlashRenderer {

    /** 父体 ID 解析方法缓存（按实体类）。 */
    private static final Map<Class<?>, Method> PARENT_INT_CACHE = new ConcurrentHashMap<>();
    private static final Set<Class<?>> NO_PARENT = ConcurrentHashMap.newKeySet();

    /** 当前是否处于我们设置的着色状态（避免误复位其它模组的颜色）。 */
    private static final ThreadLocal<Boolean> TINTED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private AdaptationFlashRenderer() {
    }

    // ==================== 渲染钩子 ====================

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        TINTED.set(Boolean.FALSE);
        if (!Config.COMMON.adaptationVisualEnabled.get()) return;

        LivingEntity entity = event.getEntity();
        int kind = AdaptationFlashPacket.ClientFlashState.kindOf(visualRootId(entity));
        if (kind == 0) return;

        float[] color = kind == AdaptationFlashPacket.KIND_FULL
                ? parseColor(Config.COMMON.adaptationFullColor.get(), 0.72F, 0.30F, 1.0F)
                : parseColor(Config.COMMON.adaptationAdaptingColor.get(), 0.25F, 1.00F, 0.35F);

        float alpha = Config.COMMON.adaptationFlashAlpha.get().floatValue();
        RenderSystem.setShaderColor(color[0], color[1], color[2], alpha);
        TINTED.set(Boolean.TRUE);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (!Boolean.TRUE.equals(TINTED.get())) return;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        TINTED.set(Boolean.FALSE);
    }

    // ==================== 视觉根解析（多段实体） ====================

    /** 把环节实体解析回它所属的父体 ID；解析不到则返回自身。 */
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
                NO_PARENT.add(cls); // 该类没有可用的父体 ID，后续直接走自身
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
            if (method.getParameterCount() != 0) return null;
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    /** 解析 "r:g:b" 形式的颜色配置；格式错误回退到默认值。 */
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
        if (v > 2.0F) return 2.0F;
        return v;
    }

    /** 退出世界时清理（由网络包状态处统一调用即可，这里留一个入口）。 */
    public static void clear() {
        AdaptationFlashPacket.ClientFlashState.clear();
        TINTED.set(Boolean.FALSE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

}
