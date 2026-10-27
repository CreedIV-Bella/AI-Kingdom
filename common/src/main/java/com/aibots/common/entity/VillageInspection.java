package com.aibots.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A bounded, actual Minecraft block scan. Does not pretend to read MineColonies
 * inventories, the whole village, or undiscovered/unloaded chunks.
 */
public final class VillageInspection {
    private VillageInspection() {
    }

    public static String inspect(AiBotEntity npc) {
        BlockPos center = npc.blockPosition();
        int doors = 0, beds = 0, chests = 0, furnaces = 0, crafting = 0, farmland = 0;
        int scanned = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
            if (!npc.level().hasChunkAt(pos)) {
                continue;
            }
            BlockState state = npc.level().getBlockState(pos);
            scanned++;

            if (state.getBlock() instanceof DoorBlock
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                doors++;
            } else if (state.getBlock() instanceof BedBlock
                    && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                beds++;
            } else if (state.getBlock() instanceof ChestBlock) {
                // Counts chest blocks, not combined inventories.
                chests++;
            } else if (state.getBlock() instanceof AbstractFurnaceBlock) {
                furnaces++;
            } else if (state.getBlock() instanceof CraftingTableBlock) {
                crafting++;
            } else if (state.is(Blocks.FARMLAND)) {
                farmland++;
            }
        }

        return "Inspektion vid " + center.toShortString()
                + " (högst 8 block åt sidorna och 3 upp/ned, " + scanned
                + " laddade blockpositioner): " + doors + " dörrar, "
                + beds + " sängar, " + chests + " kistblock, "
                + furnaces + " ugnar, " + crafting + " arbetsbänkar och "
                + farmland + " jordbruksblock. "
                + "Detta är en lokal mätning, inte en fullständig rapport över byn.";
    }
}
