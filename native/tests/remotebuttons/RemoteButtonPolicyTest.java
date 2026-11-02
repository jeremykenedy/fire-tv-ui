package com.jeremykenedy.firetv.ui.nativeapp.device.remotebuttons;

public final class RemoteButtonPolicyTest {
    private static int launches;
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        RemoteButtonPolicy policy = new RemoteButtonPolicy();
        RemoteButtonPolicy.Launch open = () -> { launches++; return true; };
        for (int key = 0; key <= 350; key++) {
            if (key >= 289 && key <= 300) continue;
            check(!policy.dispatch(key, 0, 0, false, open), "Protected or unknown key consumed: " + key);
        }
        check(launches == 0, "Protected keys launched an action");
        check(!policy.dispatch(289, 1, 0, false, open), "Orphan release consumed");
        check(!policy.dispatch(289, 0, 1, false, open), "Orphan repeat consumed");
        check(!policy.dispatch(289, 0, 0, true, open), "Cancelled press launched");
        check(policy.dispatch(289, 0, 0, false, open), "Initial press unhandled");
        check(policy.dispatch(289, 0, 5, false, open), "Handled repeat escaped");
        check(launches == 1, "Repeat relaunched action");
        check(policy.dispatch(290, 0, 0, false, open), "Second key unhandled");
        check(policy.dispatch(289, 1, 0, true, open), "Consumed press release escaped");
        check(policy.dispatch(290, 1, 0, false, open), "Second key release escaped");
        check(!policy.dispatch(289, 1, 0, false, open), "Duplicate release consumed");
        check(!policy.dispatch(289, 0, 0, false, () -> false), "Unavailable target consumed");
        check(!policy.dispatch(289, 1, 0, false, open), "Failed target release consumed");
        check(!policy.dispatch(289, 2, 0, false, open), "Multiple action consumed");
        check(policy.dispatch(300, 0, 0, false, open), "Last featured key excluded");
        policy.clear();
        check(!policy.dispatch(300, 1, 0, false, open), "Lifecycle clear retained a press");
        System.out.println("RemoteButtonPolicyTest passed: protected keys, complete pairs, repeats, failed launch, cancellation, lifecycle");
    }
}
