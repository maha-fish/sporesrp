package com.maha_fish.sporesrp;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;


import java.util.List;


public class Config {
    public static class Common {
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> phaseThresholds;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> healthMultipliers;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> attackMultipliers;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> speedBonuses;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> armorBonuses;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> knockbackResist;
        public final ForgeConfigSpec.ConfigValue<List<? extends Integer>> phaseUpgradeCooldownSeconds;

        // 适应性系统配置
        public final ForgeConfigSpec.BooleanValue enableAdaptation;
        public final ForgeConfigSpec.DoubleValue reductionPerHit;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> baseImmunity;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> finalMaxReduction;

        // 支援系统配置
        public final ForgeConfigSpec.BooleanValue enableSupportSystem;
        public final ForgeConfigSpec.DoubleValue supportBaseChance;
        public final ForgeConfigSpec.DoubleValue supportPhaseIncrement;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> supportLevelMultipliers;
        public final ForgeConfigSpec.DoubleValue supportMoundChance;
        public final ForgeConfigSpec.DoubleValue supportSameLevelChance;
        public final ForgeConfigSpec.DoubleValue supportSecondLowerChance;
        public final ForgeConfigSpec.DoubleValue supportScentChance;
        public final ForgeConfigSpec.IntValue supportCooldownSeconds;
        public final ForgeConfigSpec.DoubleValue supportMinSpawnDistance;
        public final ForgeConfigSpec.DoubleValue supportMaxSpawnDistance;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> poolSmall;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> poolMedium;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> poolLarge;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> poolBoss;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> organoids;

        // 转化系统配置
        public final ForgeConfigSpec.DoubleValue conversionSmallMaxHp;
        public final ForgeConfigSpec.DoubleValue conversionMediumMaxHp;
        public final ForgeConfigSpec.DoubleValue conversionLargeMaxHp;
        public final ForgeConfigSpec.DoubleValue conversionHpThresholdFactor;
        public final ForgeConfigSpec.DoubleValue conversionBaseFactor;
        public final ForgeConfigSpec.DoubleValue conversionMaxChance;
        public final ForgeConfigSpec.IntValue conversionMaxAttempts;
        public final ForgeConfigSpec.BooleanValue enableConversion;

        // 生态系统转化配置
        public final ForgeConfigSpec.BooleanValue enableEcosystemConversion;
        public final ForgeConfigSpec.IntValue conversionIntervalTicks;
        public final ForgeConfigSpec.IntValue conversionBaseRadius;
        public final ForgeConfigSpec.IntValue conversionRadiusPerPhase;
        public final ForgeConfigSpec.DoubleValue conversionChance;
        public final ForgeConfigSpec.IntValue maxBlocksPerBiome;
        public final ForgeConfigSpec.IntValue globalLimitPerTick;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> conversionMappings;

        // 菌囊系统配置
        public final ForgeConfigSpec.BooleanValue enableMoundSystem;
        public final ForgeConfigSpec.IntValue moundSpawnIntervalTicks;
        public final ForgeConfigSpec.DoubleValue moundSpawnBaseChance;
        public final ForgeConfigSpec.DoubleValue moundSpawnPhaseBonus;
        public final ForgeConfigSpec.IntValue moundSpawnRadius;
        public final ForgeConfigSpec.IntValue moundSpawnMaxPerTick;

        public final ForgeConfigSpec.BooleanValue enableTracking;
        public final ForgeConfigSpec.IntValue maxMarkerAmplifier;

        // 感染群系配置（新增）
        public final ForgeConfigSpec.BooleanValue enableInfectedBiome;
        public final ForgeConfigSpec.IntValue protoInitialRadius;
        public final ForgeConfigSpec.IntValue protoRadiusIncrement;
        public final ForgeConfigSpec.LongValue protoIncreaseIntervalMinutes;
        public final ForgeConfigSpec.IntValue biomeVerticalHeight;
        public final ForgeConfigSpec.IntValue maxCommandsPerTick;

        // ========== 演化点数获取/减少配置 ==========
// 击杀/转换获得点数：目标最大生命值倍数
        public final ForgeConfigSpec.DoubleValue killPointMultiplier;
        // Proto 每30秒奖励点数
        public final ForgeConfigSpec.DoubleValue protoPeriodicPoints;
        // Mound 每30秒奖励点数
        public final ForgeConfigSpec.DoubleValue moundPeriodicPoints;
        // 真菌生物死亡扣减点数（按等级）
        public final ForgeConfigSpec.DoubleValue smallDeathPenalty;
        public final ForgeConfigSpec.DoubleValue mediumDeathPenalty;
        public final ForgeConfigSpec.DoubleValue largeDeathPenalty;
        public final ForgeConfigSpec.DoubleValue bossDeathPenalty;
        public final ForgeConfigSpec.DoubleValue protoDeathPenalty;   // 单独扣减（叠加）
        public final ForgeConfigSpec.DoubleValue moundDeathPenalty;   // 单独扣减
        // 群系转换区块奖励点数
        public final ForgeConfigSpec.DoubleValue biomeConvertChunkPoints;

        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> phasePointMultipliers;

        public final ForgeConfigSpec.BooleanValue eternalNightAfterPhase6;

        public final ForgeConfigSpec.ConfigValue<List<? extends String>> moundSupportConfigs; // 各等级支援参数
        public final ForgeConfigSpec.IntValue moundScanIntervalTicks;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> moundLevelPointMultipliers;

        public final ForgeConfigSpec.BooleanValue protoMarkedMoundEnabled;
        public final ForgeConfigSpec.IntValue protoMarkedMoundCheckIntervalTicks;
        public final ForgeConfigSpec.IntValue protoMarkedMoundMoveIntervalTicks;
        public final ForgeConfigSpec.IntValue protoMarkedMoundTargetCount;
        public final ForgeConfigSpec.IntValue protoMarkedMoundMinDistance;
        public final ForgeConfigSpec.IntValue protoMarkedMoundMaxDistance;

        public final ForgeConfigSpec.BooleanValue protoSkillsEnabled;
        public final ForgeConfigSpec.IntValue protoSkill1CooldownTicks;
        public final ForgeConfigSpec.IntValue protoSkill1MoveMinDist;
        public final ForgeConfigSpec.IntValue protoSkill1MoveMaxDist;
        public final ForgeConfigSpec.IntValue protoSkill2CheckIntervalTicks;
        public final ForgeConfigSpec.IntValue protoSkill2CooldownTicks;
        public final ForgeConfigSpec.IntValue protoSkill2TargetCount;
        public final ForgeConfigSpec.IntValue protoSkill2SpawnRange;
        public final ForgeConfigSpec.IntValue protoSkill3CooldownTicks;
        public final ForgeConfigSpec.IntValue protoSkill3SpawnCountMin;
        public final ForgeConfigSpec.IntValue protoSkill3SpawnCountMax;
        public final ForgeConfigSpec.IntValue protoSkillLargePoolThreshold;
        public final ForgeConfigSpec.IntValue protoSkill4CooldownTicks;
        public final ForgeConfigSpec.IntValue protoSkill4SpawnRange;
        public final ForgeConfigSpec.IntValue protoSkill4CheckRange;
        public final ForgeConfigSpec.IntValue protoSkill5CooldownTicks;
        public final ForgeConfigSpec.IntValue protoSkill5SpawnRange;
        public final ForgeConfigSpec.IntValue protoSkill5BossCheckRange;
        public final ForgeConfigSpec.IntValue protoSkill5SpawnCount;

        public final ForgeConfigSpec.BooleanValue protoResurrectionEnabled;
        public final ForgeConfigSpec.IntValue protoResurrectionCheckRadius;
        public final ForgeConfigSpec.IntValue protoResurrectionLargePoolThreshold;
        public final ForgeConfigSpec.DoubleValue protoResurrectionHealthPercent;
        public final ForgeConfigSpec.IntValue protoResurrectionResistanceDuration;
        public final ForgeConfigSpec.IntValue protoResurrectionResistanceAmplifier;
        public final ForgeConfigSpec.DoubleValue protoResurrectionMoveSpeedMultiplier;
        public final ForgeConfigSpec.DoubleValue protoResurrectionPushRadius;
        public final ForgeConfigSpec.DoubleValue protoResurrectionPushStrength;
        public final ForgeConfigSpec.ConfigValue<String> protoResurrectionSoundId;

        // ========== Gastgaber Builder 配置 ==========
        public final ForgeConfigSpec.DoubleValue gastgaberBuilderSpawnChance;
        public final ForgeConfigSpec.DoubleValue gastgaberBuilderCollisionRadius;
        public final ForgeConfigSpec.IntValue gastgaberBuilderMaxPoints;
        public final ForgeConfigSpec.IntValue gastgaberBuilderSmallPoints;
        public final ForgeConfigSpec.IntValue gastgaberBuilderMediumPoints;
        public final ForgeConfigSpec.IntValue gastgaberBuilderLargePoints;
        public final ForgeConfigSpec.IntValue gastgaberBuilderCallInterval;
        public final ForgeConfigSpec.IntValue gastgaberBuilderCallRadius;
        public final ForgeConfigSpec.IntValue gastgaberBuilderCooldownSeconds;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> gastgaberBuilderStructures;

        // ========== 完整心智配置 ==========
        public final ForgeConfigSpec.IntValue fullHivemindMinProtoCount;
        public final ForgeConfigSpec.IntValue fullHivemindMinPhase;
        public final ForgeConfigSpec.DoubleValue fullHivemindHealthMultiplier;
        public final ForgeConfigSpec.IntValue fullHivemindArmorBonus;

        // 完整心智技能配置
        public final ForgeConfigSpec.IntValue fullHivemindSkill1Cooldown;
        public final ForgeConfigSpec.IntValue fullHivemindSkill1MoveMinDist;
        public final ForgeConfigSpec.IntValue fullHivemindSkill1MoveMaxDist;
        public final ForgeConfigSpec.IntValue fullHivemindSkill2CheckInterval;
        public final ForgeConfigSpec.IntValue fullHivemindSkill2Cooldown;
        public final ForgeConfigSpec.IntValue fullHivemindSkill2TargetCount;
        public final ForgeConfigSpec.IntValue fullHivemindSkill2SpawnRange;
        public final ForgeConfigSpec.IntValue fullHivemindSkill3Cooldown;
        public final ForgeConfigSpec.IntValue fullHivemindSkill3SpawnCountMin;
        public final ForgeConfigSpec.IntValue fullHivemindSkill3SpawnCountMax;
        public final ForgeConfigSpec.IntValue fullHivemindSkillLargePoolThreshold;
        public final ForgeConfigSpec.IntValue fullHivemindSkill4Cooldown;
        public final ForgeConfigSpec.IntValue fullHivemindSkill4SpawnRange;
        public final ForgeConfigSpec.IntValue fullHivemindSkill4CheckRange;
        public final ForgeConfigSpec.IntValue fullHivemindSkill5Cooldown;
        public final ForgeConfigSpec.IntValue fullHivemindSkill5SpawnRange;
        public final ForgeConfigSpec.IntValue fullHivemindSkill5BossCheckRange;
        public final ForgeConfigSpec.IntValue fullHivemindSkill5SpawnCount;

        // ========== 完整心智袭击配置 ==========
        public final ForgeConfigSpec.DoubleValue assaultValueIncrementPerTick;
        public final ForgeConfigSpec.IntValue assaultValueIncrementInterval;
        public final ForgeConfigSpec.DoubleValue assaultSmallPoolKillPoints;
        public final ForgeConfigSpec.DoubleValue assaultMediumPoolKillPoints;
        public final ForgeConfigSpec.DoubleValue assaultLargePoolKillPoints;
        public final ForgeConfigSpec.DoubleValue assaultBossPoolKillPoints;
        public final ForgeConfigSpec.IntValue assaultTriggerCheckInterval;
        public final ForgeConfigSpec.IntValue assaultDistanceThreshold;
        public final ForgeConfigSpec.IntValue assaultMaxDuration;
        public final ForgeConfigSpec.IntValue assaultCooldownSeconds;

        // 世界净化之战配置
        public final ForgeConfigSpec.BooleanValue enablePurificationBattle;
        public final ForgeConfigSpec.IntValue playerCheckRadius;



        public final ForgeConfigSpec.IntValue hudX;
        public final ForgeConfigSpec.IntValue hudY;

        // ========== 写入 Spore 配置的目标值（运行时热更新，免重启） ==========
        // 数值保持与原 SporeConfigModifier / SporeDataModifier 完全一致
        public final ForgeConfigSpec.BooleanValue sporeConfigHotApply;
        public final ForgeConfigSpec.DoubleValue sporeMoundRangeDefault;
        public final ForgeConfigSpec.DoubleValue sporeMoundRangeAge2;
        public final ForgeConfigSpec.DoubleValue sporeMoundRangeAge3;
        public final ForgeConfigSpec.DoubleValue sporeMoundRangeAge4;
        public final ForgeConfigSpec.IntValue sporeMoundAgeingCooldown;
        public final ForgeConfigSpec.IntValue sporeMoundInfectionCooldown;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> sporeVerwaSummons;
        public final ForgeConfigSpec.IntValue sporeBiomassLumpKills;
        public final ForgeConfigSpec.IntValue sporeHiveSpawnKills;

        // ========== 阶段门控（SRP 的世界惩罚） ==========
        public final ForgeConfigSpec.BooleanValue enablePhaseGate;
        public final ForgeConfigSpec.ConfigValue<List<? extends Double>> cropGrowthBlockChance;
        public final ForgeConfigSpec.IntValue fishingBlockPhase;
        public final ForgeConfigSpec.IntValue noXpPhase;
        public final ForgeConfigSpec.IntValue healPenaltyPhase;
        public final ForgeConfigSpec.DoubleValue healPenaltyFactor;
        // ========== 诱饵 ==========
        public final ForgeConfigSpec.IntValue decoyAggroRadius;
        public final ForgeConfigSpec.BooleanValue decoyProvokeProto;

        Common(ForgeConfigSpec.Builder builder) {
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

            builder.push("pools");
            poolSmall = builder.comment("小等级生物池")
                    .defineList("small", List.of(
                            "spore:inf_human",
                            "spore:inf_husk",
                            "spore:inf_drowned",
                            "spore:inf_villager",
                            "spore:inf_diseased_villager",
                            "spore:inf_wanderer",
                            "spore:inf_witch",
                            "spore:inf_pillager",
                            "spore:inf_player",
                            "spore:inf_hazmat",
                            "spore:inf_vindicator",
                            "spore:inf_evoker"
                    ), o -> o instanceof String);

            poolMedium = builder.comment("中等级生物池")
                    .defineList("medium", List.of(
                            "spore:knight",
                            "spore:griefer",
                            "spore:braiomil",
                            "spore:busser",
                            "spore:thorn",
                            "spore:jagd",
                            "spore:scavenger",
                            "spore:bloater",
                            "spore:naiad",
                            "spore:leaper",
                            "spore:slasher",
                            "spore:spitter",
                            "spore:volatile",
                            "spore:mephitic",
                            "spore:gorgon",
                            "spore:howler",
                            "spore:stalker",
                            "spore:brute",
                            "spore:nuclea",
                            "spore:protector",
                            "spore:gargoyle",
                            "spore:conductor",
                            "spore:chemist",
                            "spore:inebriater",
                            "spore:saugling",
                            "spore:vanguard",
                            "spore:reaper",
                            "spore:mound",
                            "spore:umarmed",
                            "spore:usurper",
                            "spore:vigil",
                            "spore:braurei",
                            "spore:verva",
                            "spore:delusioner",
                            "spore:reconstructor",
                            "spore:hivetumor"
                    ), o -> o instanceof String);

            poolLarge = builder.comment("大等级生物池")
                    .defineList("large", List.of(
                            "spore:inquisitor",
                            "spore:brot",
                            "spore:grober",
                            "spore:wendigo",
                            "spore:ogre",
                            "spore:hvindicator",
                            "spore:hevoker",
                            "spore:proto"
                    ), o -> o instanceof String);

            poolBoss = builder.comment("Boss等级生物池")
                    .defineList("boss", List.of(
                            "spore:sieger",
                            "spore:howitzer",
                            "spore:stahl",
                            "spore:hohlfresser",
                            "spore:gazenbreacher",
                            "spore:kraken",
                            "spore:leviathan",
                            "spore:hindenburg",
                            "spore:verfall"
                    ), o -> o instanceof String);

            organoids = builder.comment("类器官体列表（用于过滤）")
                    .defineList("organoids", List.of(
                            "spore:mound",
                            "spore:umarmed",
                            "spore:usurper",
                            "spore:vigil",
                            "spore:braurei",
                            "spore:verva",
                            "spore:delusioner",
                            "spore:reconstructor",
                            "spore:proto",
                            "spore:hivetumor"
                    ), o -> o instanceof String);
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
            reductionPerHit = builder.comment("每次受击增加的减伤比例（例如 0.05 = 5%）")
                    .defineInRange("reductionPerHit", 0.05, 0.0, 1.0);
            baseImmunity = builder.comment("各生物等级（0=SMALL,1=MEDIUM,2=LARGE,3=BOSS）在阶段0时的基础减伤上限")
                    .defineList("baseImmunity", List.of(0.05, 0.10, 0.15, 0.20), o -> o instanceof Double);
            finalMaxReduction = builder.comment("各生物等级在最大演化阶段时的最终减伤上限")
                    .defineList("finalMaxReduction", List.of(0.70, 0.85, 0.95, 1.00), o -> o instanceof Double);
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

            builder.pop();

            eternalNightAfterPhase6 = builder
                    .comment("当演化阶段达到6后是否强制永夜（世界时间锁定为夜晚）")
                    .define("eternalNightAfterPhase6", true);

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
                    .defineInRange("smallPoolKillPoints", 5, 0.0, 100);
            assaultMediumPoolKillPoints = builder
                    .comment("玩家击杀 MediumPool 生物获得的袭击值")
                    .defineInRange("mediumPoolKillPoints", 10, 0.0, 100);
            assaultLargePoolKillPoints = builder
                    .comment("玩家击杀 LargePool 生物获得的袭击值")
                    .defineInRange("largePoolKillPoints", 15, 0.0, 100);
            assaultBossPoolKillPoints = builder
                    .comment("玩家击杀 BossPool 生物获得的袭击值")
                    .defineInRange("bossPoolKillPoints", 25, 0.0, 100);
            assaultTriggerCheckInterval = builder
                    .comment("袭击触发检查间隔（tick），默认 5 分钟 = 6000 tick")
                    .defineInRange("triggerCheckInterval", 6000, 600, 12000);
            assaultDistanceThreshold = builder
                    .comment("触发袭击的玩家距离阈值（格）")
                    .defineInRange("distanceThreshold", 64, 32, 512);
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
            // 运行时直接改 Spore 已加载的 ForgeConfigSpec（免重启），随后写回其配置文件。
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

        }

        // 动态获取最大阶段数（阈值列表长度-1）
        public int getMaxPhase() {
            return phaseThresholds.get().size() - 1;
        }
    }

    public static final Common COMMON;
    public static final ForgeConfigSpec COMMON_SPEC;

    static {
        Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }
}