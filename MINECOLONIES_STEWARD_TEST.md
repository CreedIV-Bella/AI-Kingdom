# Aldric – MineColonies building queue and new plots

Status 2026-10-27: implementation on the development branch, **not yet Forge-compiled or game-tested**.
This document supersedes the housing-only status in older development notes.

## Behavior (updated: continued search and walking steward)

The owner grants ongoing building authority with `colony auto on`. The planner reads actual
MineColonies records and queues existing buildings or missing essentials. It does not require
per-building approval. Decisions remain deterministic, not free-form LLM planning.

- Existing unfinished buildings can enter the queue; housing upgrades depend on full capacity.
- Missing essentials: Builder, needed Residence, Warehouse, Courier, Restaurant, Forester, Mine, Farm.
- New plots reuse the colony's existing server structure pack and normal survival placement handler.
- Queue target remains twice the registered Builder huts, clamped to 3–12 active/waiting orders.
- New order IDs must be verified; plot registration and actual work-order creation are separate results.

### Search

The old 16–80 block ring search is replaced by a four-block grid over the colony's **loaded chunks**.
Each cell tries every discovered design and all four rotations. The NPC stores its cursor and
candidate count in Forge entity persistent data. Each planning pass checks up to 24 candidates,
then resumes from the following candidate rather than restarting the same small sample.
Changing the blueprint catalog resets the cursor; changes in the loaded chunk list can revisit
some locations. A completed pass defers that building need for a minute so other needs can be
tried. Hinder counts distinguish overlap, unloaded land, colony border, elevation, fluids,
block entities, unfamiliar blocks and inaccessible standing spots.

The checks reserve discovered upgrade footprints, including rotation. Trees with a nearby canopy
and leaves can be accepted for Builder clearance. A natural-terrain rise of up to eight blocks
can be accepted within the blueprint; unsupported foundations, fluids, registered building
footprints and block entities remain rejected. This does NOT terraform land itself, guarantee
that any terrain is buildable, or establish ownership of player-placed natural blocks/logs.
Actual clearing and foundation work still depend on the selected blueprint and MineColonies.

### Physical work cycle

1. Search and choose a candidate.
2. Save a visit task and walk to a clear standing spot near it.
3. On physical arrival, stop for a three-second inspection.
4. Recheck the plot, current need, queue and permissions, then place the hut and request the order.
5. While continuing planning, patrol existing construction sites (or registered buildings).

Long travel uses local waypoints. Doors can be opened. Lack of movement and repeated navigation
failures end the route; an inspection that cannot be reached is abandoned and search continues.
This is real Minecraft navigation, not teleportation. It cannot solve every terrain/path obstacle,
clear a walking route by itself, assign workers or resolve all material/research dependencies.
Builders still perform the actual house construction. Roads are not implemented here.

Planning checks run about every five seconds; inspection/movement is checked every second.
Chat reports are throttled, while `/aibots colony status` includes movement and latest planning.
Saved visits/search cursors resume after world reload. `auto off`, explicit follow/stay/wander,
and explicit go orders cancel the visit and pause autonomous work so the manual order wins.

**The owner-to-Aldric 128-block distance gate is removed.** The owner must still be online in the
same dimension and the NPC/terrain must be loaded. No chunks are force-loaded. Initial colony
binding happens near the colony; thereafter the saved colony ID/dimension determines his scope.
Moving to another colony does not silently grant control over it.

Survival needs the appropriate ordinary hut item in the owner's inventory. Creative needs no
hut item. Moved/prebuilt hut items with NBT are rejected. Normal MineColonies permissions,
Builder levels, distance, research gates and material supply remain active.

## Build and install (Windows CMD)

From the existing clone, first ensure your working tree has no uncommitted work:

```cmd
cd /d C:\Users\johan\AI-Kingdom
git status --short
git switch feature/npc-identity-v0-2
git pull --ff-only
java -version
javac -version
cd forge
gradlew.bat clean build
```

Use JDK 17. If the build fails, keep the current mod JAR and capture the error.
After `BUILD SUCCESSFUL`, close Minecraft, copy the normal release JAR from
`forge\build\libs` to the copied CurseForge profile's `mods`, leaving only one AI Bots JAR.
Test on a copy of the complete world because `develop` now places actual hut blocks.

## Game test

1. In a copied world, stand near Aldric and run:

```mcfunction
/aibots select Aldric Ravencrest
/aibots colony probe
/aibots colony status
/aibots colony auto on
```

Spoken activation: **Aldric, bygg ut staden självständigt, du får bestämma.**

2. Expect continuing search counts and obstacle reasons. During search he should visit actual
   registered construction sites rather than remain in STAY mode indefinitely.
3. Once a suitable plot is found, expect a physical walk and an inspection. No new plot should
   appear until he reaches the inspection spot and the site is revalidated.
4. Confirm the hut's rotation/style, footprint gaps and the real order ID in Town Hall.
5. Block a route: the NPC should report failure and continue searching, not claim successful inspection.
6. Walk more than 128 blocks away while keeping the NPC/colony loaded. Work should continue.
   Unloading the NPC is a different condition and pauses its simulation normally.
7. Save/reload during search and during a visit. Verify continued progress and no duplicate plots/orders.
8. Issue follow/stay or `auto off` during a visit: movement/work must obey the manual order and no
   delayed placement may occur. `auto on` explicitly resumes autonomous work.
9. Existing Residence #11 must not duplicate or block unrelated queued projects. Let a Builder
   complete a job, then check queue refill. Missing hut items/research must not produce false success.

## Validation performed here

- The existing 70 dependency-free Java assertions passed.
- Search regression verifies 384 unique cell/design/rotation combinations across resumed batches,
  bounds and asymmetric footprint rotation.
- Eight visit lifecycle cases passed, including arrival, inspection delay, displacement and timeout.
- JDK 17 parser accepted all 42 common/Forge Java files (syntax only, not type checking).
- New integration contracts inspected against MineColonies v1.20.1-1.1.1278-snapshot
  (`getLoadedChunks`, `getColonyByWorld`) and Structurize v1.20.1-1.0.818 (absolute rotation).
- Full Gradle/Forge compilation remains blocked here by the previously observed
  `UnknownHostException: services.gradle.org` while downloading Gradle 8.1.1.
- The earlier screenshot confirmed blueprint loading reaches site search; **this walking/search
  update has not yet been Forge-compiled or tested in Minecraft**.

## Regression: blocked upgrades and chat

The planner now previews MineColonies builder-level/distance checks before requesting an order. A blocked eligible housing upgrade can select one staffed builder hut upgrade first, with queue priority above waiting projects. An existing builder construction/upgrade prevents redundant builder upgrades. This does not interrupt a builder's current job. Other request failures are retried at most once per minute per NPC/site/level. Automatic identical status reports are suppressed; changed non-action status reports are limited to once per minute. Successful order receipts remain visible.

In a copied world, fill housing with only level-1 builder huts and eligible level-1 housing. Enable auto: expect a builder-hut upgrade to level 2 before a residence upgrade, no repeated level-required warnings every five seconds, and no repeated builder-hut orders while one is pending. After the builder upgrade completes, housing should become eligible. Check a project outside builder range is skipped quietly. Check research-locked requests are not retried faster than one minute. Confirm manual colony status still provides the current report. Full Forge build and in-game verification remain required.

## Full-colony housing priority

When population reaches capacity, residence construction/upgrades join ahead of waiting ordinary work. One extra queue slot is reserved for housing if no residential order already exists. A necessary builder upgrade retains its prerequisite exception. This prevents a full ordinary queue from indefinitely blocking housing; it does not cancel active projects, create residents, provide missing materials, or guarantee a terrain match. Verify with six ordinary queued orders, full housing, and an eligible residence: one residential order may be added, but repeated planning must not keep expanding the queue. If no existing residence can be upgraded, site search still continues for a new residence.

## Placement terrain correction and diagnostics

Level-1 terrain validation now uses level-1 bounds, independently rotated with the blueprint. Future-level horizontal bounds remain reserved against other registered buildings, but terrain within future-only areas no longer rejects the initial hut. Future upgrades still need their own terrain assessment. The natural terrain allowlist now includes replaceable vegetation, overworld base-stone tags, clay, mud, moss, mycelium, snow blocks, tuff, calcite and red sand. Fluids and block entities are checked before this allowlist. Unknown blocks remain rejected.

Search diagnostics record one example blueprint/anchor and the rejected block registry ID and coordinate, or terrain/foundation/anchor/top heights. Server-log diagnostics are throttled to one per minute. Attempt counts explicitly include revisits; they are not unique plots. The search identity changes to restart enumeration under the revised rules.

Game regression: test all four rotations beside (a) terrain that affects only a future upgrade, (b) clay/moss/replaceable plants, (c) a chest, (d) water, and (e) an existing colony building. Initial terrain should accept (a)/(b) when other checks pass, reject (c)/(d)/(e), and only submit an order after the physical visit and revalidation. No successful placement in the user's world has been verified yet. Java parsing passed; full Forge build is still blocked in this environment by Gradle download access.
