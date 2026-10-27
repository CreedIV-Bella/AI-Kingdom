package com.aibots.common.ai;

/** Can run directly using javac/java JDK17 without launching Minecraft. */
public final class NpcNaturalIntentSelfTest {
    private NpcNaturalIntentSelfTest() {
    }

    public static void main(String[] args) {
        expect("Aldric, skulle du kunna följa med mig?",
                NpcNaturalIntent.Action.FOLLOW);
        expect("Aldric vill du vara så snäll o komma här eller följa med här",
                NpcNaturalIntent.Action.FOLLOW);
        expect("Följe mig.", NpcNaturalIntent.Action.FOLLOW);
        expect("Följa med.", NpcNaturalIntent.Action.FOLLOW);
        expect("Skulle du kunna stanna här och vänta?", NpcNaturalIntent.Action.STAY);
        expect("Aldric, gå runt lite i byn", NpcNaturalIntent.Action.WANDER);
        expect("Kan du inspektera byn?", NpcNaturalIntent.Action.INSPECT);
        expect("Aldric, inspektera Create-maskinerna", NpcNaturalIntent.Action.INSPECT_CREATE);

        expect("Hur kan jag få dig att följa mig?", null);
        expect("Aldric, följ mig inte!", null);
        expect("Vad händer om du följer mig?", null);
        expect("Jag vill prata med dig om vår nya by.", null);
        expect("Gå runt och stanna här", null);
        expect("Aldric, hur går det med byggarna?", NpcNaturalIntent.Action.COLONY_STATUS);
        expect("Kan jag få en rapport om vår koloni?", NpcNaturalIntent.Action.COLONY_STATUS);
        expect("Hur många invånare har vår stad?", NpcNaturalIntent.Action.COLONY_STATUS);

        System.out.println("16 natural-language NPC intent tests passed.");
    }

    private static void expect(String phrase, NpcNaturalIntent.Action expected) {
        NpcNaturalIntent.Action actual = NpcNaturalIntent.parse(phrase);
        if (actual != expected) {
            throw new AssertionError(phrase + ": expected " + expected + ", actual " + actual);
        }
    }
}
