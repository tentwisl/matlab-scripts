# AW2 Integration Branch Audit Report

## Scope of this audit
This report reviews the AW2 integration work on the current branch (`work`) relative to the pre-AW2 baseline commit (`ab6e5cc`).

## Executive summary
- Claude **did add a large AW2 code surface area** (new `net.mca.aw2` package, registration, MCA villager hooks, assets).
- However, there is a **critical architectural blocker**: the AW2 blocks return `null` from `createBlockEntity(...)`, which prevents their block entities from being instantiated at placement time.
- Because block entities are where all behavior lives (automation tick logic, inventories, torque, ownership, assignment), this single issue can make most placed AW2 blocks appear effectively non-functional in-game.
- Asset-wise, AW2 textures are present but are tiny placeholder PNGs (16x16, ~167–174 bytes each), consistent with “basic placeholder textures,” not full production assets.

## What was actually implemented

### 1) New AW2 systems and registries
Claude added a substantial integration package under `common/src/main/java/net/mca/aw2`, including:
- Block registry (`AW2Blocks`), item registry (`AW2Items`), block entity type registry (`AW2BlockEntityTypes`), and bootstrap (`AW2Integration`).
- Worksites, torque generators, research, engineering station logic, warehouse, worker assignment manager, and production tracking.

### 2) MCA integration touchpoints
AW2 bootstrap is called during initialization on all loaders:
- Fabric, Forge, Quilt each call `AW2Integration.bootstrap()`.

Villager integration was also added:
- `VillagerCommandHandler` adds `assignworksite` and worksite unassignment behavior.
- `VillagerTasksMCA` includes `WorkAtWorksiteTask` in working/chore packages.
- `Village` now periodically collects AW2 worksite production logs.

### 3) Resource/model pipeline
- Blockstates, models, and item models were added for AW2 blocks.
- Textures were added for AW2 blocks, but they are small placeholders (all 16x16 and mostly 167–174 bytes).

## Critical blocker found (likely root cause of “does nothing”)
All core AW2 `BlockWithEntity` blocks currently return `null` from `createBlockEntity(...)`:
- `WorksiteBlock`
- `TorqueGeneratorBlock`
- `ResearchTableBlock`
- `EngineeringStationBlock`
- `WarehouseBlock`

Since behavior is in block entities, returning `null` means no BE instance to tick/interact with. That can make:
- no automation
- no torque behavior
- no inventories/state
- no worker assignment effects

In practical terms: this aligns with your report that the integration feels non-functional.

## Additional functional gaps/quality concerns
- Several on-use comments/messages mention GUI workflows, but on-use handlers mainly send chat/status text (or attempt to open warehouse GUI), with no obvious completed UI flow for research/engineering interactions.
- The implementation appears closer to a broad “mechanical scaffold + simulations” than a validated parity port.
- No AW2 recipes/data pack content was found in `data/.../recipes` for the new AW2 item IDs in this branch snapshot, so progression/crafting unlock paths may be incomplete depending on intended design.

## Build/test status in this environment
Attempted compile:
- `./gradlew :common:compileJava --no-daemon`

Result:
- Build could not run to code-compile stage due to environment Java/toolchain incompatibility (`Unsupported class file major version 69`).

So runtime validation could not be completed here, but static audit already found the BE instantiation blocker above.

## Conclusion
Your assessment is directionally correct:
- This is not “nothing,” but it is currently in a state where a major core bug can make the AW2 feature set feel effectively dead in-game.
- Texture work is clearly placeholder-level.
- The branch looks like an ambitious direct scaffolding/port attempt that wasn’t fully wired/validated for functional gameplay.

## Suggested next steps (priority order)
1. Fix all AW2 `createBlockEntity(...)` implementations to return proper BE instances by block type.
2. Validate placement + ticking + interaction for each AW2 block in a dev world.
3. Verify villager assignment loop end-to-end (`assignworksite` → pathing → `onWorkerTick` effect).
4. Add/verify missing recipe/data content and tech-tree gating behavior.
5. Replace placeholder textures with real assets once gameplay functionality is stable.
