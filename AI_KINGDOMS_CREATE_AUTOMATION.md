# AI Kingdoms — Create factory, railway and logistics integration

**Status 2026-10-27:** This is an implementation design, NOT a working autonomous
Create factory builder. A safe, read-only first survey is implemented as
`/aibots create inspect`. It counts real nearby `create:` block IDs in loaded
chunks, groups a few common components and explicitly **does not** verify
physical connectivity, rotation, power, item movement or train schedules.

## Roles

1. Aldric (city steward): decide **why** city needs new infrastructure using
   verified current colony needs and the player-approved strategic goals.
2. Engineer planner: select catalogued, *tested* Create schematics. Plan
   consistent inputs, outputs, rotations, logistics routes, kinetic capacity,
   and collision-free assembly of modules.
3. MineColonies Builders: perform **construction** from Structurize blueprints.
4. Create machines and trains: actually perform processing and transporting
   after commissioning; Builders do not invent schematics or become train drivers.
5. Create inspector: confirm machines are connected, spinning with sufficient
   stress capacity, processing expected recipe inputs, and delivering output.
6. Player: approves large, risky or destructive plans before construction.

## Reading game state (not guessing)

Start with actual block registry IDs and positions. Then add a Forge 1.20.1
Create integration compiled against the *exact installed Create version*.

Read-only view must include:
- Kinetic networks: rotation direction/speed, network identity, source,
  stress capacity versus impact, overstress and blocked consumers.
- Item logistics: source inventories, filtered belts/funnels/tunnels/chutes,
  reachable destinations and recipe input/output inventory locations.
- Fluid logistics: pipes, pump direction, reachable tanks and fluid types.
- Rails: actual train track graph, junctions, signals and train stations;
  unloaded network boundaries must be marked as unknown.
- Train service: assembled trains, existing schedules, loading/unloading
  capabilities and destination inventories.
- MineColonies: verified builder/warehouse inventories, requests, assigned
  workers, job states, valid colony construction zones.

An LLM must never invent connectivity. If a Create API or modpack block cannot
be inspected, label the data **unknown** and request player inspection.

## How to CONNECT prebuilt factories: blueprint ports

Each approved module stores rotated local input/output ports, capability
requirements, required clearance and schematic/version ID. Below is a
**proposed metadata format**, not implemented code:

```json
{
  "id": "ai-kingdoms:create/crushing_line_v1",
  "minecraft": "1.20.1",
  "blocks": {"x": 13, "y": 9, "z": 19},
  "inputs": [
    {"port": "ore_in", "kind": "item", "local": [0, 2, 6], "facing": "west"}
  ],
  "outputs": [
    {"port": "processed_out", "kind": "item", "local": [12, 2, 6], "facing": "east"}
  ],
  "power": {
    "kind": "rotation",
    "port": "main_shaft",
    "local": [5, 1, 0],
    "facing": "north",
    "required_rpm_range": [16, 64]
  },
  "needs_confirmation": true
}
```

The planner can connect modules only if their transformed ports line up
with compatible physical paths. Validate all rotations and coordinate offsets.
Required RPM, stress impact and produced rates must come from actual Create
behavior or measured and tested blueprint metadata — **not guessed values**.

## Build pipeline

```
Verified city deficit (ore or material delivery)
  -> Aldric proposes a factory or new rail segment
  -> Engineer selects known-tested module blueprints
  -> Planner checks terrain, areas, inventories, power, train route graph
  -> Player approves the blueprint preview and project budget
  -> Convert Create schematic to Structurize blueprint where necessary
  -> MineColonies Builder builds from actual resources
  -> Test/commission real kinetic and logistics connections
  -> Monitor output rates, stress limits, deadlocks and deliveries
  -> Mark project complete only after actual measured output
```

Do not give the LLM arbitrary world editing powers or unrestricted
Minecraft command execution.

## Why CreateColonies matters

CreateColonies repairs multiple Create blocks when built with
Structurize/MineColonies blueprints, including Create rails, belts, train
stations, and bogeys. Its Schematic Workbench converts Create schematics
to Structurize blueprints and back.

It **does not** add automatic MineColonies train-riding citizens or
Create-machine-crafting workers. Item logistics and train scheduling require
additional engineering. Confirm dependency versions and modpack compatibility
before installing it.

## First real pilot project

1. In a separate Creative world, build a small working waterwheel-driven
   crushing line with a chest input and output.
2. Run it with real items to measure output and stress capacity; document ports.
3. Scan/export it, convert with CreateColonies if installed and compatible.
4. Have a MineColonies Builder build the converted Structurize blueprint
   from supplied materials **in a backup world**.
5. Confirm the machine assembles, rotates and processes items after being built.
6. Run `/aibots create inspect` close to the built assembly.
7. Later implement an API-powered `/aibots create diagnose` so Aldric can
   identify a broken shaft, missing rotation source or output jam.

## Trains as logistics, not NPC pathfinding

Define stations as actual Create track-network nodes with inventories. Have
Aldric propose a cargo line (e.g. mine -> smeltery -> city warehouse). The
rail planner validates physical connection/turnouts, track clearance, signals
and stations, and schedules trains based on actual availability.

Do not pretend MineColonies colonists board Create trains: this is not
part of CreateColonies' feature set. If transport between cities is wanted,
move **materials** by train and keep citizen movement as its own feature.

## Current gameplay tests

After rebuilding Forge:
- Select Aldric by right-click, then stand next to some Create blocks.
- Run `/aibots create inspect`.
- The result should show counts for Create blocks in a ±10 X/Z, ±4 Y cube,
  for loaded chunks only. Counts may be zero when away from machinery.
- No block must be changed by this read-only command.
- The result must explicitly state **no power, connectivity or rail link
  verification has been performed**.
- Ask `/ask Fungerar vår fabrik?` and confirm it refuses to invent the
  factory's operating status.

## References
- https://www.curseforge.com/minecraft/mc-mods/create
- https://github.com/Creators-of-Create/Create/wiki/Stress-Units%2C-Capacity-and-Impact
- https://www.curseforge.com/minecraft/mc-mods/createcolonies
- https://minecolonies.com/wiki/tutorials/schematics/
