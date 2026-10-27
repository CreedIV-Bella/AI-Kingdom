package com.aibots.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only local survey of actual Create blocks in loaded Minecraft chunks.
 *
 * This is a first building block for the future Create logistics graph. Merely
 * being present nearby does not mean machines are connected, powered or working.
 * We deliberately avoid a hard compile dependency on Create for this survey.
 */
public final class CreateInspection {
    private static final int RADIUS_XZ = 10;
    private static final int RADIUS_Y = 4;

    private CreateInspection() {
    }

    public static String inspect(AiBotEntity npc) {
        BlockPos center = npc.blockPosition();
        Map<String, Integer> counts = new LinkedHashMap<>();
        int allCreateBlocks = 0;
        int loadedPositions = 0;

        for (BlockPos position : BlockPos.betweenClosed(
                center.offset(-RADIUS_XZ, -RADIUS_Y, -RADIUS_XZ),
                center.offset(RADIUS_XZ, RADIUS_Y, RADIUS_XZ))) {

            if (!npc.level().hasChunkAt(position)) {
                continue;
            }
            loadedPositions++;

            BlockState state = npc.level().getBlockState(position);
            if (state.isAir()) {
                continue;
            }

            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!"create".equals(id.getNamespace())) {
                continue;
            }

            allCreateBlocks++;
            String group = classify(id.getPath());
            counts.merge(group, 1, Integer::sum);
        }

        StringBuilder report = new StringBuilder();
        report.append("Create-inspektion vid ")
                .append(center.toShortString())
                .append(": ")
                .append(allCreateBlocks)
                .append(" Create-block i ")
                .append(loadedPositions)
                .append(" laddade blockpositioner (±")
                .append(RADIUS_XZ).append(" X/Z, ±")
                .append(RADIUS_Y).append(" Y).");

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            report.append(" ").append(entry.getKey())
                    .append(": ").append(entry.getValue()).append(".");
        }

        report.append(" Anslutningar, rotationskraft, varuflöden och tåglinjer ")
                .append("är ÄNNU INTE verifierade. Inga maskiner har styrts.");
        return report.toString();
    }

    static String classify(String id) {
        if (includes(id, "track", "bogey", "train_station", "train_signal",
                "track_signal", "train_observer")) {
            return "Järnväg";
        }
        if (includes(id, "water_wheel", "windmill_bearing", "steam_engine",
                "creative_motor", "hand_crank", "valve_handle")) {
            return "Kraftkällor";
        }
        if (includes(id, "shaft", "cogwheel", "gearbox", "chain_drive",
                "gearshift", "clutch", "speed_controller")) {
            return "Kraftöverföring";
        }
        if (includes(id, "belt", "funnel", "chute", "depot", "item_vault",
                "mechanical_arm", "tunnel", "packager")) {
            return "Varutransport";
        }
        if (includes(id, "pipe", "pump", "fluid_tank", "hose_pulley",
                "spout")) {
            return "Vätskesystem";
        }
        if (includes(id, "press", "mixer", "crushing_wheel", "millstone",
                "deployer", "mechanical_saw", "encased_fan",
                "blaze_burner", "mechanical_drill")) {
            return "Bearbetning";
        }
        return "Övriga Create-block";
    }

    private static boolean includes(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
