package com.aibots.forge;

import com.aibots.common.ai.AiConfig;
import com.aibots.common.ai.AiService;
import com.aibots.common.entity.AiBotEntity;
import com.aibots.common.entity.PlayerConversation;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import com.mojang.brigadier.arguments.StringArgumentType;
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
            String identityReply = target.verifiedReply(message);
            if (identityReply != null) {
                player.sendSystemMessage(Component.literal(prefix + identityReply).withStyle(ChatFormatting.WHITE));
                return 1;
            }
            system = target.getPersonality();
        } else {
            prefix = "[AI-assistent] ";
            system = AiConfig.get().systemPrompt;
            player.sendSystemMessage(Component.literal("Ingen NPC vald. Högerklicka på en NPC eller använd /aibots who.")
                    .withStyle(ChatFormatting.YELLOW));
        }

        player.sendSystemMessage(Component.literal(prefix + "Thinking...").withStyle(ChatFormatting.GRAY));
        AiService.ask(system, message).thenAccept(reply -> {
            player.server.execute(() -> {
                player.sendSystemMessage(Component.literal(prefix + reply).withStyle(ChatFormatting.WHITE));
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
