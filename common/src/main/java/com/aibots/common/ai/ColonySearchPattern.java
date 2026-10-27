package com.aibots.common.ai;

/** Resumeable enumeration: every four-block grid cell, design and rotation. */
public final class ColonySearchPattern {
    private ColonySearchPattern() {}
    public record Candidate(int chunk, int x, int z, int design, int rotation) {}
    public static long size(int chunks, int designs) { return (long) chunks * 16 * designs * 4; }
    public static Candidate at(long cursor, int designs) {
        if (cursor < 0 || designs < 1) throw new IllegalArgumentException();
        int rotation = (int) (cursor % 4); cursor /= 4;
        int design = (int) (cursor % designs); cursor /= designs;
        int cell = (int) (cursor % 16);
        return new Candidate((int) (cursor / 16), 2 + (cell % 4) * 4, 2 + (cell / 4) * 4, design, rotation);
    }
    public static ColonyBuildPolicy.Footprint rotate(ColonyBuildPolicy.Footprint f, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> f;
            case 1 -> new ColonyBuildPolicy.Footprint(-f.maxZ(), f.minX(), -f.minZ(), f.maxX());
            case 2 -> new ColonyBuildPolicy.Footprint(-f.maxX(), -f.maxZ(), -f.minX(), -f.minZ());
            default -> new ColonyBuildPolicy.Footprint(f.minZ(), -f.maxX(), f.maxZ(), -f.minX());
        };
    }
}
