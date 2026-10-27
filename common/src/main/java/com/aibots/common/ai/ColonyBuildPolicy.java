package com.aibots.common.ai;

import java.util.List;
import java.util.Map;

/** Deterministic first MineColonies planner. No model-generated world facts. */
public final class ColonyBuildPolicy {
    private ColonyBuildPolicy() {}

    // A rolling construction horizon, not a limit on the final city size.
    public static int queueTarget(int builders) {
        return Math.min(12, Math.max(3, builders * 2));
    }

    public static String nextMissing(int population, int capacity,
                                     Map<String, Integer> buildings,
                                     boolean residencePending) {
        if (buildings.getOrDefault("builder", 0) == 0) return "builder";
        if (population >= capacity && !residencePending) return "residence";
        for (String type : List.of("warehouse", "deliveryman", "cook", "lumberjack", "miner", "farmer")) {
            if (buildings.getOrDefault(type, 0) == 0) return type;
        }
        return null;
    }

    public static int priority(String type, boolean housingFull) {
        if (type.equals("builder")) return 0;
        if (type.equals("residence") && housingFull) return 1;
        return switch (type) {
            case "warehouse" -> 2;
            case "deliveryman" -> 3;
            case "cook" -> 4;
            case "lumberjack" -> 5;
            case "miner" -> 6;
            case "farmer" -> 7;
            default -> 8;
        };
    }

    public record Footprint(int minX, int minZ, int maxX, int maxZ) {
        public boolean overlaps(Footprint other, int clearance) {
            return minX <= other.maxX + clearance && maxX + clearance >= other.minX
                    && minZ <= other.maxZ + clearance && maxZ + clearance >= other.minZ;
        }
    }
}
