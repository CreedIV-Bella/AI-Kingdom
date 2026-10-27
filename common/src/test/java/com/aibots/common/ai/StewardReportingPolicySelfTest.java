package com.aibots.common.ai;

public final class StewardReportingPolicySelfTest {
    public static void main(String[] args) {
        check(true, "waiting", "", 0, 0, false);
        check(false, "waiting", "waiting", 2400, 1200, false);
        check(false, "new site", "waiting", 100, 1200, false);
        check(true, "new site", "waiting", 1200, 1200, false);
        check(true, "order created", "waiting", 100, 1200, true);
        check(true, "order created", "order created", 100, 1200, true);
        System.out.println("StewardReportingPolicy: 6 checks passed");
    }
    private static void check(boolean expected, String message, String previous, long now, long next, boolean modified) {
        if (StewardReportingPolicy.shouldReport(message, previous, now, next, modified) != expected)
            throw new AssertionError(message + " at " + now);
    }
}
