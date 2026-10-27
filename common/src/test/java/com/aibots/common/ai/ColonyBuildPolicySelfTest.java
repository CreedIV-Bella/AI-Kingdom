package com.aibots.common.ai;

import java.util.Map;

public final class ColonyBuildPolicySelfTest {
    public static void main(String[] args) {
        check("builder".equals(ColonyBuildPolicy.nextMissing(4, 4, Map.of(), false)), "bootstrap builder");
        var colony = Map.of("builder", 3, "residence", 1);
        check("residence".equals(ColonyBuildPolicy.nextMissing(4, 4, colony, false)), "full housing");
        check("warehouse".equals(ColonyBuildPolicy.nextMissing(4, 4, colony, true)), "housing order must not block logistics");
        check("warehouse".equals(ColonyBuildPolicy.nextMissing(4, 8, colony, false)), "no unnecessary housing");
        var complete = Map.of("builder", 3, "warehouse", 1, "deliveryman", 1, "cook", 1,
                "lumberjack", 1, "miner", 1, "farmer", 1);
        check(ColonyBuildPolicy.nextMissing(4, 8, complete, false) == null, "no endless construction");
        check(ColonyBuildPolicy.queueTarget(3) == 6, "queue work for free builders");
        check(ColonyBuildPolicy.queueTarget(100) == 12, "bounded horizon");
        var a = new ColonyBuildPolicy.Footprint(-10, -10, 0, 0);
        check(a.overlaps(new ColonyBuildPolicy.Footprint(-2, -2, 8, 8), 3), "overlap negative coords");
        check(a.overlaps(new ColonyBuildPolicy.Footprint(3, 0, 5, 5), 3), "clearance");
        check(!a.overlaps(new ColonyBuildPolicy.Footprint(4, 0, 5, 5), 3), "separated sites");
        System.out.println("10 colony build policy tests passed.");
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }
}
