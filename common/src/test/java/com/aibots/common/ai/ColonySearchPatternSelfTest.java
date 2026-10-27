package com.aibots.common.ai;

import java.util.HashSet;
import java.util.Set;

public final class ColonySearchPatternSelfTest {
    public static void main(String[] args) {
        Set<ColonySearchPattern.Candidate> visited = new HashSet<>();
        long total = ColonySearchPattern.size(3, 2);
        // Simulate 24-candidate batches separated by saves/restarts.
        for (long start = 0; start < total; start += 24) {
            for (long i = start; i < Math.min(start + 24, total); i++) {
                var c = ColonySearchPattern.at(i, 2);
                check(visited.add(c), "candidate repeated after resume");
                check(c.chunk() < 3 && c.design() < 2 && c.rotation() < 4, "bounds");
                check(c.x() >= 0 && c.x() < 16 && c.z() >= 0 && c.z() < 16, "chunk-local coordinates");
            }
        }
        check(visited.size() == 384, "all candidates must be covered");
        var f = new ColonyBuildPolicy.Footprint(-2, -1, 5, 3);
        var rotated = ColonySearchPattern.rotate(f, 1);
        check(rotated.equals(new ColonyBuildPolicy.Footprint(-3, -2, 1, 5)), "asymmetric footprint rotation");
        for (int n = 0; n < 4; n++) rotated = ColonySearchPattern.rotate(rotated, 1);
        check(rotated.equals(ColonySearchPattern.rotate(f, 1)), "four turns preserve footprint");
        check(ColonySearchPattern.size(0, 2) == 0, "no loaded chunks");
        System.out.println("Search regression passed: 384 unique candidates across saved batches; rotations and bounds.");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
