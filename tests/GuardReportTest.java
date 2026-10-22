package com.jeremykenedy.firetv.ui;

public final class GuardReportTest {
    public static void main(String[] args) {
        GuardReport report = new GuardReport();
        String initial = report.snapshot(true, true, "com.old/.Dream", "com.new/.Dream", null);
        require(initial.contains("preview_pending=true\nprevious_dream=com.old/.Dream"));
        require(initial.contains("current_dream=None"));
        require(initial.contains("last_guard_failure=Not checked"));
        report.failed(new SecurityException("private identity"));
        String failed = report.snapshot(false, false, "secret\nnew_field=private", null, "com.tv/.Dream");
        require(failed.contains("last_guard_failure=Permission denied"));
        require(failed.contains("previous_dream=Unavailable"));
        require(!failed.contains("private"));
        report.failed(new IllegalStateException("secret"));
        require(report.snapshot(true, false, null, null, null).contains("Settings repair failed"));
        report.healthy();
        require(report.snapshot(true, false, null, null, "com.tv/.Dream")
                .contains("last_guard_failure=None"));
        System.out.println("Guard failure recovery and private report checks passed.");
    }

    private static void require(boolean value) {
        if (!value) throw new AssertionError();
    }
}
