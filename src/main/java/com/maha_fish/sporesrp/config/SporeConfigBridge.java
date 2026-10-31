package com.maha_fish.sporesrp.config;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.SporeSRP;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 在运行时把 Spore 的配置项改成我们想要的值 —— **不需要重启**。
 *
 * <p>背景：原实现（{@code SporeConfigModifier} / {@code SporeDataModifier}）是在模组构造期
 * 直接改写 Spore 的 TOML 文件。但 Spore 在本局启动时已经把配置读进内存，改文件只对**下一局**生效；
 * 而且「等文件出现 → sleep → 读全文 → 整文件写回」有读到半截文件、覆盖别人配置的风险，
 * 还会把主线程阻塞最长 8.5 秒（两处叠加 17 秒）。
 *
 * <p>现在改为直接写 Spore 已加载的 {@link ModConfigSpec}：
 * {@link ModConfigSpec.ConfigValue#set} 的语义就是「直接设置，不触发事件、不写盘，
 * 之后需自行调用 {@code save()}」，且 {@code restartType == NONE} 时会同步缓存，
 * 因此**立即生效**；随后调用 {@code spec.save()} 由 NightConfig 序列化回文件，
 * 这样下次启动也能保持，且不会破坏该文件里的其它键。
 *
 * <p>调用时机必须是 {@link ServerStartedEvent}：Spore 的 {@code sporeconfig.toml} /
 * {@code sporedata.toml} 是 SERVER 类型配置，服务器启动时才加载。
 */
public final class SporeConfigBridge {

    private static final String S_CONFIG_CLASS = "com.Harbinger.Spore.Core.SConfig";

    /** 同一个提示只打一次，避免每次开世界刷屏。 */
    private static final Set<String> WARNED = new HashSet<>();

    private SporeConfigBridge() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        applyAll();
    }

    public static void applyAll() {
        if (!Config.COMMON.sporeConfigHotApply.get()) {
            SporeSRP.LOGGER.info("Spore 配置热更新已关闭（sporeConfigHotApply=false），跳过");
            return;
        }

        Class<?> sConfig;
        try {
            sConfig = Class.forName(S_CONFIG_CLASS);
        } catch (Throwable t) {
            warnOnce("no-spore", "未找到 Spore 的 SConfig，跳过配置热更新（不影响其它功能）");
            return;
        }

        Object serverHolder = staticField(sConfig, "SERVER");
        Object datagenHolder = staticField(sConfig, "DATAGEN");
        Object serverSpec = staticField(sConfig, "SERVER_SPEC");
        Object datagenSpec = staticField(sConfig, "DATAGEN_SPEC");

        int changed = 0;
        boolean serverDirty = false;
        boolean datagenDirty = false;

        // ==================== sporeconfig.toml -> [Mobs.Mound] ====================
        if (isLoaded(serverSpec)) {
            int c = 0;
            c += apply(serverHolder, "mound_range_default", Config.COMMON.sporeMoundRangeDefault.get());
            c += apply(serverHolder, "mound_range_age2", Config.COMMON.sporeMoundRangeAge2.get());
            c += apply(serverHolder, "mound_range_age3", Config.COMMON.sporeMoundRangeAge3.get());
            c += apply(serverHolder, "mound_range_age4", Config.COMMON.sporeMoundRangeAge4.get());
            c += apply(serverHolder, "mound_age", Config.COMMON.sporeMoundAgeingCooldown.get());
            c += apply(serverHolder, "mound_cooldown", Config.COMMON.sporeMoundInfectionCooldown.get());
            c += apply(serverHolder, "verwa_summons", Config.COMMON.sporeVerwaSummons.get());
            changed += c;
            serverDirty = c > 0;
        } else {
            warnOnce("server-spec", "Spore 的 SERVER 配置尚未加载，本次跳过（不影响重启后生效）");
        }

        // ==================== sporedata.toml ====================
        if (isLoaded(datagenSpec)) {
            int c = 0;
            c += apply(datagenHolder, "biomass_lump_kills", Config.COMMON.sporeBiomassLumpKills.get());
            c += apply(datagenHolder, "hive_spawn_kills", Config.COMMON.sporeHiveSpawnKills.get());
            changed += c;
            datagenDirty = c > 0;
        } else {
            warnOnce("datagen-spec", "Spore 的 DATAGEN 配置尚未加载，本次跳过（不影响重启后生效）");
        }

        if (serverDirty) saveSpec(serverSpec);
        if (datagenDirty) saveSpec(datagenSpec);

        if (changed > 0) {
            SporeSRP.LOGGER.info("Spore 配置热更新完成：共改动 {} 项，已写回配置文件", changed);
        } else {
            SporeSRP.LOGGER.debug("Spore 配置热更新：无需改动（已是目标值）");
        }
    }

    /**
     * 只在值不同的时候写入，因此完全幂等：
     * 玩家手改过 Spore 配置会被纠正回来，重复开世界不会反复写盘。
     *
     * @return 实际改动数（0 或 1）
     */
    @SuppressWarnings("unchecked")
    private static int apply(Object holder, String fieldName, Object target) {
        if (holder == null || target == null) return 0;
        try {
            Object raw = holder.getClass().getField(fieldName).get(holder);
            if (!(raw instanceof ModConfigSpec.ConfigValue<?> configValue)) return 0;

            Object current = configValue.get();
            if (current != null && current.equals(target)) return 0;

            // 类型必须严格匹配：把 Integer 塞进 Double 的 ConfigValue 会在后续 get() 时炸掉
            ((ModConfigSpec.ConfigValue<Object>) configValue).set(target);
            SporeSRP.LOGGER.info("Spore 配置 {}: {} -> {}", fieldName, current, target);
            return 1;
        } catch (NoSuchFieldException e) {
            warnOnce("missing:" + fieldName,
                    "Spore 配置项 " + fieldName + " 不存在（可能版本变更），已跳过该项");
            return 0;
        } catch (Throwable t) {
            warnOnce("fail:" + fieldName,
                    "设置 Spore 配置项 " + fieldName + " 失败：" + t);
            return 0;
        }
    }

    private static boolean isLoaded(Object spec) {
        if (!(spec instanceof ModConfigSpec modSpec)) return false;
        try {
            return modSpec.isLoaded();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void saveSpec(Object spec) {
        if (!(spec instanceof ModConfigSpec modSpec)) return;
        try {
            modSpec.save();
        } catch (Throwable t) {
            warnOnce("save", "写回 Spore 配置文件失败（本次改动仍已在本局生效）：" + t);
        }
    }

    private static Object staticField(Class<?> owner, String name) {
        try {
            return owner.getField(name).get(null);
        } catch (Throwable t) {
            warnOnce("sf:" + name, "无法读取 Spore 的 SConfig." + name + "：" + t);
            return null;
        }
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            SporeSRP.LOGGER.warn(message);
        }
    }
}
