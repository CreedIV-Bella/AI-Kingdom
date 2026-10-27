package com.aibots.common.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A real server-side Minecraft pathfinding goal for selected NPC orders.
 *
 * There is no LLM tool execution here: orders are explicit, validated commands.
 */
final class KingdomTravelGoal extends Goal {
    private final AiBotEntity npc;
    private int repathTicks;

    KingdomTravelGoal(AiBotEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return npc.getMoveMode() == AiBotEntity.MoveMode.FOLLOW
                || npc.getMoveMode() == AiBotEntity.MoveMode.GO_TO;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repathTicks = 20;
    }

    @Override
    public void tick() {
        if (++repathTicks < 20) {
            return;
        }
        repathTicks = 0;

        if (npc.getMoveMode() == AiBotEntity.MoveMode.FOLLOW) {
            followLeader();
        } else if (npc.getMoveMode() == AiBotEntity.MoveMode.GO_TO) {
            travelToLocation();
        }
    }

    private void followLeader() {
        if (npc.getFollowPlayerUUID() == null || npc.getServer() == null) {
            npc.stay();
            return;
        }

        ServerPlayer leader = npc.getServer().getPlayerList()
                .getPlayer(npc.getFollowPlayerUUID());
        // Never jump across dimensions or teleport on an unloaded route.
        if (leader == null || leader.level() != npc.level()) {
            npc.getNavigation().stop();
            return;
        }

        double distance = npc.distanceToSqr(leader);
        if (distance <= 9.0D) {
            npc.getNavigation().stop();
        } else if (distance <= 4096.0D) {
            npc.getNavigation().moveTo(leader, 1.1D);
        } else {
            // Leader is too distant for normal pathfinding; pause until nearby.
            npc.getNavigation().stop();
        }
    }

    private void travelToLocation() {
        BlockPos target = npc.getDestination();
        if (target == null) {
            npc.stay();
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(target);
        if (npc.position().distanceToSqr(center) <= 4.0D) {
            npc.arrived();
            return;
        }
        // We do not forcibly load remote chunks or move in inaccessible dimensions.
        if (!npc.level().hasChunkAt(target)) {
            npc.getNavigation().stop();
            return;
        }
        boolean foundPath = npc.getNavigation().moveTo(
                center.x, center.y, center.z, 1.0D);
        if (foundPath) {
            npc.clearPathFailures();
        } else if (npc.recordPathFailure()) {
            npc.pathFailed();
        }
    }

    @Override
    public void stop() {
        npc.getNavigation().stop();
    }
}
