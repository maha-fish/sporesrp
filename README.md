# Spore:SRP!

Spore 附属 Mod 的源码仓库，Forge 与 NeoForge 各占一个分支。

| 平台 | 版本 | 分支 | JDK |
|---|---|---|---|
| Forge | 1.20.1（47.x） | [`forge-1.20.1`](https://github.com/maha-fish/sporesrp/tree/forge-1.20.1) | 17 |
| NeoForge | 1.21.1（21.x） | [`neo-1.21.1`](https://github.com/maha-fish/sporesrp/tree/neo-1.21.1) | 21 |

`main` 分支只放本 README 与 `.gitignore`，不含源码。

---

## 中文

### 环境要求

- 两个分支都需要 JDK（Forge 17 / NeoForge 21），由 Gradle toolchain 校验
- 平台 SDK 由构建插件自动拉取，无需手动安装
- Gradle Wrapper 已随仓库提供，无需本地 Gradle

### 构建

```bash
git clone -b forge-1.20.1 https://github.com/maha-fish/sporesrp.git
cd sporesrp
./gradlew build          # Windows: gradlew.bat build
```

产物在 `build/libs/`。常用任务：

| 任务 | 作用 |
|---|---|
| `build` | 编译并打包 |
| `runClient` / `runServer` | 启动开发环境客户端 / 服务端 |
| `runData` | 数据生成（如分支内配置了 datagen） |

### 设计约束

**没有编译期 Spore 依赖。** 与 Spore 的一切交互都走两条路：

1. 注册名字符串比对（`spore:proto`、`spore:mound`、`spore:scent` …），封在 `util/SporeEntityHelper`
2. 反射读取 Spore 的公开方法（`Mound#getAge`、`Mound#getLinked`）与配置字段，封在 `util/SporeBridge`，全部失败降级

因此 Spore 缺失时 Mod 不会崩，只是相关功能不生效。新增对 Spore 的依赖时请保持这个约定。

### 代码结构

| 包 / 类 | 职责 |
|---|---|
| `data/WorldEvolutionData` | 演化状态（SavedData）。点数、阶段、冷却的**唯一**写入者 |
| `phase/PhaseEffects` | 阶段变化的副作用（播报、属性刷新、事件派发） |
| `handler/*` | 各类事件处理器（刷怪、转化、支援、净化、阶段门控…） |
| `block/`、`item/` | 诱饵方块与物品、创造栏 |
| `config/SporeConfigBridge` | 启动后把配置写进 Spore 的内存 spec 并回写文件 |
| `network/` | 客户端同步包与 HUD 数据持有者 |
| `util/SporeEntityHelper` | 实体判定与生物池，带注册名缓存 |
| `util/SporeEntityCache` | 按维度分组的存活实体索引，每 tick 刷新 |
| `util/SporeBridge` | 反射读取 Spore 内部状态，失败降级 |
| `util/EntityRemoval` | 直写 `removalReason` + 手动发移除包 |
| `client/HUDOverlay` | 演化信息 HUD，本地按 tick 倒数 |

### 两条硬性约定

**① 冷却只能增减，不能绝对设置。**

`WorldEvolutionData.adjustPointCooldown(tick, seconds)` 是唯一入口：`seconds > 0` 累加，`< 0` 扣减，结果不会早于当前 tick。不要新增 `setXxxCooldown`。降阶本身不改冷却。

**② 所有写操作自动同步。**

`setDirty()` 被覆写为「标记待同步」，由 `SporeEntityCache` 的 tick 钩子每 tick 统一 flush 一次。新增改动状态的方法只要照常调 `setDirty()`，不必在调用点手动发同步包。

### 两端 API 差异备忘

迁移代码时基本都会撞上这几处：

| 场景 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| 方块右键 | `Block#use(…, InteractionHand, …)` | 拆成 `useItemOn` / `useWithoutItem` |
| 带方块实体的方块 | 无需实现 | 必须实现 `codec()` |
| 物品附加数据 | `ItemStack` 的 NBT | `DataComponents` |
| 配方目录 | `data/<ns>/recipes/` | `data/<ns>/recipe/` |
| 配方结果键 | `"item"` | `"id"` |
| 作物生长事件 | `BlockEvent.CropGrowEvent.Pre` | `event.level.block.CropGrowEvent.Pre` |
| 作物取消方式 | `Event.Result.DENY` | `CropGrowEvent.Pre.Result.DO_NOT_GROW` |
| 刷怪终局事件 | `MobSpawnEvent.FinalizeSpawn`（嵌套） | `FinalizeSpawnEvent`（顶层） |
| 生产环境反射字段名 | SRG，须用 `ObfuscationReflectionHelper` | 官方映射，可直接 `getDeclaredField` |

---

## English

Source repository for a Spore addon, one branch per mod loader.

| Platform | Version | Branch | JDK |
|---|---|---|---|
| Forge | 1.20.1 (47.x) | [`forge-1.20.1`](https://github.com/maha-fish/sporesrp/tree/forge-1.20.1) | 17 |
| NeoForge | 1.21.1 (21.x) | [`neo-1.21.1`](https://github.com/maha-fish/sporesrp/tree/neo-1.21.1) | 21 |

The `main` branch holds only this README and `.gitignore`; it contains no source code.

### Requirements

- A JDK matching the branch (17 for Forge, 21 for NeoForge); enforced by the Gradle toolchain
- Platform SDKs are fetched by the build plugin — nothing to install manually
- The Gradle wrapper is committed, so no local Gradle is needed

### Building

```bash
git clone -b forge-1.20.1 https://github.com/maha-fish/sporesrp.git
cd sporesrp
./gradlew build          # Windows: gradlew.bat build
```

Outputs land in `build/libs/`. Common tasks:

| Task | Purpose |
|---|---|
| `build` | Compile and package |
| `runClient` / `runServer` | Launch a dev client / server |
| `runData` | Data generation (where configured) |

### Design constraint

**There is no compile-time dependency on Spore.** Every interaction goes through one of two paths:

1. Registry id string comparison (`spore:proto`, `spore:mound`, `spore:scent`, …), contained in `util/SporeEntityHelper`
2. Reflection over Spore's public methods (`Mound#getAge`, `Mound#getLinked`) and config fields, contained in `util/SporeBridge`, with degraded behaviour on failure

As a result the mod does not crash when Spore is absent; the related features simply do nothing. Keep this contract when adding new Spore interactions.

### Code layout

| Package / class | Responsibility |
|---|---|
| `data/WorldEvolutionData` | Evolution state (SavedData). The **only** writer of points, phase and cooldown |
| `phase/PhaseEffects` | Side effects of a phase change (broadcast, attribute refresh, event dispatch) |
| `handler/*` | Event handlers (spawning, conversion, support, purification, phase gating, …) |
| `block/`, `item/` | Decoy block and items, creative tab |
| `config/SporeConfigBridge` | Writes config into Spore's in-memory specs after startup, then flushes to disk |
| `network/` | Client sync packet and HUD data holder |
| `util/SporeEntityHelper` | Entity checks and spawn pools, with registry-id caching |
| `util/SporeEntityCache` | Per-dimension index of living entities, refreshed each tick |
| `util/SporeBridge` | Reflective reads of Spore internals, degrades on failure |
| `util/EntityRemoval` | Writes `removalReason` directly plus sends the removal packet |
| `client/HUDOverlay` | Evolution HUD, counts down locally per tick |

### Two hard rules

**① Cooldown can only be added or subtracted, never set absolutely.**

`WorldEvolutionData.adjustPointCooldown(tick, seconds)` is the single entry point: positive adds, negative subtracts, and the result never lands before the current tick. Do not introduce `setXxxCooldown`. Dropping a phase does not touch the cooldown.

**② Every write syncs itself.**

`setDirty()` is overridden to mark the state dirty, and `SporeEntityCache`'s tick hook flushes it once per tick. Any new mutator only needs to call `setDirty()`; call sites never send sync packets manually.

### Platform API notes

These are the differences you will hit when porting code:

| Case | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Block right-click | `Block#use(…, InteractionHand, …)` | split into `useItemOn` / `useWithoutItem` |
| Block with a block entity | not required | `codec()` must be implemented |
| Item-attached data | `ItemStack` NBT | `DataComponents` |
| Recipe directory | `data/<ns>/recipes/` | `data/<ns>/recipe/` |
| Recipe result key | `"item"` | `"id"` |
| Crop growth event | `BlockEvent.CropGrowEvent.Pre` | `event.level.block.CropGrowEvent.Pre` |
| Cancelling crop growth | `Event.Result.DENY` | `CropGrowEvent.Pre.Result.DO_NOT_GROW` |
| Spawn finalisation | `MobSpawnEvent.FinalizeSpawn` (nested) | `FinalizeSpawnEvent` (top level) |
| Reflection on production field names | SRG — use `ObfuscationReflectionHelper` | official mappings — plain `getDeclaredField` |
