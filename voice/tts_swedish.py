"""Offline Swedish NPC speech output using Piper and Windows audio.

Voice output is opt-in. Install piper-tts and download the voice model in
voice/models/ using setup_tts.py. The Minecraft Forge client writes a single
local speech reply to %USERPROFILE%/AI-Kingdoms/voice-reply.txt.

This process NEVER executes model text as commands. It only plays audio.
"""
from __future__ import annotations

import os
from pathlib import Path
import tempfile
import threading
import time
import wave

VOICE_FOLDER = Path(__file__).resolve().parent / "models"
SPEECH_FILE = Path.home() / "AI-Kingdoms" / "voice-reply.txt"
DEFAULT_VOICE = os.environ.get("AI_KINGDOMS_TTS_VOICE", "sv_SE-nst-medium")
MAX_SPEECH_AGE_MS = 60_000
MAX_UTTERANCE_CHARS = 500


def run_speech_loop(shutdown: threading.Event) -> None:
    """Run on a separate thread; microphone recognition remains responsive."""
    model_path = VOICE_FOLDER / f"{DEFAULT_VOICE}.onnx"
    if not model_path.is_file():
        print("Talad NPC-röst är ännu inte konfigurerad.")
        print(f"Saknar svensk röstmodell: {model_path}")
        print("Se voice/TTS_README.md. Röstkommandon fortsätter fungera.")
        return

    try:
        import winsound
        from piper import PiperVoice
        voice = PiperVoice.load(str(model_path))
    except Exception as error:
        print(f"Kunde inte starta Piper-talsyntesen: {error}")
        print("Kontrollera att piper-tts är installerat i voice-miljön.")
        return

    print(f"NPC-tal aktiverat: {DEFAULT_VOICE}. Aldric kan nu svara högt.")
    while not shutdown.is_set():
        try:
            if not SPEECH_FILE.is_file():
                shutdown.wait(0.25)
                continue

            payload = SPEECH_FILE.read_text(encoding="utf-8")
            SPEECH_FILE.unlink(missing_ok=True)
            parts = payload.splitlines()
            if len(parts) < 3:
                continue
            try:
                sent_ms = int(parts[0])
            except ValueError:
                continue

            age = int(time.time() * 1000) - sent_ms
            if age > MAX_SPEECH_AGE_MS or age < -2_000:
                continue

            speaker = parts[1][:80]
            speech = parts[2].strip()[:MAX_UTTERANCE_CHARS]
            if not speech:
                continue

            print(f"{speaker} säger: {speech}")
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as file:
                wav_path = Path(file.name)

            try:
                with wave.open(str(wav_path), "wb") as wav_file:
                    voice.synthesize_wav(speech, wav_file)
                winsound.PlaySound(str(wav_path), winsound.SND_FILENAME)
            finally:
                wav_path.unlink(missing_ok=True)
        except Exception as error:
            print(f"NPC-tal misslyckades: {error}")
            shutdown.wait(0.5)
