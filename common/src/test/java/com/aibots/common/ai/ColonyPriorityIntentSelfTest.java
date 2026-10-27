package com.aibots.common.ai;

/** Standalone test: javac -encoding UTF-8 ... && java ... */
public final class ColonyPriorityIntentSelfTest {
    private ColonyPriorityIntentSelfTest() {}

    public static void main(String[] args) {
        expectId("Aldric, prioritera arbetsorder 12.", 12);
        expectId("Kan du prioritera order #12, tack?", 12);
        expectId("Jag vill att du ger prioritet åt nummer 6.", 6);
        expectTower("Aldric, kan du prioritera vakttornet?");
        expectTower("Prioritera Guard Tower!");

        expectNull("Jag vill att du inte prioriterar order 12.");
        expectNull("Hur kan jag prioritera order 12?");
        expectNull("Ska vi prioritera vakttornet?");
        expectNull("Vad är prioritet?");
        expectNull("Prioritera order 0");
        expectNull("Kan du prioritera alla byggnader?");
        expectNull("Vad tycker du om våra builders?");
        System.out.println("12 MineColonies priority intent tests passed.");
    }

    private static void expectId(String message, int id) {
        ColonyPriorityIntent.Order result = ColonyPriorityIntent.parse(message);
        if (result == null || result.guardTower() || result.id() != id) {
            throw new AssertionError(message + ": expected order " + id + ", got " + result);
        }
    }

    private static void expectTower(String message) {
        ColonyPriorityIntent.Order result = ColonyPriorityIntent.parse(message);
        if (result == null || !result.guardTower()) {
            throw new AssertionError(message + ": expected guard tower, got " + result);
        }
    }

    private static void expectNull(String message) {
        if (ColonyPriorityIntent.parse(message) != null) {
            throw new AssertionError(message + ": unexpectedly executable!");
        }
    }
}
