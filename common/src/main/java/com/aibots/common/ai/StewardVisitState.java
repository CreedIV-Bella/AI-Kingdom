package com.aibots.common.ai;

/** A visit may execute only after physical arrival and an inspection interval. */
public final class StewardVisitState {
    private StewardVisitState() {}
    public enum Step { WALK, START_INSPECTION, INSPECT, EXECUTE, RETRY }
    public static Step next(long now, long started, boolean arrived, boolean walking, long inspectionUntil) {
        if (arrived) {
            if (inspectionUntil < 0) return Step.START_INSPECTION;
            return now < inspectionUntil ? Step.INSPECT : Step.EXECUTE;
        }
        if (now - started > 2400 || !walking) return Step.RETRY;
        return Step.WALK;
    }
}
