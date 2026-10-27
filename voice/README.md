# AI Kingdoms – experimental Swedish voice input (Windows)

**Status: proof of concept, not a native Minecraft voice mod.**
This runs alongside Minecraft, not inside Ollama. Audio is transcribed
locally and converted into existing `/ask` / `/aibots` commands.

## Setup

Use a Windows Python 3.11 or 3.12 installation (not WSL). Open Windows PowerShell
at this repository's root and run:

```powershell
py -3.12 -m venv voice\.venv
voice\.venv\Scripts\python.exe -m pip install -r voice\requirements.txt
voice\.venv\Scripts\python.exe voice\voice_to_minecraft.py
```

If Python 3.12 isn't installed, install it or use a supported existing Python
version (for example, change the `py -3.12` argument). Install a microphone.
The first run downloads a multilingual faster-whisper base model. Subsequent
recognition can run offline. The model uses CPU so the GPU can serve Minecraft
and Ollama.

1. Start Minecraft, select an NPC by right-clicking on them, and **close chat**.
2. Leave the separate Python window running, focus the Minecraft game window.
3. Press **F9**; speak Swedish for up to five seconds.
4. The script pastes the recognized command into Minecraft chat.
5. To stop the script, press **Ctrl+Shift+F12**.

Examples:
- "Följ mig" -> `/aibots follow`
- "Stanna här" -> `/aibots stay`
- "Vandra" -> `/aibots wander`
- "Inspektera byn" -> `/aibots inspect`
- "Hur många soldater har vi?" -> `/ask Hur många soldater har vi?`

**Limitations**
- No actual audio response from NPCs yet: voice **input** only.
- This experimental solution relies on simulated keyboard input, the Minecraft
  window being focused, and chat being closed. Other mods might bind F9.
- Whisper can mishear commands; check in-game feedback before assuming an order
  was accepted. Only an exact short list of movement/inspection commands is
  executed; other phrases are sent as conversation.
- Can conflict with some hotkey utilities, antivirus policy or fullscreen modes.
- Speech recognition and inference are local after model download. No microphone
  recording is saved, but Minecraft chat and its usual logs can retain text.
- Later versions should use an authenticated local interface and in-game
  push-to-talk; text-to-speech can use offline Piper with Swedish voice models.
