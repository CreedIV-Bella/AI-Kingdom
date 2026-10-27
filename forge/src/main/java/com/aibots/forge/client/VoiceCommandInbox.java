package com.aibots.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small local-only bridge between the Windows speech recognizer and Minecraft.
 *
 * The Python app writes a timestamped, atomic file. The Minecraft client reads
 * it on the game thread and sends *only* allowlisted commands over Minecraft's
 * normal command channel. No simulated keypresses, HTTP listener or network port.
 */
public final class VoiceCommandInbox {

    private static final Path INBOX = Path.of(
            System.getProperty("user.home"), "AI-Kingdoms", "voice-command.txt");
    private static final long MAX_AGE_MS = 15_000L;
    private static int ticks;

    private VoiceCommandInbox() {
    }

    public static void poll(Minecraft minecraft) {
        if (++ticks % 5 != 0 || minecraft.player == null
                || minecraft.getConnection() == null || minecraft.screen != null
                || !Files.isRegularFile(INBOX)) {
            return;
        }

        try {
            // Limit file size even if another local process modifies it.
            if (Files.size(INBOX) > 2048) {
                Files.deleteIfExists(INBOX);
                return;
            }
            String payload = Files.readString(INBOX, StandardCharsets.UTF_8);
            Files.deleteIfExists(INBOX);

            int separator = payload.indexOf('\n');
            if (separator <= 0) {
                return;
            }

            long timestamp;
            try {
                timestamp = Long.parseLong(payload.substring(0, separator).trim());
            } catch (NumberFormatException ignored) {
                return;
            }

            long age = System.currentTimeMillis() - timestamp;
            if (age < -2_000L || age > MAX_AGE_MS) {
                minecraft.player.displayClientMessage(
                        Component.literal("Röstorder ignorerad: den har blivit för gammal."),
                        false);
                return;
            }

            String command = payload.substring(separator + 1).strip();
            if (!allowed(command)) {
                minecraft.player.displayClientMessage(
                        Component.literal("Röstorder avvisad: ogiltigt kommando."),
                        false);
                return;
            }

            // This sends the command through the game's regular client-to-server
            // route, exactly as if the player had issued it in chat.
            minecraft.getConnection().sendCommand(command.substring(1));
            minecraft.player.displayClientMessage(
                    Component.literal("Röstorder skickad: " + command), false);
        } catch (IOException error) {
            System.err.println("[AI Kingdoms] Could not read voice command: "
                    + error.getMessage());
        }
    }

    static boolean allowed(String command) {
        if (command == null || command.length() > 340
                || command.contains("\n") || command.contains("\r")
                || command.indexOf('\0') >= 0) {
            return false;
        }

        return command.equals("/aibots who")
                || command.equals("/aibots follow")
                || command.equals("/aibots stay")
                || command.equals("/aibots wander")
                || command.equals("/aibots status")
                || command.equals("/aibots inspect")
                || (command.startsWith("/ask ")
                    && command.length() > 5);
    }
}
