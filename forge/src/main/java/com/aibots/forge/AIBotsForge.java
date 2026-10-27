package com.aibots.forge;

import com.aibots.common.ai.AiConfig;
import com.aibots.common.ai.AiService;
import com.aibots.common.entity.AiBotEntity;
import com.aibots.common.entity.PlayerConversation;
import com.aibots.common.entity.VillageInspection;
import com.aibots.common.entity.CreateInspection;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.ModList;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

@Mod(AIBotsForge.MOD_ID)
public class AIBotsForge {

    public static final String MOD_ID = "aibots";

    public AIBotsForge() {
        AiConfig.load(FMLPaths.CONFIGDIR.get());
        ModNetwork.register();
        ModEntities.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ask")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(this::ask))
        );
        event.getDispatcher().register(
            Commands.literal("aibots")
                .then(Commands.literal("who")
                    .executes(this::who))
                .then(Commands.literal("select")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(this::selectByName)))
                .then(Commands.literal("follow")
                    .executes(this::follow))
                .then(Commands.literal("stay")
                    .executes(this::stay))
                .then(Commands.literal("wander")
                    .executes(this::wander))
                .then(Commands.literal("status")
                    .executes(this::status))
                .then(Commands.literal("inspect")
                    .executes(this::inspect))
                .then(Commands.literal("create")
                    .then(Commands.literal("inspect")
                        .executes(this::inspectCreate)))
                .then(Commands.literal("colony")
                    .then(Commands.literal("probe")
                        .executes(this::colonyProbe))
                    .then(Commands.literal("status")
                        .executes(this::colonyStatus))
                    .then(Commands.literal("prioritize")
                        .then(Commands.argument("orderId", IntegerArgumentType.integer(1))
                            .executes(this::colonyPrioritize)))
                    .then(Commands.literal("develop")
                        .executes(this::colonyDevelop))
                    .then(Commands.literal("auto")
                        .then(Commands.literal("on")
                            .executes(ctx -> setColonyAuto(ctx, true)))
                        .then(Commands.literal("off")
                            .executes(ctx -> setColonyAuto(ctx, false)))))
                .then(Commands.literal("go")
                    .then(Commands.argument("position", Vec3Argument.vec3())
                        .executes(this::go)))
                .then(Commands.literal("stop")
                    .executes(this::stop))
                .then(Commands.literal("reload")
                    .requires(ctx -> ctx.hasPermission(2))
                    .executes(this::reload))
        );
    }

    @SubscribeEvent
    public void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof AiBotEntity bot && !event.getEntity().level().isClientSide) {
            PlayerConversation.setTarget(event.getEntity(), bot);
        }
    }

    private int ask(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        String message = StringArgumentType.getString(ctx, "message");
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        AiBotEntity target = PlayerConversation.getTarget(player);
        String prefix;
        String system;
        if (target != null) {
            prefix = "<" + target.botName() + "> ";
            String executed = NpcIntentExecutor.tryExecute(player, target, message);
            if (executed != null) {
                player.sendSystemMessage(Component.literal(prefix + executed).withStyle(ChatFormatting.WHITE));
                ModNetwork.speakNpc(player, target, executed);
                return 1;
            }
            String identityReply = target.verifiedReply(message);
            if (identityReply != null) {
                player.sendSystemMessage(Component.literal(prefix + identityReply).withStyle(ChatFormatting.WHITE));
                ModNetwork.speakNpc(player, target, identityReply);
                return 1;
            }
            system = target.getPersonality();
        } else {
            player.sendSystemMessage(Component.literal(
                    "Ingen NPC vald. Högerklicka på Aldric eller använd /aibots select Aldric Ravencrest.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        String contextualMessage = target != null ? target.messageWithHistory(message) : message;
        player.sendSystemMessage(Component.literal(prefix + "Thinking...").withStyle(ChatFormatting.GRAY));
        AiService.ask(system, contextualMessage).thenAccept(reply -> {
            player.server.execute(() -> {
                if (target != null && target.isAlive()) {
                    target.rememberExchange(message, reply);
                }
                player.sendSystemMessage(Component.literal(prefix + reply).withStyle(ChatFormatting.WHITE));
                ModNetwork.speakNpc(player, target, reply);
            });
        });
        return 1;
    }

    private int who(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }

        AiBotEntity target = PlayerConversation.getTarget(player);
        if (target == null) {
            player.sendSystemMessage(Component.literal(
                    "Ingen NPC vald. Högerklicka på en AI Bot för att börja prata.")
                    .withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.literal(
                    "Du pratar med " + target.botName() + " (" + target.getRole() + ").")
                    .withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }


    /** Select an NPC by its saved in-world name; useful after relogging or unloading. */
    private int selectByName(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Detta kommando kräver en spelare."));
            return 0;
        }

        String requested = StringArgumentType.getString(ctx, "name").trim();
        if (requested.isBlank() || requested.length() > 80) {
            ctx.getSource().sendFailure(Component.literal("Ange ett giltigt NPC-namn."));
            return 0;
        }
        AiBotEntity match = null;
        for (var entity : player.serverLevel().getAllEntities()) {
            if (entity instanceof AiBotEntity bot
                    && bot.isAlive()
                    && bot.botName().equalsIgnoreCase(requested)
                    && bot.distanceToSqr(player) <= 32.0D * 32.0D) {
                if (match != null) {
                    ctx.getSource().sendFailure(Component.literal(
                            "Flera NPC:er har det namnet. Högerklicka på den rätta."));
                    return 0;
                }
                match = bot;
            }
        }
        if (match == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Hittade inte " + requested
                    + " inom 32 block i laddade delar av denna dimension."));
            return 0;
        }
        PlayerConversation.setTarget(player, match);
        player.sendSystemMessage(Component.literal(
                "Nu pratar du med " + match.botName() + ".")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private AiBotEntity selectedNpc(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Detta kommando kräver en spelare."));
            return null;
        }
        AiBotEntity bot = PlayerConversation.getTarget(player);
        if (bot == null) {
            player.sendSystemMessage(Component.literal(
                    "Ingen NPC vald. Högerklicka på invånaren först.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return bot;
    }

    private int follow(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiBotEntity bot = selectedNpc(ctx);
        if (bot == null) return 0;
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null || bot.level() != player.level()) return 0;
        bot.followPlayer(player);
        player.sendSystemMessage(Component.literal(bot.botName() + ": Jag följer dig.")
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private int stay(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiBotEntity bot = selectedNpc(ctx);
        if (bot == null) return 0;
        bot.stay();
        ctx.getSource().sendSuccess(() ->
                Component.literal(bot.botName() + " stannar här."), false);
        return 1;
    }

    private int wander(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiBotEntity bot = selectedNpc(ctx);
        if (bot == null) return 0;
        bot.wander();
        ctx.getSource().sendSuccess(() ->
                Component.literal(bot.botName() + " börjar vandra omkring."), false);
        return 1;
    }

    private int status(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiBotEntity bot = selectedNpc(ctx);
        if (bot == null) return 0;
        ctx.getSource().sendSuccess(() ->
                Component.literal(bot.botName() + ": " + bot.getTravelReport()
                        + " (läge " + bot.getMoveMode() + ")"), false);
        return 1;
    }

    private int inspect(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiBotEntity bot = selectedNpc(ctx);
        if (bot == null) return 0;
        String report = VillageInspection.inspect(bot);
        ctx.getSource().sendSuccess(() ->
                Component.literal(bot.botName() + ": " + report), false);
        return 1;
    }

    /**
     * Diagnostics only. Before compiling against MineColonies we must know
     * precisely which mod versions the player has installed in their modpack.
     */
    private int colonyProbe(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        StringBuilder result = new StringBuilder("AI Kingdoms modpack: ");
        for (String modId : new String[]{"minecolonies", "structurize", "create"}) {
            String version = ModList.get().getModContainerById(modId)
                    .map(mod -> mod.getModInfo().getVersion().toString())
                    .orElse("saknas");
            result.append(modId).append("=").append(version).append("; ");
        }
        ctx.getSource().sendSuccess(() -> Component.literal(result.toString()), false);
        return 1;
    }

    private int colonyStatus(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Bara spelare kan läsa kolonistatus."));
            return 0;
        }
        AiBotEntity bot = PlayerConversation.getTarget(player);
        String report = MineColoniesBridge.status(player, bot);
        player.sendSystemMessage(Component.literal(report).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private int colonyPrioritize(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Bara spelare kan hantera kolonin."));
            return 0;
        }
        int orderId = IntegerArgumentType.getInteger(ctx, "orderId");
        AiBotEntity bot = PlayerConversation.getTarget(player);
        if (bot == null) {
            player.sendSystemMessage(Component.literal(
                    "Välj Aldric först. Prioriteringar måste ges till en NPC.")
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        String report = MineColoniesBridge.prioritize(player, bot, orderId);
        player.sendSystemMessage(Component.literal(
                bot.botName() + ": " + report).withStyle(ChatFormatting.AQUA));
        ModNetwork.speakNpc(player, bot, report);
        return 1;
    }


    /** Run one verified housing-development decision on the Minecraft server. */
    private int colonyDevelop(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        AiBotEntity npc = PlayerConversation.getTarget(player);
        if (npc == null) {
            player.sendSystemMessage(Component.literal(
                    "Välj Aldric först, till exempel /aibots select Aldric Ravencrest."));
            return 0;
        }
        ColonyDevelopmentPlanner.Result result =
                ColonyDevelopmentPlanner.develop(player, npc);
        player.sendSystemMessage(Component.literal(npc.botName() + ": "
                + result.description()).withStyle(ChatFormatting.AQUA));
        ModNetwork.speakNpc(player, npc, result.description());
        return 1;
    }

    private int setColonyAuto(
            CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            boolean enable) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        AiBotEntity npc = PlayerConversation.getTarget(player);
        if (npc == null || npc.level() != player.level()) {
            player.sendSystemMessage(Component.literal("Välj Aldric först."));
            return 0;
        }
        if (!ColonyDevelopmentPlanner.isOwner(player, npc)) {
            player.sendSystemMessage(Component.literal(
                    "Endast MineColonies-kolonins ägare kan slå på eller av automatiskt stadsansvar. "
                    + "Var nära Aldric och kolonin."));
            return 0;
        }
        if (enable) {
            npc.setStewardEnabled(true, player.getUUID());
            player.sendSystemMessage(Component.literal(
                    npc.botName() + " är nu aktiv stadsförvaltare. "
                    + "Varannan minut undersöker han bostadsbehovet när du är i närheten. "
                    + "Han kan bara beställa bygg/uppgradering på registrerade bostadstomter; "
                    + "nya tomter och vägar kräver fortfarande ritningar.")
                    .withStyle(ChatFormatting.GREEN));
            ColonyDevelopmentPlanner.Result first =
                    ColonyDevelopmentPlanner.develop(player, npc);
            player.sendSystemMessage(Component.literal(npc.botName() + ": "
                    + first.description()).withStyle(ChatFormatting.AQUA));
        } else {
            if (!player.getUUID().equals(npc.getStewardOwnerUUID())
                    && npc.isStewardEnabled()) {
                player.sendSystemMessage(Component.literal(
                        "Endast den som slog på stadsförvaltningen får stänga av den."));
                return 0;
            }
            npc.setStewardEnabled(false, null);
            player.sendSystemMessage(Component.literal(
                    npc.botName() + " har pausat automatisk stadsplanering.")
                    .withStyle(ChatFormatting.GREEN));
        }
        return 1;
    }

    /**
     * Deliberately infrequent and gated by an online owner near a loaded NPC.
     * No chunks are force-loaded, and no LLM is allowed to mutate the colony.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 2400 != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof AiBotEntity bot) || !bot.isAlive()
                        || !bot.isStewardEnabled()) continue;

                ServerPlayer owner = server.getPlayerList().getPlayer(
                        bot.getStewardOwnerUUID());
                if (owner == null || owner.level() != bot.level()
                        || owner.distanceToSqr(bot) > 128.0D * 128.0D) {
                    continue;
                }
                ColonyDevelopmentPlanner.Result result =
                        ColonyDevelopmentPlanner.develop(owner, bot);
                if (result.modified()) {
                    owner.sendSystemMessage(Component.literal(
                            bot.botName() + " (stadsförvaltare): "
                            + result.description()).withStyle(ChatFormatting.GREEN));
                }
            }
        }
    }

    private int inspectCreate(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiBotEntity bot = selectedNpc(ctx);
        if (bot == null) return 0;
        String report = CreateInspection.inspect(bot);
        ctx.getSource().sendSuccess(() ->
                Component.literal(bot.botName() + ": " + report), false);
        return 1;
    }

    private int go(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiBotEntity bot = selectedNpc(ctx);
        if (bot == null) return 0;
        Vec3 vec = Vec3Argument.getVec3(ctx, "position");
        BlockPos pos = BlockPos.containing(vec.x, vec.y, vec.z);

        // Bound navigation work and avoid making the mod a chunk-loading tool.
        double maxRange = 96.0D;
        if (bot.distanceToSqr(Vec3.atCenterOf(pos)) > maxRange * maxRange) {
            ctx.getSource().sendFailure(Component.literal(
                    "Destinationen är för långt bort (max 96 block). "
                    + "Använd /aibots follow för längre resor."));
            return 0;
        }
        if (pos.getY() < bot.level().getMinBuildHeight()
                || pos.getY() >= bot.level().getMaxBuildHeight()
                || !bot.level().hasChunkAt(pos)) {
            ctx.getSource().sendFailure(Component.literal(
                    "Destinationen måste vara en laddad, giltig position."));
            return 0;
        }

        bot.goTo(pos);
        ctx.getSource().sendSuccess(() ->
                Component.literal(bot.botName() + " går mot " + pos.toShortString()
                        + ". Kontrollera /aibots status efteråt."), false);
        return 1;
    }

    private int stop(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Must be run by a player"));
            return 0;
        }
        PlayerConversation.clear(player.getUUID());
        player.sendSystemMessage(Component.literal("Stopped talking.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private int reload(CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        AiConfig.load(FMLPaths.CONFIGDIR.get());
        ctx.getSource().sendSuccess(() ->
            Component.literal("AI Bots config reloaded.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
