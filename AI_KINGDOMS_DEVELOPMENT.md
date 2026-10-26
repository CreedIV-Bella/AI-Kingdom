# AI Kingdoms – development notes

This repository is a fork of `devoffeed/ai-job-mod`. Original license and notices remain in place.

## v0.2 – persistent NPC identity (first iteration)

### Included

- NPCs spawned **without** an explicit custom name receive a medieval-style first and last name.
- Generated names and one random personality trait are stored in the Minecraft entity NBT and survive saving/reloading the world.
- Existing `/summon ... {CustomName:...}` names are preserved.
- NPC replies receive a per-character system prompt: their persistent name, a default royal-advisor role, Swedish-language output, brief replies, and instructions not to invent confirmed in-game facts or claim tasks were completed.
- `setRole` and `setPersonality` are available in code for later expansion.

### Not included yet

- LLM-generated names (the first iteration draws from curated name lists).
- Persistent conversational memory.
- NPC job commands, pathfinding tasks, role-selection GUI, unique skins, voices, or knowledge of live MineColonies/Create data.
- Guaranteed uniqueness of generated names. Distinct NPCs may share a generated name.

## Test on Minecraft Forge 1.20.1

1. Keep the existing working CurseForge profile and world as a backup.
2. Check out the `feature/npc-identity-v0-2` branch in the local clone.
3. Confirm JDK 17 is selected: `java -version`, `javac -version`.
4. From the `forge` folder run `gradlew.bat build`.
5. Find the new jar in `forge/build/libs/`. Use the **reobfuscated** normal release jar, not `*-sources.jar` or `*-dev.jar`.
6. Shut down Minecraft. In a **copy** of your modpack, move the original `aibots-forge-1.0.0.jar` out of `mods/` and put the newly built jar there instead. Do **not** load both jars.
7. Open a **copy** of the creative test world. Run `/summon aibots:ai_bot ~ ~ ~` without `CustomName` and verify that a generated name appears.
8. Right-click the new NPC, press `M` or use `/ask`, and ask in Swedish: `Vad heter du?`.
9. Save and quit the world. Reopen it and confirm that the name and conversational identity stay the same.
10. Test a manually named NPC with `/summon aibots:ai_bot ~ ~ ~ {CustomName:'"Kunglig Rådgivare"'}`; its name must not be overwritten.
11. Ask about troops or resources that have not been provided. The bot should admit that it doesn't know.

**Important:** A prompt improves reliability but cannot eliminate hallucinations. The NPC still has no read-access to verified game state. Later features must retrieve world facts and validate commands in code.

### Feedback

If the build fails, save the error output. If the game crashes, keep `latest.log` and the crash report from the copied CurseForge profile. Do not edit or replace the original backup.
