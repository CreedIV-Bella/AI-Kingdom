"""
Experimental LOCAL Swedish push-to-talk bridge for AI Kingdoms on Windows.

F9 (with Minecraft focused) -> record 5 seconds -> transcribe using faster-whisper
-> paste a safe slash command into the Minecraft chat.

This is a convenience prototype, not a secure network API or NPC voice playback.
No recorded audio files are stored. The speech model downloads once.
"""
from __future__ import annotations

import ctypes
import re
import sys
import threading
import time

import keyboard
import numpy as np
import pyperclip
import sounddevice as sd
from faster_whisper import WhisperModel

SAMPLE_RATE = 16000
SECONDS = 5
HOTKEY = "f9"
EXIT_HOTKEY = "ctrl+shift+f12"
MAX_CHARS = 320

# CPU int8 keeps the GPU free for Minecraft and Ollama. Start with "base".
MODEL = "base"
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
    # Always treat free speech as chat text; never execute arbitrary slash commands.
    cleaned = " ".join(text.split())[:MAX_CHARS].strip()
    if not cleaned:
        return None
    plain = re.sub(r"[^\wåäö ]+", "", cleaned.casefold()).strip()

    if plain in {"följ mig", "kom efter mig", "följ efter mig"}:
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
    return "/ask " + cleaned


def record_and_send() -> None:
    if not busy.acquire(blocking=False):
        print("Upptagen med föregående talkommando.")
        return
    try:
        if not minecraft_is_focused():
            print("Ha Minecraft i förgrunden när du trycker F9.")
            return

        print(f"Spelar in {SECONDS} sekunder... Prata nu!")
        audio = sd.rec(
            int(SECONDS * SAMPLE_RATE),
            samplerate=SAMPLE_RATE,
            channels=1,
            dtype="float32",
        )
        sd.wait()
        # Discard silence instead of sending likely hallucinated transcriptions.
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
        )
        message = " ".join(segment.text.strip() for segment in segments).strip()
        cmd = command_from_speech(message)
        if not cmd:
            print("Ingen begriplig fras registrerades.")
            return

        print(f"Hörde: {message!r} -> {cmd}")
        # The player must still have Minecraft focused and the chat closed.
        if not minecraft_is_focused():
            print("Minecraft tappade fokus. Kommandot skickades inte.")
            return

        pyperclip.copy(cmd)
        keyboard.press_and_release("t")
        time.sleep(0.20)
        keyboard.press_and_release("ctrl+v")
        time.sleep(0.15)
        keyboard.press_and_release("enter")
        print("Skickat till Minecraft.")
    except Exception as error:
        print(f"Röstkommando misslyckades: {error}")
    finally:
        busy.release()


def start_recording() -> None:
    threading.Thread(target=record_and_send, name="ai-kingdoms-voice",
                     daemon=True).start()


def main() -> None:
    global model
    if sys.platform != "win32":
        raise SystemExit("Den här första röstbryggan är avsedd för Windows.")
    print(f"Laddar Whisper {MODEL} (första gången laddas modellen ned)...")
    model = WhisperModel(MODEL, device="cpu", compute_type="int8")
    print(f"Redo! Fokusera Minecraft och tryck {HOTKEY.upper()} för att tala.")
    print(f"Avsluta med {EXIT_HOTKEY.upper()} (globalt kortkommando).")
    keyboard.add_hotkey(HOTKEY, start_recording)
    keyboard.wait(EXIT_HOTKEY)


if __name__ == "__main__":
    main()
