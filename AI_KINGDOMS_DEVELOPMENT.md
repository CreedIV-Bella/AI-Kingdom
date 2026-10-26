# AI Kingdoms – development notes

This repository is a fork of `devoffeed/ai-job-mod`. Original license and notices remain in place.

## v0.2 – persistent NPC identity (first iteration)

### Included

- NPCs spawned **without** an explicit custom name receive a medieval-style first and last name.
- Generated names and one random personality trait are stored in the Minecraft entity NBT and survive saving/reloading the world.
- Existing `/summon ... {CustomName:...}` names are preserved.
- NPCs are marked persistent; they must not despawn solely because the player moves away. This does **not** prevent death, `/kill`, mod conflicts, or deletion of world data.
- NPC replies receive a per-character system prompt: their persistent name, a default royal-advisor role, Swedish-language output, brief replies, and instructions not to invent confirmed in-game facts or claim tasks were completed.
- `setRole` and `setPersonality` are available in code for later expansion.

### Not included yet

- LLM-generated names (the first iteration draws from curated name lists).
- Persistent conversational memory.
- NPC job commands, pathfinding tasks, role-selection GUI, unique skins, voices, or knowledge of live MineColonies/Create data.
- Guaranteed uniqueness of generated names. Distinct NPCs may share a generated name.
- Guaranteed Swedish output for arbitrary conversations. Prompts influence the model but cannot enforce language or prevent hallucinations. Bare name/identity questions use a deterministic response.

## Test on Minecraft Forge 1.20.1

1. Keep the existing working CurseForge profile and world as a backup.
2. Check out the `feature/npc-identity-v0-2` branch in the local clone.
3. Confirm JDK 17 is selected: `java -version`, `javac -version`.
4. From the `forge` folder run `gradlew.bat build`.
5. Find the new jar in `forge/build/libs/`. Use the **reobfuscated** normal release jar, not `*-sources.jar` or `*-dev.jar`.
6. Shut down Minecraft. In a **copy** of your modpack, move the original `aibots-forge-1.0.0.jar` out of `mods/` and put the newly built jar there instead. Do **not** load both jars.
7. Open a **copy** of the creative test world. Run `/summon aibots:ai_bot ~ ~ ~` without `CustomName` and verify that a generated name appears.
8. Right-click the new NPC, then run `/aibots who`: it must show the exact NPC name and role. If it says no NPC is selected, right-click again. Conversation selection is temporary, not persisted across world/server restarts.
9. Ask `/ask Vad heter du?`: the exact persisted name should be returned in Swedish without a language-model call. Repeat using `M` and the same question.
10. Ask `/ask Vad vet du om vårt kungadöme?`: the model should answer in Swedish and avoid inventing real game facts. This is a *behavioral prompt test*, not a guarantee against hallucinations.
11. Save and quit the world. Reopen it and confirm that the name and conversational identity stay the same; right-click again to select the NPC for chat.
12. Test a manually named NPC with `/summon aibots:ai_bot ~ ~ ~ {CustomName:'"Kunglig Rådgivare"'}`; its name must not be overwritten.
13. Ask about troops or resources that have not been provided. The bot should admit that it doesn't know.

**Important:** A prompt improves reliability but cannot eliminate hallucinations. The NPC still has no read-access to verified game state. Later features must retrieve world facts and validate commands in code.

### Feedback

If the build fails, save the error output. If the game crashes, keep `latest.log` and the crash report from the copied CurseForge profile. Do not edit or replace the original backup.

## If a named NPC disappears

A failed `/execute if entity @e[type=aibots:ai_bot,name="Freya Ironwood"]` only proves that the NPC is not **loaded in the current dimension**. It does not inspect entities stored in unloaded chunks or other dimensions.

- First revisit Freya's last known location (in the same world and dimension), to force her chunk to load.
- Then run `/execute as @e[type=aibots:ai_bot] run data get entity @s CustomName` to inspect the names of currently loaded bots.
- If Freya exists in a backup, restore a **copy** of the world from before her disappearance to recover the same entity/UUID.
- If she has died or been deleted, this mod currently has no resurrection/history system. You can summon a **new** bot under that name, but it will not be the original entity:
  `/summon aibots:ai_bot ~ ~ ~ {CustomName:'{"text":"Freya Ironwood"}',PersistenceRequired:1b}`

From this commit forward, new and reloaded NPCs are marked persistent against distance despawning. Build and test this change before merging the pull request. The cause of a specific missing NPC cannot be established from a failed selector alone.

## v0.2 – temporary kingdom-facts guard (regression fix)

AI Kingdoms does not yet have a verified MineColonies/army/resources data connector. The earlier language-model-only system prompt was **not** sufficient: NPCs made up army counts (e.g. 3,500 infantry and 500 hussars).

The new `WorldFactGuard` handles common factual questions about the CURRENT kingdom without calling Ollama, for both `/ask` and the M chat GUI. It replies that verified data is unavailable rather than inventing military strength or resources. Hypothetical strategy questions and explicit requests for creative fiction still reach the model.

**Limitations:** This is a conservative keyword-based temporary guard, not general hallucination detection, not a source of real world data, and it may not catch every phrasing. Other model replies can still hallucinate. The long-term fix is live game-state adapters, typed tool outputs, permission-controlled commands and validation.

To run the small standalone tests from a Windows CMD prompt at the repository root:

```cmd
mkdir build-test
javac -d build-test common\src\main\java\com\aibots\common\ai\WorldFactGuard.java common\src\test\java\com\aibots\common\ai\WorldFactGuardSelfTest.java
java -cp build-test com.aibots.common.ai.WorldFactGuardSelfTest
```

Expected: `10 WorldFactGuard self-tests passed.`

Then build the mod from `forge\` with `gradlew.bat build` and test:
- `/ask Hur många soldater har vårt kungadöme?` => says it has no verified data
- `/ask Har vi 500 husarer?` => says it has no verified data
- `/ask Vilka resurser har vi?` => says it has no verified data
- `/ask Vi har 40 soldater och fienden 70. Föreslå en strategi.` => forwarded to Ollama for hypothetical strategy
