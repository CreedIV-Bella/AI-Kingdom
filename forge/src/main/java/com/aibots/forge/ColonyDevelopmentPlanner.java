package com.aibots.forge;

import com.aibots.common.entity.AiBotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-thread MineColonies city steward: queue existing projects, grow housing,
 * discover missing essential buildings and place registered server blueprints.
 * requestUpgrade is the authoritative BUILD/UPGRADE gate; success requires a new order ID.
 */
public final class ColonyDevelopmentPlanner {
    private static final long MAX_DISTANCE_SQ = 128L * 128L;

    private ColonyDevelopmentPlanner() {
    }

    public record Result(boolean modified, String description) {
    }

    public static boolean isOwner(ServerPlayer player, AiBotEntity npc) {
        if (player == null || npc == null || npc.level() != player.level()) {
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
        Result result = developNow(player, npc);
        if (npc != null) npc.setStewardReport(result.description());
        return result;
    }

    private static Result developNow(ServerPlayer player, AiBotEntity npc) {
        if (player == null || npc == null) {
            return new Result(false, "Jag måste prata med en vald NPC.");
        }
        if (npc.level() != player.level()) {
            return new Result(false, "Kolonins ägare behöver vara i samma dimension som Aldric.");
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

            npc.getPersistentData().putInt("AIKingdomsColonyId", ((Number) invoke(colony, "getID")).intValue());
            npc.getPersistentData().putString("AIKingdomsColonyDimension", npc.level().dimension().location().toString());
            Object manager = invoke(colony, "getCitizenManager");
            int residents = ((Number) invoke(manager, "getCurrentCitizenCount")).intValue();
            int capacity = ((Number) invoke(manager, "getMaxCitizens")).intValue();
            Object work = invoke(colony, "getWorkManager");
            Map<?, ?> orders = (Map<?, ?>) invoke(work, "getWorkOrders");
            Object structureManager = invoke(colony, "getServerBuildingManager");
            Map<?, ?> buildings = (Map<?, ?>) invoke(structureManager, "getBuildings");

            List<Object> allBuildings = new ArrayList<>(buildings.values());
            allBuildings.removeIf(java.util.Objects::isNull);
            Map<String, Integer> counts = new java.util.HashMap<>();
            Set<BlockPos> occupiedWorkSites = new HashSet<>();
            boolean residencePending = false;
            for (Object order : orders.values()) {
                if (order == null) continue;
                Object location = invoke(order, "getLocation");
                if (location instanceof BlockPos pos) occupiedWorkSites.add(pos);
                residencePending |= isResidentialOrder(order);
            }
            List<Candidate> eligible = new ArrayList<>();
            for (Object building : allBuildings) {
                String type = buildingType(building);
                counts.merge(type, 1, Integer::sum);
                BlockPos position = (BlockPos) invoke(building, "getPosition");
                int currentLevel = ((Number) invoke(building, "getBuildingLevel")).intValue();
                int maxLevel = ((Number) invoke(building, "getMaxBuildingLevel")).intValue();
                if (type.equals("residence") && currentLevel == 0) residencePending = true;
                if (occupiedWorkSites.contains(position) || currentLevel >= maxLevel) continue;
                String pack = String.valueOf(invoke(building, "getStructurePack"));
                String path = String.valueOf(invoke(building, "getBlueprintPath"));
                if (pack.isBlank() || path.isBlank() || pack.equals("null") || path.equals("null")) continue;
                // Finish any existing registered project; upgrade housing only when capacity is full.
                if (currentLevel == 0 || type.equals("residence") && residents >= capacity
                        && !residencePending && Boolean.TRUE.equals(invoke(building, "isBuilt"))) {
                    eligible.add(new Candidate(building, position, currentLevel, maxLevel));
                }
            }
            // A blocked housing/project upgrade needs builder capacity before more retries.
            int requiredBuilderLevel = 0;
            for (Candidate candidate : eligible) {
                if (builderProblem(colony, candidate.building()).equals("nivå"))
                    requiredBuilderLevel = Math.max(requiredBuilderLevel, candidate.level() + 1);
            }
            boolean builderUpgradePending = false;
            for (Object building : allBuildings) {
                if (buildingType(building).equals("builder") && occupiedWorkSites.contains(invoke(building, "getPosition")))
                    builderUpgradePending = true;
            }
            Object prerequisite = null;
            int bestBuilderLevel = -1;
            for (Object building : allBuildings) {
                if (builderUpgradePending || !buildingType(building).equals("builder")) continue;
                int level = ((Number) invoke(building, "getBuildingLevel")).intValue();
                if (level >= requiredBuilderLevel || level >= ((Number) invoke(building, "getMaxBuildingLevel")).intValue()
                        || level <= bestBuilderLevel || occupiedWorkSites.contains(invoke(building, "getPosition"))
                        || ((java.util.Collection<?>) invoke(building, "getAllAssignedCitizen")).isEmpty()) continue;
                String blueprint = String.valueOf(invoke(building, "getBlueprintPath"));
                String pack = String.valueOf(invoke(building, "getStructurePack"));
                if (blueprint.isBlank() || blueprint.equals("null") || pack.isBlank() || pack.equals("null")) continue;
                if (!builderProblem(colony, building).isEmpty()) continue;
                prerequisite = building;
                bestBuilderLevel = level;
            }
            if (prerequisite != null) {
                Object selected = prerequisite;
                eligible.removeIf(candidate -> candidate.building() == selected);
                eligible.add(new Candidate(prerequisite, (BlockPos) invoke(prerequisite, "getPosition"),
                        bestBuilderLevel, ((Number) invoke(prerequisite, "getMaxBuildingLevel")).intValue()));
            }
            int target = com.aibots.common.ai.ColonyBuildPolicy.queueTarget(counts.getOrDefault("builder", 0));
            if (orders.size() >= target && prerequisite == null) {
                ColonyStewardController.cancelVisit(npc);
                return new Result(false, "Byggkön har " + orders.size() + " projekt. Jag besöker byggplatserna och fyller på när kön minskar; målet är "
                        + target + " samtidiga eller väntande projekt.");
            }
            final boolean full = residents >= capacity;
            eligible.sort(Comparator.comparingInt((Candidate c) -> {
                try { return com.aibots.common.ai.ColonyBuildPolicy.priority(buildingType(c.building()), full); }
                catch (ReflectiveOperationException e) { return 99; }
            }).thenComparingInt(Candidate::level).thenComparing(c -> c.position().asLong()));
            List<String> queued = new ArrayList<>();
            int rejected = 0;
            List<String> blockers = new ArrayList<>();
            for (Candidate candidate : eligible) {
                boolean isPrerequisite = candidate.building() == prerequisite;
                if (((Map<?, ?>) invoke(work, "getWorkOrders")).size() >= target && !isPrerequisite) continue;
                String type = buildingType(candidate.building());
                if (candidate.level() > 0 && type.equals("residence") && residencePending) continue;
                String problem = builderProblem(colony, candidate.building());
                if (!problem.isEmpty()) {
                    blockers.add(type + " nivå " + (candidate.level() + 1) + ": "
                            + (problem.equals("nivå") ? "behöver bemannat byggarhus på rätt nivå" : "behöver byggare inom räckhåll"));
                    continue;
                }
                String receipt = queue(player, colony, work, candidate.building(), npc, isPrerequisite);
                if (receipt == null) { rejected++; continue; }
                queued.add(receipt);
                if (type.equals("residence")) residencePending = true;
            }
            Map<String, Integer> planningCounts = new java.util.HashMap<>(counts);
            var deferred = npc.getPersistentData().getCompound("AIKingdomsDeferredBuilds");
            boolean housingDeferred = false;
            boolean anyDeferred = false;
            for (String type : deferred.getAllKeys()) {
                if (deferred.getLong(type) <= npc.level().getGameTime()) continue;
                planningCounts.putIfAbsent(type, 1);
                if (type.equals("residence")) housingDeferred = true;
                anyDeferred = true;
            }
            String next = com.aibots.common.ai.ColonyBuildPolicy.nextMissing(residents, capacity, planningCounts,
                    residencePending || housingDeferred);
            if (ColonyStewardController.hasTask(npc)
                    && !java.util.Objects.equals(next, ColonyStewardController.task(npc).getString("Type"))) {
                ColonyStewardController.cancelVisit(npc);
            }
            String nextReport = "";
            if (((Map<?, ?>) invoke(work, "getWorkOrders")).size() < target && next != null) {
                ColonyBlueprintPlacement.Placement placement =
                        ColonyBlueprintPlacement.place(player, colony, next, allBuildings, npc);
                nextReport = placement.description();
                if (placement.building() != null) {
                    String receipt = queue(player, colony, work, placement.building(), npc, false);
                    if (receipt != null) queued.add(receipt);
                    else nextReport += ". Tomten finns, men MineColonies skapade ingen byggorder. "
                            + "Kontrollera Builder-nivå, avstånd och forskningskrav. Jag försöker med samma tomt nästa gång.";
                }
            }
            String report = queued.isEmpty() ? "Inga nya byggordrar skapades." :
                    "Verkliga MineColonies-ordrar i byggkön: " + String.join("; ", queued) + ".";
            if (rejected > 0) report += " " + rejected
                    + " projekt väntar efter nekad beställning eller under återförsökspaus; kontrollera forskningskrav och byggarnas nivå/avstånd.";
            if (!blockers.isEmpty()) report += " Väntar: " + String.join("; ", blockers) + ".";
            if (!nextReport.isEmpty()) report += " " + nextReport;
            if (queued.isEmpty() && next == null && rejected == 0) report +=
                    anyDeferred ? " Några tomtsökningar behöver ett nytt försök. Jag besöker byggplatser och försöker igen inom en minut."
                            : " Grundfunktionerna finns redan eller är planerade. Jag bevakar bostadsbehov och byggkö.";
            return new Result(!queued.isEmpty(), report);

        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            com.mojang.logging.LogUtils.getLogger().warn("AI Kingdoms colony development failed", error);
            Throwable cause = error instanceof InvocationTargetException invocation
                    ? invocation.getTargetException() : error;
            return new Result(false, "Stadsplaneringen kunde inte slutföras: "
                    + cause.getClass().getSimpleName() + ". Ett tidigare delsteg kan ha genomförts. "
                    + "Kontrollera kolonistatus och serverloggen innan nästa försök.");
        }
    }

    private static String buildingType(Object building) throws ReflectiveOperationException {
        return String.valueOf(invoke(invoke(building, "getBuildingType"), "getRegistryName"))
                .replace("minecolonies:", "");
    }

    private static String queue(ServerPlayer player, Object colony, Object work, Object building, AiBotEntity npc, boolean prerequisite)
            throws ReflectiveOperationException {
        BlockPos position = (BlockPos) invoke(building, "getPosition");
        Map<?, ?> beforeOrders = (Map<?, ?>) invoke(work, "getWorkOrders");
        for (Object order : beforeOrders.values()) {
            if (order != null && position.equals(invoke(order, "getLocation"))) return null;
        }
        if (!builderProblem(colony, building).isEmpty()) return null;
        // Unmodelled gates (research, parents, etc.) may emit vanilla chat once, not every planning tick.
        var retries = npc.getPersistentData().getCompound("AIKingdomsBuildRetries");
        String retryKey = position.asLong() + ":" + invoke(building, "getBuildingLevel");
        long now = npc.level().getGameTime();
        if (now < retries.getLong(retryKey)) return null;
        retries.putLong(retryKey, now + 1200);
        npc.getPersistentData().put("AIKingdomsBuildRetries", retries);
        Set<?> before = new HashSet<>(beforeOrders.keySet());
        building.getClass().getMethod("requestUpgrade", Player.class, BlockPos.class)
                .invoke(building, player, BlockPos.ZERO);
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) invoke(work, "getWorkOrders")).entrySet()) {
            if (before.contains(entry.getKey()) || entry.getValue() == null
                    || !position.equals(invoke(entry.getValue(), "getLocation"))) continue;
            // Ordinary tasks join the end. A necessary builder upgrade goes first among
            // waiting work, without changing existing orders or interrupting active workers.
            int lowest = 0;
            for (Object order : beforeOrders.values()) {
                if (order != null && order != entry.getValue()) lowest = Math.min(lowest,
                        ((Number) invoke(order, "getPriority")).intValue());
            }
            int priority = lowest > -100000 ? lowest - 1 : lowest;
            if (prerequisite) {
                int highest = 0;
                for (Object order : beforeOrders.values()) {
                    if (order != null && order != entry.getValue()) highest = Math.max(highest,
                            ((Number) invoke(order, "getPriority")).intValue());
                }
                priority = highest == Integer.MAX_VALUE ? highest : highest + 1;
            }
            retries.remove(retryKey);
            entry.getValue().getClass().getMethod("setPriority", int.class).invoke(entry.getValue(), priority);
            work.getClass().getMethod("setDirty", boolean.class).invoke(work, true);
            invoke(colony, "markDirty");
            return "#" + entry.getKey() + " " + buildingType(building) + " vid " + position.toShortString();
        }
        return null;
    }

    /** Use MineColonies' own read-only checks before requestUpgrade broadcasts a rejection. */
    private static String builderProblem(Object colony, Object building) throws ReflectiveOperationException {
        Class<?> orderType = Class.forName("com.minecolonies.api.colony.workorders.WorkOrderType");
        String action = ((Number) invoke(building, "getBuildingLevel")).intValue() == 0 ? "BUILD" : "UPGRADE";
        Object kind = null;
        for (Object value : orderType.getEnumConstants()) if (((Enum<?>) value).name().equals(action)) kind = value;
        Class<?> colonyType = Class.forName("com.minecolonies.api.colony.IColony");
        Object preview = Class.forName("com.minecolonies.core.colony.workorders.WorkOrderBuilding")
                .getMethod("create", orderType, Class.forName("com.minecolonies.api.colony.buildings.IBuilding"))
                .invoke(null, kind, building);
        int level = ((Number) invoke(preview, "getTargetLevel")).intValue();
        boolean special = Boolean.TRUE.equals(building.getClass().getMethod("canBeBuiltByBuilder", int.class).invoke(building, level));
        boolean resolved = Boolean.TRUE.equals(preview.getClass().getMethod("canBeResolved", colonyType, int.class).invoke(preview, colony, level));
        if (!special && !resolved) return "nivå";
        if (Boolean.TRUE.equals(preview.getClass().getMethod("tooFarFromAnyBuilder", colonyType, int.class).invoke(preview, colony, level))) return "avstånd";
        return "";
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
        Object action = null;
        for (Object value : actionType.getEnumConstants()) {
            if ("MANAGE_HUTS".equals(((Enum<?>) value).name())) {
                action = value;
                break;
            }
        }
        if (action == null) return false;
        return Boolean.TRUE.equals(
                permissions.getClass().getMethod(
                        "hasPermission", Player.class, actionType)
                        .invoke(permissions, player, action));
    }

    static Object nearbyColony(ServerPlayer player, AiBotEntity npc)
            throws ReflectiveOperationException {
        Level world = player.serverLevel();
        BlockPos location = npc.blockPosition();
        Class<?> managerType = Class.forName("com.minecolonies.api.colony.IColonyManager");
        Object manager = managerType.getMethod("getInstance").invoke(null);
        if (npc.getPersistentData().contains("AIKingdomsColonyId")) {
            if (!world.dimension().location().toString().equals(npc.getPersistentData().getString("AIKingdomsColonyDimension"))) return null;
            return managerType.getMethod("getColonyByWorld", int.class, Level.class)
                    .invoke(manager, npc.getPersistentData().getInt("AIKingdomsColonyId"), world);
        }
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

    static Object invoke(Object value, String name)
            throws ReflectiveOperationException {
        if (value == null) throw new ReflectiveOperationException(name + " on null");
        return value.getClass().getMethod(name).invoke(value);
    }
}
