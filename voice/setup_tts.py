"""Download a Swedish Piper voice for offline AI Kingdoms NPC replies.

Supported names come from https://huggingface.co/rhasspy/piper-voices .
This is an explicit one-time install action, not an implicit download when
Minecraft starts. Check each model's MODEL_CARD license before redistribution.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import urllib.request

ROOT = "https://huggingface.co/rhasspy/piper-voices/resolve/main"
MODELS = {
    "sv_SE-nst-medium": "sv/sv_SE/nst/medium",
    "sv_SE-lisa-medium": "sv/sv_SE/lisa/medium",
}
OUT = Path(__file__).resolve().parent / "models"


def download_voice(voice: str) -> None:
    if voice not in MODELS:
        raise ValueError(f"Okänd röst: {voice}; välj bland {', '.join(MODELS)}")
    OUT.mkdir(exist_ok=True, parents=True)
    for extension in (".onnx", ".onnx.json"):
        name = voice + extension
        destination = OUT / name
        if destination.is_file() and destination.stat().st_size > 4000:
            print(f"Redan nedladdad: {destination}")
            continue

        url = f"{ROOT}/{MODELS[voice]}/{name}"
        temp = OUT / (name + ".download")
        print(f"Laddar ner {name} ...")
        try:
            urllib.request.urlretrieve(url, temp)
            min_size = 1_000_000 if extension == ".onnx" else 1000
            if temp.stat().st_size < min_size:
                raise RuntimeError("Röstfilen verkar ofullständig.")
            os.replace(temp, destination)
        finally:
            temp.unlink(missing_ok=True)

        print(f"Sparad: {destination}")
    print(f"Klart! Svensk röstmodell installerad: {voice}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--voice", choices=sorted(MODELS), default="sv_SE-nst-medium")
    args = parser.parse_args()
    download_voice(args.voice)
