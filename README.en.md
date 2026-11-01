**English** | [中文](README.md)

# Spore:SRP!

Source repository for a Spore addon, one branch per mod loader.

| Platform | Version | Branch | JDK |
|---|---|---|---|
| Forge | 1.20.1 (47.x) | [`forge-1.20.1`](https://github.com/maha-fish/sporesrp/tree/forge-1.20.1) | 17 |
| NeoForge | 1.21.1 (21.x) | [`neo-1.21.1`](https://github.com/maha-fish/sporesrp/tree/neo-1.21.1) | 21 |

The `main` branch holds only the READMEs and `.gitignore`; it contains no source code.

---

## Requirements

- A JDK matching the branch (17 for Forge, 21 for NeoForge); enforced by the Gradle toolchain
- Platform SDKs are fetched by the build plugin — nothing to install manually
- The Gradle wrapper is committed, so no local Gradle is needed

## Building

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

## Design constraint

**There is no compile-time dependency on Spore.** Every interaction goes through one of two paths:

1. Registry id string comparison (`spore:proto`, `spore:mound`, `spore:scent`, …), contained in `util/SporeEntityHelper`
2. Reflection over Spore's public methods (`Mound#getAge`, `Mound#getLinked`) and config fields, contained in `util/SporeBridge`, with degraded behaviour on failure

As a result the mod does not crash when Spore is absent; the related features simply do nothing. Keep this contract when adding new Spore interactions.

## Code layout

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

## Two hard rules

**① Cooldown can only be added or subtracted, never set absolutely.**

`WorldEvolutionData.adjustPointCooldown(tick, seconds)` is the single entry point: positive adds, negative subtracts, and the result never lands before the current tick. Do not introduce `setXxxCooldown`. Dropping a phase does not touch the cooldown.

**② Every write syncs itself.**

`setDirty()` is overridden to mark the state dirty, and `SporeEntityCache`'s tick hook flushes it once per tick. Any new mutator only needs to call `setDirty()`; call sites never send sync packets manually.

## Platform API notes

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
