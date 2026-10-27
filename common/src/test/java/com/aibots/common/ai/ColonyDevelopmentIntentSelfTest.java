package com.aibots.common.ai;

/** Direct javac test for spoken city development (no Minecraft dependency). */
public final class ColonyDevelopmentIntentSelfTest {
    private ColonyDevelopmentIntentSelfTest() {}

    public static void main(String[] args) {
        check("Aldric, se vad vi behöver i Lakeside och bygg det.",
                ColonyDevelopmentIntent.Action.DEVELOP_ONCE);
        check("Aldric, bygg det som behövs i vår stad.",
                ColonyDevelopmentIntent.Action.DEVELOP_ONCE);
        check("Aldric, bygg det vi behöver.",
                ColonyDevelopmentIntent.Action.DEVELOP_ONCE);
        check("Aldric, bygg ut staden självständigt, du får bestämma.",
                ColonyDevelopmentIntent.Action.AUTO_ON);
        check("Aldric, du får ta ansvar för staden och bygga automatiskt.",
                ColonyDevelopmentIntent.Action.AUTO_ON);
        check("Aldric, pausa stadsplaneringen.",
                ColonyDevelopmentIntent.Action.AUTO_OFF);
        check("Stoppa utvecklingen av staden.",
                ColonyDevelopmentIntent.Action.AUTO_OFF);
        check("Hur ska vi utveckla staden?", null);
        check("Kan du förklara hur byggordrar fungerar i Lakeside?", null);
        check("Aldric, bygg inte nya bostäder!", null);
        check("Vad händer om du får full behörighet över staden?", null);
        check("Aldric, var ligger vår stad?", null);
        System.out.println("12 city development intent tests passed.");
    }

    private static void check(String phrase, ColonyDevelopmentIntent.Action expected) {
        ColonyDevelopmentIntent.Action result = ColonyDevelopmentIntent.parse(phrase);
        if (result != expected) {
            throw new AssertionError("For " + phrase + ": expected " + expected + ", got " + result);
        }
    }
}
