package com.aibots.forge;

import com.aibots.common.entity.AiBotEntity;
import com.aibots.common.ai.StewardVisitState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Physical inspection visits plus continued planning and construction-site patrols. */
final class ColonyStewardController {
    private static final String TASK = "AIKingdomsStewardTask";
    private ColonyStewardController() {}
    static CompoundTag task(AiBotEntity npc) { return npc.getPersistentData().getCompound(TASK); }
    static boolean hasTask(AiBotEntity npc) { return task(npc).contains("Anchor"); }

    static void visit(ServerPlayer owner, AiBotEntity npc, String type, String pack, String path,
                      BlockPos anchor, int rotation, BlockPos approach) {
        CompoundTag task = new CompoundTag();
        task.putUUID("Owner", owner.getUUID()); task.putString("Type", type);
        task.putString("Pack", pack); task.putString("Path", path);
        task.putLong("Anchor", anchor.asLong()); task.putLong("Approach", approach.asLong());
        task.putInt("Rotation", rotation); task.putLong("Started", npc.level().getGameTime());
        npc.getPersistentData().put(TASK, task);
        npc.goTo(approach);
    }

    static void cancelVisit(AiBotEntity npc) {
        if (hasTask(npc)) npc.stay();
        npc.getPersistentData().remove(TASK);
    }

    static void pause(AiBotEntity npc) {
        cancelVisit(npc);
        npc.setStewardEnabled(false, null);
        npc.stay();
        npc.setStewardReport("Stadsarbetet pausat av spelaren.");
    }

    static void tick(ServerPlayer owner, AiBotEntity npc) {
        long now = npc.level().getGameTime();
        CompoundTag state = npc.getPersistentData();
        if (hasTask(npc)) {
            CompoundTag task = task(npc);
            if (!task.hasUUID("Owner") || !owner.getUUID().equals(task.getUUID("Owner"))) {
                cancelVisit(npc); return;
            }
            BlockPos approach = BlockPos.of(task.getLong("Approach"));
            StewardVisitState.Step step = StewardVisitState.next(now, task.getLong("Started"),
                    npc.blockPosition().distSqr(approach) <= 9, npc.getMoveMode() == AiBotEntity.MoveMode.GO_TO,
                    task.contains("InspectUntil") ? task.getLong("InspectUntil") : -1);
            switch (step) {
                case START_INSPECTION -> {
                    npc.stay(); task.putLong("InspectUntil", now + 60);
                    npc.setStewardReport("På plats: inspekterar terräng och utrymme för " + task.getString("Type") + ".");
                }
                case INSPECT -> npc.stay();
                case EXECUTE -> { task.putBoolean("Ready", true); plan(owner, npc, now); }
                case RETRY -> {
                    cancelVisit(npc);
                    npc.setStewardReport("Kom inte fram till tomten. Jag söker en annan plats.");
                    owner.sendSystemMessage(Component.literal(npc.botName() + ": " + npc.getStewardReport()));
                    state.putLong("AIKingdomsNextPlan", now + 100);
                }
                case WALK -> { }
            }
            return;
        }
        if (!npc.isStewardEnabled()) return;
        if (now >= state.getLong("AIKingdomsNextPlan")) plan(owner, npc, now);
        if (!hasTask(npc) && npc.getMoveMode() != AiBotEntity.MoveMode.GO_TO
                && now >= state.getLong("AIKingdomsNextPatrol")) {
            patrol(owner, npc);
            state.putLong("AIKingdomsNextPatrol", now + 200);
        }
    }

    private static void plan(ServerPlayer owner, AiBotEntity npc, long now) {
        boolean wasVisiting = hasTask(npc);
        ColonyDevelopmentPlanner.Result result = ColonyDevelopmentPlanner.develop(owner, npc);
        CompoundTag state = npc.getPersistentData();
        state.putLong("AIKingdomsNextPlan", now + 100);
        if (result.modified() || wasVisiting != hasTask(npc) || now >= state.getLong("AIKingdomsNextReport")) {
            owner.sendSystemMessage(Component.literal(npc.botName() + ": " + result.description()));
            state.putLong("AIKingdomsNextReport", now + 1200);
        }
    }

    private static void patrol(ServerPlayer owner, AiBotEntity npc) {
        try {
            Object colony = ColonyDevelopmentPlanner.nearbyColony(owner, npc);
            if (colony == null) return;
            List<BlockPos> sites = new ArrayList<>();
            Object work = ColonyDevelopmentPlanner.invoke(colony, "getWorkManager");
            for (Object order : ((Map<?, ?>) ColonyDevelopmentPlanner.invoke(work, "getWorkOrders")).values()) {
                if (order != null && ColonyDevelopmentPlanner.invoke(order, "getLocation") instanceof BlockPos p) sites.add(p);
            }
            if (sites.isEmpty()) {
                Object manager = ColonyDevelopmentPlanner.invoke(colony, "getServerBuildingManager");
                for (Object building : ((Map<?, ?>) ColonyDevelopmentPlanner.invoke(manager, "getBuildings")).values()) {
                    if (building != null && ColonyDevelopmentPlanner.invoke(building, "getPosition") instanceof BlockPos p) sites.add(p);
                }
            }
            sites.sort(Comparator.comparingLong(BlockPos::asLong));
            if (sites.isEmpty()) return;
            int index = Math.floorMod(npc.getPersistentData().getInt("AIKingdomsPatrolIndex"), sites.size());
            npc.getPersistentData().putInt("AIKingdomsPatrolIndex", index + 1);
            BlockPos target = approach(npc.level(), sites.get(index));
            if (target != null && npc.blockPosition().distSqr(target) > 9) npc.goTo(target);
        } catch (ReflectiveOperationException | RuntimeException error) {
            npc.setStewardReport("Kunde inte läsa inspektionsrutten: " + error.getClass().getSimpleName());
        }
    }

    static BlockPos approach(Level world, BlockPos target) {
        for (int r = 3; r <= 9; r += 3) {
            for (int[] direction : new int[][]{{1,0},{0,1},{-1,0},{0,-1}}) {
                int x = target.getX() + direction[0] * r, z = target.getZ() + direction[1] * r;
                if (!world.hasChunkAt(new BlockPos(x, target.getY(), z))) continue;
                BlockPos p = new BlockPos(x, ColonyBlueprintPlacement.groundY(world, x, z), z);
                if (world.getBlockState(p).getCollisionShape(world, p).isEmpty()
                        && world.getBlockState(p.above()).getCollisionShape(world, p.above()).isEmpty()
                        && world.getFluidState(p).isEmpty() && world.getFluidState(p.above()).isEmpty()
                        && world.getBlockState(p.below()).isSolidRender(world, p.below())) return p;
            }
        }
        return null;
    }
}
