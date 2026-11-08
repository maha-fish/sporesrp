package com.maha_fish.sporesrp;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class Config {
    public static class Common {
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> phaseThresholds;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> healthMultipliers;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> attackMultipliers;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> speedBonuses;
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> armorBonuses;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> knockbackResist;
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> phaseUpgradeCooldownSeconds;

        // 适应性系统配置
        public final ModConfigSpec.BooleanValue enableAdaptation;
        public final ModConfigSpec.ConfigValue<List<? extends String>> adaptationTiers;
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> adaptationMaxTypes;
        public final ModConfigSpec.DoubleValue adaptationPhaseGrowth;
        public final ModConfigSpec.DoubleValue adaptationMaxTotalReduction;

        // ========== 伤害适应的视觉反馈 ==========
        public final ModConfigSpec.BooleanValue adaptationVisualEnabled;
        public final ModConfigSpec.IntValue adaptationFlashDurationTicks;
        public final ModConfigSpec.ConfigValue<String> adaptationAdaptingColor;
        public final ModConfigSpec.ConfigValue<String> adaptationFullColor;
        public final ModConfigSpec.DoubleValue adaptationFlashAlpha;
        public final ModConfigSpec.BooleanValue adaptationFlashParticles;
        public final ModConfigSpec.IntValue adaptationFlashParticleCount;
        public final ModConfigSpec.BooleanValue adaptationFullSound;

        // 支援系统配置
        public final ModConfigSpec.BooleanValue enableSupportSystem;
        public final ModConfigSpec.DoubleValue supportBaseChance;
        public final ModConfigSpec.DoubleValue supportPhaseIncrement;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> supportLevelMultipliers;
        public final ModConfigSpec.DoubleValue supportMoundChance;
        public final ModConfigSpec.DoubleValue supportSameLevelChance;
        public final ModConfigSpec.DoubleValue supportSecondLowerChance;
        public final ModConfigSpec.DoubleValue supportScentChance;
        public final ModConfigSpec.IntValue supportCooldownSeconds;
        public final ModConfigSpec.DoubleValue supportThrowHorizontal;
        public final ModConfigSpec.DoubleValue supportThrowUpward;
        public final ModConfigSpec.IntValue supportSummonedNoAggroSeconds;
        public final ModConfigSpec.IntValue supportSummonedLifetimeSeconds;
        public final ModConfigSpec.DoubleValue supportMinSpawnDistance;
        public final ModConfigSpec.DoubleValue supportMaxSpawnDistance;

        // ========== 生物等级（按 Spore 继承体系推导，不再维护刷怪名单） ==========
        public final ModConfigSpec.ConfigValue<List<? extends String>> sporeTierOrganoid;
        public final ModConfigSpec.ConfigValue<List<? extends String>> sporeTierUtility;
        public final ModConfigSpec.ConfigValue<List<? extends String>> sporeTierExtra;
        public final ModConfigSpec.ConfigValue<List<? extends String>> sporeTierExclude;

        // 转化系统配置
        public final ModConfigSpec.DoubleValue conversionSmallMaxHp;
        public final ModConfigSpec.DoubleValue conversionMediumMaxHp;
        public final ModConfigSpec.DoubleValue conversionLargeMaxHp;
        public final ModConfigSpec.DoubleValue conversionHpThresholdFactor;
        public final ModConfigSpec.DoubleValue conversionBaseFactor;
        public final ModConfigSpec.DoubleValue conversionMaxChance;
        public final ModConfigSpec.IntValue conversionMaxAttempts;
        public final ModConfigSpec.BooleanValue enableConversion;

        // 生态系统转化配置
        public final ModConfigSpec.BooleanValue enableEcosystemConversion;
        public final ModConfigSpec.IntValue conversionIntervalTicks;
        public final ModConfigSpec.IntValue conversionBaseRadius;
        public final ModConfigSpec.IntValue conversionRadiusPerPhase;
        public final ModConfigSpec.DoubleValue conversionChance;
        public final ModConfigSpec.IntValue maxBlocksPerBiome;
        public final ModConfigSpec.IntValue globalLimitPerTick;
        public final ModConfigSpec.ConfigValue<List<? extends String>> conversionMappings;

        // 菌囊系统配置
        public final ModConfigSpec.BooleanValue enableMoundSystem;
        public final ModConfigSpec.IntValue moundSpawnIntervalTicks;
        public final ModConfigSpec.DoubleValue moundSpawnBaseChance;
        public final ModConfigSpec.DoubleValue moundSpawnPhaseBonus;
        public final ModConfigSpec.IntValue moundSpawnRadius;
        public final ModConfigSpec.IntValue moundSpawnMaxPerTick;

        public final ModConfigSpec.BooleanValue enableTracking;
        public final ModConfigSpec.IntValue maxMarkerAmplifier;

        // 感染群系配置
        public final ModConfigSpec.BooleanValue enableInfectedBiome;
        public final ModConfigSpec.IntValue protoInitialRadius;
        public final ModConfigSpec.IntValue protoRadiusIncrement;
        public final ModConfigSpec.LongValue protoIncreaseIntervalMinutes;
        public final ModConfigSpec.IntValue biomeVerticalHeight;
        public final ModConfigSpec.IntValue maxCommandsPerTick;

        // 演化点数获取/减少配置
        public final ModConfigSpec.DoubleValue killPointMultiplier;
        public final ModConfigSpec.DoubleValue protoPeriodicPoints;
        public final ModConfigSpec.DoubleValue moundPeriodicPoints;
        public final ModConfigSpec.DoubleValue smallDeathPenalty;
        public final ModConfigSpec.DoubleValue mediumDeathPenalty;
        public final ModConfigSpec.DoubleValue largeDeathPenalty;
        public final ModConfigSpec.DoubleValue bossDeathPenalty;
        public final ModConfigSpec.DoubleValue protoDeathPenalty;
        public final ModConfigSpec.DoubleValue moundDeathPenalty;
        public final ModConfigSpec.DoubleValue biomeConvertChunkPoints;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> phasePointMultipliers;
        public final ModConfigSpec.BooleanValue eternalNightAfterPhase6;
        public final ModConfigSpec.BooleanValue preventSleep;
        public final ModConfigSpec.ConfigValue<List<? extends String>> spawnTiersByPhase;
        public final ModConfigSpec.IntValue nonSporeSpawnBlockPhase;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> spawnRateByPhase;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> spawnExtraByPhase;
        public final ModConfigSpec.IntValue sunlightIgnorePhase;
        public final ModConfigSpec.ConfigValue<List<? extends String>> moundSupportConfigs;
        public final ModConfigSpec.IntValue moundScanIntervalTicks;
        public final ModConfigSpec.ConfigValue<List<? extends Integer>> moundMinLevelByPhase;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> moundLevelPointMultipliers;

        public final ModConfigSpec.BooleanValue protoMarkedMoundEnabled;
        public final ModConfigSpec.IntValue protoMarkedMoundCheckIntervalTicks;
        public final ModConfigSpec.IntValue protoMarkedMoundMoveIntervalTicks;
        public final ModConfigSpec.IntValue protoMarkedMoundTargetCount;
        public final ModConfigSpec.IntValue protoMarkedMoundMinDistance;
        public final ModConfigSpec.IntValue protoMarkedMoundMaxDistance;

        public final ModConfigSpec.BooleanValue protoSkillsEnabled;
        public final ModConfigSpec.IntValue protoSkill1CooldownTicks;
        public final ModConfigSpec.IntValue protoSkill1MoveMinDist;
        public final ModConfigSpec.IntValue protoSkill1MoveMaxDist;
        public final ModConfigSpec.IntValue protoSkill2CheckIntervalTicks;
        public final ModConfigSpec.IntValue protoSkill2CooldownTicks;
        public final ModConfigSpec.IntValue protoSkill2TargetCount;
        public final ModConfigSpec.IntValue protoSkill2SpawnRange;
        public final ModConfigSpec.IntValue protoSkill3CooldownTicks;
        public final ModConfigSpec.IntValue protoSkill3SpawnCountMin;
        public final ModConfigSpec.IntValue protoSkill3SpawnCountMax;
        public final ModConfigSpec.IntValue protoSkillLargePoolThreshold;
        public final ModConfigSpec.IntValue protoSkill4CooldownTicks;
        public final ModConfigSpec.IntValue protoSkill4SpawnRange;
        public final ModConfigSpec.IntValue protoSkill4CheckRange;
        public final ModConfigSpec.IntValue protoSkill5CooldownTicks;
        public final ModConfigSpec.IntValue protoSkill5SpawnRange;
        public final ModConfigSpec.IntValue protoSkill5BossCheckRange;
        public final ModConfigSpec.IntValue protoSkill5SpawnCount;

        public final ModConfigSpec.BooleanValue protoResurrectionEnabled;
        public final ModConfigSpec.IntValue protoResurrectionCheckRadius;
        public final ModConfigSpec.IntValue protoResurrectionLargePoolThreshold;
        public final ModConfigSpec.DoubleValue protoResurrectionHealthPercent;
        public final ModConfigSpec.IntValue protoResurrectionResistanceDuration;
        public final ModConfigSpec.IntValue protoResurrectionResistanceAmplifier;
        public final ModConfigSpec.DoubleValue protoResurrectionMoveSpeedMultiplier;
        public final ModConfigSpec.DoubleValue protoResurrectionPushRadius;
        public final ModConfigSpec.DoubleValue protoResurrectionPushStrength;
        public final ModConfigSpec.ConfigValue<String> protoResurrectionSoundId;

        // Gastgaber Builder
        public final ModConfigSpec.DoubleValue gastgaberBuilderSpawnChance;
        public final ModConfigSpec.DoubleValue gastgaberBuilderCollisionRadius;
        public final ModConfigSpec.IntValue gastgaberBuilderMaxPoints;
        public final ModConfigSpec.IntValue gastgaberBuilderSmallPoints;
        public final ModConfigSpec.IntValue gastgaberBuilderMediumPoints;
        public final ModConfigSpec.IntValue gastgaberBuilderLargePoints;
        public final ModConfigSpec.IntValue gastgaberBuilderCallInterval;
        public final ModConfigSpec.IntValue gastgaberBuilderCallRadius;
        public final ModConfigSpec.IntValue gastgaberBuilderCooldownSeconds;
        public final ModConfigSpec.ConfigValue<List<? extends String>> gastgaberBuilderStructures;

        // 完整心智配置
        public final ModConfigSpec.IntValue fullHivemindMinProtoCount;
        public final ModConfigSpec.IntValue fullHivemindMinPhase;
        public final ModConfigSpec.DoubleValue fullHivemindHealthMultiplier;
        public final ModConfigSpec.IntValue fullHivemindArmorBonus;

        public final ModConfigSpec.IntValue fullHivemindSkill1Cooldown;
        public final ModConfigSpec.IntValue fullHivemindSkill1MoveMinDist;
        public final ModConfigSpec.IntValue fullHivemindSkill1MoveMaxDist;
        public final ModConfigSpec.IntValue fullHivemindSkill2CheckInterval;
        public final ModConfigSpec.IntValue fullHivemindSkill2Cooldown;
        public final ModConfigSpec.IntValue fullHivemindSkill2TargetCount;
        public final ModConfigSpec.IntValue fullHivemindSkill2SpawnRange;
        public final ModConfigSpec.IntValue fullHivemindSkill3Cooldown;
        public final ModConfigSpec.IntValue fullHivemindSkill3SpawnCountMin;
        public final ModConfigSpec.IntValue fullHivemindSkill3SpawnCountMax;
        public final ModConfigSpec.IntValue fullHivemindSkillLargePoolThreshold;
        public final ModConfigSpec.IntValue fullHivemindSkill4Cooldown;
        public final ModConfigSpec.IntValue fullHivemindSkill4SpawnRange;
        public final ModConfigSpec.IntValue fullHivemindSkill4CheckRange;
        public final ModConfigSpec.IntValue fullHivemindSkill5Cooldown;
        public final ModConfigSpec.IntValue fullHivemindSkill5SpawnRange;
        public final ModConfigSpec.IntValue fullHivemindSkill5BossCheckRange;
        public final ModConfigSpec.IntValue fullHivemindSkill5SpawnCount;

        // 完整心智袭击
        public final ModConfigSpec.DoubleValue assaultValueIncrementPerTick;
        public final ModConfigSpec.IntValue assaultValueIncrementInterval;
        public final ModConfigSpec.DoubleValue assaultSmallPoolKillPoints;
        public final ModConfigSpec.DoubleValue assaultMediumPoolKillPoints;
        public final ModConfigSpec.DoubleValue assaultLargePoolKillPoints;
        public final ModConfigSpec.DoubleValue assaultBossPoolKillPoints;
        public final ModConfigSpec.IntValue assaultTriggerCheckInterval;
        public final ModConfigSpec.IntValue assaultDistanceThreshold;
        public final ModConfigSpec.IntValue assaultMaxDuration;
        public final ModConfigSpec.IntValue assaultCooldownSeconds;

        // 世界净化之战
        public final ModConfigSpec.BooleanValue enablePurificationBattle;
        public final ModConfigSpec.IntValue playerCheckRadius;

        // ---- 净化之战：天气与闪电 ----
        public final ModConfigSpec.BooleanValue battleThunderstorm;
        public final ModConfigSpec.BooleanValue battleLightningEnabled;
        public final ModConfigSpec.IntValue battleLightningIntervalTicks;
        public final ModConfigSpec.IntValue battleLightningRadiusMin;
        public final ModConfigSpec.IntValue battleLightningRadiusMax;
        public final ModConfigSpec.IntValue battleLightningSafeRadius;

        // ---- 净化之战：开启高度 ----
        public final ModConfigSpec.IntValue battleMinY;

        // ---- 净化之战：刷怪锚点 ----
        public final ModConfigSpec.IntValue spawnAnchorCount;
        public final ModConfigSpec.IntValue spawnRingMinRadius;
        public final ModConfigSpec.IntValue spawnRingMaxRadius;
        public final ModConfigSpec.IntValue spawnMinCoreDistance;
        public final ModConfigSpec.IntValue spawnAttemptsPerMob;
        public final ModConfigSpec.IntValue spawnJitter;
        public final ModConfigSpec.IntValue spawnMaxSurfaceDelta;
        public final ModConfigSpec.IntValue spawnRequireAirAbove;
        public final ModConfigSpec.BooleanValue autoPlatformSpawns;
        public final ModConfigSpec.IntValue spawnPadCount;
        public final ModConfigSpec.IntValue spawnPadRadius;
        public final ModConfigSpec.BooleanValue restoreSpawnPads;
        public final ModConfigSpec.ConfigValue<String> spawnPadBlock;

        // ---- 净化之战：阶段1 菌囊环 ----
        public final ModConfigSpec.IntValue moundRingCount;
        public final ModConfigSpec.IntValue moundRingRadius;

        // ---- 净化之战：阶段2 飞行池 ----
        public final ModConfigSpec.ConfigValue<List<? extends String>> phase2FlyingPool;
        public final ModConfigSpec.IntValue phase2HinderburgPerWave;

        // ---- 净化之战：阶段4 灾厄 ----
        public final ModConfigSpec.IntValue calamityWaveIntervalTicks;
        public final ModConfigSpec.IntValue calamityPerWaveMin;
        public final ModConfigSpec.IntValue calamityPerWaveMax;

        public final ModConfigSpec.IntValue hudX;
        public final ModConfigSpec.IntValue hudY;

        // ========== 写入 Spore 配置的目标值（运行时热更新，免重启） ==========
        // 数值保持与原 SporeConfigModifier / SporeDataModifier 完全一致
        public final ModConfigSpec.BooleanValue sporeConfigHotApply;
        public final ModConfigSpec.DoubleValue sporeMoundRangeDefault;
        public final ModConfigSpec.DoubleValue sporeMoundRangeAge2;
        public final ModConfigSpec.DoubleValue sporeMoundRangeAge3;
        public final ModConfigSpec.DoubleValue sporeMoundRangeAge4;
        public final ModConfigSpec.IntValue sporeMoundAgeingCooldown;
        public final ModConfigSpec.IntValue sporeMoundInfectionCooldown;
        public final ModConfigSpec.ConfigValue<List<? extends String>> sporeVerwaSummons;
        public final ModConfigSpec.IntValue sporeBiomassLumpKills;
        public final ModConfigSpec.IntValue sporeHiveSpawnKills;

        // ========== 阶段门控（SRP 的世界惩罚） ==========
        public final ModConfigSpec.BooleanValue enablePhaseGate;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> cropGrowthBlockChance;
        public final ModConfigSpec.IntValue fishingBlockPhase;
        public final ModConfigSpec.IntValue noXpPhase;
        public final ModConfigSpec.IntValue healPenaltyPhase;
        public final ModConfigSpec.DoubleValue healPenaltyFactor;
        // ========== 诱饵 ==========
        public final ModConfigSpec.IntValue decoyAggroRadius;
        public final ModConfigSpec.BooleanValue decoyProvokeProto;

        // ========== 环境效果（纯客户端） ==========
        public final ModConfigSpec.BooleanValue environmentEnabled;
        public final ModConfigSpec.BooleanValue environmentFogEnabled;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> environmentFogNearByPhase;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> environmentFogFarByPhase;
        public final ModConfigSpec.ConfigValue<List<? extends Double>> environmentFogTintByPhase;
        public final ModConfigSpec.DoubleValue environmentFogTintRed;
        public final ModConfigSpec.DoubleValue environmentFogTintGreen;
        public final ModConfigSpec.DoubleValue environmentFogTintBlue;
        public final ModConfigSpec.BooleanValue environmentParticlesEnabled;
        public final ModConfigSpec.IntValue environmentParticlePhase;
        public final ModConfigSpec.BooleanValue environmentBiomeFogEnabled;
        public final ModConfigSpec.DoubleValue environmentBiomeFogNear;
        public final ModConfigSpec.DoubleValue environmentBiomeFogFar;
        public final ModConfigSpec.DoubleValue environmentParticlesPerSecond;

        // ========== CDU 净化 ==========
        public final ModConfigSpec.IntValue cduPurifyRadius;
        public final ModConfigSpec.IntValue cduPurifyHalfHeight;

        Common(ModConfigSpec.Builder builder) {
            builder.push("evolution");
            phaseThresholds = builder.comment("各阶段所需点数阈值，列表长度即为最大阶段数+1")
                    .defineList("phaseThresholds",
                            List.of(0, 400, 800, 1800, 10000, 100000,
                                    500000, 1000000, 10000000, 50000000, 100000000),
                            o -> o instanceof Integer);
            healthMultipliers = builder.comment("各阶段生命倍率（长度需与阶段数一致）")
                    .defineList("healthMultipliers",
                            List.of(1.0, 1.2, 1.4, 1.7, 2.0, 2.3,
                                    2.6, 3.0, 3.5, 4.0, 4.5),
                            o -> o instanceof Double);
            attackMultipliers = builder.comment("各阶段攻击倍率")
                    .defineList("attackMultipliers",
                            List.of(1.0, 1.1, 1.25, 1.45, 1.7, 2.0,
                                    2.3, 2.7, 3.0, 3.5, 4.0),
                            o -> o instanceof Double);
            speedBonuses = builder.comment("各阶段移动速度增加值")
                    .defineList("speedBonuses",
                            List.of(0.0, 0.0125, 0.025, 0.0375, 0.05, 0.0625,
                                    0.075, 0.0875, 0.1, 0.1125, 0.125),
                            o -> o instanceof Double);
            armorBonuses = builder.comment("各阶段额外护甲值")
                    .defineList("armorBonuses",
                            List.of(0, 2, 4, 6, 8, 10,
                                    12, 14, 16, 18, 20),
                            o -> o instanceof Integer);
            knockbackResist = builder.comment("各阶段击退抗性")
                    .defineList("knockbackResist",
                            List.of(0.0, 0.1, 0.2, 0.35, 0.5, 0.6,
                                    0.7, 0.75, 0.8, 0.85, 0.9),
                            o -> o instanceof Double);
            phaseUpgradeCooldownSeconds = builder
                    .comment("各阶段升级冷却时间（秒），索引0对应阶段0→1，索引9对应阶段9→10",
                            "冷却时间内即使点数足够也不能继续升级，且每次只能升一级")
                    .defineList("phaseUpgradeCooldownSeconds",
                            List.of(2000, 2400, 2350, 2250, 2100, 1900, 1850, 1850, 1900, 6000),
                            o -> o instanceof Integer);

            builder.pop();

            // ==================== 生物等级（按 Spore 继承体系推导） ====================
            builder.push("spore_tier");
            sporeTierOrganoid = builder
                    .comment("类器官的等级标注：类器官既属于 organoids 组，也要归入某个等级",
                            "格式：实体ID=等级（small / medium / large / boss）",
                            "未标注的类器官仍属于类器官组，但不会进入任何等级池")
                    .defineList("organoidTier",
                            List.of(
                                    "mound=medium",
                                    "umarmed=medium",
                                    "usurper=medium",
                                    "vigil=medium",
                                    "braurei=medium",
                                    "verva=medium",
                                    "delusioner=medium",
                                    "reconstructor=medium",
                                    "hivetumor=medium",
                                    "proto=large"
                            ),
                            o -> o instanceof String);
            sporeTierUtility = builder
                    .comment("无继承等级的敌对生物补充标注（这些类不继承 Infected，也不是 Organoid / Calamity）",
                            "格式同上")
                    .defineList("utilityTier",
                            List.of(
                                    "vanguard=medium",
                                    "reaper=medium"
                            ),
                            o -> o instanceof String);
            sporeTierExtra = builder
                    .comment("★玩家入口★ 把任意模组的生物加进等级池，或覆盖已有分级",
                            "格式：实体ID=等级   （等级：small / medium / large / boss）",
                            "等级写 organoids 表示只加入类器官组、不指定等级",
                            "同一个实体可写多行，例如同时写 =medium 和 =organoids",
                            "省略命名空间时按 spore: 处理；示例：",
                            "  minecraft:zombie=small",
                            "  spore:knight=large          ← 覆盖继承体系给出的 medium",
                            "  mymod:my_boss=boss")
                    .defineList("extraTier", List.of(), o -> o instanceof String);
            sporeTierExclude = builder
                    .comment("★玩家入口★ 从所有等级池中移除生物（包含继承体系自动派生出来的）",
                            "只写实体ID，例如：minecraft:zombie",
                            "注意：这不会阻止该生物自然生成，只是不再由本模组的刷怪逻辑选用")
                    .defineList("exclude", List.of(), o -> o instanceof String);
            builder.pop();
            builder.push("conversion");
            enableConversion = builder.comment("是否启用转化系统").define("enableConversion", true);
            conversionSmallMaxHp = builder.comment("小生物池的最大生命值（< 此值）").defineInRange("smallMaxHp", 50.0, 1.0, 1000.0);
            conversionMediumMaxHp = builder.comment("中生物池的最大生命值（< 此值）").defineInRange("mediumMaxHp", 110.0, 1.0, 1000.0);
            conversionLargeMaxHp = builder.comment("大生物池的最大生命值（≤ 此值）").defineInRange("largeMaxHp", 350.0, 1.0, 1000.0);
            conversionHpThresholdFactor = builder.comment("血量阈值比例（threshold = phase * factor）").defineInRange("hpThresholdFactor", 0.10, 0.0, 1.0);
            conversionBaseFactor = builder.comment("转化概率基础系数（chance = phase * factor）").defineInRange("baseFactor", 0.05, 0.0, 1.0);
            conversionMaxChance = builder.comment("转化概率上限").defineInRange("maxChance", 0.5, 0.0, 1.0);
            conversionMaxAttempts = builder.comment("从池中随机选择实体时的最大尝试次数").defineInRange("maxAttempts", 5, 1, 20);
            builder.pop();

            builder.push("adaptation");
            enableAdaptation = builder.comment("启用适应性系统").define("enableAdaptation", true);
            adaptationTiers = builder
                    .comment("各生物等级的适应参数：等级:产生概率:每次减免:最大次数:是否免疫",
                            "等级：0=small(BasicInfected) 1=medium(EvolvedInfected) 2=large(Hyper/Experiment) 3=boss(Calamity)",
                            "默认参照 SRP：medium≈原始种(70%/5%/12次/60%)、large≈纯粹种(95%/12.5%/8次/免疫)、boss≈卓越种(100%/20%/5次/免疫)",
                            "small 在 SRP 里本不产生适应，这里给了较弱的一组(40%/3%/10次)，可按需调整")
                    .defineList("tiers", List.of(
                            "0:0.40:0.03:10:false",
                            "1:0.70:0.05:12:false",
                            "2:0.95:0.125:8:true",
                            "3:1.00:0.20:5:true"
                    ), o -> o instanceof String);
            adaptationMaxTypes = builder
                    .comment("各等级可适应的来源类型数上限（阶段0），随演化阶段增长",
                            "对应 mcmod 表1：原始种 5、纯粹种 12、卓越种 20；small 给 3")
                    .defineList("maxAdaptedTypes", List.of(3, 5, 12, 20), o -> o instanceof Integer);
            adaptationPhaseGrowth = builder
                    .comment("随演化阶段增强的系数：每阶段 +此值（0.03 → 阶段10 = ×1.30）",
                            "作用于产生概率、每次减免、可适应类型数上限；次数上限不变，所以总减伤随阶段提高")
                    .defineInRange("phaseGrowthPerStage", 0.03, 0.0, 0.2);
            adaptationMaxTotalReduction = builder
                    .comment("非免疫等级的减伤总上限。1.0 = 允许靠累积次数达到完全免伤",
                            "标记为 immune 的等级（tiers 第 5 段为 true）在达到次数上限后直接免疫，不受此值影响")
                    .defineInRange("maxTotalReduction", 1.0, 0.0, 1.0);            builder.pop();

            // ==================== 伤害适应的视觉反馈 ====================
            builder.push("adaptation_visual");
            adaptationVisualEnabled = builder
                    .comment("启用伤害适应的视觉反馈（原版 SRP 的绿光/紫光）")
                    .define("enabled", true);

            adaptationFlashDurationTicks = builder
                    .comment("闪光持续 tick（默认 10，与原版受击红闪等长）")
                    .defineInRange("flashDurationTicks", 10, 1, 40);
            adaptationAdaptingColor = builder
                    .comment("产生适应时的体色（R:G:B，0..1）")
                    .define("adaptingColor", "0.25:1.0:0.35");
            adaptationFullColor = builder
                    .comment("完全适应时的体色（R:G:B，0..1）")
                    .define("fullColor", "0.72:0.30:1.0");
            adaptationFlashAlpha = builder
                    .comment("体色不透明度，1.0 为不透明；低于 1 会让实体整体半透明，一般保持 1.0")
                    .defineInRange("flashAlpha", 1.0, 0.1, 1.0);
            adaptationFlashParticles = builder
                    .comment("产生适应时是否喷红色星形粒子")
                    .define("particles", true);
            adaptationFlashParticleCount = builder
                    .comment("粒子数量")
                    .defineInRange("particleCount", 8, 0, 40);
            adaptationFullSound = builder
                    .comment("完全适应时是否播放金属敲击音效")
                    .define("fullSound", true);
            builder.pop();

            builder.push("support");
            enableSupportSystem = builder.comment("启用支援系统").define("enableSupportSystem", true);
            supportBaseChance = builder.comment("阶段0时的基础触发概率").defineInRange("supportBaseChance", 0.05, 0.0, 1.0);
            supportPhaseIncrement = builder.comment("每增加一个演化阶段增加的概率").defineInRange("supportPhaseIncrement", 0.02, 0.0, 0.1);
            supportLevelMultipliers = builder.comment("生物等级系数（0=Small,1=Medium,2=Large,3=Boss）")
                    .defineList("supportLevelMultipliers", List.of(1.0, 1.2, 1.35, 1.5), o -> o instanceof Double);
            supportMoundChance = builder.comment("召唤菌囊的概率").defineInRange("supportMoundChance", 0.3, 0.0, 1.0);
            supportSameLevelChance = builder.comment("召唤同级生物的概率").defineInRange("supportSameLevelChance", 0.2, 0.0, 1.0);
            supportSecondLowerChance = builder.comment("召唤第二只下一级生物的概率").defineInRange("supportSecondLowerChance", 0.3, 0.0, 1.0);
            supportScentChance = builder.comment("召唤支援孢气的概率").defineInRange("supportScentChance", 0.2, 0.0, 1.0);
            supportCooldownSeconds = builder.comment("每次触发支援后的冷却时间（秒）").defineInRange("supportCooldownSeconds", 25, 0, 300);

            // ---- 召唤物：抛出效果与生命周期 ----
            supportThrowHorizontal = builder
                    .comment("召唤时水平方向的推力（格/tick），配合 360° 随机方向")
                    .defineInRange("throwHorizontal", 0.25, 0.0, 2.0);
            supportThrowUpward = builder
                    .comment("召唤时向上的推力（格/tick），模拟被抛出")
                    .defineInRange("throwUpward", 0.42, 0.0, 2.0);
            supportSummonedNoAggroSeconds = builder
                    .comment("召唤物「没有仇恨」多少秒后消失")
                    .defineInRange("summonedNoAggroSeconds", 10, 1, 600);
            supportSummonedLifetimeSeconds = builder
                    .comment("召唤物最长存在多少秒（不论是否有仇恨）")
                    .defineInRange("summonedLifetimeSeconds", 100, 1, 3600);
            supportMinSpawnDistance = builder.comment("召唤援军的最小距离（格）").defineInRange("supportMinSpawnDistance", 10.0, 0.0, 50.0);
            supportMaxSpawnDistance = builder.comment("召唤援军的最大距离（格）").defineInRange("supportMaxSpawnDistance", 15.0, 1.0, 100.0);
            builder.pop();

            builder.push("ecosystem");
            enableEcosystemConversion = builder.comment("启用生态系统转化").define("enableEcosystemConversion", true);
            conversionIntervalTicks = builder.comment("转化间隔（tick）").defineInRange("conversionIntervalTicks", 100, 20, 600);
            conversionBaseRadius = builder.comment("基础转化半径（格）").defineInRange("conversionBaseRadius", 3, 1, 20);
            conversionRadiusPerPhase = builder.comment("每演化阶段增加的半径（格）").defineInRange("conversionRadiusPerPhase", 2, 0, 10);
            conversionChance = builder.comment("每次尝试转化的概率").defineInRange("conversionChance", 0.3, 0.0, 1.0);
            maxBlocksPerBiome = builder.comment("每个生物每次最多转化的方块数").defineInRange("maxBlocksPerBiome", 10, 1, 20);
            globalLimitPerTick = builder.comment("每个 tick 最多转化的方块总数").defineInRange("globalLimitPerTick", 10, 1, 50);
            conversionMappings = builder.comment("方块映射列表，格式：源ID->目标ID")
                    .defineList("conversionMappings",
                            List.of(
                                    "minecraft:grass_block->minecraft:mycelium",
                                    "minecraft:stone->spore:infested_stone",
                                    "minecraft:cobblestone->spore:infested_cobblestone",
                                    "minecraft:dirt->spore:infested_dirt",
                                    "minecraft:sand->spore:infested_sand",
                                    "minecraft:gravel->spore:infested_gravel",
                                    "minecraft:clay->spore:infested_clay",
                                    "minecraft:netherrack->spore:infested_netherrack",
                                    "minecraft:soul_sand->spore:infested_soul_sand",
                                    "minecraft:red_sand->spore:infested_red_sand",
                                    "minecraft:end_stone->spore:infested_end_stone",
                                    "minecraft:deepslate->spore:infested_deepslate",
                                    "minecraft:cobbled_deepslate->spore:infested_cobbled_deepslate"
                            ),
                            o -> o instanceof String);
            builder.pop();

            builder.push("mound");
            enableMoundSystem = builder.comment("启用菌囊系统（自然生成+年龄升级）").define("enableMoundSystem", true);
            moundSpawnIntervalTicks = builder.comment("自然生成检测间隔（tick）").defineInRange("spawnIntervalTicks", 100, 20, 1200);
            moundSpawnBaseChance = builder.comment("阶段0时的基础生成概率").defineInRange("spawnBaseChance", 0.02, 0.0, 1.0);
            moundSpawnPhaseBonus = builder.comment("每演化阶段增加的概率").defineInRange("spawnPhaseBonus", 0.01, 0.0, 0.1);
            moundSpawnRadius = builder.comment("检测菌囊存在的半径（格）").defineInRange("spawnRadius", 16, 5, 64);
            moundSpawnMaxPerTick = builder.comment("每tick最多生成菌囊的数量（全局）").defineInRange("spawnMaxPerTick", 2, 1, 10);
            moundLevelPointMultipliers = builder
                    .comment("菌囊各等级每30秒获得点数的倍率，索引0对应等级1，索引3对应等级4",
                            "实际获得点数 = moundPeriodicPoints * 倍率")
                    .defineList("moundLevelPointMultipliers",
                            List.of(1.0, 1.5, 2.5, 4.0),
                            o -> o instanceof Double);
            builder.pop();

            builder.push("tracking");
            enableTracking = builder.comment("启用循迹系统（Marker效果）").define("enableTracking", true);
            maxMarkerAmplifier = builder.comment("最大Marker效果等级（不超过此值）").defineInRange("maxMarkerAmplifier", 10, 0, 255);
            builder.pop();

            // ==================== 感染群系配置（新增） ====================
            builder.push("infected_biome");
            enableInfectedBiome = builder.comment("启用感染群系系统").define("enableInfectedBiome", true);

            protoInitialRadius = builder
                    .comment("Proto 实体群系转换初始半径（格）")
                    .defineInRange("protoInitialRadius", 30, 1, 500);
            protoRadiusIncrement = builder
                    .comment("Proto 实体每次半径增加量（格）")
                    .defineInRange("protoRadiusIncrement", 10, 1, 100);
            protoIncreaseIntervalMinutes = builder
                    .comment("Proto 实体半径扩大间隔（分钟）")
                    .defineInRange("protoIncreaseIntervalMinutes", 5L, 1L, 60L);

            biomeVerticalHeight = builder
                    .comment("群系替换垂直范围总高度（格，以实体 Y 为中心上下各一半）")
                    .defineInRange("biomeVerticalHeight", 30, 10, 256);
            maxCommandsPerTick = builder
                    .comment("每 tick 最多执行的 fillbiome 子命令数量")
                    .defineInRange("maxCommandsPerTick", 20, 1, 100);

            builder.pop();

            builder.push("evolution_points");
            killPointMultiplier = builder
                    .comment("击杀/转化获得点数 = 目标最大生命值 * 倍数")
                    .defineInRange("killPointMultiplier", 1.0, 0.0, 100.0);
            phasePointMultipliers = builder
                    .comment("各阶段点数获取倍率")
                    .defineList("phasePointMultipliers",
                            List.of(0.2, 0.2, 0.3, 0.5, 0.7, 1.0, 1.2, 1.3, 1.4, 1.6, 2.0),
                            o -> o instanceof Double);
            protoPeriodicPoints = builder
                    .comment("每个 Proto 每60秒奖励点数")
                    .defineInRange("protoPeriodicPoints", 1500.0, 0.0, 100000.0);
            moundPeriodicPoints = builder
                    .comment("每个 Mound 每60秒奖励点数")
                    .defineInRange("moundPeriodicPoints", 30.0, 0.0, 100000.0);
            smallDeathPenalty = builder
                    .comment("小等级真菌死亡扣减点数")
                    .defineInRange("smallDeathPenalty", 10.0, 0.0, 10000.0);
            mediumDeathPenalty = builder
                    .comment("中等级真菌死亡扣减点数")
                    .defineInRange("mediumDeathPenalty", 40.0, 0.0, 10000.0);
            largeDeathPenalty = builder
                    .comment("大等级真菌死亡扣减点数")
                    .defineInRange("largeDeathPenalty", 150.0, 0.0, 10000.0);
            bossDeathPenalty = builder
                    .comment("Boss等级真菌死亡扣减点数")
                    .defineInRange("bossDeathPenalty", 600.0, 0.0, 10000.0);
            protoDeathPenalty = builder
                    .comment("Proto死亡额外扣减点数（叠加）")
                    .defineInRange("protoDeathPenalty", 5000.0, 0.0, 100000.0);
            moundDeathPenalty = builder
                    .comment("Mound死亡扣减点数")
                    .defineInRange("moundDeathPenalty", 250.0, 0.0, 10000.0);
            biomeConvertChunkPoints = builder
                    .comment("群系转换每个新区块奖励点数")
                    .defineInRange("biomeConvertChunkPoints", 80.0, 0.0, 1000.0);
            builder.pop();

            builder.push("mound_support");
            // 菌囊等级不再由本模组计时提升：等级直接镜像 Spore 的成长阶段，
            // 老化速度用 Spore 的「Sets Mound Ageing Cooldown」控制（见 spore_bridge.moundAgeingCooldown）。
            moundSupportConfigs = builder
                    .comment("Mound 各等级支援参数，格式：等级:范围:最小small:最大small:最小medium:最大medium:冷却秒数:是否生成孢气(0/1)",
                            "例如：1:15:0:0:0:0:60:1  表示一级，范围15格，不生成small/medium，冷却30秒，生成孢气",
                            "二级：2:20:1:2:0:0:20:0",
                            "三级：3:30:1:1:1:1:20:0",
                            "四级：4:40:0:0:1:2:20:0")
                    .defineList("supportConfigs",
                            List.of(
                                    "1:15:0:0:0:0:250:1",
                                    "2:20:1:2:0:0:15:0",
                                    "3:30:1:3:1:1:15:0",
                                    "4:40:0:0:2:3:15:0"
                            ),
                            o -> o instanceof String && ((String) o).split(":").length == 8);
            moundScanIntervalTicks = builder
                    .comment("菌囊实体扫描间隔（tick），即使冷却已结束，也不会每 tick 扫描")
                    .defineInRange("scanIntervalTicks", 40, 5, 100);

            moundMinLevelByPhase = builder
                    .comment("阶段节点强制菌囊最低等级：索引 = 演化阶段（0..10），值为最低等级（1..4）",
                            "阶段达到该值时，所有菌囊会被强制提升到不低于此等级",
                            "默认：阶段0-4 不干预，阶段5-6 → 2，阶段7-8 → 3，阶段9-10 → 4")
                    .defineList("minLevelByPhase",
                            List.of(1, 1, 1, 1, 1, 2, 2, 3, 3, 4, 4),
                            o -> o instanceof Integer);


            builder.pop();

            builder.push("proto_marked_mound");
            protoMarkedMoundEnabled = builder
                    .comment("启用 Proto 标记菌囊自动生成与维护")
                    .define("enabled", true);
            protoMarkedMoundCheckIntervalTicks = builder
                    .comment("检查标记菌囊数量的间隔（tick，20 tick = 1秒）")
                    .defineInRange("checkIntervalTicks", 100, 20, 600);
            protoMarkedMoundMoveIntervalTicks = builder
                    .comment("标记菌囊移动间隔（tick，默认 5分钟 = 6000 tick）")
                    .defineInRange("moveIntervalTicks", 6000, 20, 12000);
            protoMarkedMoundTargetCount = builder
                    .comment("目标标记菌囊数量（低于此值则生成）")
                    .defineInRange("targetCount", 5, 1, 20);
            protoMarkedMoundMinDistance = builder
                    .comment("生成/移动标记菌囊的最小距离（格）")
                    .defineInRange("minDistance", 32, 10, 100);
            protoMarkedMoundMaxDistance = builder
                    .comment("生成/移动标记菌囊的最大距离（格）")
                    .defineInRange("maxDistance", 128, 50, 500);
            builder.pop();

            builder.push("proto_skills");
            protoSkillsEnabled = builder
                    .comment("启用 Proto 技能系统")
                    .define("enabled", true);
            protoSkill1CooldownTicks = builder
                    .comment("技能1冷却时间（tick）")
                    .defineInRange("skill1CooldownTicks", 100, 20, 600);
            protoSkill1MoveMinDist = builder
                    .comment("技能1移动 mound 的最小距离（格）")
                    .defineInRange("skill1MoveMinDist", 15, 5, 50);
            protoSkill1MoveMaxDist = builder
                    .comment("技能1移动 mound 的最大距离（格）")
                    .defineInRange("skill1MoveMaxDist", 25, 5, 50);
            protoSkill2CheckIntervalTicks = builder
                    .comment("技能2检查间隔（tick）")
                    .defineInRange("skill2CheckIntervalTicks", 20, 10, 100);
            protoSkill2CooldownTicks = builder
                    .comment("技能2每次生成后的冷却（tick）")
                    .defineInRange("skill2CooldownTicks", 60, 10, 200);
            protoSkill2TargetCount = builder
                    .comment("技能2目标 largepool 生物数量（低于此值则生成保育体）")
                    .defineInRange("skill2TargetCount", 3, 1, 20);
            protoSkill2SpawnRange = builder
                    .comment("技能2生成保育体的搜索范围（格）")
                    .defineInRange("skill2SpawnRange", 64, 16, 128);
            protoSkill3CooldownTicks = builder
                    .comment("技能3冷却时间（tick）")
                    .defineInRange("skill3CooldownTicks", 200, 20, 600);
            protoSkill3SpawnCountMin = builder
                    .comment("技能3每次生成最少保育体数量（每个位置）")
                    .defineInRange("skill3SpawnCountMin", 1, 1, 10);
            protoSkill3SpawnCountMax = builder
                    .comment("技能3每次生成最多保育体数量（每个位置）")
                    .defineInRange("skill3SpawnCountMax", 2, 2, 10);
            protoSkillLargePoolThreshold = builder
                    .comment("largepool 数量阈值，低于此值启用技能2/3，高于等于此值启用技能4")
                    .defineInRange("largePoolThreshold", 5, 1, 20);
            protoSkill4CooldownTicks = builder
                    .comment("技能4冷却（tick）")
                    .defineInRange("skill4CooldownTicks", 200, 20, 600);
            protoSkill4SpawnRange = builder
                    .comment("技能4生成 delusioner 的最大距离（格）")
                    .defineInRange("skill4SpawnRange", 16, 5, 30);
            protoSkill4CheckRange = builder
                    .comment("技能4检测 delusioner 存在的范围（格）")
                    .defineInRange("skill4CheckRange", 64, 10, 128);
            protoSkill5CooldownTicks = builder
                    .comment("技能5冷却（tick）")
                    .defineInRange("skill5CooldownTicks", 300, 20, 600);
            protoSkill5SpawnRange = builder
                    .comment("技能5生成 reconstructor 的最大距离（格）")
                    .defineInRange("skill5SpawnRange", 16, 5, 30);
            protoSkill5BossCheckRange = builder
                    .comment("技能5检测 boss 存在的范围（格）")
                    .defineInRange("skill5BossCheckRange", 128, 10, 256);
            protoSkill5SpawnCount = builder
                    .comment("技能5每次生成 reconstructor 的数量")
                    .defineInRange("skill5SpawnCount", 2, 1, 5);
            builder.pop();

            builder.push("proto_resurrection");
            protoResurrectionEnabled = builder
                    .comment("是否启用 Proto 致命反击技能")
                    .define("enabled", true);
            protoResurrectionCheckRadius = builder
                    .comment("检测范围（格），在此范围内统计 LargePool 和 Boss 生物")
                    .defineInRange("checkRadius", 128, 16, 256);
            protoResurrectionLargePoolThreshold = builder
                    .comment("触发技能所需的最低 LargePool 生物数量")
                    .defineInRange("largePoolThreshold", 5, 1, 30);
            protoResurrectionHealthPercent = builder
                    .comment("复活后恢复的血量百分比（0.0~1.0）")
                    .defineInRange("healthPercent", 1.0 / 3.0, 0.0, 1.0);
            protoResurrectionResistanceDuration = builder
                    .comment("抗性提升效果持续时间（秒）")
                    .defineInRange("resistanceDuration", 20, 1, 120);
            protoResurrectionResistanceAmplifier = builder
                    .comment("抗性提升效果等级（0=I, 1=II, 2=III, 3=IV, 4=V）")
                    .defineInRange("resistanceAmplifier", 4, 0, 10);
            protoResurrectionMoveSpeedMultiplier = builder
                    .comment("召回生物移动速度倍率（1.0 = 正常速度，2.0 = 两倍速度）")
                    .defineInRange("moveSpeedMultiplier", 1.5, 0.5, 4.0);
            protoResurrectionPushRadius = builder
                    .comment("击退非真菌生物的半径（格）")
                    .defineInRange("pushRadius", 16.0, 0.0, 64.0);
            protoResurrectionPushStrength = builder
                    .comment("击退力度（1.0 = 正常击退，2.0 = 两倍力度）")
                    .defineInRange("pushStrength", 2.5, 0.0, 10.0);
            protoResurrectionSoundId = builder
                    .comment("触发时播放的音效 ID，格式：modid:sound_name",
                            "留空则不播放音效")
                    .define("soundId", "sporesrp:proto_resurrection");
            builder.pop();

            builder.push("gastgaber_builder");
            gastgaberBuilderSpawnChance = builder
                    .comment("Gastgaber 转化为 Builder 的概率 (0.0 ~ 1.0)")
                    .defineInRange("spawnChance", 0.2, 0.0, 1.0);
            gastgaberBuilderCollisionRadius = builder
                    .comment("Builder 吞噬检测半径（格）")
                    .defineInRange("collisionRadius", 2.0, 0.5, 8.0);
            gastgaberBuilderMaxPoints = builder
                    .comment("Builder 触发结构生成所需的最大点数")
                    .defineInRange("maxPoints", 100, 10, 1000);
            gastgaberBuilderSmallPoints = builder
                    .comment("吞噬 Small 等级生物获得的点数")
                    .defineInRange("smallPoints", 15, 1, 50);
            gastgaberBuilderMediumPoints = builder
                    .comment("吞噬 Medium 等级生物获得的点数")
                    .defineInRange("mediumPoints", 35, 1, 50);
            gastgaberBuilderLargePoints = builder
                    .comment("吞噬 Large 等级生物获得的点数")
                    .defineInRange("largePoints", 50, 1, 50);
            gastgaberBuilderCallInterval = builder
                    .comment("Builder 呼叫周围生物的间隔（秒）")
                    .defineInRange("callInterval", 15, 5, 120);
            gastgaberBuilderCallRadius = builder
                    .comment("Builder 呼叫周围生物的半径（格）")
                    .defineInRange("callRadius", 20, 5, 64);
            gastgaberBuilderCooldownSeconds = builder
                    .comment("Builder 结构生成后的冷却时间（秒）")
                    .defineInRange("cooldownSeconds", 180, 10, 600);
            gastgaberBuilderStructures = builder
                    .comment("Builder 可能生成的结构列表（文件名，不含 .nbt 后缀）",
                            "例如：[\"builder_structure_1\", \"builder_structure_2\", \"builder_structure_3\"]",
                            "程序会从列表中随机选择一个生成",
                            "结构文件必须放在 assets/sporesrp/structures/ 目录下")
                    .defineList("structures",
                            List.of("overgrown_spawner"),
                            o -> o instanceof String);
            builder.pop();

            builder.push("full_hivemind");
            fullHivemindMinProtoCount = builder
                    .comment("转化完整心智所需的最低 Proto 数量")
                    .defineInRange("minProtoCount", 3, 1, 10);
            fullHivemindMinPhase = builder
                    .comment("转化完整心智所需的最低演化阶段")
                    .defineInRange("minPhase", 8, 0, 10);
            fullHivemindHealthMultiplier = builder
                    .comment("完整心智生命值倍率")
                    .defineInRange("healthMultiplier", 1.5, 1.0, 5.0);
            fullHivemindArmorBonus = builder
                    .comment("完整心智额外护甲值")
                    .defineInRange("armorBonus", 10, 0, 30);
            builder.pop();

            builder.push("full_hivemind_skills");
            fullHivemindSkill1Cooldown = builder
                    .comment("技能1冷却（tick）")
                    .defineInRange("skill1Cooldown", 60, 20, 600);
            fullHivemindSkill1MoveMinDist = builder
                    .comment("技能1移动最小距离")
                    .defineInRange("skill1MoveMinDist", 20, 5, 50);
            fullHivemindSkill1MoveMaxDist = builder
                    .comment("技能1移动最大距离")
                    .defineInRange("skill1MoveMaxDist", 35, 5, 50);
            fullHivemindSkill2CheckInterval = builder
                    .comment("技能2检查间隔（tick）")
                    .defineInRange("skill2CheckInterval", 10, 10, 100);
            fullHivemindSkill2Cooldown = builder
                    .comment("技能2冷却（tick）")
                    .defineInRange("skill2Cooldown", 40, 10, 200);
            fullHivemindSkill2TargetCount = builder
                    .comment("技能2目标 largepool 数量阈值")
                    .defineInRange("skill2TargetCount", 7, 1, 20);
            fullHivemindSkill2SpawnRange = builder
                    .comment("技能2生成范围")
                    .defineInRange("skill2SpawnRange", 48, 16, 128);
            fullHivemindSkill3Cooldown = builder
                    .comment("技能3冷却（tick）")
                    .defineInRange("skill3Cooldown", 100, 20, 600);
            fullHivemindSkill3SpawnCountMin = builder
                    .comment("技能3生成最少数量")
                    .defineInRange("skill3SpawnCountMin", 2, 1, 10);
            fullHivemindSkill3SpawnCountMax = builder
                    .comment("技能3生成最多数量")
                    .defineInRange("skill3SpawnCountMax", 4, 2, 10);
            fullHivemindSkillLargePoolThreshold = builder
                    .comment("largepool 数量阈值")
                    .defineInRange("largePoolThreshold", 13, 1, 20);
            fullHivemindSkill4Cooldown = builder
                    .comment("技能4冷却（tick）")
                    .defineInRange("skill4Cooldown", 120, 20, 600);
            fullHivemindSkill4SpawnRange = builder
                    .comment("技能4生成最大距离")
                    .defineInRange("skill4SpawnRange", 16, 5, 30);
            fullHivemindSkill4CheckRange = builder
                    .comment("技能4检测范围")
                    .defineInRange("skill4CheckRange", 64, 10, 128);
            fullHivemindSkill5Cooldown = builder
                    .comment("技能5冷却（tick）")
                    .defineInRange("skill5Cooldown", 180, 20, 600);
            fullHivemindSkill5SpawnRange = builder
                    .comment("技能5生成最大距离")
                    .defineInRange("skill5SpawnRange", 16, 5, 30);
            fullHivemindSkill5BossCheckRange = builder
                    .comment("技能5 Boss 检测范围")
                    .defineInRange("skill5BossCheckRange", 128, 10, 256);
            fullHivemindSkill5SpawnCount = builder
                    .comment("技能5生成数量")
                    .defineInRange("skill5SpawnCount", 3, 1, 5);
            builder.pop();

            builder.push("full_hivemind_assault");
            assaultValueIncrementPerTick = builder
                    .comment("每次增加袭击值的数值")
                    .defineInRange("incrementPerTick", 20.0, 1.0, 100.0);
            assaultValueIncrementInterval = builder
                    .comment("袭击值增加间隔（tick），默认 5 分钟 = 6000 tick")
                    .defineInRange("incrementInterval", 6000, 600, 12000);
            assaultSmallPoolKillPoints = builder
                    .comment("玩家击杀 SmallPool 生物获得的袭击值")
                    .defineInRange("smallPoolKillPoints", 5, 0.0, 100.0);
            assaultMediumPoolKillPoints = builder
                    .comment("玩家击杀 MediumPool 生物获得的袭击值")
                    .defineInRange("mediumPoolKillPoints", 10, 0.0, 100.0);
            assaultLargePoolKillPoints = builder
                    .comment("玩家击杀 LargePool 生物获得的袭击值")
                    .defineInRange("largePoolKillPoints", 15, 0.0, 100.0);
            assaultBossPoolKillPoints = builder
                    .comment("玩家击杀 BossPool 生物获得的袭击值")
                    .defineInRange("bossPoolKillPoints", 25, 0.0, 100.0);
            assaultTriggerCheckInterval = builder
                    .comment("袭击触发检查间隔（tick），默认 5 分钟 = 6000 tick")
                    .defineInRange("triggerCheckInterval", 6000, 600, 12000);
            assaultDistanceThreshold = builder
                    .comment("触发袭击的玩家距离阈值（格）")
                    .defineInRange("distanceThreshold", 128, 32, 512);
            assaultMaxDuration = builder
                    .comment("袭击最大持续时间（秒），超时自动结束")
                    .defineInRange("maxDuration", 600, 60, 3600);
            assaultCooldownSeconds = builder
                    .comment("袭击结束后冷却时间（秒）")
                    .defineInRange("cooldownSeconds", 1200, 60, 7200);
            builder.pop();

            // ========== 世界净化之战配置 ==========
            builder.push("purification_battle");
            enablePurificationBattle = builder
                    .comment("启用世界净化之战")
                    .define("enablePurificationBattle", true);
            playerCheckRadius = builder
                    .comment("玩家守点检测半径（格）")
                    .defineInRange("playerCheckRadius", 10, 1, 100);

            // ---- 天气与闪电 ----
            battleThunderstorm = builder.comment("战斗期间强制雷雨").define("battleThunderstorm", true);
            battleLightningEnabled = builder.comment("战斗期间定期落雷").define("battleLightningEnabled", true);
            battleLightningIntervalTicks = builder.comment("落雷基准间隔（tick）")
                    .defineInRange("battleLightningIntervalTicks", 200, 40, 2400);
            battleLightningRadiusMin = builder.comment("落雷环最小半径")
                    .defineInRange("battleLightningRadiusMin", 8, 2, 64);
            battleLightningRadiusMax = builder.comment("落雷环最大半径")
                    .defineInRange("battleLightningRadiusMax", 32, 4, 128);
            battleLightningSafeRadius = builder.comment("核心/信标安全半径（不落雷）")
                    .defineInRange("battleLightningSafeRadius", 8, 0, 64);

            // ---- 开启高度 ----
            battleMinY = builder.comment("开战最低 Y（-1 = 使用维度海平面；超平坦世界不受限）")
                    .defineInRange("battleMinY", -1, -1, 320);

            // ---- 刷怪锚点 ----
            spawnAnchorCount = builder.comment("每阶段预计算的地面锚点数")
                    .defineInRange("spawnAnchorCount", 24, 4, 64);
            spawnRingMinRadius = builder.comment("刷怪环最小半径")
                    .defineInRange("spawnRingMinRadius", 22, 8, 64);
            spawnRingMaxRadius = builder.comment("刷怪环最大半径")
                    .defineInRange("spawnRingMaxRadius", 40, 10, 128);
            spawnMinCoreDistance = builder.comment("刷怪点离核心的最小距离")
                    .defineInRange("spawnMinCoreDistance", 10, 0, 64);
            spawnAttemptsPerMob = builder.comment("单只怪最多换几个位置")
                    .defineInRange("spawnAttemptsPerMob", 12, 1, 64);
            spawnJitter = builder.comment("锚点周围抖动半径（格）")
                    .defineInRange("spawnJitter", 2, 0, 8);
            spawnMaxSurfaceDelta = builder.comment("锚点与战斗中心的最大高度差")
                    .defineInRange("spawnMaxSurfaceDelta", 10, 2, 64);
            spawnRequireAirAbove = builder.comment("站立所需净空高度")
                    .defineInRange("spawnRequireAirAbove", 2, 1, 4);
            autoPlatformSpawns = builder.comment("海上/高空自动搭建刷怪平台")
                    .define("autoPlatformSpawns", true);
            spawnPadCount = builder.comment("平台模式下的平台数量")
                    .defineInRange("spawnPadCount", 12, 1, 64);
            spawnPadRadius = builder.comment("平台环半径")
                    .defineInRange("spawnPadRadius", 28, 8, 64);
            restoreSpawnPads = builder.comment("战后恢复平台原始方块")
                    .define("restoreSpawnPads", true);
            spawnPadBlock = builder.comment("平台方块 id")
                    .define("spawnPadBlock", "minecraft:obsidian");

            // ---- 阶段1 菌囊环 ----
            moundRingCount = builder.comment("阶段1 开局生成的 1 阶菌囊数量")
                    .defineInRange("moundRingCount", 8, 0, 32);
            moundRingRadius = builder.comment("菌囊环半径")
                    .defineInRange("moundRingRadius", 28, 8, 64);

            // ---- 阶段2 飞行池 ----
            phase2FlyingPool = builder.comment("阶段2 的飞行生物池")
                    .defineList("phase2FlyingPool",
                            List.of("spore:busser", "spore:gargoyl", "spore:scavenger"),
                            o -> o instanceof String);
            phase2HinderburgPerWave = builder.comment("阶段2 每波额外生成的灾厄级飞行生物数量")
                    .defineInRange("phase2HinderburgPerWave", 1, 0, 3);

            // ---- 阶段4 灾厄 ----
            calamityWaveIntervalTicks = builder.comment("阶段4 每波间隔（tick）")
                    .defineInRange("calamityWaveIntervalTicks", 400, 40, 2400);
            calamityPerWaveMin = builder.comment("阶段4 每波最少灾厄数")
                    .defineInRange("calamityPerWaveMin", 2, 1, 10);
            calamityPerWaveMax = builder.comment("阶段4 每波最多灾厄数")
                    .defineInRange("calamityPerWaveMax", 3, 1, 10);
            builder.pop();

            builder.push("hud");
            hudX = builder
                    .comment("HUD 左上角 X 坐标（相对于屏幕左侧）")
                    .defineInRange("x", 150, 0, 1920);
            hudY = builder
                    .comment("HUD 左上角 Y 坐标（相对于屏幕顶部）")
                    .defineInRange("y", 5, 0, 1080);
            builder.pop();

            // ==================== 写入 Spore 配置的目标值 ====================
            // 运行时直接改 Spore 已加载的 ModConfigSpec（免重启），随后写回其配置文件。
            // 只有与当前值不同的时候才会写，玩家手改回旧值会被纠正。
            builder.push("spore_bridge");
            sporeConfigHotApply = builder
                    .comment("是否在服务器启动时把下面这些值热更新进 Spore 的配置（false 则完全不碰 Spore 配置）")
                    .define("hotApply", true);
            sporeMoundRangeDefault = builder
                    .comment("写入 Spore「Sets Mound default range」的目标值（1 阶，格）")
                    .defineInRange("moundRangeDefault", 15.0, 1.0, Double.MAX_VALUE);
            sporeMoundRangeAge2 = builder
                    .comment("写入 Spore「Sets Mound range at the age of 2」的目标值（格）")
                    .defineInRange("moundRangeAge2", 20.0, 1.0, Double.MAX_VALUE);
            sporeMoundRangeAge3 = builder
                    .comment("写入 Spore「Sets Mound range at the age of 3」的目标值（格）")
                    .defineInRange("moundRangeAge3", 30.0, 1.0, Double.MAX_VALUE);
            sporeMoundRangeAge4 = builder
                    .comment("写入 Spore「Sets Mound range at the age of 4」的目标值（格）")
                    .defineInRange("moundRangeAge4", 40.0, 1.0, Double.MAX_VALUE);
            sporeMoundAgeingCooldown = builder
                    .comment("写入 Spore「Sets Mound Ageing Cooldown」的目标值（秒，每阶所需时间）")
                    .defineInRange("moundAgeingCooldown", 300, 1, Integer.MAX_VALUE);
            sporeMoundInfectionCooldown = builder
                    .comment("写入 Spore「Sets Mound Infection Cooldown」的目标值（秒）",
                            "注意：Spore 在菌囊创建时就把该值快照成实例字段，",
                            "所以热更新只影响之后**新生成**的菌囊")
                    .defineInRange("moundInfectionCooldown", 20, 1, Integer.MAX_VALUE);
            sporeVerwaSummons = builder
                    .comment("写入 Spore「Verwahrung summons」的目标列表（与原硬编码值保持一致，",
                            "其中 spore:hevoker 在原值里出现两次，这里一并保留）")
                    .defineList("verwaSummons",
                            List.of("spore:wendigo", "spore:inquisitor", "spore:brot", "spore:ogre",
                                    "spore:hevoker", "spore:hvindicator", "spore:hevoker"),
                            o -> o instanceof String);
            sporeBiomassLumpKills = builder
                    .comment("写入 Spore「Biomass Lump minimal kills」的目标值")
                    .defineInRange("biomassLumpKills", 0, 0, Integer.MAX_VALUE);
            sporeHiveSpawnKills = builder
                    .comment("写入 Spore「Reconstructed Mind Block kills」的目标值")
                    .defineInRange("hiveSpawnKills", 150, 0, Integer.MAX_VALUE);
            builder.pop();

            // ==================== 阶段门控 ====================
            builder.push("phase_gate");
            enablePhaseGate = builder
                    .comment("是否启用阶段门控（演化阶段越高，世界越不适宜生存）")
                    .define("enabled", true);
            cropGrowthBlockChance = builder
                    .comment("各演化阶段的作物生长受阻概率，索引 = 演化阶段（0..10）",
                            "默认按 SRP：6/7/8/9 阶段 = 10% / 30% / 60% / 100%")
                    .defineList("cropGrowthBlockChance",
                            List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.10, 0.30, 0.60, 1.0, 1.0),
                            o -> o instanceof Double);
            fishingBlockPhase = builder
                    .comment("从该阶段起无法钓鱼（默认 6）")
                    .defineInRange("fishingBlockPhase", 6, 0, 10);
            noXpPhase = builder
                    .comment("从该阶段起孢子生物死亡不掉经验（默认 8）")
                    .defineInRange("noXpPhase", 8, 0, 10);
            healPenaltyPhase = builder
                    .comment("从该阶段起非孢子生物回血受罚（默认 9）")
                    .defineInRange("healPenaltyPhase", 9, 0, 10);
            healPenaltyFactor = builder
                    .comment("非孢子生物回血倍率（默认 0.5 = 减半）")
                    .defineInRange("healPenaltyFactor", 0.5, 0.0, 1.0);
            eternalNightAfterPhase6 = builder
                    .comment("阶段达到 6 后是否强制永夜（世界时间锁定为夜晚）")
                    .define("eternalNightAfterPhase6", true);
            preventSleep = builder
                    .comment("阶段达到 6 后是否禁止睡觉")
                    .define("preventSleep", true);

            // ---- 阶段化刷怪（只作用于天然生成，感染群系独立不干预）----
            spawnTiersByPhase = builder
                    .comment("各演化阶段允许**天然生成**的孢子生物等级，索引 = 阶段（0..10）",
                            "逗号分隔，可选 small / medium / large / boss",
                            "只作用于普通群系的天然生成；sporesrp:infected_biome 的刷怪表独立，不受此影响",
                            "留空表示该阶段不限制")
                    .defineList("spawnTiersByPhase",
                            List.of("small", "small", "small,medium", "small,medium", "small,medium",
                                    "small,medium,large", "small,medium,large", "small,medium,large",
                                    "small,medium,large,boss", "small,medium,large,boss", "small,medium,large,boss"),
                            o -> o instanceof String);
            nonSporeSpawnBlockPhase = builder
                    .comment("从该阶段起停止**非孢子生物**的天然生成（只作用于普通群系），0 = 关闭")
                    .defineInRange("nonSporeSpawnBlockPhase", 10, 0, 10);

            spawnRateByPhase = builder
                    .comment("阶段刷怪量曲线（保留概率，0..1）：天然生成的孢子生物按此概率保留",
                            "低阶段过滤掉一部分 → 稀疏；高阶段 1.0 → 完整密度")
                    .defineList("spawnRateByPhase",
                            List.of(0.35, 0.40, 0.50, 0.55, 0.60, 0.70, 0.78, 0.85, 0.92, 0.97, 1.00),
                            o -> o instanceof Double);
            spawnExtraByPhase = builder
                    .comment("阶段额外追加概率（0..1）：成功天然生成后，再追加生成一只同类的概率",
                            "高阶段可超过原版密度上限，做出真正的难度曲线")
                    .defineList("spawnExtraByPhase",
                            List.of(0.00, 0.00, 0.00, 0.00, 0.05, 0.10, 0.18, 0.28, 0.40, 0.55, 0.75),
                            o -> o instanceof Double);
            sunlightIgnorePhase = builder
                    .comment("从该阶段起孢子生物可无视阳光自然生成（感染群系始终无视，不受此限制），-1 = 关闭")
                    .defineInRange("sunlightIgnorePhase", 2, -1, 10);


            builder.pop();

            // ==================== 诱饵 ====================
            builder.push("decoy");
            decoyAggroRadius = builder
                    .comment("饵堆生效时吸引周围真菌生物仇恨的半径（格），0 = 不吸引")
                    .defineInRange("aggroRadius", 32, 0, 128);
            decoyProvokeProto = builder
                    .comment("世界存在 Proto 时，饵堆必定让其中一只注意到玩家")
                    .define("forceProtoAggro", true);
            builder.pop();

            // ==================== 环境效果（纯客户端） ====================
            builder.push("environment");
            environmentEnabled = builder
                    .comment("总开关：阶段环境效果（迷雾 / 雾色 / 孢子粒子）")
                    .define("enabled", true);
            environmentFogEnabled = builder
                    .comment("启用迷雾距离与雾色（仅影响客户端渲染）")
                    .define("fogEnabled", true);
            environmentFogNearByPhase = builder
                    .comment("各阶段的雾起点（格），索引 = 演化阶段；配合 fogFarByPhase 使用")
                    .defineList("fogNearByPhase",
                            List.of(0.0, 0.0, 0.0, 0.0, 0.0, 4.0, 8.0, 12.0, 16.0, 20.0, 24.0),
                            o -> o instanceof Double);
            environmentFogFarByPhase = builder
                    .comment("各阶段的雾终点（格）。0 = 该阶段保持原版雾，便于让低阶段完全不受影响")
                    .defineList("fogFarByPhase",
                            List.of(0.0, 0.0, 0.0, 0.0, 256.0, 208.0, 160.0, 128.0, 96.0, 72.0, 48.0),
                            o -> o instanceof Double);
            environmentFogTintByPhase = builder
                    .comment("各阶段雾色向 tintR/G/B 混合的强度（0 = 原色，1 = 完全变成目标色）")
                    .defineList("fogTintByPhase",
                            List.of(0.0, 0.0, 0.0, 0.05, 0.10, 0.18, 0.32, 0.45, 0.60, 0.78, 1.0),
                            o -> o instanceof Double);
            environmentFogTintRed = builder
                    .comment("雾色目标 R（0..1）")
                    .defineInRange("fogTintRed", 0.55, 0.0, 1.0);
            environmentFogTintGreen = builder
                    .comment("雾色目标 G（0..1）")
                    .defineInRange("fogTintGreen", 0.10, 0.0, 1.0);
            environmentFogTintBlue = builder
                    .comment("雾色目标 B（0..1）")
                    .defineInRange("fogTintBlue", 0.08, 0.0, 1.0);
            environmentParticlesEnabled = builder
                    .comment("阶段达到 particlePhase 后，玩家周围是否飘散孢子粒子")
                    .define("particlesEnabled", true);
            environmentParticlePhase = builder
                    .comment("从该阶段起出现孢子粒子（默认 6）")
                    .defineInRange("particlePhase", 6, 0, 10);

            // ---- 群系浓雾：进入孢子感染群系时强制浓雾 ----
            environmentBiomeFogEnabled = builder
                    .comment("进入孢子感染群系（sporesrp:infected_biome）时是否强制浓雾")
                    .define("biomeFog", true);
            environmentBiomeFogNear = builder
                    .comment("群系内雾起点（格）")
                    .defineInRange("biomeFogNear", 2.0, 0.0, 256.0);
            environmentBiomeFogFar = builder
                    .comment("群系内雾终点（格）：越小能见度越低")
                    .defineInRange("biomeFogFar", 48.0, 8.0, 512.0);

            environmentParticlesPerSecond = builder
                    .comment("孢子粒子密度（个/秒，默认 20）")
                    .defineInRange("particlesPerSecond", 20.0, 0.0, 120.0);
            builder.pop();

            // ==================== CDU 净化 ====================
            builder.push("cdu");
            cduPurifyRadius = builder
                    .comment("CDU 净化感染群系时的水平半径（格）")
                    .defineInRange("purifyRadius", 16, 1, 128);
            cduPurifyHalfHeight = builder
                    .comment("CDU 净化的垂直半高（格）",
                            "总高度约为该值的两倍")
                    .defineInRange("purifyHalfHeight", 24, 1, 128);
            builder.pop();

        }

        public int getMaxPhase() {
            return phaseThresholds.get().size() - 1;
        }
    }

    public static final Common COMMON;
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }
}
