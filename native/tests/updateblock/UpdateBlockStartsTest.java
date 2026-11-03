package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

public final class UpdateBlockStartsTest {
    public static void main(String[] args) {
        UpdateBlockStarts starts = new UpdateBlockStarts();
        check(starts.request(false), "retained request starts one startup");
        check(!starts.request(false), "duplicate request joins existing startup");
        check(!starts.request(true) && starts.manual(), "manual retry joins and takes precedence");
        starts.cancel();
        check(!starts.pending() && !starts.manual() && !starts.request(true), "revocation invalidates pending and future callbacks");

        check(!UpdateBlockStarts.automaticAllowed(27, false), "API 27 automatic start is unqualified");
        check(!UpdateBlockStarts.automaticAllowed(28, false), "API 28 automatic start is unqualified");
        check(!UpdateBlockStarts.automaticAllowed(29, true), "lockdown prevents an automatic attempt");
        check(UpdateBlockStarts.automaticAllowed(29, false), "qualified retained attempt may continue to scope and authorization checks");

        System.out.println("Retained start admission and cancellation passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
