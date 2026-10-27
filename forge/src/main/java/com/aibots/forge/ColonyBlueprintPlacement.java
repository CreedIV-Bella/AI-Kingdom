package com.aibots.forge;

import com.aibots.common.ai.ColonyBuildPolicy.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Uses the same survival placement handler as MineColonies' Build Tool.
 * Source contract: MineColonies 1.1.1278 / Structurize 1.0.818, Minecraft 1.20.1.
 * Disk/blueprint decoding is async; all world inspection and mutation is server-thread only.
 */
final class ColonyBlueprintPlacement {
    private static final String PACKS = "com.ldtteam.structurize.storage.StructurePacks";
    private static final Map<String, CompletableFuture<List<Design>>> CATALOG = new ConcurrentHashMap<>();

    private ColonyBlueprintPlacement() {}
    static void clear() { CATALOG.clear(); }

    record Placement(Object building, String description) {}
    private record Design(Object blueprint, String pack, String path, Block block,
                          int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    static Placement place(ServerPlayer player, Object colony, String type, List<Object> buildings)
            throws ReflectiveOperationException {
        String pack = buildings.stream().map(b -> {
            try { return String.valueOf(call(b, "getStructurePack")); }
            catch (ReflectiveOperationException e) { return ""; }
        }).filter(p -> !p.isBlank() && !p.equals("null"))
                .collect(java.util.stream.Collectors.groupingBy(p -> p, java.util.stream.Collectors.counting()))
                .entrySet().stream().sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed().thenComparing(Map.Entry::getKey)).map(Map.Entry::getKey).findFirst().orElse("");
        if (pack.isEmpty()) return new Placement(null, "Ingen byggnadsstil kan avläsas från kolonin.");

        Object meta = Class.forName(PACKS).getMethod("getStructurePack", String.class).invoke(null, pack);
        if (meta == null) return new Placement(null, "Byggnadsstilen " + pack + " saknas på servern.");
        Path root = (Path) call(meta, "getPath");
        String key = root.toString() + ":" + pack + ":" + type;
        CompletableFuture<List<Design>> future = CATALOG.computeIfAbsent(key,
                ignored -> CompletableFuture.supplyAsync(() -> discover(root, pack, type)));
        if (!future.isDone()) return new Placement(null,
                "Läser MineColonies-ritningar för " + type + " i " + pack
                        + ". Automatiken försöker igen; med engångskommandot, kör develop igen om en stund.");
        if (future.isCompletedExceptionally()) {
            CATALOG.remove(key);
            return new Placement(null, "Ritningskatalogen kunde inte läsas. Kontrollera serverns strukturpaket.");
        }
        List<Design> designs = future.join();
        if (designs.isEmpty()) return new Placement(null,
                "Hittar ingen nivå-1-ritning för " + type + " i kolonins stil " + pack + ".");

        // Reserve the full future footprint of every registered building, including queued ones.
        List<Footprint> occupied = new ArrayList<>();
        for (Object b : buildings) {
            Object corners = call(b, "getCorners");
            BlockPos a = (BlockPos) call(corners, "getA");
            BlockPos z = (BlockPos) call(corners, "getB");
            if (a.equals(z)) return new Placement(null,
                    "Väntar på att MineColonies ska läsa befintliga byggnaders ritningsgränser.");
            int minX = Math.min(a.getX(), z.getX()), minZ = Math.min(a.getZ(), z.getZ());
            int maxX = Math.max(a.getX(), z.getX()), maxZ = Math.max(a.getZ(), z.getZ());
            if (call(b, "getTileEntity") instanceof BlockEntity tile) {
                int[] reserved = tile.getPersistentData().getIntArray("AIKingdomsReservedFootprint");
                if (reserved.length == 4) {
                    minX = Math.min(minX, reserved[0]); minZ = Math.min(minZ, reserved[1]);
                    maxX = Math.max(maxX, reserved[2]); maxZ = Math.max(maxZ, reserved[3]);
                }
            }
            occupied.add(new Footprint(minX, minZ, maxX, maxZ));
        }

        BlockPos center = (BlockPos) call(colony, "getCenter");
        Level world = player.level();
        int inspected = 0;
        int sector = (int) ((world.getGameTime() / 2400) % 4);
        // A reproducible street grid with gaps between maximum-level footprints.
        for (int ring = 16; ring <= 80; ring += 8) {
            for (int dx = -ring; dx <= ring; dx += 8) {
                for (int dz = -ring; dz <= ring; dz += 8) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    if (((dx + ring) / 8 + (dz + ring) / 8) % 4 != sector) continue;
                    BlockPos column = center.offset(dx, 0, dz);
                    if (!world.hasChunkAt(column)) continue;
                    int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            column.getX(), column.getZ());
                    BlockPos anchor = new BlockPos(column.getX(), y, column.getZ());
                    for (Design design : designs) {
                        Footprint footprint = new Footprint(anchor.getX() + design.minX, anchor.getZ() + design.minZ,
                                anchor.getX() + design.maxX, anchor.getZ() + design.maxZ);
                        if (occupied.stream().anyMatch(f -> f.overlaps(footprint, 3))) continue;
                        // Bound world scans per decision. Later decisions may choose another type/site.
                        if (++inspected > 48) return new Placement(null,
                                "Ingen ledig byggbar tomt hittades i denna sökning. Behöver öppnare, laddad mark i kolonin.");
                        if (!validSite(world, colony, anchor, design, footprint)) continue;
                        if (!hasHutItem(player, design.block)) {
                            return new Placement(null, "För att placera " + type
                                    + " behövs ett vanligt hut-block i din inventory (Creative behöver inget). "
                                    + "Flyttade eller förbyggda hut-block måste först tas ur inventory. Byggmaterial hämtar Builders som vanligt.");
                        }
                        Class<?> blueprintClass = Class.forName("com.ldtteam.structurize.blueprints.v1.Blueprint");
                        Class<?> settingsClass = Class.forName("com.ldtteam.structurize.util.PlacementSettings");
                        Object handler = Class.forName("com.minecolonies.core.placementhandlers.main.SurvivalHandler")
                                .getConstructor().newInstance();
                        handler.getClass().getMethod("handle", blueprintClass, String.class, String.class,
                                        boolean.class, Level.class, Player.class, BlockPos.class, settingsClass)
                                .invoke(handler, design.blueprint, pack, design.path, false, world, player, anchor,
                                        settingsClass.getConstructor().newInstance());
                        Object manager = call(colony, "getServerBuildingManager");
                        Object created = manager.getClass().getMethod("getBuilding", BlockPos.class).invoke(manager, anchor);
                        if (created == null) return new Placement(null,
                                "MineColonies accepterade inte tomten för " + type + ". Se spelets felmeddelande.");
                        String actual = String.valueOf(call(call(created, "getBuildingType"), "getRegistryName"));
                        if (!actual.equals("minecolonies:" + type)) {
                            return new Placement(null, "En tomt registrerades med oväntad typ " + actual
                                    + ". Ingen byggorder skickades; kontrollera " + anchor.toShortString() + ".");
                        }
                        // Reserve all available levels immediately, before another planner can place a neighbour.
                        if (call(created, "getTileEntity") instanceof BlockEntity tile) {
                            tile.getPersistentData().putIntArray("AIKingdomsReservedFootprint", new int[] {
                                    footprint.minX(), footprint.minZ(), footprint.maxX(), footprint.maxZ() });
                            tile.setChanged();
                        }
                        call(created, "markDirty");
                        return new Placement(created, "Registrerade " + type + " vid " + anchor.toShortString());
                    }
                }
            }
        }
        return new Placement(null, "Ingen ledig tomt för " + type
                + " hittades inom kolonins laddade område. Befintliga hus och köade projekt behålls.");
    }

    private static List<Design> discover(Path root, String pack, String type) {
        try (var paths = Files.walk(root, 8)) {
            List<Path> candidates = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("(?i).*1\\.blueprint"))
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.contains(type) || type.equals("cook") && name.contains("restaurant")
                                || type.equals("deliveryman") && name.contains("courier")
                                || type.equals("lumberjack") && name.contains("forester");
                    }).sorted().limit(16).toList();
            List<Design> result = new ArrayList<>();
            Class<?> packs = Class.forName(PACKS);
            var loader = packs.getMethod("getBlueprint", String.class, Path.class, boolean.class);
            Class<?> hutType = Class.forName("com.minecolonies.api.blocks.AbstractBlockHut");
            for (Path file : candidates) {
                Object blueprint = loader.invoke(null, pack, file, true);
                if (blueprint == null) continue;
                BlockPos offset = (BlockPos) call(blueprint, "getPrimaryBlockOffset");
                BlockState state = (BlockState) blueprint.getClass().getMethod("getBlockState", BlockPos.class)
                        .invoke(blueprint, offset);
                if (!hutType.isInstance(state.getBlock())
                        || !type.equals(String.valueOf(call(state.getBlock(), "getBlueprintName")))) continue;
                int minX = -offset.getX(), minY = -offset.getY(), minZ = -offset.getZ();
                int maxX = number(blueprint, "getSizeX") - offset.getX() - 1;
                int maxY = number(blueprint, "getSizeY") - offset.getY() - 1;
                int maxZ = number(blueprint, "getSizeZ") - offset.getZ() - 1;
                String name = file.getFileName().toString();
                for (int level = 2; level <= 5; level++) {
                    Path upgradedFile = file.resolveSibling(name.replaceFirst("1\\.blueprint$", level + ".blueprint"));
                    if (!Files.isRegularFile(upgradedFile)) continue;
                    Object upgraded = loader.invoke(null, pack, upgradedFile, true);
                    if (upgraded == null) throw new IllegalStateException("Unreadable upgrade: " + upgradedFile);
                    BlockPos o = (BlockPos) call(upgraded, "getPrimaryBlockOffset");
                    minX = Math.min(minX, -o.getX()); minY = Math.min(minY, -o.getY()); minZ = Math.min(minZ, -o.getZ());
                    maxX = Math.max(maxX, number(upgraded, "getSizeX") - o.getX() - 1);
                    maxY = Math.max(maxY, number(upgraded, "getSizeY") - o.getY() - 1);
                    maxZ = Math.max(maxZ, number(upgraded, "getSizeZ") - o.getZ() - 1);
                }
                if (maxX - minX > 63 || maxZ - minZ > 63 || maxY - minY > 63) continue;
                result.add(new Design(blueprint, pack, root.relativize(file).toString().replace('\\', '/'),
                        state.getBlock(), minX, minY, minZ, maxX, maxY, maxZ));
            }
            result.sort(Comparator.comparingInt(d -> (d.maxX - d.minX + 1) * (d.maxZ - d.minZ + 1)));
            return List.copyOf(result);
        } catch (Exception e) {
            throw new java.util.concurrent.CompletionException(e);
        }
    }

    private static boolean validSite(Level world, Object colony, BlockPos anchor, Design d, Footprint f)
            throws ReflectiveOperationException {
        int bottom = anchor.getY() + d.minY, top = anchor.getY() + d.maxY;
        if (bottom <= world.getMinBuildHeight() || top >= world.getMaxBuildHeight()) return false;
        var inColony = colony.getClass().getMethod("isCoordInColony", Level.class, BlockPos.class);
        for (int x = f.minX(); x <= f.maxX(); x++) {
            for (int z = f.minZ(); z <= f.maxZ(); z++) {
                BlockPos column = new BlockPos(x, anchor.getY(), z);
                if (!world.hasChunkAt(column) || !world.getWorldBorder().isWithinBounds(column)
                        || !Boolean.TRUE.equals(inColony.invoke(colony, world, column))) return false;
                int surface = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(surface - anchor.getY()) > 2) return false;
                for (int y = Math.min(bottom, surface - 1); y <= top; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(p);
                    if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || !natural(state)) return false;
                }
            }
        }
        return world.getBlockState(anchor).canBeReplaced()
                && world.getBlockState(anchor.below()).isSolidRender(world, anchor.below());
    }

    private static boolean natural(BlockState s) {
        return s.isAir() || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT)
                || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.STONE)
                || s.is(Blocks.GRANITE) || s.is(Blocks.DIORITE) || s.is(Blocks.ANDESITE)
                || s.is(Blocks.DEEPSLATE) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)
                || s.is(Blocks.LARGE_FERN) || s.is(Blocks.SNOW) || s.is(BlockTags.FLOWERS);
    }

    private static boolean hasHutItem(ServerPlayer player, Block block) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.is(block.asItem()) && !stack.isEmpty()) return !stack.hasTag();
        }
        return player.isCreative();
    }

    private static int number(Object value, String name) throws ReflectiveOperationException {
        return ((Number) call(value, name)).intValue();
    }
    private static Object call(Object value, String name) throws ReflectiveOperationException {
        return ColonyDevelopmentPlanner.invoke(value, name);
    }
}
