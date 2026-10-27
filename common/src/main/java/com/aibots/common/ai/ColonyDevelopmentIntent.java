package com.aibots.common.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Narrow, explicit city-steward intent parser. The full server-side action is
 * authorized by the MineColonies owner and validated by the development planner.
 */
public final class ColonyDevelopmentIntent {

    public enum Action { DEVELOP_ONCE, AUTO_ON, AUTO_OFF }

    private ColonyDevelopmentIntent() {
    }

    public static Action parse(String message) {
        if (message == null || message.isBlank() || message.length() > 320) {
            return null;
        }
        String s = message.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N} ]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (find(s, "\\b(vad händer om|hur fungerar|förklara|varför|hur ska|"
                + "hur kan|jag undrar|tänk om|inte|aldrig|om du)\\b")) {
            return null;
        }

        boolean city = find(s,
                "\\b(staden|stadens|byn|kolonin|lakeside|bostäder|"
                        + "bostadsbrist|byggprojekt|byggbehov|stadsplanering|stadsplaneringen)\\b");
        city = city || find(s, "\\b(se vad vi behöver och bygg det|"
                + "bygg det vi behöver|se vad som behövs och bygg|"
                + "bygg det som behövs)\\b");
        boolean stop = find(s,
                "\\b(pausa|stoppa|avsluta|stäng av|sluta)\\b");
        boolean authorization = find(s,
                "\\b(full behörighet|full frihet|ta över|ta ansvar|"
                        + "självständigt|automatiskt|utan att fråga mig|"
                        + "du får bestämma|du bestämmer)\\b");
        boolean build = find(s,
                "\\b(bygg|bygga|bygg ut|utveckla|utveckling|"
                        + "planera|ordna|se vad|undersök|fixa)\\b");

        if (city && stop && (authorization || find(s, "\\b(stadsplanering|stadsplaneringen|bygga|utveckla)\\b"))) {
            return Action.AUTO_OFF;
        }
        if (city && authorization && build) {
            return Action.AUTO_ON;
        }
        // "Aldric, se vad vi behöver i Lakeside och bygg det."
        if (city && build && find(s, "\\b(behöver|behövs|brist|bygg|bygga|"
                + "utveckla|bostäder|planera)\\b")) {
            return Action.DEVELOP_ONCE;
        }
        return null;
    }

    private static boolean find(String source, String regex) {
        return Pattern.compile(regex).matcher(source).find();
    }
}
