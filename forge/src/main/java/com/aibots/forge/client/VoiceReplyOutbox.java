package com.aibots.forge.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Export spoken NPC responses from the Minecraft client to the local Windows
 * Python voice process. This is client-only; never write from a game server.
 */
public final class VoiceReplyOutbox {
    private static final Path DIRECTORY = Path.of(
            System.getProperty("user.home"), "AI-Kingdoms");
    private static final Path OUTBOX = DIRECTORY.resolve("voice-reply.txt");
    private static final Path TEMP = DIRECTORY.resolve("voice-reply.tmp");

    private VoiceReplyOutbox() {
    }

    public static void send(String npcName, String message) {
        if (npcName == null || npcName.isBlank()
                || message == null || message.isBlank()) {
            return;
        }

        // A single outgoing message. Keep a bounded copy and clean line breaks.
        String speaker = npcName.replace('\n', ' ').replace('\r', ' ').strip();
        String line = message.replace('\n', ' ').replace('\r', ' ').strip();
        if (speaker.length() > 80) {
            speaker = speaker.substring(0, 80);
        }
        if (line.length() > 700) {
            line = line.substring(0, 700);
        }

        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(TEMP,
                    System.currentTimeMillis() + "\n" + speaker + "\n" + line + "\n",
                    StandardCharsets.UTF_8);
            try {
                Files.move(TEMP, OUTBOX, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(TEMP, OUTBOX, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            System.err.println("[AI Kingdoms] Voice output failed: "
                    + error.getMessage());
        }
    }
}
