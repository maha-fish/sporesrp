# Spore:SRP!

面向 **Spore（真菌感染：孢子）** 的附属 Mod，同时维护 Forge 与 NeoForge 两个平台。

| 平台 | 版本 | 源码分支 |
|---|---|---|
| Forge | 1.20.1（47.x） | [`forge-1.20.1`](https://github.com/maha-fish/sporesrp/tree/forge-1.20.1) |
| NeoForge | 1.21.1（21.x） | [`neo-1.21.1`](https://github.com/maha-fish/sporesrp/tree/neo-1.21.1) |

当前版本 **1.9.0**。两个分支各自独立、可单独克隆构建。

> 本 Mod **不包含**任何 Spore 的编译期依赖 —— 它完全通过注册名字符串（`spore:proto`、`spore:mound`、`spore:scent` …）与配置里的名称池来识别 Spore 内容，因此 Spore 缺失时会安静降级而不是崩溃。

---

## 中文

### 这个 Mod 做什么

Spore 本体提供了寄生体、菌囊、演化阶段这套框架，但**演化阶段的压迫感是断的**：阶段除了换更强的怪，几乎不影响世界本身。本 Mod 补上这一环，并修正若干原版实现里的性能与逻辑问题。

### 主要特性

**演化系统**
- 演化点数 / 阶段 / 难度三层结构，全域共享一份数据
- 升阶会延长演化时间冷却，冷却期间无法获得点数；冷却只增不减，且按加法累加
- 降阶不额外收取冷却，代价仅来自诱饵本身

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

不靠改写 Spore 的配置文件。启动完成后直接把目标值写进 Spore 已加载的 Config 对象的内存 spec，立即生效，再序列化回文件。

### 安装

1. 安装对应平台的 Spore（必需）
2. 把 `sporesrp-1.9.0.jar`（1.20.1）或 `sporesrp-1.9.0-neo.jar`（1.21.1）放进 `mods/`
3. 启动游戏

### 构建

对应分支内直接运行：

```bash
./gradlew build        # Windows: gradlew.bat build
```

产物在 `build/libs/` 下。Forge 分支需要 JDK 17，NeoForge 分支需要 JDK 21。

### 说明

贴图为本项目自行生成的原创素材。

---

## English

An addon for **Spore (Fungal Infection: Spore)**, maintained for both Forge and NeoForge.

| Platform | Version | Source branch |
|---|---|---|
| Forge | 1.20.1 (47.x) | [`forge-1.20.1`](https://github.com/maha-fish/sporesrp/tree/forge-1.20.1) |
| NeoForge | 1.21.1 (21.x) | [`neo-1.21.1`](https://github.com/maha-fish/sporesrp/tree/neo-1.21.1) |

Current version: **1.9.0**. Each branch is standalone and can be cloned and built on its own.

> There is **no compile-time dependency** on Spore. Content is identified purely by registry id strings (`spore:proto`, `spore:mound`, `spore:scent`, …) plus config-driven name pools, so the mod degrades quietly instead of crashing when Spore is absent.

### What it does

Spore gives you parasites, mounds and evolution phases — but the pressure of an evolving world stops at "stronger mobs spawn". This mod closes that gap and fixes a number of performance and logic issues along the way.

### Features

**Evolution system**
- Points / phase / difficulty, stored once per world
- Advancing a phase extends the evolution-time cooldown, during which no points can be gained; the cooldown is additive and never reset
- Dropping a phase costs no extra cooldown — only the decoy's own

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

No more rewriting Spore's config files. After startup, target values are written straight into Spore's already-loaded in-memory config specs, taking effect immediately and then being serialised back to disk.

### Installation

1. Install Spore for your platform (required)
2. Drop `sporesrp-1.9.0.jar` (1.20.1) or `sporesrp-1.9.0-neo.jar` (1.21.1) into `mods/`
3. Launch

### Building

Inside the corresponding branch:

```bash
./gradlew build        # Windows: gradlew.bat build
```

Outputs land in `build/libs/`. The Forge branch needs JDK 17, the NeoForge branch needs JDK 21.

### Notes

All textures are original assets generated for this project.
