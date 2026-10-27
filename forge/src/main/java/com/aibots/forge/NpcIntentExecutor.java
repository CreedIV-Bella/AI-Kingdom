package com.aibots.forge;

import com.aibots.common.ai.NpcNaturalIntent;
import com.aibots.common.entity.AiBotEntity;
import com.aibots.common.entity.CreateInspection;
import com.aibots.common.entity.VillageInspection;
import net.minecraft.server.level.ServerPlayer;

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
