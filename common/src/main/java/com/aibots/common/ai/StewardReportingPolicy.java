package com.aibots.common.ai;

/** Suppress repeated automatic chatter; actual work receipts are always visible. */
public final class StewardReportingPolicy {
    private StewardReportingPolicy() {}
    public static boolean shouldReport(String message, String previous, long now, long nextReport, boolean changedWorld) {
        return changedWorld || now >= nextReport && !message.equals(previous);
    }
}
