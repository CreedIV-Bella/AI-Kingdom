# Aldric – MineColonies building queue and new plots

Status 2026-10-27: implementation on the development branch, **not yet Forge-compiled or game-tested**.
This document supersedes the housing-only status in older development notes.

## Behavior

Aldric uses the server's real colony/building/work-order records. Owner-enabled autonomy does
not ask for approval per building. Decisions are deterministic rules, not an LLM city planner.

- Queue unfinished, registered MineColonies buildings, with housing/builders/logistics first.
- Request housing upgrades when citizen capacity is full.
- An existing housing order prevents another housing expansion, but does not block other work.
- Discover missing essentials: Builder, Residence when needed, Warehouse, Courier
  (`deliveryman`), Restaurant (`cook`), Forester (`lumberjack`), Mine, Farm.
- Use the most common existing structure pack, discover matching level-1 blueprints on the
  server and verify their actual MineColonies hut anchors. This first catalog recognizes
  conventional filenames plus restaurant/courier/forester aliases.
- Place one new plot per planning cycle through MineColonies' `SurvivalHandler.handle`, then
  request BUILD via `requestUpgrade(player, BlockPos.ZERO)`. Builders perform construction;
  there is no instant structure paste or resource spawning.
- Keep a rolling queue of `clamp(2 * registered Builder huts, 3, 12)` total work orders.
  Three Builder huts means a target of six active/waiting projects. Existing orders count.
- New orders go behind existing orders and receive decreasing priorities. Builders choose
  feasible work according to MineColonies; priority is not a guarantee of strict FIFO.
- Confirm new order IDs at the intended location before reporting queued construction.
- If placement succeeds but ordering fails, report a registered plot without a build order.
  The next cycle retries that plot instead of creating another copy.
- Autonomy runs every 2400 ticks (about two minutes at 20 TPS), while the owner is online,
  within 128 blocks and in the same dimension as the loaded NPC. Changed explanations also
  appear in chat; `/aibots colony status` includes the latest planning report for this session.

## Placement constraints in this first implementation

The search uses an unrotated grid 16–80 blocks from the colony center, rotating search sectors
between cycles. It only scans loaded colony land and checks the whole discovered footprint.
It rejects fluids, block entities, non-natural blocks and terrain varying by more than two
blocks. It leaves at least three blocks between registered footprints. New plots record a
persistent footprint covering available levels 1–5 in their hut block entity, so later placement
can leave room for upgrades. MineColonies maintains its own building geometry separately.

Only designs up to 64 blocks on each axis are considered. Disk reads and blueprint decoding
run asynchronously; an initial `Läser ... ritningar` response is expected. With manual
`develop`, run it again after loading; with `auto on`, the next cycle retries automatically.
An unavailable/unknown existing building footprint can block placement rather than inventing
its bounds. The algorithm cannot distinguish player-built natural stone/dirt from terrain;
use the copied test world first. Road planning and arbitrary city-wide LLM decisions are not
implemented. Farmer buildings still need the normal fields/workers setup; this does not assign
citizens or resolve every supply/research dependency.

Survival needs the appropriate ordinary hut item in the owner's inventory. Creative needs no
hut item. Moved/prebuilt hut items with NBT are rejected before calling the handler, avoiding
its special restored-building/instant-paste branch. Normal MineColonies permissions, builder
levels, distance, research gates and material supply still apply.

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

1. Stand near Aldric in Lakeside; keep the Town Hall/build area loaded.
2. Run:

```mcfunction
/aibots select Aldric Ravencrest
/aibots colony probe
/aibots colony status
/aibots colony auto on
```

Equivalent spoken instruction: **Aldric, bygg ut staden självständigt, du får bestämma.**
Autonomy stays enabled over world reloads; the owner must remain online and nearby.

3. Check Town Hall's work-order list. Compare the IDs before/after; existing Residence #11
   must not produce a duplicate, and it must not prevent an unrelated project entering the queue.
4. If the blueprint catalog is loading, wait for the next automatic cycle or run
   `/aibots colony develop` to retry sooner. Check the latest explanation with `colony status`.
5. For new plots, verify the hut block, correct style, gap to neighbours and real work-order ID.
   Let a Builder finish one project and verify the queue can refill later.
6. Repeat `develop` and save/reload: no second plot/order for the same pending requirement.
7. Verify missing hut item, missing research, insufficient Builder level, terrain obstruction and
   full queue yield explanations rather than false completion reports.
8. Pause with `/aibots colony auto off` or **Aldric, pausa stadsplaneringen.**

## Validation performed here

- 70 dependency-free Java assertions passed: build policy (10), development intents (12),
  priority intents (12), natural intents (16), world-fact guard (20).
- JDK 17 parser accepted all 37 common/Forge Java files (syntax only, not type checking).
- Reflection contracts inspected against source tags:
  `ldtteam/minecolonies v1.20.1-1.1.1278-snapshot` and
  `ldtteam/Structurize v1.20.1-1.0.818`.
- Full Gradle/Forge compilation was attempted but blocked before compilation by
  `UnknownHostException: services.gradle.org` while downloading Gradle 8.1.1.
- No Minecraft session or live colony was modified from this environment.
