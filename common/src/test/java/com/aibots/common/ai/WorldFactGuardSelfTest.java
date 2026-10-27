package com.aibots.common.ai;

/**
 * Standalone checks for the early-world-state guard.
 * Run with plain JDK 17; no Minecraft installation is required.
 */
public final class WorldFactGuardSelfTest {
    private WorldFactGuardSelfTest() {
    }

    public static void main(String[] args) {
        expectBlocked("Hur många soldater har vårt kungadöme?");
        expectBlocked("Hur många husarer har vi?");
        expectBlocked("Vilka resurser har vi?");
        expectBlocked("Vad vet du om vårt kungadöme?");
        expectBlocked("Ge mig en rapport om armén.");
        expectBlocked("Har vi 500 husarer?");
        expectBlocked("Hur många infanterisoldater finns i vår armé?");
        expectBlocked("Hur många husarer har vårt rike?");
        expectBlocked("Vad vet du om våra förråd?");

        expectFree("Hur många soldater hade romarriket?");
        expectFree("Vi har 40 soldater och fienden 70. Föreslå en strategi.");
        expectFree("Hitta på en historia om vårt rike.");
        expectBlocked("Fungerar vår fabrik?");
        expectBlocked("Är vår järnväg igång?");
        expectBlocked("Hur många tåg har vi?");
        expectBlocked("Fungerar maskinerna?");
        expectFree("Hur fungerar Create transportband?");
        expectFree("Föreslå en ny fabrik för vår stad.");
        expectFree("Vad heter du?");

        String unknown = WorldFactGuard.UNKNOWN_WORLD_STATE;
        if (!unknown.contains("vårt") || !unknown.contains("på några")
                || unknown.contains("Ã") || unknown.contains("Â")) {
            throw new AssertionError("Swedish letters decoded incorrectly: " + unknown);
        }
        System.out.println("20 WorldFactGuard self-tests passed (including UTF-8 text).");
    }

    private static void expectBlocked(String input) {
        if (!WorldFactGuard.UNKNOWN_WORLD_STATE.equals(WorldFactGuard.replyIfUnknown(input))) {
            throw new AssertionError("Expected blocked: " + input);
        }
    }

    private static void expectFree(String input) {
        if (WorldFactGuard.replyIfUnknown(input) != null) {
            throw new AssertionError("Expected creative/general response: " + input);
        }
    }
}
