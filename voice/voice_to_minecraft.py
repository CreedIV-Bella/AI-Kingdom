"""
Local Swedish push-to-talk input for AI Kingdoms (Windows / Minecraft Forge 1.20.1).

Ctrl+Shift+F8 -> capture 5 sec -> faster-whisper -> write a timestamped command
to a private local inbox. Our Forge client sends it directly to Minecraft's
command handler on the game thread. There are NO simulated keyboard presses.

The voice bridge uses only permitted /ask or /aibots commands, never arbitrary
shell commands or unrestricted Minecraft commands. Not a voice-output engine.
"""
from __future__ import annotations

import ctypes
import os
from pathlib import Path
import re
import sys
import threading
import time

import keyboard
from tts_swedish import run_speech_loop
import numpy as np
import sounddevice as sd
from faster_whisper import WhisperModel

SAMPLE_RATE = 16000
SECONDS = 5
HOTKEY = os.environ.get("AI_KINGDOMS_VOICE_HOTKEY", "ctrl+shift+f8")
EXIT_HOTKEY = "ctrl+shift+f12"
MAX_CHARS = 320
MAX_AGE_MS = 15000
MODEL = os.environ.get("AI_KINGDOMS_WHISPER_MODEL", "base")
INBOX = Path.home() / "AI-Kingdoms" / "voice-command.txt"
model: WhisperModel
busy = threading.Lock()


def minecraft_is_focused() -> bool:
    if sys.platform != "win32":
        return False
    hwnd = ctypes.windll.user32.GetForegroundWindow()
    length = ctypes.windll.user32.GetWindowTextLengthW(hwnd)
    title_buffer = ctypes.create_unicode_buffer(length + 1)
    ctypes.windll.user32.GetWindowTextW(hwnd, title_buffer, length + 1)
    title = title_buffer.value.casefold()
    return "minecraft" in title or "minecolonies" in title


def command_from_speech(text: str) -> str | None:
    cleaned = " ".join(text.split())[:MAX_CHARS].strip()
    if not cleaned:
        return None

    # Normalize punctuation/case, but only whitelist very short, unambiguous
    # command phrases. Anything else is a question sent to /ask.
    plain = re.sub(r"[^\wåäö ]+", "", cleaned.casefold()).strip()
    if plain in {
        "följ mig", "följ med", "följe mig", "följa med", "följa mig",
        "kom efter mig", "följ efter mig",
    }:
        return "/aibots follow"
    if plain in {"stanna", "stå still", "stanna här", "vänta här"}:
        return "/aibots stay"
    if plain in {"vandra", "gå runt", "gå omkring"}:
        return "/aibots wander"
    if plain in {"rapportera status", "vad gör du", "visa status"}:
        return "/aibots status"
    if plain in {"inspektera", "inspektera byn", "undersök området",
                 "inspektera området"}:
        return "/aibots inspect"
    if plain in {"vem pratar jag med", "vem är vald"}:
        return "/aibots who"
    if plain in {"välj aldric", "välj aldric ravencrest",
                 "jag vill prata med aldric", "prata med aldric"}:
        return "/aibots select Aldric Ravencrest"
    return "/ask " + cleaned


def queue_command(command: str) -> bool:
    INBOX.parent.mkdir(parents=True, exist_ok=True)
    if INBOX.exists():
        try:
            first_line = INBOX.read_text(encoding="utf-8").splitlines()[0]
            age = int(time.time() * 1000) - int(first_line)
            if 0 <= age < MAX_AGE_MS:
                print("Föregående röstkommando väntar på Minecraft. Skickar inte ett till.")
                return False
        except (OSError, IndexError, ValueError):
            pass

    # Atomic replace prevents the game from seeing a half-written command.
    temporary = INBOX.with_suffix(".tmp")
    temporary.write_text(
        f"{int(time.time() * 1000)}\n{command}\n", encoding="utf-8"
    )
    os.replace(temporary, INBOX)
    return True


def record_and_send() -> None:
    if not busy.acquire(blocking=False):
        print("Upptagen med föregående talkommando.")
        return
    try:
        if not minecraft_is_focused():
            print("Ha Minecraft i förgrunden när du startar inspelningen.")
            return

        print(f"Spelar in {SECONDS} sekunder... Prata nu!")
        audio = sd.rec(
            int(SECONDS * SAMPLE_RATE),
            samplerate=SAMPLE_RATE,
            channels=1,
            dtype="float32",
        )
        sd.wait()

        samples = np.asarray(audio[:, 0], dtype=np.float32)
        if float(np.sqrt(np.mean(samples * samples))) < 0.005:
            print("Ingen tydlig röst upptäcktes.")
            return

        print("Tolkar svenska lokalt...")
        segments, _ = model.transcribe(
            samples,
            language="sv",
            vad_filter=True,
            beam_size=3,
            condition_on_previous_text=False,
            initial_prompt="Minecraft. Aldric Ravencrest. Följ mig. Stanna här. Inspektera byn.",
        )
        message = " ".join(segment.text.strip() for segment in segments).strip()
        command = command_from_speech(message)
        if not command:
            print("Ingen begriplig fras registrerades.")
            return

        print(f"Hörde: {message!r} -> {command}")

        if not minecraft_is_focused():
            print("Minecraft tappade fokus. Röstkommandot skickades inte.")
            return

        if queue_command(command):
            print("Röstkommandot är köat. Minecraft ska bekräfta det i spelchatten.")
    except Exception as error:
        print(f"Röstkommando misslyckades: {error}")
    finally:
        busy.release()


def start_recording() -> None:
    threading.Thread(
        target=record_and_send,
        name="ai-kingdoms-voice",
        daemon=True,
    ).start()


def main() -> None:
    global model
    if sys.platform != "win32":
        raise SystemExit("Den här röstbryggan är avsedd för Windows.")
    print(f"Laddar Whisper {MODEL} (första gången laddas modellen ned)...")
    model = WhisperModel(MODEL, device="cpu", compute_type="int8")
    print(f"Redo! Fokusera Minecraft och tryck {HOTKEY.upper()} för att tala.")
    print(f"Köfil: {INBOX}")
    print(f"Avsluta med {EXIT_HOTKEY.upper()} (globalt kortkommando).")
    # On Windows, prevent the hotkey from also activating a game keybind.
    # Some raw-input configurations may still receive it; choose an unused key there.
    keyboard.add_hotkey(HOTKEY, start_recording, suppress=True, trigger_on_release=True)
    shutdown = threading.Event()
    speech_worker = threading.Thread(
        target=run_speech_loop, args=(shutdown,), name="ai-kingdoms-tts", daemon=True)
    speech_worker.start()
    try:
        keyboard.wait(EXIT_HOTKEY)
    finally:
        shutdown.set()
        speech_worker.join(timeout=2.0)


if __name__ == "__main__":
    main()
