package com.jeremykenedy.firetv.ui.nativeapp.device.updates;

import android.app.Instrumentation;
import android.os.Bundle;
import java.util.*;

/** In-memory fixture only: does not start a VPN, access accounts or write preferences. */
public final class UpdateBlockInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!"com.jeremykenedy.firetv.updateproof".equals(getTargetContext().getPackageName())) throw new AssertionError("Wrong target.");
            FakePackages packages = new FakePackages(); FakeBuilder builder = new FakeBuilder();
            try { UpdateBlockPolicy.build(builder, UpdateBlockPolicy.installed(packages, false, 10000)); throw new AssertionError("Empty scope accepted."); }
            catch (IllegalStateException expected) { check(builder.established == 0); }
            packages.installed = true;
            check("fixture".equals(UpdateBlockPolicy.build(builder, UpdateBlockPolicy.installed(packages, false, 10000))));
            check(builder.names.equals(Collections.singletonList("com.amazon.device.software.ota")) && builder.established == 1);
            packages.shared = true;
            try { UpdateBlockPolicy.installed(packages, false, 10000); throw new AssertionError("Shared unrelated UID accepted."); }
            catch (IllegalStateException expected) { }
            result.putString("status", "PASS");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finish(0, result);
    }
    static void check(boolean value) { if (!value) throw new AssertionError("VPN scope regression."); }
    static final class FakePackages implements UpdateBlockPolicy.Packages {
        boolean installed, shared;
        public Integer uid(String name) { return installed && name.equals("com.amazon.device.software.ota") ? 10001 : null; }
        public String[] packagesForUid(int uid) { return shared ? new String[]{"com.amazon.device.software.ota", "fixture.other"}
                : new String[]{"com.amazon.device.software.ota"}; }
    }
    static final class FakeBuilder implements UpdateBlockPolicy.Builder<String> {
        final List<String> names = new ArrayList<>(); int established;
        public void add(String name) { names.add(name); }
        public String establish() { established++; return "fixture"; }
    }
}
