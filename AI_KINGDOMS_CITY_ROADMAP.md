# AI Kingdoms – Aldric as autonomous city steward

Updated 2026-10-27. This is the target architecture and a stepwise implementation plan,
not a claim that MineColonies integration or AI city automation already works.

## Goal

Aldric Ravencrest becomes the city steward who can converse in Swedish, speak
with a consistent local voice, read verified colony state, propose expansions,
plan what to build and where, delegate to actual MineColonies workers, and
report real progress. The player remains the sovereign who sets priorities,
permission boundaries and approves major changes.

## Separate responsibilities

### 1. NPC conversational identity
Persistent UUID/name/role, bounded per-NPC memory, chosen Swedish TTS voice.
Model may narrate personality, but may not invent factual world state.

### 2. Read-only world facts (server authoritative)
Minecraft/Forge adapter: nearby blocks, terrain, protected areas, chunks,
pathing, villagers. MineColonies adapter (to be implemented against the
actual installed 1.20.1 mod version): colony ID, claimed area, citizens/jobs,
registered buildings/huts, builder work orders and current states, inventory
requests/shortages and research where available.

Never ask an LLM to guess an integer already obtainable from server API.

### 3. City planner
Aldric receives a structured snapshot and a registry of approved building
blueprints with footprints, material costs, roles and requirements. He outputs
**proposals**, not free-form executable commands.

Example proposal:
```json
{
  "kind": "propose_building",
  "blueprint_id": "ai-kingdoms/decoration/castle/north_tower_1",
  "target": {"x": 160, "y": 72, "z": -210},
  "rotation": 90,
  "reason": "Complete the northern defense line"
}
```

The command validator checks blueprint IDs, buildable colony area, terrain,
footprint overlaps, village paths, height limit, budgets, chunk loading,
protected blocks and other mod restrictions. Rejected proposals include a
real explanation and may be re-planned.

### 4. Approval & permissions

- **Observer** (default): conversation, inspect, propose.
- **Supervisor**: issue low-impact worker requests from a whitelist
  (e.g. inspection or hauling) in validated colony territory.
- **City steward**: auto-queue vetted low-risk buildings under a player-set
  material/resource budget, max simultaneous jobs and area limit.
- **Royal decree**: ask for explicit player approval for demolition,
  huge castle phases, expensive construction and military acts.

No AI-controlled commands or world edits are considered successful until the
underlying Minecraft/MineColonies work order or block state confirms success.

### 5. Execute and observe
MineColonies work order adapter (not yet built) creates legitimate tasks in
the colony system and leaves actual block placement to Builders; maintain
an execution ledger containing order ID, citizen/builder ID if available,
blueprint, coordinates, requested resources, status and confirmations.
No fabricated progress or "it's done" before reading real completion.

### 6. Building catalog and schematic conversion

Two different concepts:

- Create's `.nbt` schematic (Create tools/Schematicannon).
- Structurize's `.blueprint` in a MineColonies style pack.
- The third-party **CreateColonies** compatibility mod (verify installed
  version and dependencies in the actual modpack first) provides a
  **Schematic Workbench** to convert between these formats on Forge 1.20.1.

Official MineColonies docs also describe creating a Structurize blueprint
directly with Scan Tool, placing it through Build Tool, and having a Builder
construct it with supplied materials.

`AI-Kingdoms` style pack plan:

```
blueprints/AI-Kingdoms/pack.json
blueprints/AI-Kingdoms/decorations/castle/gatehouse.blueprint
blueprints/AI-Kingdoms/decorations/castle/north_tower.blueprint
blueprints/AI-Kingdoms/decorations/castle/north_wall.blueprint
blueprints/AI-Kingdoms/decorations/castle/great_hall.blueprint
blueprints/AI-Kingdoms/decorations/castle/throne_hall.blueprint
blueprints/AI-Kingdoms/decorations/castle/barracks_wing.blueprint
```

This illustrates the target catalog structure; no actual blueprints or
`pack.json` are committed yet. Study a real style pack for required metadata.

For functional MineColonies buildings, scans need the proper **hut block
anchor**, and special blocks/tags if required. Ordinary castle towers,
walls and halls are better as decorations unless they serve a genuine
functional hut purpose.

**Mega-castles should be modular**: multiple blueprints/work orders keep
paths, resource lists, ownership, expansion, upgrades and troubleshooting
manageable. Respect Structurize's configured schematic block limits and
concurrent Builders' actual capabilities.

## Next milestones

1. **Voice I/O**: verify timestamped local voice inbox (Ctrl+Alt+V).
   Once it works, add offline Swedish Piper speech output with a persisted
   voice identifier per NPC (Aldric can use a male voice, other citizens can
   have different voices; respect model licenses).
2. **MineColonies integration audit**: pin installed API/version, identify
   colony/worker/work-order classes and dependency setup for ForgeGradle.
   Start with read-only facts and `/aibots colony status`.
3. **City blueprint pack**: build one decorative watchtower in a separate
   Creative world, scan it, import as Structurize blueprint, and manually
   demonstrate a MineColonies Builder building it from materials.
4. **Verified city proposals**: NPC proposes a valid blueprint, placement,
   reason, material cost, and explicit player approval. First run with
   a small building and no automatic placement.
5. **Work-order bridge**: create validated real colony work orders and
   track them until completion or error; AI may retry after inspection.
6. **Automation policy**: budgets, protected areas, limits and daily plans.
7. **Mega-castle project manager**: modular plans, tower/wall/phased build,
   materials and builder task assignment.
8. **Rival kingdom / diplomacy / battles** after economic simulation works.

## Technical/operational limits

- Keep LLM requests asynchronous and bounded; few "brains", many
  low-cost in-game citizens.
- Never force-load arbitrary remote chunks to simulate work.
- Persist pending jobs carefully, account for saved/loaded world state
  and server-authoritative mutations.
- Integrate with mod versions **actually present** in the CurseForge pack,
  not guessed interfaces from a newer MineColonies release.
- Back up worlds and modpack before testing new builders or block editing.
- Keep Apache-2.0 notices and check licensing of third-party schematics,
  textures and TTS voice models before distributing assets.

## References

- https://minecolonies.com/wiki/tutorials/schematics/
- https://minecolonies.com/wiki/tutorials/schematics/style-packs/
- https://minecolonies.com/wiki/items/sceptersteel/
- https://www.curseforge.com/minecraft/mc-mods/createcolonies
- https://www.curseforge.com/minecraft/mc-mods/createcolonies/files/7134076

## 2026-10-27 voice + conversation milestone

The new voice work (still awaiting the user's Minecraft test):
- `voice/setup_tts.py` installs the Swedish Piper **nst-medium** voice from
  the public voice repository; see `voice/TTS_README.md`.
- `voice/test_tts.py` generates and plays Aldric's first Swedish sentence.
- `voice/tts_swedish.py` reads actual NPC server replies through the client
  voice outbox; text is synthesized into WAV by Piper, then played by Windows.
  All speech is local (not a clone of an actor or celebrity).
- Per-NPC last six chat exchanges stored in entity NBT and sent as bounded
  context during future Gemma calls. This is *dialogue* context, not verified
  Minecraft world state. No unlimited permanent life-memory yet.
- `NpcNaturalIntent` and `NpcIntentExecutor` turn polite, natural Swedish
  requests (including 'Aldric, vill du vara så snäll och följa med mig?')
  into explicitly whitelisted commands FOLLOW/STAY/WANDER/INSPECT.
  Other chat remains conversation. The LLM cannot invent arbitrary commands.
- `/aibots colony probe` reports detected MineColonies, Structurize and
  Create versions. This is read-only diagnosis, *not* autonomous building yet.

### Next MineColonies integration implementation (after voice test)

1. Run `/aibots colony probe` and match the actual installed 1.20.1
   Forge MineColonies API dependencies.
2. Implement `MineColoniesSnapshot`: colony ID, bounds, citizen count,
   active Builders, actual assignments/work-order IDs, town hall, builder huts,
   delivery requests. Server read-only, version-pinned APIs.
3. Add `/aibots colony status` and natural 'Hur går det med vår stad?'
   that reads ONLY snapshot facts.
4. Implement city proposal type:
   blueprint, placement, plan and materials, validation and optional
   user approval. No free-form block edits.
5. Add a real queued work-order adapter for one test blueprint (a small
   decorative watchtower), including worker status until finished.
6. Then let Aldric wander/inspect while updating plans in the small village.
   Only after reliable job completion and budget checks should the player
   enable automatic placement for small buildings; later for modular castles.

Compatibility warning: the modpack 'MineColonies – Create & Conquer' has
multiple released versions for 1.20.1, and newer versions may have additional
logistics add-ons. Never assume a particular installed MineColonies build.

## 2026-10-27 – first real MineColonies read-only adapter

Tested installation versions reported by `/aibots colony probe`:
- **MineColonies** `1.20.1-1.1.1278-snapshot`
- **Structurize** `1.20.1-1.0.818`
- **Create** `6.0.8`

The exact source tag for MineColonies has been checked:
`v1.20.1-1.1.1278-snapshot`, including `IColonyManager`,
`IColony`, `ICitizenManager`, `IWorkManager`, and `IWorkOrder`.

**New in code (not yet game-tested):**
- `MineColoniesBridge.status` uses those exact public server API method names via
  read-only reflection. It has no new compile-time mod dependency.
- `/aibots colony status` reads a nearby registered colony: name/ID,
  location, current/capacity population, registered building count, builder
  huts, Builder jobs and real MineColonies work order status.
- Natural question `/ask Aldric, hur går det med byggarna?` is routed to the
  same source of truth without a generative model inventing job progress.
- The bridge **never places blocks or writes work orders**. Integration still
  needs player confirmation and a dedicated typed MineColonies work-order
  adapter before any actual city construction is delegated.

### Test in a backed-up world

```cmd
cd /d C:\Users\johan\AI-Kingdom
git pull --ff-only
cd forge
gradlew.bat clean build
```

After replacing the previous JAR and launching Minecraft:
1. Stand within/near the MineColonies town hall or right-click Aldric near it.
2. Run `/aibots colony status`. Expect noninvented colony and Builder counts,
   and any currently registered real work orders.
3. Ask `/ask Aldric, hur går det med byggarna?` and compare its answer
   with `/aibots colony status` (both should read the same data).
4. If the adapter cannot find a registered colony, go closer to the Town Hall.
   An abandoned vanilla village populated with workers is not necessarily
   registered as a MineColonies colony; check the Town Hall block.
5. Save/restart and verify Aldric's previous voice and movement tests still pass.

The next feature will **not** be arbitrary building control: implement a
safe, API-supported, audit-logged `WorkOrder` bridge for a SINGLE approved
blueprint, then generalize. User requests broad autonomy, which should be
implemented with a snapshot/rollback strategy and a restricted region, not
unrestricted chat-to-command execution.

Reference: https://github.com/ldtteam/minecolonies/tree/v1.20.1-1.1.1278-snapshot

## 2026-10-27 — First write-enabled MineColonies bridge

New (unbuilt/untested in the modpack): authorized reprioritization of a
real *existing unclaimed* work order via MineColonies' WorkManager.
Verified from source tag v1.20.1-1.1.1278-snapshot:
- IWorkOrder.getPriority()/setPriority(int).
- WorkManager.getOrderedList() sorts by **decreasing** priority.
- IWorkManager.setDirty(true), IColony.markDirty().
- MineColonies permissions Action.MANAGE_HUTS via IPermissions.hasPermission(Player, Action).

Runtime bridge action:
- /aibots colony prioritize 12 (requires Aldric selected).
- /ask Aldric, prioritera arbetsorder 12.
- /ask Aldric, kan du prioritera vakttornet?

The named-guard-tower order is discovered by inspecting actual currently
unclaimed work orders and matching localized/English displayed names.
It is NOT hardcoded to Lakeside's work order ID, which can change.
The code refuses ambiguous guard-tower matches, invalid IDs, already-claimed
orders and insufficient MineColonies player permissions.
It sets a new uniquely highest priority, marks the work manager and colony dirty,
and includes actual numeric priorities in the /aibots colony status report.
Most important: this does **NOT** create structures, teleport builders,
abort ongoing jobs or guarantee an available worker/resource stock.
The first practical test should use Lakeside's earlier waiting Guard Tower #12,
only if it still exists and remains unclaimed.

Safety: test in a BACKUP world, and verify the work order priority in the
Town Hall interface and /aibots colony status. You can restore priorities
manually in MineColonies' Town Hall work-orders tab.

Run standalone Java parser tests with JDK 17:
    cd /d C:\Users\johan\AI-Kingdom
    mkdir build-test
    javac -encoding UTF-8 -d build-test common\src\main\java\com\aibots\common\ai\ColonyPriorityIntent.java common\src\test\java\com\aibots\common\ai\ColonyPriorityIntentSelfTest.java
    java -cp build-test com.aibots.common.ai.ColonyPriorityIntentSelfTest

Expected: 12 MineColonies priority intent tests passed.

These tests prove parsing only. They do not compile the Forge mod or verify
MineColonies changes until the user runs gradlew clean build and tests in-game.
## 2026-10-27 — Autonomous city-steward prototype (housing only)

**Committed but needs the user's fresh Forge compile + in-game verification.**
The player does NOT want the Guard Tower as the test; the intended first
project is to diagnose Lakeside housing and automatically queue legitimate
MineColonies work orders for existing registered residential hut sites.

Code: ColonyDevelopmentPlanner + ColonyDevelopmentIntent + steward NBT flags.

Available commands:
- `/aibots colony develop` - analyze housing shortage, and create a new
  MineColonies BUILD or UPGRADE order on ONE registered eligible Residence hut
  when needed, using building.requestUpgrade(player, BlockPos.ZERO).
- `/aibots colony auto on` - colony OWNER explicitly enables steward mode.
  Every ~2 Minecraft minutes, while owner and NPC are nearby and loaded,
  the planner checks housing; it may queue work without asking each time.
- `/aibots colony auto off` - pause automatic checks.
- `/ask Aldric, se vad vi behöver i Lakeside och bygg det.` - one-shot planner.
- `/ask Aldric, bygg ut staden självständigt, du får bestämma.` - opt-in auto.
- `/ask Aldric, pausa stadsplaneringen.` - disable auto.

Important validation:
- MineColonies MANAGE_HUTS is required to create work orders.
- Prevent duplicate Residence orders; if a Residence order is already in
  progress or waiting, he reports that instead of creating a second one.
- Only real, registered MineColonies Residence hut sites with valid existing
  schematic names and level requirements are candidates.
- No removal, arbitrary commands, arbitrary locations, or custom block edits.
- A result is reported as a newly created order ONLY if its new ID is found
  in MineColonies' actual work order registry afterward.
- Newly placed residential huts and road/path layouts are NOT yet possible;
  those need a separately validated Structurize blueprint placement adapter.
- The original Lakeside had 4/4 citizens and a pending/assigned Residence
  order #11, so expect the initial command to report that construction is
  already queued. It is correct to do nothing until that order completes.

Java intent self-test:
    cd /d C:\Users\johan\AI-Kingdom
    mkdir build-test
    javac -encoding UTF-8 -d build-test common\src\main\java\com\aibots\common\ai\ColonyDevelopmentIntent.java common\src\test\java\com\aibots\common\ai\ColonyDevelopmentIntentSelfTest.java
    java -cp build-test com.aibots.common.ai.ColonyDevelopmentIntentSelfTest

Expected: 12 city development intent tests passed.

Game test in a COPY of the world:
    git pull --ff-only
    cd forge
    gradlew.bat clean build

Install the new JAR only if build succeeded. Then select Aldric,
run `/aibots colony status`, `/aibots colony develop`,
`/aibots colony auto on`, and `/aibots colony auto off`. Compare actual
work-order changes with MineColonies Town Hall GUI.

Do NOT assume this prototype constitutes unrestricted city construction.