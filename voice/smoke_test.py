"""Non-audio smoke tests for the local voice command mapping.

Run from repo root:
    voice\.venv\Scripts\python.exe voice\smoke_test.py
"""
from voice_to_minecraft import command_from_speech


CASES = {
    "Följa med.": "/aibots follow",
    "Följe mig.": "/aibots follow",
    "Följ mig.": "/aibots follow",
    "Prata med Aldric.": "/aibots select Aldric Ravencrest",
    "Stanna här!": "/aibots stay",
    "Inspektera byn.": "/aibots inspect",
    "Vem pratar jag med?": "/aibots who",
    "Hej Aldric!": "/ask Hej Aldric!",
    "Har vi 500 husarer?": "/ask Har vi 500 husarer?",
}


def main() -> None:
    for heard, expected in CASES.items():
        actual = command_from_speech(heard)
        if actual != expected:
            raise AssertionError(
                f"Voice mapping failed for {heard!r}: {actual!r} != {expected!r}"
            )
    print(f"{len(CASES)} voice command mapping tests passed.")


if __name__ == "__main__":
    main()
