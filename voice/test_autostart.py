"""Tests Minecraft/voice detection without actually opening Minecraft.

Windows only:
    voice\.venv\Scripts\python.exe voice\test_autostart.py
"""
from auto_start import is_minecraft_client, is_ollama_process, is_voice_process


def main() -> None:
    minecraft = {
        "Name": "javaw.exe",
        "ProcessId": 25000,
        "CommandLine": (
            r'"C:\Program Files\Java\bin\javaw.exe" --username Player '
            r'--gameDir "C:\Users\Player\curseforge\minecraft\Instances\AI Kingdoms" '
            "--launchTarget forgeclient"
        ),
    }
    forge = {
        "Name": "javaw.exe",
        "CommandLine": (
            r'"C:\javaw.exe" cpw.mods.modlauncher.Launcher '
            "--launchTarget forgeclient"
        ),
    }
    server = {
        "Name": "java.exe",
        "CommandLine": r'"C:\java.exe" -jar forge-1.20.1-server.jar nogui',
    }
    gradle = {
        "Name": "java.exe",
        "CommandLine": r'"C:\java.exe" org.gradle.wrapper.GradleWrapperMain build',
    }
    assert is_minecraft_client(minecraft)
    assert is_minecraft_client(forge)
    assert not is_minecraft_client(server)
    assert not is_minecraft_client(gradle)

    assert is_voice_process({
        "Name": "python.exe",
        "CommandLine": r'"C:\AI-Kingdom\voice\.venv\Scripts\python.exe" C:\AI-Kingdom\voice\voice_to_minecraft.py'
    })
    assert not is_voice_process({
        "Name": "pythonw.exe", "CommandLine": r'C:\AI-Kingdom\voice\auto_start.py'
    })
    assert is_ollama_process({"Name": "ollama.exe"})
    assert not is_ollama_process({"Name": "python.exe"})
    print("8 auto-start detection tests passed.")


if __name__ == "__main__":
    main()
