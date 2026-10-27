# AI Kingdoms - automatic Windows startup and Razer Naga

Windows 11 / CurseForge / Minecraft 1.20.1 Forge. Optional helper; no Java mod changes.

## How it works

At Windows login, a lightweight watcher starts silently from the user's Startup folder.
It waits for a CurseForge Minecraft client process (a Java command line with Minecraft client args).
Once Minecraft starts, it checks Ollama's local API at http://127.0.0.1:11434/api/tags.
If Ollama is ready, it leaves it running. If an Ollama process is already starting, it waits.
Otherwise it launches ollama serve without an additional console.
It also checks for an existing voice_to_minecraft.py process; if none, it starts one minimized.
Whisper input and Piper Swedish voice output run together in that program.
When Minecraft closes, only the voice process started by the watcher is terminated.
Ollama is left running to avoid disrupting other local AI programs.
A Windows lock ensures only one watcher runs at once. No admin or registry edits.

## One-time installation (CMD)

    cd /d C:\Users\johan\AI-Kingdom
    git pull --ff-only
    voice\.venv\Scripts\python.exe voice\test_autostart.py
    voice\install_autostart.cmd

This installs a shortcut in the user's Startup folder, and also starts the watcher immediately.
The existing venv must have voice\.venv\Scripts\pythonw.exe.
Existing Ollama and Piper installations are reused; no models are downloaded automatically.
Run Minecraft as usual from CurseForge, including after the next Windows restart.

## Configure Razer Naga side buttons in Razer Synapse

Choose the Naga under Devices, then Customize, click the numbered side button,
and choose Keyboard Function with the key combination. Save the mapping.
Prefer a dedicated Minecraft profile to preserve other games' bindings.

| Naga button | Keyboard shortcut | Function |
| --- | --- | --- |
| 11 | Ctrl + Shift + F8 | Listen to your voice (five seconds) |
| 12 | Ctrl + Shift + F9 | Select Aldric Ravencrest within 32 loaded blocks |

These mouse buttons are not standard Windows mouse XBUTTON1/XBUTTON2 inputs.
Use Synapse to emit keyboard shortcuts, not raw Python mouse button detection.
Keep Synapse running for software remaps as supported by the specific Naga model.
If a shortcut conflicts with Minecraft mod keybindings, choose another unused combination.
The voice process also supports AI_KINGDOMS_VOICE_HOTKEY and AI_KINGDOMS_SELECT_HOTKEY environment settings.

## Diagnose when Minecraft is running

    cd /d C:\Users\johan\AI-Kingdom
    voice\.venv\Scripts\python.exe voice\auto_start.py --check

Expected after services load: Minecraft hittat: JA, Ollama API redo: JA,
Whisper/Piper-process: JA.
The Minecraft launcher may need several seconds to show as a client process.

Read the watcher log:

    type "%USERPROFILE%\AI-Kingdoms\auto-start.log"

An ollama-server.log is also saved in that folder if the watcher starts Ollama.
If Minecraft is detected as NEJ after loading the game, save this diagnostic.

## Disable

Run voice\uninstall_autostart.cmd. It removes the Startup shortcut.
An already-running watcher stops on next logout; it does not uninstall any AI software.
Alternatively open shell:startup from Win+R and delete the AI Kingdoms shortcut.

## Limits

The watcher does not launch Minecraft itself, modify CurseForge or download models.
It checks local process metadata using Windows PowerShell.
A nonstandard launcher may need a detection-rule adjustment.
An already-running Ollama GUI that never provides its local API must be repaired manually.
Stopping voice with Ctrl+Shift+F12 may cause automatic restart while Minecraft is open.
Automatic startup has been committed to GitHub but still needs the user's Windows gameplay test.
