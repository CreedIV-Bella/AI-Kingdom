package com.aibots.forge;

import com.aibots.common.ai.NpcNaturalIntent;
import com.aibots.common.entity.AiBotEntity;
import com.aibots.common.entity.CreateInspection;
import com.aibots.common.entity.VillageInspection;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only effects of natural text are these explicit server-side actions.
 * We never run arbitrary slash commands emitted by an LLM.
 */
public final class NpcIntentExecutor {

    private NpcIntentExecutor() {
    }

    public static String tryExecute(ServerPlayer player, AiBotEntity bot, String speech) {
        if (player == null || bot == null || bot.level() != player.level()) {
            return null;
        }
        // A natural, affirmative order may affect ONLY a real existing work order.
        // Other freeform sentences still go to conversation, never to a shell.
        String normalized = speech == null ? "" : speech.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}# ]+", " ")
                .replaceAll("\\s+", " ").trim();
        boolean prioritization = Pattern.compile(
                "\\b(prioritera|prioritet åt|högst prioritet)\\b")
                .matcher(normalized).find();
        boolean negated = Pattern.compile(
                "\\b(inte|aldrig|låt bli|utan att|om vi|om du|tänk om|"
                        + "förklara|varför|hur gör)\\b")
                .matcher(normalized).find();
        if (prioritization && !negated) {
            Matcher order = Pattern.compile(
                    "\\b(?:arbetsorder|byggorder|order|nummer)\\s*#?\\s*(\\d{1,6})\\b")
                    .matcher(normalized);
            if (order.find()) {
                int orderId = Integer.parseInt(order.group(1));
                return MineColoniesBridge.prioritize(player, bot, orderId);
            }
            if (normalized.matches(".*\\b(?:vakttorn|vakttornet|guard\\s*tower)\\b.*")) {
                return MineColoniesBridge.prioritizeGuardTower(player, bot);
            }
        }

        NpcNaturalIntent.Action action = NpcNaturalIntent.parse(speech);
        if (action == null) return null;

        return switch (action) {
            case FOLLOW -> {
                bot.followPlayer(player);
                yield "Ja, jag följer med dig.";
            }
            case STAY -> {
                bot.stay();
                yield "Självklart, jag stannar här.";
            }
            case WANDER -> {
                bot.wander();
                yield "Jag tar en promenad och håller mig i närheten.";
            }
            case INSPECT -> VillageInspection.inspect(bot);
            case INSPECT_CREATE -> CreateInspection.inspect(bot);
            case COLONY_STATUS -> MineColoniesBridge.status(player, bot);
        };
    }
}
