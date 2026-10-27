# AI Kingdoms – Aldric pratar svenska med Piper

Version: **experimentell** – lokal talsyntes för NPC-svar, ännu inte
verifierad i spelarens Windows-modpack. Kombineras med fungerande Whisper
röstinmatning och Minecraft Forge klient. Inga moln-API:er används för talet.

## 1. Uppdatera modden

Avsluta det gamla Python-programmet och Minecraft. I CMD:

```cmd
cd /d C:\Users\johan\AI-Kingdom
git pull --ff-only
cd forge
gradlew.bat clean build
```

När bygget lyckats, ersätt den gamla AI Bots JAR:en i CurseForge-profilens
`mods`-mapp. Använd en backup av världen och behåll bara EN AI Bots-JAR.

## 2. Installera Piper i befintlig Windows-Python-miljö

Öppna en ny CMD-terminal:

```cmd
cd /d C:\Users\johan\AI-Kingdom
voice\.venv\Scripts\python.exe -m pip install "piper-tts==1.8.0"
```

Den här versionen är tillgänglig via PyPI (kontrollera eventuella
kompatibilitetsproblem med ditt system). Piper använder CPU som standard för
att låta Minecraft och Ollama ha GPU:n.

## 3. Hämta svensk röst och testa den fristående

```cmd
voice\.venv\Scripts\python.exe voice\setup_tts.py --voice sv_SE-nst-medium
voice\.venv\Scripts\python.exe voice\test_tts.py
```

Nst är en svensk röst i Pipers modellregister och provet ska spela upp en
svensk mening med Aldrics namn. Håll datorns ljudvolym på en rimlig nivå.

Rösten är en första prototyp, inte nödvändigtvis slutgiltiga Aldric-rösten.
Väljer vi en annan licensierad röst längre fram kan NPC:erna få olika röster.

## 4. Starta röstprogrammet

```cmd
voice\.venv\Scripts\python.exe voice\voice_to_minecraft.py
```

Python ska skriva `NPC-tal aktiverat: sv_SE-nst-medium` när Piper har startat.
Högerklicka på Aldric i Minecraft och ställ frågan:

```
/ask Vad heter du?
```

Han ska då svara i text OCH hans svar ska läsas upp lokalt av Piper.
Ställ därefter en fråga med Ctrl+Shift+F8 och mikrofonen. Frasen kan
gärna vara naturlig, t.ex. "Aldric, skulle du vara så snäll och följa med mig?"

## Felsökning

- **Om ljudtestet misslyckas:** Kontrollera installation, högtalare,
  och att båda Piper-röstfilerna ligger i `voice\models`.
- **Om ljudtestet fungerar men NPC är tyst:** Kontrollera att du har nybyggd
  Java-JAR samt att Python faktiskt visar `NPC-tal aktiverat`. Starta om
  både Minecraft och skriptet efter uppdateringen. Testa `/ask Vad heter du?`
  när rätt NPC är vald.
- **Om texten visas men ljudet är fördröjt:** Piper synthesize_wav och
  Windows ljuduppspelning kan ta lite tid, särskilt samtidigt med Whisper
  och Ollama. Det betyder inte att kommandot misslyckades.
- **Om Windows hindrar installation:** Visa hela feltexten från pip; prova
  inte slumpmässiga binärfiler från tredje part.
- **Om F8-kombinationen öppnar en annan funktion:** Se voice/README.md och
  ändra kortkommandot eller använd en ledig tangent i Minecraft.

## Säkerhet och licens

Röstmodellerna laddas ned på uttrycklig begäran. Piper-kodens och varje
modells licens behöver granskas innan programvara/ljudfiler distribueras.
Installerade röster och genererat ljud används lokalt. MineColonies-order
påverkas inte av röstens installation.

## Status

Denna version innehåller:

- Verklig NPC-text skickas klient-säkert till en lokal talsynteskö.
- Endast den valda NPC:ns svar läses upp; vanliga servermeddelanden och
  chatten från andra spelare ignoreras.
- De senaste 6 samtalsutbytena per NPC sparas i entity NBT.
- Trygga naturliga följ/stanna/vandra/inspektera-intentioner går direkt
  till Minecrafts egna rörelsekommandon (utan LLM-exekvering).
- Ingen autonom byggorder eller MineColonies-worker-styrning ännu.

Referenser:
- https://pypi.org/project/piper-tts/
- https://huggingface.co/rhasspy/piper-voices/tree/main/sv/sv_SE/nst/medium
