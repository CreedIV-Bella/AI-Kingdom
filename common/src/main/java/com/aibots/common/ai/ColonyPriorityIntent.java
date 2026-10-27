package com.aibots.common.ai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse a deliberately small set of natural Swedish instructions for an
 * existing MineColonies work order. This does not create, delete or build.
 */
public final class ColonyPriorityIntent {
    public record Order(int id, boolean guardTower) {}

    private static final Pattern PRIORITY = Pattern.compile(
            "\\b(prioritera|prioritet åt|högst prioritet)\\b");
    private static final Pattern NEGATION = Pattern.compile(
            "\\b(inte|aldrig|låt bli|utan att|om vi|om du|tänk om|"
                    + "förklara|varför|hur gör|hur kan|ska vi)\\b");
    private static final Pattern NUMBER = Pattern.compile(
            "\\b(?:arbetsorder|byggorder|order|nummer)\\s*#?\\s*(\\d{1,6})\\b");
    private static final Pattern TOWER = Pattern.compile(
            "\\b(vakttorn|vakttornet|guard\\s*tower)\\b");

    private ColonyPriorityIntent() {}

    /** Returns null unless a positive, specific authorized command is evident. */
    public static Order parse(String utterance) {
        if (utterance == null || utterance.isBlank() || utterance.length() > 320) {
            return null;
        }
        String text = utterance.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}# ]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (!PRIORITY.matcher(text).find() || NEGATION.matcher(text).find()) {
            return null;
        }
        Matcher number = NUMBER.matcher(text);
        if (number.find()) {
            int id = Integer.parseInt(number.group(1));
            return id > 0 && id <= 100000 ? new Order(id, false) : null;
        }
        if (TOWER.matcher(text).find()) {
            return new Order(0, true);
        }
        return null;
    }
}
