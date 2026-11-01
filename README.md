[English](README.en.md) | **中文**

# Spore:SRP!

Spore 附属 Mod 的源码仓库，Forge 与 NeoForge 各占一个分支。

| 平台 | 版本 | 分支 | JDK |
|---|---|---|---|
| Forge | 1.20.1（47.x） | [`forge-1.20.1`](https://github.com/maha-fish/sporesrp/tree/forge-1.20.1) | 17 |
| NeoForge | 1.21.1（21.x） | [`neo-1.21.1`](https://github.com/maha-fish/sporesrp/tree/neo-1.21.1) | 21 |

`main` 分支只放这两份 README 与 `.gitignore`，不含源码。

---

## 环境要求

- 两个分支都需要 JDK（Forge 17 / NeoForge 21），由 Gradle toolchain 校验
- 平台 SDK 由构建插件自动拉取，无需手动安装
- Gradle Wrapper 已随仓库提供，无需本地 Gradle

## 构建

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

## 设计约束

**没有编译期 Spore 依赖。** 与 Spore 的一切交互都走两条路：

1. 注册名字符串比对（`spore:proto`、`spore:mound`、`spore:scent` …），封在 `util/SporeEntityHelper`
2. 反射读取 Spore 的公开方法（`Mound#getAge`、`Mound#getLinked`）与配置字段，封在 `util/SporeBridge`，全部失败降级

因此 Spore 缺失时 Mod 不会崩，只是相关功能不生效。新增对 Spore 的依赖时请保持这个约定。

## 代码结构

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

## 两条硬性约定

**① 冷却只能增减，不能绝对设置。**

`WorldEvolutionData.adjustPointCooldown(tick, seconds)` 是唯一入口：`seconds > 0` 累加，`< 0` 扣减，结果不会早于当前 tick。不要新增 `setXxxCooldown`。降阶本身不改冷却。

**② 所有写操作自动同步。**

`setDirty()` 被覆写为「标记待同步」，由 `SporeEntityCache` 的 tick 钩子每 tick 统一 flush 一次。新增改动状态的方法只要照常调 `setDirty()`，不必在调用点手动发同步包。

## 两端 API 差异备忘

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
