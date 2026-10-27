package com.aibots.forge;

import com.aibots.common.entity.AiBotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Verified MineColonies integration for 1.20.1 / 1.1.1278.
 * Status is read-only. Explicit, authorized prioritization is a narrowly
 * bounded write operation on existing unclaimed work orders.
 *
 * Reflection deliberately avoids compile/link-time requirements on MineColonies.
 * All method names below were checked against the tagged public IColony API
 * v1.20.1-1.1.1278-snapshot. Unlike LLM responses, this reports real data from
 * the running Minecraft server, or states why it cannot access the colony.
 *
 * Never create/delete colonies, place blocks or assign active Builders here.
 */
public final class MineColoniesBridge {
    private static final int MAX_WORK_ORDERS_IN_MESSAGE = 6;
    private static final int MAX_BUILDER_NAMES = 5;
    private static final long MAX_NEARBY_DISTANCE_SQUARED = 128L * 128L;

    private MineColoniesBridge() {
    }

    public static String status(ServerPlayer player, AiBotEntity bot) {
        if (player == null) return "Kan bara köras av en spelare.";
        if (!net.minecraftforge.fml.ModList.get().isLoaded("minecolonies")) {
            return "MineColonies är inte laddat i modpaketet.";
        }

        try {
            Level level = player.serverLevel();
            BlockPos location = bot != null && bot.level() == level
                    ? bot.blockPosition() : player.blockPosition();

            Class<?> managerClass = Class.forName(
                    "com.minecolonies.api.colony.IColonyManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Object colony = managerClass.getMethod(
                    "getColonyByPosFromWorld", Level.class, BlockPos.class)
                    .invoke(manager, level, location);

            // A player walking just outside the claimed border should still
            // be able to inspect a near colony, but never one far away.
            if (colony == null) {
                Object nearest = managerClass.getMethod(
                        "getClosestColony", Level.class, BlockPos.class)
                        .invoke(manager, level, location);
                if (nearest != null) {
                    Object center = call(nearest, "getCenter");
                    if (center instanceof BlockPos pos
                            && location.distSqr(pos) <= MAX_NEARBY_DISTANCE_SQUARED) {
                        colony = nearest;
                    }
                }
            }

            if (colony == null) {
                return "Ingen registrerad MineColonies-koloni hittades inom 128 block "
                        + "från " + location.toShortString()
                        + ". Gå till stadens Town Hall eller flytta Aldric närmare.";
            }

            String name = String.valueOf(call(colony, "getName"));
            int id = ((Number) call(colony, "getID")).intValue();
            Object center = call(colony, "getCenter");

            Object citizens = call(colony, "getCitizenManager");
            int citizenCount = ((Number) call(citizens,
                    "getCurrentCitizenCount")).intValue();
            int citizenCapacity = ((Number) call(citizens,
                    "getMaxCitizens")).intValue();
            List<?> citizenList = (List<?>) call(citizens, "getCitizens");

            Object buildingManager = call(colony, "getServerBuildingManager");
            Map<?, ?> buildingMap = (Map<?, ?>) call(buildingManager, "getBuildings");
            int builderHuts = 0;
            for (Object building : buildingMap.values()) {
                if (building != null && building.getClass().getSimpleName()
                        .equals("BuildingBuilder")) {
                    builderHuts++;
                }
            }

            int builderCitizens = 0;
            StringBuilder builderNames = new StringBuilder();
            for (Object citizen : citizenList) {
                if (citizen == null) continue;
                Object job = call(citizen, "getJob");
                if (job != null && job.getClass().getSimpleName()
                        .equals("JobBuilder")) {
                    builderCitizens++;
                    if (builderCitizens <= MAX_BUILDER_NAMES) {
                        if (builderNames.length() > 0) builderNames.append(", ");
                        builderNames.append(call(citizen, "getName"));
                    }
                }
            }

            Object workManager = call(colony, "getWorkManager");
            Map<?, ?> workOrders = (Map<?, ?>) call(workManager, "getWorkOrders");

            StringBuilder report = new StringBuilder();
            report.append("MineColonies: ").append(name)
                    .append(" (#").append(id).append("), centrum ")
                    .append(center)
                    .append(". Invånare ").append(citizenCount).append("/")
                    .append(citizenCapacity).append(". Byggnader ")
                    .append(buildingMap.size())
                    .append(". Builder-hus ").append(builderHuts)
                    .append(". Builders ").append(builderCitizens).append(".");
            if (builderNames.length() > 0) {
                report.append(" Builders: ").append(builderNames).append(".");
            }
            report.append(" Pågående/registrerade arbetsorder: ")
                    .append(workOrders.size()).append(".");

            int shown = 0;
            for (Object order : workOrders.values()) {
                if (order == null || shown++ >= MAX_WORK_ORDERS_IN_MESSAGE) break;

                Object display = call(order, "getDisplayName");
                String title = display instanceof Component component
                        ? component.getString() : String.valueOf(display);
                title = title.replace('\n', ' ').replace('\r', ' ');

                report.append(" [#")
                        .append(call(order, "getID")).append(": ")
                        .append(title, 0, Math.min(title.length(), 70))
                        .append("; nivå ")
                        .append(call(order, "getTargetLevel"))
                        .append("; prioritet ")
                        .append(call(order, "getPriority"))
                        .append("; ")
                        .append(Boolean.TRUE.equals(call(order, "isClaimed"))
                                ? "tilldelad" : "väntar")
                        .append("]");
            }

            if (workOrders.size() > MAX_WORK_ORDERS_IN_MESSAGE) {
                report.append(" (endast de första ")
                        .append(MAX_WORK_ORDERS_IN_MESSAGE).append(" visas)");
            }
            report.append(" Detta är en avläst serverrapport. ")
                    .append("Inga byggordrar har ändrats.");
            if (bot != null) report.append(" Stadsförvaltning: ")
                    .append(bot.isStewardEnabled() ? "aktiv" : "pausad")
                    .append(". Rörelse: ").append(bot.getTravelReport())
                    .append(" Senaste planering: ").append(bot.getStewardReport());
            return report.toString();
        } catch (ReflectiveOperationException
                 | RuntimeException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException e
                    ? e.getTargetException() : exception;
            return "Kunde inte läsa MineColonies API: "
                    + cause.getClass().getSimpleName()
                    + ". Kontrollera den installerade modversionen.";
        }
    }


    /**
     * Elevates one already-registered, *unclaimed* MineColonies order.
     * Explicit player instruction only. This does not interrupt builders,
     * create a blueprint, build blocks or bypass colony permissions.
     */
    public static String prioritize(ServerPlayer player, AiBotEntity bot, int orderId) {
        if (player == null || orderId < 1) {
            return "Ogiltig spelare eller arbetsorder.";
        }
        if (!net.minecraftforge.fml.ModList.get().isLoaded("minecolonies")) {
            return "MineColonies är inte laddat.";
        }

        try {
            Level world = player.serverLevel();
            BlockPos playerPos = player.blockPosition();

            Class<?> managerClass = Class.forName(
                    "com.minecolonies.api.colony.IColonyManager");
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Object colony = managerClass.getMethod(
                    "getColonyByPosFromWorld", Level.class, BlockPos.class)
                    .invoke(manager, world, playerPos);

            if (colony == null) {
                Object nearest = managerClass.getMethod(
                        "getClosestColony", Level.class, BlockPos.class)
                        .invoke(manager, world, playerPos);
                if (nearest != null
                        && call(nearest, "getCenter") instanceof BlockPos center
                        && playerPos.distSqr(center) <= MAX_NEARBY_DISTANCE_SQUARED) {
                    colony = nearest;
                }
            }

            if (colony == null) {
                return "Ingen koloni i närheten. Gå till Lakesides stadshus.";
            }

            // Server-side authorization, using MineColonies' own permissions API.
            // MANAGE_HUTS is the permission for changing work-order settings.
            Object permissions = call(colony, "getPermissions");
            Class<?> actionType = Class.forName(
                    "com.minecolonies.api.colony.permissions.Action");
            Object manageHuts = null;
            for (Object action : actionType.getEnumConstants()) {
                if ("MANAGE_HUTS".equals(((Enum<?>) action).name())) {
                    manageHuts = action;
                    break;
                }
            }
            if (manageHuts == null) {
                return "MineColonies saknar en verifierad behörighetskontroll.";
            }
            boolean allowed = Boolean.TRUE.equals(
                    permissions.getClass().getMethod(
                            "hasPermission", Player.class, actionType)
                            .invoke(permissions, player, manageHuts));
            if (!allowed) {
                return "Du har inte rätt att hantera byggordrar i kolonin.";
            }

            Object workManager = call(colony, "getWorkManager");
            Map<?, ?> orders = (Map<?, ?>) call(workManager, "getWorkOrders");
            Object chosen = orders.get(orderId);
            if (chosen == null) {
                return "Arbetsorder " + orderId + " finns inte i den här kolonin.";
            }
            if (Boolean.TRUE.equals(call(chosen, "isClaimed"))) {
                return "Order " + orderId
                        + " är redan tilldelad en byggare. Jag ändrar bara väntande order.";
            }

            int highest = Integer.MIN_VALUE;
            int highestOther = Integer.MIN_VALUE;
            for (Object order : orders.values()) {
                if (order == null) continue;
                int priority = ((Number) call(order, "getPriority")).intValue();
                highest = Math.max(highest, priority);
                if (order != chosen) highestOther = Math.max(highestOther, priority);
            }
            int original = ((Number) call(chosen, "getPriority")).intValue();
            if (original > highestOther) {
                return "Order " + orderId + " har redan ensam högst prioritet.";
            }
            if (highest >= 100000) {
                return "Arbetskön har för höga prioritetstal för en säker ändring.";
            }

            int next = Math.max(1, highest + 1);
            chosen.getClass().getMethod("setPriority", int.class)
                    .invoke(chosen, next);
            workManager.getClass().getMethod("setDirty", boolean.class)
                    .invoke(workManager, true);
            call(colony, "markDirty");

            Object title = call(chosen, "getDisplayName");
            String name = title instanceof Component component
                    ? component.getString() : String.valueOf(title);
            name = name.replace('\r', ' ').replace('\n', ' ');
            if (name.length() > 70) name = name.substring(0, 70);
            return "Klart. Jag har höjt prioriteten på order " + orderId
                    + " (" + name + ") från " + original + " till " + next
                    + " i MineColonies. Byggaren börjar först när den är ledig "
                    + "och kan utföra arbetet med tillgängliga material.";
        } catch (ReflectiveOperationException
                 | RuntimeException | LinkageError exception) {
            Throwable cause = exception instanceof InvocationTargetException e
                    ? e.getTargetException() : exception;
            return "MineColonies-ändringen misslyckades: "
                    + cause.getClass().getSimpleName()
                    + ". Kontrollera modversionen och spel-loggen.";
        }
    }


    /**
     * Resolve a named waiting project by examining real colony work orders.
     * Refuses ambiguous matches; never hardcodes Lakeside's order IDs.
     */
    public static String prioritizeGuardTower(ServerPlayer player, AiBotEntity bot) {
        if (player == null || !net.minecraftforge.fml.ModList.get().isLoaded("minecolonies")) {
            return "MineColonies är inte tillgängligt.";
        }
        try {
            Level world = player.serverLevel();
            BlockPos pos = player.blockPosition();
            Class<?> type = Class.forName("com.minecolonies.api.colony.IColonyManager");
            Object manager = type.getMethod("getInstance").invoke(null);
            Object colony = type.getMethod(
                    "getColonyByPosFromWorld", Level.class, BlockPos.class)
                    .invoke(manager, world, pos);
            if (colony == null) {
                colony = type.getMethod(
                        "getClosestColony", Level.class, BlockPos.class)
                        .invoke(manager, world, pos);
                if (colony != null
                        && call(colony, "getCenter") instanceof BlockPos center
                        && pos.distSqr(center) > MAX_NEARBY_DISTANCE_SQUARED) {
                    colony = null;
                }
            }
            if (colony == null) {
                return "Ingen koloni i närheten. Gå till stadshuset.";
            }
            Object workManager = call(colony, "getWorkManager");
            Map<?, ?> orders = (Map<?, ?>) call(workManager, "getWorkOrders");
            Integer matchingId = null;
            for (Object order : orders.values()) {
                if (order == null || Boolean.TRUE.equals(call(order, "isClaimed"))) {
                    continue;
                }
                Object display = call(order, "getDisplayName");
                String name = display instanceof Component component
                        ? component.getString() : String.valueOf(display);
                String normalized = name.toLowerCase(java.util.Locale.ROOT)
                        .replace(" ", "").replace("_", "");
                if (normalized.contains("guardtower")
                        || normalized.contains("watchtower")
                        || normalized.contains("vakttorn")) {
                    int id = ((Number) call(order, "getID")).intValue();
                    if (matchingId != null) {
                        return "Det finns flera väntande vakttorn. Säg arbetsorderns nummer.";
                    }
                    matchingId = id;
                }
            }
            if (matchingId == null) {
                return "Jag hittar ingen väntande arbetsorder för ett vakttorn.";
            }
            return prioritize(player, bot, matchingId);
        } catch (ReflectiveOperationException
                 | RuntimeException | LinkageError exception) {
            return "Jag kan inte kontrollera vakttornets arbetsorder just nu.";
        }
    }

    private static Object call(Object object, String method)
            throws ReflectiveOperationException {
        if (object == null) {
            throw new ReflectiveOperationException(method + " on null");
        }
        Method m = object.getClass().getMethod(method);
        return m.invoke(object);
    }
}
