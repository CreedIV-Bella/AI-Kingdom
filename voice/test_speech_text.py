"""No audio model required: test that Piper never speaks raw debug punctuation.

Run: voice\.venv\Scripts\python.exe voice\test_speech_text.py
"""
from tts_swedish import prepare_speech_text

REPORT = (
    "MineColonies: Lakeside (#1), centrum BlockPos{x=331, y=70, z=49}. "
    "Invånare 4/4. Byggnader 12. Builder-hus 3. Builders 3. "
    "Builders: Kase V. Crickett, Jolene G. Fletcher, Kayla N. Bardolf. "
    "Pågående/registrerade arbetsorder: 4. "
    "[#6: Warehouse; nivå 1; tilldelad] "
    "[#10: Town Hall; nivå 1; tilldelad] "
    "[#11: Residence; nivå 1; tilldelad] "
    "[#12: Guard Tower; nivå 1; väntar] "
    "Detta är en avläst serverrapport. Inga byggordrar har ändrats."
)

def main() -> None:
    actual = prepare_speech_text(REPORT)
    expected = (
        "Kolonin Lakeside har 4 av 4 invånare, 12 registrerade byggnader "
        "och 3 byggare. Det finns 4 registrerade arbetsordrar."
    )
    assert actual == expected, (actual, expected)
    assert all(char not in actual for char in ("#", "{", "}", "[", "]", "/"))
    assert prepare_speech_text("God morgon, Aldric! Hur mår du?") == (
        "God morgon, Aldric! Hur mår du?"
    )
    fallback = prepare_speech_text(
        "Pos BlockPos{x=1, y=2, z=3}, [#12: Warehouse], nivå 3/5."
    )
    assert "BlockPos" not in fallback and "#" not in fallback
    assert "nivå 3 eller 5" in fallback, fallback
    print("Speech text normalization tests passed.")
    print("Spoken colony report:", actual)

if __name__ == "__main__":
    main()
