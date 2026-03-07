# AW2 → MCA Unbound Integration Roadmap (Long-Form)

## Source reference
Primary upstream reference for behavior/assets:
- https://github.com/P3pp3rF1y/AncientWarfare2.git

## Goal (interpreted from product intent)
Do **not** do a literal subsystem-by-subsystem clone. Instead, deliver a fluid MCA-native experience inspired by AW2 and MineColonies:
- MCA villagers perform visible, physical labor jobs.
- AW2-like automation chains exist, but villagers remain central actors.
- Troop command and deployment loops are first-class.
- AW2 food/supplies logistics are preserved as gameplay pressure.
- Structure progression is coherent and data-driven.
- Torque is optional and not a dependency for core gameplay.

## Product decisions (frozen for implementation)
1. **Torque is demoted to optional extension**
   - Core worksite production must function with worker labor alone.
2. **MCA villagers are the only worker entity layer**
   - No separate AW2 NPC stack.
3. **Job system is explicit and inspectable**
   - Assignments, pathing state, current task, and blocked reason exposed to players.
4. **Structures drive unlocks**
   - Building progression unlocks jobs/chains; research is supportive, not mandatory for basic loops.
5. **Supply/Food system affects throughput and combat readiness**
   - Worker productivity and troop endurance are tied to logistics.

## Delivery phases

### Phase 0 — Stabilization (immediate)
- Ensure every placed AW2 block instantiates the correct block entity.
- Validate tick loops, inventories, ownership, and assignment persistence.
- Add debug command outputs for assignment + worksite health.

### Phase 1 — Worker-first job loop
- Define MCA job roles mapped from AW2 function groups:
  - Farmer, Forester, Miner, Crafter, Hauler, Researcher, Soldier.
- Implement deterministic assignment lifecycle:
  - Unassigned → Assigned → Traveling → Working → Returning/Idle.
- Add blocked-state diagnostics:
  - missing tool, missing food, no path, no storage, no valid target.

### Phase 2 — Structure and colony progression
- Introduce structure tiers that unlock:
  - work radius,
  - max workers,
  - allowed recipes/tasks,
  - troop capacity.
- Implement structure registry/data layer for easy balancing.

### Phase 3 — Food and supply logistics (AW2-inspired)
- Add colony stockpile + distribution rules.
- Workers consume food over time; starvation throttles output.
- Troops consume rations and optional ammo on patrol/deploy.
- Haulers move materials between production and storage nodes.

### Phase 4 — Troop command and combat integration
- Add command surfaces:
  - defend area,
  - follow player,
  - patrol route,
  - rally point.
- Use MCA entities with profession/loadout logic (no duplicate NPC stack).
- Integrate upkeep from Phase 3 for non-janky combat loops.

### Phase 5 — Asset/data parity pass
- Import required AW2 visual assets and remap paths/licenses cleanly.
- Replace placeholder textures and verify model/UV correctness.
- Add missing recipe/loot/data definitions tied to structure/job unlocks.

## Technical implementation notes
- Keep behavior in server-authoritative managers + block entities.
- Prefer data-driven tables for jobs, unlocks, and food costs.
- Keep GUI thin; expose state via synced data + translatable messages.
- Add migration guards for persistent state schema changes.

## Validation strategy (required each milestone)
- Unit-level checks for manager serialization and state transitions.
- In-world scripted validation of assignment/path/work/produce loops.
- Regression checks for save/reload and chunk unload/reload.
- Performance checks for large villages (tick budget + scan bounds).

## Definition of done (high-level)
A player can:
1. Place colony structures.
2. Assign MCA villagers to jobs.
3. Observe villagers physically doing useful work.
4. Sustain workers and troops through food/supply.
5. Command troops reliably.
6. Progress colony capabilities through structures without broken/janky loops.

## Initial implementation status (this branch)
- ✅ Phase 0 started: runtime stabilization hooks are now wired through real block entities and persistent colony runtime state.
- ✅ Phase 1 started: worker state diagnostics now track `ASSIGNED/TRAVELING/WORKING/BLOCKED/STARVING` with reasons.
- ✅ Phase 2 started: worksite worker contribution now scales by nearby village completed-building tier.
- ✅ Phase 3 started: village food points are derived from worksite production and consumed as worker rations.

This is the first interconnected foundation pass. Next iterations should refine balancing constants, improve food-source classification, and add in-game UI/commands for richer observability.
