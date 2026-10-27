"""AI Kingdoms background supervisor for Windows / CurseForge.

Run automatically at Windows login (install_autostart.ps1 creates a shortcut).
This lightweight process waits for an actual Minecraft Java client. Once a game
starts, it:
  - uses an already running Ollama API, or launches 'ollama serve' if missing;
  - uses an already running voice_to_minecraft.py, or launches one instance;
  - does NOT download models, open Minecraft, or execute game commands.

It stops only the voice process it personally started after the game exits.
Ollama is left running because other applications may depend on it.
No elevated permissions or server/network listener are required.
"""
from __future__ import annotations

import json
import logging
import msvcrt
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.request import urlopen

HOME = Path.home() / "AI-Kingdoms"
HOME.mkdir(exist_ok=True, parents=True)
LOG = HOME / "auto-start.log"
OLLAMA_LOG = HOME / "ollama-server.log"
LOCK_FILE = HOME / "supervisor.lock"
VOICE_SCRIPT = Path(__file__).resolve().with_name("voice_to_minecraft.py")
OLLAMA_API = "http://127.0.0.1:11434/api/tags"
CHECK_EVERY_SECONDS = 8
PROCESS_QUERY = (
    "$ErrorActionPreference='SilentlyContinue';"
    "Get-CimInstance Win32_Process -Filter "
    "\"Name='javaw.exe' OR Name='java.exe' OR "
    "Name='python.exe' OR Name='pythonw.exe'\" | "
    "Select-Object Name,ProcessId,CommandLine | ConvertTo-Json -Compress"
)


def configure_log() -> None:
    logging.basicConfig(
        filename=LOG,
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        encoding="utf-8",
    )


def current_processes() -> list[dict]:
    """Read only local process metadata; return [] if WMI is temporarily busy."""
    try:
        result = subprocess.run(
            ["powershell.exe", "-NoProfile", "-NonInteractive",
             "-Command", PROCESS_QUERY],
            capture_output=True, text=True, timeout=12, check=True,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        data = result.stdout.strip()
        if not data or data == "null":
            return []
        value = json.loads(data)
        return value if isinstance(value, list) else [value]
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        logging.warning("Cannot read local processes: %s", error)
        return []


def is_minecraft_client(info: dict) -> bool:
    name = str(info.get("Name", "")).casefold()
    if name not in {"javaw.exe", "java.exe"}:
        return False
    cmd = str(info.get("CommandLine") or "").casefold()
    # The Forge client is launched by CurseForge with a game directory,
    # username or Minecraft client launch target. Do not mistake a server
    # or an unrelated Java process for a game session.
    return (
        "--gamedir" in cmd
        and ("--username" in cmd or "forgeclient" in cmd
             or "net.minecraft.client.main.main" in cmd)
    ) or "net.minecraft.client.main.main" in cmd


def is_voice_process(info: dict) -> bool:
    if str(info.get("Name", "")).casefold() not in {"python.exe", "pythonw.exe"}:
        return False
    cmd = str(info.get("CommandLine") or "").casefold()
    return "voice_to_minecraft.py" in cmd


def ollama_is_healthy() -> bool:
    try:
        with urlopen(OLLAMA_API, timeout=2) as response:
            return response.status == 200
    except (OSError, URLError, TimeoutError):
        return False


def ollama_executable() -> str | None:
    found = shutil.which("ollama.exe") or shutil.which("ollama")
    if found:
        return found
    local = Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Ollama" / "ollama.exe"
    return str(local) if local.is_file() else None


def start_ollama() -> None:
    """Call only after confirming the API is down; rate-limited by the loop."""
    executable = ollama_executable()
    if executable is None:
        logging.error("Ollama executable not found; install Ollama for Windows.")
        return
    try:
        with OLLAMA_LOG.open("ab") as log:
            subprocess.Popen(
                [executable, "serve"],
                cwd=str(HOME),
                stdout=log,
                stderr=subprocess.STDOUT,
                stdin=subprocess.DEVNULL,
                creationflags=subprocess.CREATE_NO_WINDOW,
                close_fds=True,
            )
        logging.info("Started Ollama server (only because the API was unavailable).")
    except OSError as error:
        logging.exception("Could not start Ollama: %s", error)


def start_voice() -> subprocess.Popen | None:
    python = Path(sys.executable)
    # For a silent login task, sys.executable is pythonw.exe. Use python.exe
    # for an optional minimized console containing live recognition output.
    console = python.with_name("python.exe")
    if not console.is_file():
        console = python
    if not VOICE_SCRIPT.is_file():
        logging.error("Voice program is missing: %s", VOICE_SCRIPT)
        return None
    try:
        startup = subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startup.wShowWindow = 6  # SW_MINIMIZE
        proc = subprocess.Popen(
            [str(console), str(VOICE_SCRIPT)],
            cwd=str(VOICE_SCRIPT.parent.parent),
            stdin=subprocess.DEVNULL,
            creationflags=subprocess.CREATE_NEW_CONSOLE,
            startupinfo=startup,
        )
        logging.info("Started voice process pid=%d", proc.pid)
        return proc
    except OSError as error:
        logging.exception("Could not start voice program: %s", error)
        return None


def acquire_lock():
    handle = LOCK_FILE.open("a+b")
    if handle.seek(0, os.SEEK_END) == 0:
        handle.write(b"0")
        handle.flush()
    handle.seek(0)
    try:
        msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
    except OSError:
        handle.close()
        return None
    return handle


def main() -> None:
    configure_log()
    lock = acquire_lock()
    if lock is None:
        logging.info("Another AI Kingdoms supervisor is already active.")
        return

    logging.info("AI Kingdoms watcher started. Waiting for Minecraft client.")
    voice_we_started: subprocess.Popen | None = None
    missing_game_checks = 0
    last_ollama_attempt = 0.0
    last_voice_attempt = 0.0
    try:
        while True:
            processes = current_processes()
            # Avoid stopping a voice process because a WMI query briefly failed:
            if not processes:
                time.sleep(CHECK_EVERY_SECONDS)
                continue

            minecraft_running = any(is_minecraft_client(p) for p in processes)
            voice_running = any(is_voice_process(p) for p in processes)

            if minecraft_running:
                missing_game_checks = 0
                if not ollama_is_healthy():
                    if time.monotonic() - last_ollama_attempt >= 45:
                        last_ollama_attempt = time.monotonic()
                        start_ollama()

                if not voice_running and time.monotonic() - last_voice_attempt >= 30:
                    last_voice_attempt = time.monotonic()
                    voice_we_started = start_voice()
            else:
                missing_game_checks += 1
                # Only stop processes started by this supervisor; a manually
                # started voice process must remain under the user's control.
                if missing_game_checks >= 3 and voice_we_started is not None:
                    if voice_we_started.poll() is None:
                        voice_we_started.terminate()
                        logging.info("Stopped our voice process after Minecraft exited.")
                    voice_we_started = None

            time.sleep(CHECK_EVERY_SECONDS)
    except KeyboardInterrupt:
        logging.info("AI Kingdoms supervisor interrupted.")
    finally:
        lock.close()


if __name__ == "__main__":
    if sys.platform != "win32":
        raise SystemExit("The AI Kingdoms supervisor only supports Windows.")
    main()
