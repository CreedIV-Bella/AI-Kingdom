package com.aibots.common.ai;

import java.util.Locale;

/**
 * Temporary fact gateway until the mod can read verified MineColonies/Minecraft state.
 *
 * This intentionally handles a narrow class of questions about the CURRENT kingdom.
 * For those questions we never ask a language model to invent missing game data.
 * It is not a general-purpose hallucination detector.
 */
public final class WorldFactGuard {

    public static final String UNKNOWN_WORLD_STATE =
            "Jag har ännu inte tillgång till verifierad information om vårt rikes "
            + "soldater, resurser eller byggnader. Jag vill inte hitta på några siffror. "
            + "Jag kan ännu inte verifiera om våra Create-maskiner eller tåg fungerar.";

    private WorldFactGuard() {
    }

    /**
     * Returns an authoritative response for currently unsupported factual queries,
     * or null when a question should be handled normally by the language model.
     */
    public static String replyIfUnknown(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        String question = userMessage.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();

        // Hypothetical military planning, creative role-play and proposals are allowed.
        if (containsAny(question,
                "hitta på", "föreslå", "förslag", "hypotetisk", "hypotetiskt",
                "anta att", "tänk om", "tänk dig", "om vi hade",
                "vad händer om", "scenario", "strategi", "taktik",
                "planera", "hur bör", "hur borde", "vad borde",
                "behöver vi", "ska vi göra", "låtsas",
                "imagine", "pretend", "suppose", "what if")) {
            return null;
        }

        // Match pronouns and known local entities as words, not parts of words
        // (e.g. do not treat a question about "romarriket" as our kingdom).
        String padded = " " + question.replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ").trim() + " ";
        boolean concernsOurWorld = containsAny(padded,
                " vi ", " oss ", " vårt ", " vår ", " våra ",
                " mitt ", " mina ", " min ", " riket ", " kungadömet ",
                " kungariket ", " kolonin ", " armén ", " staden ",
                " fabriken ", " maskinerna ", " järnvägen ", " tågen ");

        boolean concernsGameState = containsAny(question,
                "soldat", "infanter", "husar", "trupp", "armé", "arme",
                "styrkor", "bågskytt", "kavalleri", "militär", "patrull",
                "fiend", "resurs", "guld", "järn", "silver", "lager",
                "förråd", "matförråd", "byggnad", "slott", "hus",
                "befolk", "invånar", "borg", "stad", "städer", "gruva",
                "territori", "gräns", "koloni", "rike", "kungadöme",
                "fabrik", "järnväg", "tåg", "räls", "maskin", "transportband",
                "kugghjul", "kraftverk", "rotationskraft", "vattenhjul",
                "produktion", "tågstation");

        boolean asksForFacts = containsAny(question,
                "hur många", "hur mycket", "hur stor", "hur stark",
                "antal", "vad vet du", "vad har vi", "har vi",
                "vilka", "status", "rapport", "hur ser det ut",
                "berätta om", "beskriv vårt", "visa våra",
                "finns det", "vad äger", "vad finns",
                "how many", "what do we have", "report",
                "tell me about our", "what is our",
                "hur fungerar", "fungerar", "är igång", "är vår", "producerar",
                "är ansluten", "är anslutna", "kopplad", "kopplat",
                "hur går", "var går", "var ligger", "räcker");

        if (concernsOurWorld && concernsGameState && asksForFacts) {
            return UNKNOWN_WORLD_STATE;
        }
        return null;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
