package com.maha_fish.sporesrp.util;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 读取 Spore 内部状态的统一入口（**只读**）。
 *
 * <p>本模组刻意不依赖 Spore 的编译期 API，所以这里用反射访问少量稳定的内部成员；
 * 任何一项失败都只降级 + 打一次日志，绝不影响游戏运行。
 *
 * <p>已反编译确认的访问点：
 * <ul>
 *   <li>{@code com.Harbinger.Spore.Sentities.Organoids.Mound#getAge()} —— 菌囊成长阶段 1..4，
 *       是 mod 自定义方法名（非 SRG），因此反射稳定</li>
 *   <li>{@code Mound#getLinked()} —— 是否被蜂巢链接</li>
 *   <li>{@code SConfig.SERVER.mound_range_default / _age2 / _age3 / _age4} —— 各阶段的感染半径（Double）</li>
 * </ul>
 *
 * <p>注意：这里读的是 Spore **运行生效值**（可能已被 {@code SporeConfigBridge} 热更新过），
 * 不是我方配置或出厂默认值。这样写盘是否成功都不会造成双边不一致。
 */
public final class SporeBridge {

    private static final String MOUND_CLASS = "com.Harbinger.Spore.Sentities.Organoids.Mound";
    private static final String S_CONFIG_CLASS = "com.Harbinger.Spore.Core.SConfig";

    private static final Set<String> WARNED = new HashSet<>();

    private static boolean moundMethodsResolved;
    private static Method getAgeMethod;
    private static Method getLinkedMethod;

    private static boolean serverHolderResolved;
    private static Object serverHolder;

    private SporeBridge() {
    }

    // ==================== 菌囊阶段 ====================

    /**
     * @return 菌囊成长阶段 1..4；无法读取时返回 {@code -1}（调用方应保守处理）
     */
    public static int getMoundAge(LivingEntity mound) {
        if (mound == null) return -1;
        Method m = getAgeMethod();
        if (m == null) return -1;
        try {
            return (Integer) m.invoke(mound);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 是否被蜂巢思维链接；读不到时退化为检查 Spore 写在 persistentData 里的 {@code hivemind} 标记。 */
    public static boolean isMoundLinked(LivingEntity mound) {
        if (mound == null) return false;
        Method m = getLinkedMethod();
        if (m != null) {
            try {
                return (Boolean) m.invoke(mound);
            } catch (Throwable ignored) {
                // 落到 persistentData 兜底
            }
        }
        try {
            return mound.getPersistentData().contains("hivemind");
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 菌囊感染半径（Spore 生效值） ====================

    /**
     * 取 Spore 对应该阶段的感染半径。
     *
     * @param age 1..4；其它值按 1 阶处理
     */
    public static double getMoundRange(int age) {
        String field = switch (age) {
            case 2 -> "mound_range_age2";
            case 3 -> "mound_range_age3";
            case 4 -> "mound_range_age4";
            default -> "mound_range_default";
        };
        Object live = configValue(field);
        if (live instanceof Number n) return n.doubleValue();

        return switch (age) {
            case 2 -> Config.COMMON.sporeMoundRangeAge2.get();
            case 3 -> Config.COMMON.sporeMoundRangeAge3.get();
            case 4 -> Config.COMMON.sporeMoundRangeAge4.get();
            default -> Config.COMMON.sporeMoundRangeDefault.get();
        };
    }

    /** 全服类器官数量上限（Spore 生效值，默认 40）。 */
    // 注意：菌囊与 Proto 本身不参与「类器官过多 → 钻地消失」的判定，所以菌囊生成不参考它。

    // ==================== 内部 ====================

    private static Method getAgeMethod() {
        resolveMoundMethods();
        return getAgeMethod;
    }

    private static Method getLinkedMethod() {
        resolveMoundMethods();
        return getLinkedMethod;
    }

    private static void resolveMoundMethods() {
        if (moundMethodsResolved) return;
        moundMethodsResolved = true;
        try {
            Class<?> c = Class.forName(MOUND_CLASS);
            getAgeMethod = c.getMethod("getAge");
            getLinkedMethod = c.getMethod("getLinked");
        } catch (Throwable t) {
            warnOnce("mound-methods",
                    "读取不到 Spore 菌囊的 getAge/getLinked，菌囊半径与链接判定将退化为配置值：" + t);
        }
    }

    private static Object configValue(String fieldName) {
        Object holder = serverHolder();
        if (holder == null) return null;
        try {
            Object raw = holder.getClass().getField(fieldName).get(holder);
            // 两端（ForgeConfigSpec.ConfigValue / ModConfigSpec.ConfigValue）都实现了 Supplier
            if (raw instanceof Supplier<?> supplier) return supplier.get();
        } catch (Throwable t) {
            warnOnce("cv:" + fieldName, "读取 Spore 配置项 " + fieldName + " 失败：" + t);
        }
        return null;
    }

    private static Object serverHolder() {
        if (serverHolderResolved) return serverHolder;
        serverHolderResolved = true;
        try {
            Class<?> c = Class.forName(S_CONFIG_CLASS);
            serverHolder = c.getField("SERVER").get(null);
        } catch (Throwable t) {
            warnOnce("holder", "读取不到 Spore 的 SConfig.SERVER，将使用本模组配置作为回退：" + t);
        }
        return serverHolder;
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            SporeSRP.LOGGER.warn(message);
        }
    }
}
