package com.aibots.common.ai;

public final class StewardVisitStateSelfTest {
    public static void main(String[] args) {
        check(StewardVisitState.next(20, 0, false, true, -1), StewardVisitState.Step.WALK);
        check(StewardVisitState.next(20, 0, true, true, -1), StewardVisitState.Step.START_INSPECTION);
        check(StewardVisitState.next(40, 0, true, false, 80), StewardVisitState.Step.INSPECT);
        check(StewardVisitState.next(80, 0, true, false, 80), StewardVisitState.Step.EXECUTE);
        // A saved deadline must not authorize a remote placement after displacement.
        check(StewardVisitState.next(100, 0, false, true, 80), StewardVisitState.Step.WALK);
        check(StewardVisitState.next(100, 0, false, false, 80), StewardVisitState.Step.RETRY);
        check(StewardVisitState.next(2401, 0, false, true, -1), StewardVisitState.Step.RETRY);
        check(StewardVisitState.next(2400, 0, false, true, -1), StewardVisitState.Step.WALK);
        System.out.println("8 steward visit lifecycle tests passed.");
    }
    private static void check(StewardVisitState.Step actual, StewardVisitState.Step expected) {
        if (actual != expected) throw new AssertionError(actual + " != " + expected);
    }
}
