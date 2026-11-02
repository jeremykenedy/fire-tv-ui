package com.jeremykenedy.firetv.ui.nativeapp.locks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class LockTest {
    public static void main(String[] args) throws Exception {
        LockState state = new LockState();
        check(!state.hasPin(), "Fresh installs must not create a PIN or a lock.");
        check(state.verify("1234".toCharArray(), 1000) == LockResult.NO_PIN, "An absent PIN cannot authorize a lock mutation.");
        rejects(new Action() { public void run() { LockState.validatePin("12".toCharArray()); } });
        rejects(new Action() { public void run() { LockState.validatePin("123x".toCharArray()); } });
        rejects(new Action() { public void run() { state.lock("com.example/.Tv", true); } });
        state.createPin("834905".toCharArray());
        state.lock("com.example/.Tv", true);
        state.lock("folder:living-room", true);
        byte[] encoded = state.encode();
        check(!new String(encoded, StandardCharsets.ISO_8859_1).contains("834905"), "PIN must never be serialized as plaintext.");
        LockState restored = LockState.decode(encoded);
        check(restored.targets.contains("com.example/com.example.Tv") && restored.targets.contains("folder:living-room"), "Stable canonical identities survive storage reload.");
        check(LockState.key("com.example/.Tv").equals(LockState.key("com.example/com.example.Tv")), "Short and fully qualified component names must share one lock identity.");
        for (int i = 0; i < 5; i++) check(restored.verify("0000".toCharArray(), 1000) == LockResult.WRONG_PIN, "Wrong PIN must be rejected.");
        restored = LockState.decode(restored.encode());
        check(restored.verify("834905".toCharArray(), 2000) == LockResult.THROTTLED, "Restart must not clear throttling, even for correct PIN.");
        check(restored.verify("834905".toCharArray(), 31000) == LockResult.GRANTED, "Correct PIN must work after the bounded delay.");
        check(restored.remaining(31000) == 0, "Success clears previous failures.");
        restored.replacePin("905738".toCharArray());
        check(restored.verify("834905".toCharArray(), 31000) == LockResult.WRONG_PIN, "The previous PIN must stop working after change.");
        check(restored.verify("905738".toCharArray(), 31000) == LockResult.GRANTED, "New PIN works.");
        restored.lock("folder:living-room", false);
        check(restored.targets.contains("com.example/com.example.Tv") && !restored.targets.contains("folder:living-room"), "Removing a folder lock must not remove an app lock.");
        for (int i = 0; i < 15; i++) {
            long now = 1000000L + i * 300001L;
            check(restored.verify("0000".toCharArray(), now) == LockResult.WRONG_PIN, "Expired attempts must count toward bounded backoff.");
            check(restored.remaining(now) <= 300000L, "Retry delays must never exceed five minutes.");
        }
        check(restored.verify("905738".toCharArray(), 1) == LockResult.THROTTLED, "Clock rollback still applies throttling.");
        check(restored.verify("905738".toCharArray(), 300001L) == LockResult.GRANTED, "Clock rollback must not cause indefinite lockout.");
        LockState other = new LockState(); other.createPin("905738".toCharArray());
        check(!Arrays.equals(restored.encode(), other.encode()), "Each verifier must use independent salt.");
        for (int i = 0; i < encoded.length; i++) {
            final byte[] truncated = Arrays.copyOf(encoded, i);
            try { LockState.decode(truncated); throw new AssertionError("Truncated lock state must fail closed."); }
            catch (IOException expected) { }
        }
        byte[] trailing = Arrays.copyOf(encoded, encoded.length + 1);
        try { LockState.decode(trailing); throw new AssertionError("Trailing data must be rejected."); }
        catch (IOException expected) { }
        rejects(new Action() { public void run() { LockState.key("app label"); } });
        rejects(new Action() { public void run() { LockState.key("folder:../../other"); } });
        privateImport();
        System.out.println("Lock behavior/security regressions passed.");
    }

    private static void privateImport() throws Exception {
        final LockState imported = new LockState();
        final Set<String> targets = new LinkedHashSet<>(Arrays.asList("folder:42", "package:com.example", "com.example/.Main"));
        check(imported.importLegacyPin("1357".toCharArray(), targets), "Private import must enroll the existing PIN and complete lock set together.");
        check(imported.isLocked("folder:42") && imported.isLocked("com.example/.Main")
                && imported.isLocked("com.example/com.example.FutureAlias"), "Imported package locks must protect current and future aliases.");
        check(!imported.isLocked("com.example.other/.Main"), "Package lock must not match a prefix-neighbor package.");
        check(imported.verify("1357".toCharArray(), 1000) == LockResult.GRANTED, "The existing four-digit PIN must remain usable.");
        for (int i = 0; i < 5; i++) imported.verify("0000".toCharArray(), 1000);
        final byte[] throttled = imported.encode();
        check(!new String(throttled, StandardCharsets.ISO_8859_1).contains("1357"), "Private import must never serialize the raw legacy PIN.");
        check(!imported.importLegacyPin("1357".toCharArray(), targets), "An identical private retry must be idempotent.");
        check(Arrays.equals(throttled, imported.encode()), "Idempotent import must not reset retry counters, salt or any existing lock state.");
        rejects(new Action() { public void run() throws Exception { imported.importLegacyPin("2468".toCharArray(), targets); } });
        rejects(new Action() { public void run() throws Exception { imported.importLegacyPin("1357".toCharArray(), Collections.singleton("folder:99")); } });
        check(Arrays.equals(throttled, imported.encode()), "Conflicting PIN or targets must preserve existing private state byte for byte.");
        LockState reloaded = LockState.decode(throttled);
        check(reloaded.isLocked("com.example/.FutureAlias") && reloaded.verify("1357".toCharArray(), 2000) == LockResult.THROTTLED,
                "Package scope and throttling must survive import reload.");
        final LockState fresh = new LockState();
        rejects(new Action() { public void run() throws Exception { fresh.importLegacyPin("13579".toCharArray(), targets); } });
        rejects(new Action() { public void run() throws Exception { fresh.importLegacyPin("1357".toCharArray(), Collections.singleton("package:com.example/other")); } });
        check(!fresh.hasPin() && fresh.targets.isEmpty(), "Invalid private import must not partially enroll or lock.");
    }

    private interface Action { void run() throws Exception; }
    private static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid operation must fail."); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
