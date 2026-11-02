package com.jeremykenedy.firetv.ui.nativeapp.device.protection;

public final class ProtectionTest {
    public static void main(String[] args) {
        check("Not reported".equals(SleepStatus.timeout(null)), "Missing timeout must not become a default");
        check("System default (-1)".equals(SleepStatus.timeout("-1")), "System sentinel must remain distinct");
        check(SleepStatus.timeout("0").contains("system-defined"), "Zero must not promise sleep is disabled");
        check("20 minutes".equals(SleepStatus.timeout("1200000")), "Positive minutes");
        check("1234 ms".equals(SleepStatus.timeout("1234")), "Exact nonminute timer");
        check("Unrecognized value".equals(SleepStatus.timeout("invalid")), "Malformed settings must remain explicit");
        check("Unrecognized value".equals(SleepStatus.timeout("9223372036854775808")), "Overflow must remain explicit");
        check(SystemSleepTimer.milliseconds("35791") == 2147460000, "Maximum minutes must fit signed settings storage");
        boolean invalid = false;
        try { SystemSleepTimer.milliseconds("35792"); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "Overflow timer must be rejected before writing");
        FakeStore unsupported = new FakeStore(null);
        check(SystemSleepTimer.write(unsupported, 60000) == SystemSleepTimer.Result.UNAVAILABLE && unsupported.writes == 0,
                "Missing setting must not create a new system behavior");
        FakeStore denied = new FakeStore("1200000");
        denied.permission = false;
        check(SystemSleepTimer.write(denied, 60000) == SystemSleepTimer.Result.UNAVAILABLE && denied.writes == 0,
                "Missing permission must not attempt a write");
        FakeStore success = new FakeStore("1200000");
        check(SystemSleepTimer.write(success, 60000) == SystemSleepTimer.Result.SAVED && "60000".equals(success.value),
                "Successful explicit write must read back");
        FakeStore reject = new FakeStore("1200000");
        reject.reject = true;
        check(SystemSleepTimer.write(reject, 60000) == SystemSleepTimer.Result.FAILED_RESTORED && reject.writes == 1,
                "Rejected unchanged setting must preserve previous value");
        FakeStore partial = new FakeStore("1200000");
        partial.partial = true;
        check(SystemSleepTimer.write(partial, 60000) == SystemSleepTimer.Result.FAILED_RESTORED
                && "1200000".equals(partial.value), "Partial failure must roll back to exact previous timeout");
        FakeStore intervening = new FakeStore("1200000");
        intervening.intervening = true;
        check(SystemSleepTimer.write(intervening, 60000) == SystemSleepTimer.Result.RECOVERY_REQUIRED
                && "300000".equals(intervening.value) && intervening.writes == 1,
                "Intervening owner/system change must not be overwritten during recovery");
        System.out.println("PASS: missing, system-default, zero, exact and malformed sleep status");
    }
    private static final class FakeStore implements SystemSleepTimer.Store {
        String value;
        boolean permission = true, reject, partial, intervening;
        int writes;
        FakeStore(String value) { this.value = value; }
        public boolean permitted() { return permission; }
        public String read() { return value; }
        public boolean write(String wanted) {
            writes++;
            if (reject) return false;
            value = intervening ? "300000" : wanted;
            return !(partial && writes == 1) && !intervening;
        }
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
