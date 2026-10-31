package com.maha_fish.sporesrp.handler;

import com.maha_fish.sporesrp.SporeSRP;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 净化世界的孢子生物管控。
 *
 * <p>原实现是在 {@code EntityJoinLevelEvent} 里对**每一个**加入世界的实体都调
 * {@code WorldEvolutionData.get(level).isPurified()} —— 而 {@code get()} 内部还会跑
 * {@code updatePhase()}，等于每只生物入场都做一次配置读取 + 阶段判定，代价不划算。
 *
 * <p>现在改为：
 * <ul>
 *   <li>净化成功的那一刻**一次性清扫**该维度已存在的孢子生物；</li>
 *   <li>之后用**每维度缓存的布尔值**做快速判断（一次 ConcurrentHashMap 查表）；</li>
 *   <li>刷怪入口再加一道 {@code FinalizeSpawn} 拦截，尽量不让新的孢子生物落地。</li>
 * </ul>
 */
public final class PurifiedWorldHandler {

    private static final Map<ResourceKey<Level>, Boolean> PURIFIED = new ConcurrentHashMap<>();

    private PurifiedWorldHandler() {
    }

    /** 该维度是否已净化。首次访问才真正读 SavedData，之后走缓存。 */
    public static boolean isPurified(ServerLevel level) {
        Boolean cached = PURIFIED.get(level.dimension());
        if (cached != null) return cached;

        boolean value = WorldEvolutionData.get(level).isPurified();
        PURIFIED.put(level.dimension(), value);
        return value;
    }

    /** 净化状态被改动时调用：写数据、刷新缓存，若变为已净化则立刻清扫一次。 */
    public static void applyPurification(ServerLevel level, boolean purified) {
        WorldEvolutionData data = WorldEvolutionData.get(level);
        data.setPurified(purified);
        PURIFIED.put(level.dimension(), purified);

        if (purified) {
            purge(level);
        }
    }

    /** 一次性清除该维度所有孢子生物，并同步清掉我方实体缓存。 */
    public static int purge(ServerLevel level) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof LivingEntity living && SporeEntityHelper.isSporeEntity(living)) {
                targets.add(living);
            }
        }
        for (LivingEntity living : targets) {
            living.discard();
        }

        SporeEntityCache.clearCache();
        SporeSRP.LOGGER.info("净化完成：已清除 {} 只孢子生物（维度 {}）",
                targets.size(), level.dimension().location());
        return targets.size();
    }

    /** 服务器停止时清空缓存，避免换存档后沿用上一世界的净化状态。 */
    public static void clear() {
        PURIFIED.clear();
    }

    // ==================== 事件 ====================

    /** 刷怪入口拦截：已净化的世界里直接取消孢子生物的生成。 */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // getEntity() 已经是 Mob，无需再判类型
        if (!SporeEntityHelper.isSporeEntity(event.getEntity())) return;
        if (!isPurified(level)) return;
        event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }
}
