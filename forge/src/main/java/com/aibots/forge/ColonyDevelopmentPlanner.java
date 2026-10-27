package com.aibots.forge;

import com.aibots.common.entity.AiBotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * First cautious, WRITE-CAPABLE city planner for MineColonies 1.20.1-1.1.1278.
 *
 * It can choose and queue BUILD or UPGRADE using already-registered residence
 * hut positions, after checking housing capacity and pending work orders.
 * Never creates imaginary hut locations, places blocks, removes a building,
 * duplicates work orders or generates schematics. All actions execute on the
 * Minecraft server thread using MineColonies' normal IBuilding.requestUpgrade.
 *
 * A future Structurize blueprint placement adapter is needed before Aldric
 * can choose completely NEW housing locations or road layouts.
 */
public final class ColonyDevelopmentPlanner {
    private static final long MAX_DISTANCE_SQ = 128L * 128L;

    private ColonyDevelopmentPlanner() {
    }

    public record Result(boolean modified, String description) {
    }

    public static boolean isOwner(ServerPlayer player, AiBotEntity npc) {
        if (player == null || npc == null || npc.level() != player.level()
                || npc.distanceToSqr(player) > MAX_DISTANCE_SQ) {
            return false;
        }
        try {
            Object colony = nearbyColony(player, npc);
            if (colony == null) return false;
            Object permissions = invoke(colony, "getPermissions");
            return player.getUUID().equals(invoke(permissions, "getOwner"));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static Result develop(ServerPlayer player, AiBotEntity npc) {
        if (player == null || npc == null) {
            return new Result(false, "Jag måste prata med en vald NPC.");
        }
        if (npc.level() != player.level()
                || npc.distanceToSqr(player) > MAX_DISTANCE_SQ) {
            return new Result(false, "Du behöver vara nära Aldric i samma dimension.");
        }
        if (!ModList.get().isLoaded("minecolonies")) {
            return new Result(false, "MineColonies är inte laddat.");
        }
        try {
            Object colony = nearbyColony(player, npc);
            if (colony == null) {
                return new Result(false, "Jag hittar ingen registrerad MineColonies-koloni i närheten.");
            }
            if (!canManageHuts(colony, player)) {
                return new Result(false, "Du har inte MineColonies-behörigheten MANAGE_HUTS.");
            }

            Object manager = invoke(colony, "getCitizenManager");
            int residents = ((Number) invoke(manager, "getCurrentCitizenCount")).intValue();
            int capacity = ((Number) invoke(manager, "getMaxCitizens")).intValue();
            Object work = invoke(colony, "getWorkManager");
            Map<?, ?> orders = (Map<?, ?>) invoke(work, "getWorkOrders");
            Object structureManager = invoke(colony, "getServerBuildingManager");
            Map<?, ?> buildings = (Map<?, ?>) invoke(structureManager, "getBuildings");

            if (residents < capacity) {
                return new Result(false, "Jag har undersökt staden: "
                        + residents + " av " + capacity + " invånarplatser används. "
                        + "Ingen akut bostadsbrist kan bekräftas. "
                        + "Nästa steg är att ansluta ett kontrollerat katalogsystem "
                        + "för vägar, lager och nya hus.");
            }

            // No duplicate residential construction: the user observed that
            // Lakeside already has a queued Residence work order.
            for (Object order : orders.values()) {
                if (order != null && isResidentialOrder(order)) {
                    return new Result(false, "Bostadskapaciteten är full (" + residents
                            + "/" + capacity + "), men en Residence-arbetsorder finns "
                            + "redan i MineColonies. Jag avvaktar den i stället för "
                            + "att beställa samma arbete igen.");
                }
            }

            Set<BlockPos> occupiedWorkSites = new HashSet<>();
            for (Object order : orders.values()) {
                if (order == null) continue;
                Object location = invoke(order, "getLocation");
                if (location instanceof BlockPos pos) occupiedWorkSites.add(pos);
            }

            List<Candidate> eligible = new ArrayList<>();
            for (Object building : buildings.values()) {
                if (building == null) continue;
                Object type = invoke(building, "getBuildingType");
                Object resourceLocation = invoke(type, "getRegistryName");
                String registry = String.valueOf(resourceLocation);
                if (!registry.equals("minecolonies:residence")) continue;

                BlockPos position = (BlockPos) invoke(building, "getPosition");
                int currentLevel = ((Number) invoke(building, "getBuildingLevel")).intValue();
                int maxLevel = ((Number) invoke(building, "getMaxBuildingLevel")).intValue();
                if (currentLevel >= maxLevel || maxLevel < 1
                        || occupiedWorkSites.contains(position)) {
                    continue;
                }

                // Ensure the target structure/blueprint pack is registered.
                String pack = String.valueOf(invoke(building, "getStructurePack"));
                String path = String.valueOf(invoke(building, "getBlueprintPath"));
                if (pack.isBlank() || path.isBlank()
                        || "null".equals(pack) || "null".equals(path)) {
                    continue;
                }
                // An upgrade should only be placed on a fully built residence,
                // never on an incomplete or partially deconstructed structure.
                if (currentLevel > 0 && !Boolean.TRUE.equals(invoke(building, "isBuilt"))) {
                    continue;
                }

                eligible.add(new Candidate(building, position, currentLevel, maxLevel));
            }
            eligible.sort(Comparator.comparingInt(Candidate::level)
                    .thenComparing(c -> c.position().asLong()));
            if (eligible.isEmpty()) {
                return new Result(false, "Lakeside behöver fler bostadsplatser, "
                        + "men jag hittar ingen tillgänglig registrerad Residence "
                        + "som kan byggas eller uppgraderas utan en ny ritningsplacering. "
                        + "Jag lägger därför ingen osäker order. "
                        + "Vi behöver ansluta Structurize-placering för nya hus eller vägar.");
            }

            // Explicit proof of a new work-order ID after invoking the official
            // requestUpgrade API. Otherwise never claim it was queued.
            Candidate selected = eligible.get(0);
            Set<?> before = new HashSet<>(orders.keySet());
            Method request = selected.building().getClass().getMethod(
                    "requestUpgrade", Player.class, BlockPos.class);
            request.invoke(selected.building(), player, BlockPos.ZERO);

            Map<?, ?> after = (Map<?, ?>) invoke(work, "getWorkOrders");
            for (Map.Entry<?, ?> entry : after.entrySet()) {
                if (before.contains(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                Object site = invoke(entry.getValue(), "getLocation");
                if (!selected.position().equals(site)) continue;

                String action = selected.level() == 0
                        ? "bygga bostaden" : "uppgradera bostaden";
                return new Result(true, "Jag har undersökt bostadsbehovet och lagt "
                        + "en VERKLIG MineColonies-arbetsorder (#" + entry.getKey()
                        + ") för att " + action + " vid "
                        + selected.position().toShortString() + ". "
                        + "Byggaren behöver tillgängligt material och rätt nivå.");
            }
            return new Result(false, "Jag försökte beställa en Residence-byggnation "
                    + "genom MineColonies, men ingen ny arbetsorder skapades. "
                    + "Kontrollera forskningskrav, ritningsnivå och Builder-hus.");

        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException invocation
                    ? invocation.getTargetException() : error;
            return new Result(false, "Stadsplaneringen kunde inte slutföras: "
                    + cause.getClass().getSimpleName() + ". Inga nya arbetsordrar "
                    + "har kunnat bekräftas.");
        }
    }

    private record Candidate(Object building, BlockPos position, int level, int maxLevel) {
    }

    private static boolean isResidentialOrder(Object order)
            throws ReflectiveOperationException {
        String path = String.valueOf(invoke(order, "getStructurePath"))
                .toLowerCase(java.util.Locale.ROOT);
        Object title = invoke(order, "getDisplayName");
        String display = title instanceof Component component
                ? component.getString().toLowerCase(java.util.Locale.ROOT)
                : String.valueOf(title).toLowerCase(java.util.Locale.ROOT);
        return path.contains("residence") || display.contains("residence")
                || display.contains("bostad") || display.contains("housing");
    }

    private static boolean canManageHuts(Object colony, ServerPlayer player)
            throws ReflectiveOperationException {
        Object permissions = invoke(colony, "getPermissions");
        Class<?> actionType = Class.forName(
                "com.minecolonies.api.colony.permissions.Action");
        @SuppressWarnings("unchecked")
        Class<? extends Enum> enumType = (Class<? extends Enum>) actionType.asSubclass(Enum.class);
        Object action = Enum.valueOf(enumType, "MANAGE_HUTS");
        return Boolean.TRUE.equals(
                permissions.getClass().getMethod(
                        "hasPermission", Player.class, actionType)
                        .invoke(permissions, player, action));
    }

    private static Object nearbyColony(ServerPlayer player, AiBotEntity npc)
            throws ReflectiveOperationException {
        Level world = player.serverLevel();
        BlockPos location = npc.blockPosition();
        Class<?> managerType = Class.forName("com.minecolonies.api.colony.IColonyManager");
        Object manager = managerType.getMethod("getInstance").invoke(null);
        Object colony = managerType.getMethod(
                "getColonyByPosFromWorld", Level.class, BlockPos.class)
                .invoke(manager, world, location);
        if (colony == null) {
            Object nearest = managerType.getMethod(
                    "getClosestColony", Level.class, BlockPos.class)
                    .invoke(manager, world, location);
            if (nearest != null && invoke(nearest, "getCenter") instanceof BlockPos center
                    && location.distSqr(center) <= MAX_DISTANCE_SQ) {
                colony = nearest;
            }
        }
        return colony;
    }

    private static Object invoke(Object value, String name)
            throws ReflectiveOperationException {
        if (value == null) throw new ReflectiveOperationException(name + " on null");
        return value.getClass().getMethod(name).invoke(value);
    }
}
