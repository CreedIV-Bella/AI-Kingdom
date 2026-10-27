# AI Kingdoms – lokal svensk röststyrning på Windows

## Vad är nytt?

Den gamla prototypen tryckte tangentbordsknappar för att öppna chatten.
I ett modpack kan tangenterna betyda något helt annat (t.ex. öppna inventory),
så Python skrev "Skickat" trots att Minecraft inte fick något kommando.

Den nya prototypen använder **ingen tangentbordssimulering för Minecraft-chatten**.
Whisper transkriberar tal till text, skriver ett tidsstämplat kommando atomiskt
till `%USERPROFILE%\AI-Kingdoms\voice-command.txt`, och Forge-klienten
läser kommandot och skickar det genom Minecrafts vanliga klientanslutning.
Endast en begränsad mängd `/aibots`-kommandon och `/ask` är tillåtna.

OBS: Python-programmet och **nybyggd Java-JAR behövs samtidigt**.

## Steg för steg

1. Avsluta gamla Python-röstprogrammet med `Ctrl+Shift+F12` och stäng Minecraft.
2. Uppdatera GitHub-grenen och bygg om modden:

   ```cmd
   cd /d C:\Users\johan\AI-Kingdom
   git pull --ff-only
   cd forge
   gradlew.bat clean build
   ```

3. Ersätt gamla test-JAR:en i en kopierad CurseForge-profil med nya
   `forge\build\libs\aibots-forge-1.0.0.jar`. Ha bara en AI Bots-JAR.
4. Från projektets rot, i en annan CMD-terminal, starta Python-scriptet
   (samma virtuella miljö som tidigare kan återanvändas):

   ```cmd
   cd /d C:\Users\johan\AI-Kingdom
   voice\.venv\Scripts\python.exe -m pip install -r voice\requirements.txt
   voice\.venv\Scripts\python.exe voice\voice_to_minecraft.py
   ```

5. Välj NPC genom att högerklicka. Verifiera manuellt med `/aibots who`.
6. Stäng Minecraft-chatten. Med spelet i förgrunden trycker du
   **Ctrl+Shift+F8** och pratar i 5 sekunder.
7. Python visar `Hörde ...` och `Röstkommandot är köat`.
8. Minecraft-chatten bör sedan visa `Röstorder skickad: ...` och resultatet
   från `/aibots` eller `/ask`.

**F9 och Ctrl+Alt+V används inte längre.** Om du fortfarande ser `F9` i terminalen
kör du den gamla Python-processen eller inte den uppdaterade koden.

### Testa följande fraser

| Säg | Skickas |
| --- | --- |
| Följ mig | /aibots follow |
| Följe mig (vanlig Whisper-feltolkning) | /aibots follow |
| Följa med (vanlig Whisper-feltolkning) | /aibots follow |
| Stanna här | /aibots stay |
| Inspektera byn | /aibots inspect |
| Vem pratar jag med | /aibots who |
| God morgon, Aldric | /ask God morgon, Aldric |

### Om inget händer

- Om Python loggar `Hörde:` men Minecraft inte visar `Röstorder skickad`,
  dubbelkolla att **nya JAR:en** är installerad. Den gamla JAR:en läser inte röstfilen.
- Kontrollera att `%USERPROFILE%\AI-Kingdoms\voice-command.txt` skapas
  under några ögonblick. Den nya Forge-klienten ska läsa och ta bort filen.
  Ta inte bort filen medan ett kommando behandlas.
- Kontrollera att både Python och Minecraft körs under **samma Windows-användare**.
- Minecraft-chatten måste vara stängd; även andra GUI-fönster ska vara stängda.
- Om Ctrl+Shift+F8 används av ett annat program, ändra `HOTKEY` i
  `voice_to_minecraft.py` (eller sätt variabeln `AI_KINGDOMS_VOICE_HOTKEY`) och starta om skriptet.
- Felaktig transkribering är ett annat problem. Du kan testa en större Whisper-modell,
  men det kostar mer CPU: i CMD kör `set AI_KINGDOMS_WHISPER_MODEL=small`
  direkt innan du startar Python-programmet.
- Filerna är lokala, men en annan process under samma Windows-konto kan ändra
  inbox-filen. Forge godkänner bara säkra, begränsade kommandon; detta är
  inte ett generellt system för fjärrstyrning.

### Säkerhet och begränsningar

- Röst spelas inte upp: det här är endast **tal till text**.
- Modellen för taligenkänning laddas ned en gång. Efter det kan transkribering
  köras lokalt utan internet.
- Whisper kan höra fel. Minecraft visar vilket kommando den tar emot.
- Inga inspelningar sparas, men text kan sparas av Minecrafts chattloggar.
- Minecraft-serverns bekräftelse betyder att kommandot mottogs – det
  garanterar inte att en vägsökning har lyckats eller att ett arbete är färdigt.
- Nästa ljuddel är NPC-specifik svensk TTS (t.ex. Piper) efter att vi har
  verifierat kommandobryggan.

### Om inventory öppnas när du pratar

Den tidigare knappen `Ctrl+Alt+V` aktiverade inventory i det aktuella
modpaketet, trots att röstkommandona nådde Minecraft. Därför är ny standard
**Ctrl+Shift+F8**, och Python försöker stoppa tangenttryckningarna från
att skickas vidare till spelet med `suppress=True` (Windows only).

Kontrollera först att nya koden har hämtats och den **gamla** Python-processen
har avslutats. Om inventory fortfarande öppnas, välj ett kortkommando som
inte är kopplat till någon åtgärd under Minecraft > Inställningar >
Kontroller > Tangentbindningar. Du kan testa t.ex.:

```cmd
set AI_KINGDOMS_VOICE_HOTKEY=ctrl+shift+f6
voice\.venv\Scripts\python.exe voice\voice_to_minecraft.py
```

Vissa spel använder raw input och kan kringgå global keyboard suppression;
därför är rätt tangentbindning i modpaketet sista säkerhetsåtgärden.

## Auto-start och Razer Naga (uppdaterat 2026-10-27)

Du behöver inte manuellt öppna flera terminaler varje gång.
Instruktioner för auto-start av Ollama och Whisper/Piper när Minecraft startas:
se `voice/AUTOSTART_README.md`.

Engångsinstallation via CMD i projektroten:

```cmd
git pull --ff-only
voice\install_autostart.cmd
```

Razer Synapse: mappa musknapp **11** till Ctrl+Shift+F8 för tal,
och musknapp **12** till Ctrl+Shift+F9 för att välja Aldric Ravencrest.
Övriga program lämnas ifred om de redan är igång. Användaren kan
stänga av autostart genom `voice\uninstall_autostart.cmd`.
