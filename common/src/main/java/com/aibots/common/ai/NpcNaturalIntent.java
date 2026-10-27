package com.aibots.common.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic, narrow command understanding for natural Swedish NPC orders.
 * Only pre-approved actions are emitted, never generated shell/game commands.
 */
public final class NpcNaturalIntent {

    public enum Action { FOLLOW, STAY, WANDER, INSPECT, INSPECT_CREATE, COLONY_STATUS }

    private NpcNaturalIntent() {
    }

    private static boolean has(String text, String pattern) {
        return Pattern.compile(pattern).matcher(text).find();
    }

    public static Action parse(String utterance) {
        if (utterance == null || utterance.isBlank()) return null;

        String s = utterance.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]+", " ")
                .replaceAll("\\s+", " ").trim();

        if (s.length() > 240) return null;
        // Read-only questions about the current colony must return real data,
        // even though they look like ordinary natural-language conversation.
        if (has(s, "\\b(hur går det med|status för|rapport om|hur mår|hur många)\\b")
                && has(s, "\\b(byggarna|byggare|builders|byggorden|byggprojekt|"
                        + "kolonin|koloni|byn|vår stad|invånare|arbetare)\\b")) {
            return Action.COLONY_STATUS;
        }

        // Explanations, negated commands, quotations and hypothetical questions
        // are conversation requests, not executable movement orders.
        if (has(s, "\\b(hur|varför|vad händer|förklara|beskriv|berätta|lär mig|"
                + "säg|inte|aldrig|utan att|tänk om|om vi|om du|scenario)\\b")) {
            return null;
        }

        boolean follow = has(s, "\\b(följ|följa|följe|följ med|häng med|"
                + "kom efter|kom hit|komma hit|komma här|gå med mig|kom med)\\b");
        boolean stay = has(s, "\\b(stanna|stå still|stå kvar|vänta här|"
                + "vänta kvar|stanna kvar|sluta gå)\\b");
        boolean wander = has(s, "\\b(vandra|gå runt|gå omkring|ta en promenad)\\b");
        boolean inspect = has(s, "\\b(inspektera|inspektion|undersök|undersöka|"
                + "kontrollera området|kolla runt)\\b");
        int matches = (follow ? 1 : 0) + (stay ? 1 : 0)
                + (wander ? 1 : 0) + (inspect ? 1 : 0);
        if (matches != 1) return null;

        if (follow) return Action.FOLLOW;
        if (stay) return Action.STAY;
        if (wander) return Action.WANDER;
        if (has(s, "\\b(create|fabrik|maskin|mekanisk|kugghjul|"
                + "vattenhjul|transportband)\\b")) {
            return Action.INSPECT_CREATE;
        }
        return Action.INSPECT;
    }
}
