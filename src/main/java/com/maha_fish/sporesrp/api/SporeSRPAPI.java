package com.maha_fish.sporesrp.api;

import com.maha_fish.sporesrp.Config;
import com.maha_fish.sporesrp.data.DifficultyManager;
import com.maha_fish.sporesrp.data.WorldEvolutionData;
import com.maha_fish.sporesrp.handler.*;
import com.maha_fish.sporesrp.util.SporeEntityCache;
import com.maha_fish.sporesrp.util.SporeEntityHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * SporeSRP 模组公共 API
 * <p>
 * 供其他模组调用，获取孢子系统状态、操作演化数据等。
 * 所有方法均为线程安全的静态方法。
 *
 * @author SporeSRP Team
 * @version 1.0
 */
public final class SporeSRPAPI {

    private SporeSRPAPI() {} // 禁止实例化

    // ============================================================
    //                     演化系统 (Evolution)
    // ============================================================

    /**
     * 获取指定世界的演化点数
     *
     * @param level 服务器世界 (必须是 ServerLevel)
     * @return 当前演化点数，如果世界无效则返回 0
     */
    public static int getEvolutionPoints(ServerLevel level) {
        if (level == null) return 0;
        return WorldEvolutionData.get(level).getEvolutionPoints();
    }

    /**
     * 获取指定世界的演化阶段 (0-10)
     *
     * @param level 服务器世界
     * @return 当前演化阶段，如果世界无效则返回 0
     */
    public static int getEvolutionPhase(ServerLevel level) {
        if (level == null) return 0;
        return WorldEvolutionData.get(level).getEvolutionPhase();
    }

    /**
     * 添加演化点数（触发阶段升级检查）
     *
     * @param level  服务器世界
     * @param amount 要添加的点数（正数增加，负数减少）
     * @return 添加后的总点数，如果世界无效则返回 -1
     */
    public static int addEvolutionPoints(ServerLevel level, int amount) {
        if (level == null) return -1;
        WorldEvolutionData data = WorldEvolutionData.get(level);
        long currentTick = level.getServer().getTickCount();
        data.addEvolutionPoints(amount, currentTick);
        data.syncToClients(level);
        return data.getEvolutionPoints();
    }

    /**
     * 强制设置演化点数（会触发阶段升级检查）
     *
     * @param level  服务器世界
     * @param points 目标点数（不能为负数）
     * @return 设置后的点数，如果世界无效则返回 -1
     */
    public static int setEvolutionPoints(ServerLevel level, int points) {
        if (level == null) return -1;
        WorldEvolutionData data = WorldEvolutionData.get(level);
        long currentTick = level.getServer().getTickCount();
        data.setEvolutionPoints(points, currentTick);
        data.syncToClients(level);
        return data.getEvolutionPoints();
    }

    /**
     * 强制设置演化阶段（会同步调整点数到该阶段阈值）
     *
     * @param level 服务器世界
     * @param phase 目标阶段 (0-10)
     * @return 是否成功
     */
    public static boolean setEvolutionPhase(ServerLevel level, int phase) {
        if (level == null) return false;
        int maxPhase = Config.COMMON.getMaxPhase();
        if (phase < 0 || phase > maxPhase) return false;
        WorldEvolutionData data = WorldEvolutionData.get(level);
        long currentTick = level.getServer().getTickCount();
        data.forceSetPhase(phase, currentTick);
        data.syncToClients(level);
        return true;
    }

    /**
     * 获取当前难度等级
     *
     * @param level 服务器世界
     * @return 难度枚举值，如果世界无效则返回 SIMPLE
     */
    public static DifficultyManager.Difficulty getDifficulty(ServerLevel level) {
        if (level == null) return DifficultyManager.Difficulty.SIMPLE;
        return WorldEvolutionData.get(level).getDifficulty();
    }

    /**
     * 设置难度等级
     *
     * @param level      服务器世界
     * @param difficulty 目标难度
     * @return 是否成功
     */
    public static boolean setDifficulty(ServerLevel level, DifficultyManager.Difficulty difficulty) {
        if (level == null || difficulty == null) return false;
        WorldEvolutionData data = WorldEvolutionData.get(level);
        if (data.getDifficulty() == difficulty) return true;
        data.setDifficulty(difficulty);
        data.syncToClients(level);
        // 刷新所有孢子生物属性
        com.maha_fish.sporesrp.phase.PhaseEffects.refreshAllInfectedAttributes(level);
        return true;
    }

    // ============================================================
    //                     净化状态 (Purification)
    // ============================================================

    /**
     * 检查世界是否已被净化
     *
     * @param level 服务器世界
     * @return true 表示世界已净化，孢子系统已停用
     */
    public static boolean isWorldPurified(ServerLevel level) {
        if (level == null) return false;
        return WorldEvolutionData.get(level).isPurified();
    }

    // ============================================================
    //                     孢子实体判断 (Spore Entity)
    // ============================================================

    /**
     * 判断一个实体是否为孢子生物（命名空间为 "spore"）
     *
     * @param entity 要检查的实体
     * @return true 表示是孢子生物
     */
    public static boolean isSporeEntity(LivingEntity entity) {
        if (entity == null) return false;
        return SporeEntityHelper.isSporeEntity(entity);
    }

    /**
     * 获取孢子生物的等级 (0=Small, 1=Medium, 2=Large, 3=Boss)
     *
     * @param entity 孢子生物实体
     * @return 等级 (0-3)，非孢子生物返回 0
     */
    public static int getInfectedLevel(LivingEntity entity) {
        if (entity == null) return 0;
        return SporeEntityHelper.getInfectedLevel(entity);
    }

    // ============================================================
    //                     完整心智 (Full Hivemind)
    // ============================================================

    /**
     * 判断一个实体是否为完整心智
     *
     * @param entity 要检查的实体
     * @return true 表示是完整心智
     */
    public static boolean isFullHivemind(LivingEntity entity) {
        if (entity == null) return false;
        return FullHivemindHandler.isFullHivemind(entity);
    }

    /**
     * 获取当前世界的完整心智 UUID
     *
     * @param level 服务器世界
     * @return 完整心智的 UUID，如果不存在则返回 null
     */
    @Nullable
    public static UUID getFullHivemindUUID(ServerLevel level) {
        if (level == null) return null;
        return WorldEvolutionData.get(level).getFullHivemindUuid();
    }

    // ============================================================
    //                     菌囊系统 (Mound)
    // ============================================================

    /**
     * 获取菌囊的等级 (1-4)
     *
     * @param mound 菌囊实体
     * @return 等级 (1-4)，非菌囊或无效实体返回 1
     */
    public static int getMoundLevel(LivingEntity mound) {
        if (mound == null) return 1;
        return MoundLevelSystem.getLevel(mound.getUUID());
    }

    /**
     * 设置菌囊的等级 (1-4)
     *
     * @param mound 菌囊实体
     * @param level 目标等级 (1-4)
     * @return 是否成功
     */
    public static boolean setMoundLevel(LivingEntity mound, int level) {
        if (mound == null) return false;
        if (level < 1 || level > 4) return false;
        MoundLevelSystem.setLevel(mound.getUUID(), level);
        // 同步 NBT
        if (mound.level() instanceof ServerLevel serverLevel) {
            MoundLevelSystem.writeLevelToNBT(mound.getUUID(), level);
        }
        return true;
    }

    // ============================================================
    //                     Builder 系统
    // ============================================================

    /**
     * 判断一个实体是否为 Builder（Gastgaber 变种）
     *
     * @param entity 要检查的实体
     * @return true 表示是 Builder
     */
    public static boolean isBuilder(LivingEntity entity) {
        if (entity == null) return false;
        return GastgaberBuilderHandler.isBuilder(entity);
    }

    /**
     * 将 Gastgaber 标记为 Builder
     *
     * @param entity 必须是 Gastgaber 实体
     * @return 是否成功
     */
    public static boolean markAsBuilder(LivingEntity entity) {
        if (entity == null) return false;
        if (!isSporeEntity(entity)) return false;
        GastgaberBuilderHandler.markAsBuilder(entity);
        return true;
    }

    // ============================================================
    //                     缓存查询 (Cache)
    // ============================================================

    /**
     * 获取指定维度所有存活的 Proto 实体列表
     *
     * @param level 服务器世界
     * @return 不可变列表，可能为空
     */
    public static List<LivingEntity> getAliveProtos(ServerLevel level) {
        if (level == null) return List.of();
        return SporeEntityCache.getAliveProtos(level);
    }

    /**
     * 获取指定维度所有存活的 Mound 实体列表
     *
     * @param level 服务器世界
     * @return 不可变列表，可能为空
     */
    public static List<LivingEntity> getAliveMounds(ServerLevel level) {
        if (level == null) return List.of();
        return SporeEntityCache.getAliveMounds(level);
    }

    /**
     * 获取指定维度所有存活的 LargePool 生物列表
     *
     * @param level 服务器世界
     * @return 不可变列表，可能为空
     */
    public static List<LivingEntity> getAliveLargePools(ServerLevel level) {
        if (level == null) return List.of();
        return SporeEntityCache.getAliveLargePools(level);
    }

    /**
     * 获取指定维度所有存活的 Boss 生物列表
     *
     * @param level 服务器世界
     * @return 不可变列表，可能为空
     */
    public static List<LivingEntity> getAliveBosses(ServerLevel level) {
        if (level == null) return List.of();
        return SporeEntityCache.getAliveBosses(level);
    }

    /**
     * 获取指定维度所有存活的 Builder 列表
     *
     * @param level 服务器世界
     * @return 不可变列表，可能为空
     */
    public static List<LivingEntity> getAliveBuilders(ServerLevel level) {
        if (level == null) return List.of();
        return SporeEntityCache.getAliveBuilders(level);
    }

    // ============================================================
    //                     配置查询 (Config)
    // ============================================================

    /**
     * 获取最大演化阶段数 (由配置决定)
     *
     * @return 最大阶段数
     */
    public static int getMaxPhase() {
        return Config.COMMON.getMaxPhase();
    }

    /**
     * 获取指定阶段的点数阈值
     *
     * @param phase 阶段 (0 到 getMaxPhase())
     * @return 该阶段所需的点数阈值，如果阶段无效返回 -1
     */
    public static int getPhaseThreshold(int phase) {
        var thresholds = Config.COMMON.phaseThresholds.get();
        if (phase < 0 || phase >= thresholds.size()) return -1;
        return (Integer) thresholds.get(phase);
    }
}