"""Quick end-to-end local Piper audio test without Minecraft."""
from __future__ import annotations

import tempfile
import wave
import winsound
from pathlib import Path

from piper import PiperVoice

MODEL = Path(__file__).resolve().parent / "models" / "sv_SE-nst-medium.onnx"


def main() -> None:
    if not MODEL.is_file():
        raise SystemExit(
            "Saknar röstmodell. Kör python voice\\setup_tts.py först."
        )
    print("Startar svensk Piper...")
    voice = PiperVoice.load(str(MODEL))
    sentence = (
        "God morgon, ers majestät! Jag är Aldric Ravencrest, "
        "och jag är redo att hjälpa er att bygga upp vår stad."
    )
    with tempfile.TemporaryDirectory() as folder:
        destination = Path(folder) / "aldrics_voice_test.wav"
        with wave.open(str(destination), "wb") as wav:
            voice.synthesize_wav(sentence, wav)
        print("Spelar upp Aldrics svenska teströst...")
        winsound.PlaySound(str(destination), winsound.SND_FILENAME)
    print("Klart. Om ljudet hördes fungerar Piper och ljudutgången.")


if __name__ == "__main__":
    main()
