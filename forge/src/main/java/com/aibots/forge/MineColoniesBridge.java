package com.aibots.forge;

import com.aibots.common.entity.AiBotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * VERIFIED read-only colony snapshot for MineColonies 1.20.1 / 1.1.1278.
 *
 * Reflection deliberately avoids compile/link-time requirements on MineColonies.
 * All method names below were checked against the tagged public IColony API
 * v1.20.1-1.1.1278-snapshot. Unlike LLM responses, this reports real data from
 * the running Minecraft server, or states why it cannot access the colony.
 *
 * Do not add write/build/createColony/deleteColony calls to this bridge.
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

    private static Object call(Object object, String method)
            throws ReflectiveOperationException {
        if (object == null) {
            throw new ReflectiveOperationException(method + " on null");
        }
        Method m = object.getClass().getMethod(method);
        return m.invoke(object);
    }
}
