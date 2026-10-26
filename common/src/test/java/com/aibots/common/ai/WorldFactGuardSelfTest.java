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

        expectFree("Hur många soldater hade romarriket?");
        expectFree("Vi har 40 soldater och fienden 70. Föreslå en strategi.");
        expectFree("Hitta på en historia om vårt rike.");
        expectFree("Vad heter du?");

        System.out.println("10 WorldFactGuard self-tests passed.");
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
