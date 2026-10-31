# Spore:SRP!

面向 **Spore（真菌感染：孢子）** 的附属 Mod，双端同步维护：

| 平台 | 版本 | 构建 |
|---|---|---|
| Forge | 1.20.1（47.x） | ForgeGradle 6 / JDK 17 |
| NeoForge | 1.21.1（21.x） | ModDevGradle 2 / JDK 21 |

当前版本 **1.9.0**。

> 本 Mod **不包含**任何 Spore 的编译期依赖 —— 它完全通过注册名字符串（`spore:proto`、`spore:mound`、`spore:scent` …）与配置里的名称池来识别 Spore 内容，因此 Spore 缺失时会安静降级而不是崩溃。

---

## 中文

### 这个 Mod 做什么

Spore 本体提供了寄生体、菌囊、演化阶段这套框架，但**演化阶段的压迫感是断的**：阶段除了换更强的怪，几乎不影响世界本身。本 Mod 补上这一环，并修正若干原版实现里的性能与逻辑问题。

### 主要特性

**演化系统**
- 演化点数 / 阶段 / 难度三层结构，全域共享一份数据
- 阶段提升与下降都会推进冷却，冷却期间无法获得点数（加法累加，不清零）

**阶段门控 —— 世界越晚越不宜生存**

| 阶段 | 效果 |
|---|---|
| 6+ | 作物生长受阻（7 阶段 30%、8 阶段 60%、9 阶段起 100%）、无法钓鱼 |
| 8+ | 孢子生物死亡不掉落经验 |
| 9+ | 非孢子生物自然回血减半 |

所有阈值与概率都在配置里，可单独关闭或调整曲线。

**诱饵与饵堆**

沿用 SRP（V1.9.13 之后）的数值与规则：

- **单个诱饵** —— 只叠加演化时间冷却，不扣点数
- **饵堆** —— 同一水平面 5×5 正方形的四个角各放一块**同等级**诱饵后右键中心，按中心等级扣演化点数，并吸引周围真菌生物的仇恨；若世界存在 Proto，必定有一只被强制注意到你

8 个等级各有独立物品（`sporesrp:decoy_1` … `decoy_8`）、独立贴图与独立配方。

**净化世界**

净化成功后一次性清扫该维度全部孢子生物，之后用每维度缓存的布尔值做闸门，并在刷怪入口拦截 —— 不再对每只入场实体读取存档数据。

**感染群系刷怪调优**

重写 `sporesrp:infected_biome` 的刷怪配置：`maxCount` 全部改为 1，并大幅收紧 `spawn_costs` 的充能速率，让原版自带的「每区块配额」真正开始限流。

**配置热更新**

不再靠改写 Spore 的配置文件。启动完成后直接把目标值写进 Spore 已加载的 Config 对象的内存 spec，立即生效，再序列化回文件。玩家手改会被纠正，重复开世界不写盘。

### 安装

1. 安装对应平台的 Spore（必需）
2. 把 `sporesrp-1.9.0.jar`（1.20.1）或 `sporesrp-1.9.0-neo.jar`（1.21.1）放进 `mods/`
3. 启动游戏

### 构建

```bash
# Forge 1.20.1（JDK 17）
cd forge-1.20.1-47.4.10-mdk-SporeSRP
gradlew build

# NeoForge 1.21.1（JDK 21）
cd MDK-1.21-ModDevGradle-main-SporeSRP-NEO
gradlew build
```

产物在各自 `build/libs/` 下。

### 指令

需要权限等级 2。

| 指令 | 作用 |
|---|---|
| `/sporesrp info` | 查看当前点数、阶段、冷却、难度 |
| `/sporesrp addpoints <数量>` | 增加演化点数 |
| `/sporesrp setpoints <数量>` | 设置演化点数 |
| `/sporesrp setphase <阶段>` | 强制设置阶段 |
| `/sporesrp addcooldown <秒数>` | 调整点数冷却（正数加、负数减） |
| `/sporesrp difficulty` | 查看/设置难度 |
| `/sporesrp mound info` | 菌囊信息 |
| `/sporesrp hud` | HUD 开关 |
| `/sporesrp cache` | 缓存状态 |
| `/sporesrp battle start\|end` | 净化之战 |

### 文档

| 文件 | 内容 |
|---|---|
| [`改动总汇.md`](改动总汇.md) | 全部改动清单 |
| [`机制与修复清单.md`](机制与修复清单.md) | 问题定位与依据 |
| [`审计与优化报告.md`](审计与优化报告.md) | 性能审计 |
| [`开工方案.md`](开工方案.md) | 分阶段实施方案与进度 |

### 说明

本仓库**不包含**从 Spore / Forge / NeoForge 反编译得到的源码（`_spore_probe`、`_forge_src`、`_neo_src`、`_ref` 均在 `.gitignore` 中）。贴图为本项目自行生成的原创素材。

---

## English

An addon for **Spore (Fungal Infection: Spore)**, maintained on two platforms at once:

| Platform | Version | Build |
|---|---|---|
| Forge | 1.20.1 (47.x) | ForgeGradle 6 / JDK 17 |
| NeoForge | 1.21.1 (21.x) | ModDevGradle 2 / JDK 21 |

Current version: **1.9.0**.

> There is **no compile-time dependency** on Spore. Content is identified purely by registry id strings (`spore:proto`, `spore:mound`, `spore:scent`, …) plus config-driven name pools, so the mod degrades quietly instead of crashing when Spore is absent.

### What it does

Spore gives you parasites, mounds and evolution phases — but the pressure of an evolving world stops at "stronger mobs spawn". This mod closes that gap and fixes a number of performance and logic issues along the way.

### Features

**Evolution system**
- Points / phase / difficulty, stored once per world
- Both phase up and phase **down** extend the cooldown; no point gain while it lasts (additive, never reset to zero)

**Phase gating — the world gets less survivable as it evolves**

| Phase | Effect |
|---|---|
| 6+ | Crops stall (30% at phase 7, 60% at 8, 100% from 9), fishing disabled |
| 8+ | Spore mobs stop dropping experience |
| 9+ | Non-spore mobs heal at half rate |

Every threshold and probability is configurable and can be disabled individually.

**Decoy and decoy pile**

Follows Spore's post-V1.9.13 numbers:

- **Single decoy** — extends the evolution-time cooldown only, no point reduction
- **Pile** — place a same-tier decoy on each corner of a 5×5 square (same Y level) and right-click the centre. Points are reduced according to the **centre** decoy's tier, nearby fungal creatures are taunted onto you, and if any Proto exists in the world, one of them is guaranteed to notice you.

Eight tiers, each a separate item (`sporesrp:decoy_1` … `decoy_8`) with its own texture and recipe.

**World purification**

On success, every spore entity in the dimension is swept once; afterwards a per-dimension cached flag gates spawns at the spawn hook — no more reading saved data for every entity that joins.

**Infected biome spawn tuning**

The spawn list was rewritten so every entry has `maxCount` 1 and `spawn_costs` recharge rates are cut by an order of magnitude, which is what actually makes vanilla's per-chunk budget bite.

**Hot config reload**

No more rewriting Spore's config files. After startup, target values are written straight into Spore's already-loaded in-memory config specs, taking effect immediately and then being serialised back to disk. Manual edits get corrected; repeated world loads don't write.

### Installation

1. Install Spore for your platform (required)
2. Drop `sporesrp-1.9.0.jar` (1.20.1) or `sporesrp-1.9.0-neo.jar` (1.21.1) into `mods/`
3. Launch

### Building

```bash
# Forge 1.20.1 (JDK 17)
cd forge-1.20.1-47.4.10-mdk-SporeSRP
gradlew build

# NeoForge 1.21.1 (JDK 21)
cd MDK-1.21-ModDevGradle-main-SporeSRP-NEO
gradlew build
```

Outputs land in each project's `build/libs/`.

### Commands

Permission level 2 required.

| Command | Purpose |
|---|---|
| `/sporesrp info` | Points, phase, cooldown, difficulty |
| `/sporesrp addpoints <n>` | Add evolution points |
| `/sporesrp setpoints <n>` | Set evolution points |
| `/sporesrp setphase <n>` | Force a phase |
| `/sporesrp addcooldown <s>` | Adjust point cooldown (positive adds, negative subtracts) |
| `/sporesrp difficulty` | View / set difficulty |
| `/sporesrp mound info` | Mound info |
| `/sporesrp hud` | Toggle HUD |
| `/sporesrp cache` | Cache status |
| `/sporesrp battle start\|end` | Purification battle |

### Documents

All design notes are in Chinese:

| File | Contents |
|---|---|
| [`改动总汇.md`](改动总汇.md) | Full changelog |
| [`机制与修复清单.md`](机制与修复清单.md) | Issue list with evidence |
| [`审计与优化报告.md`](审计与优化报告.md) | Performance audit |
| [`开工方案.md`](开工方案.md) | Phased plan and progress |

### Notes

This repository does **not** contain any code decompiled from Spore, Forge or NeoForge (`_spore_probe`, `_forge_src`, `_neo_src`, `_ref` are all git-ignored). All textures are original assets generated for this project.
