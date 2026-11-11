package com.jeremykenedy.firetv.updatecontinuityproof;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.AtomicFile;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;

/** Writes a declared synthetic completed startup only under an exact durable guard. */
public final class SeedInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (!"com.jeremykenedy.firetv.ui".equals(context.getPackageName())
                    || !(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")))
                throw new IllegalStateException("Disposable production-package emulator required.");
            String id = arguments.getString("guard_case"), hash = arguments.getString("guard_sha");
            if (id == null || !id.startsWith("enabled-protection-") || hash == null || !hash.matches("[a-f0-9]{64}"))
                throw new IllegalStateException("Explicit durable guard required.");
            GuardStore.Result before = new GuardStore(context.getDataDir()).verify(id,
                    new LinkedHashSet<>(Arrays.asList("shared_prefs", "files", "no_backup")), hash);
            if (!before.matches) throw new IllegalStateException("Guard differs before fixture writes.");
            File marker = new File(context.getNoBackupFilesDir(), "source-startup-v1");
            if (marker.exists()) throw new IllegalStateException("Existing startup marker refused.");
            AtomicFile file = new AtomicFile(marker);
            FileOutputStream out = file.startWrite(); out.write(1); file.finishWrite(out);
            if (!context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("fire_tv_ui_block_updates", true).putBoolean("fire_tv_ui_block_appstore", false)
                    .putInt("continuity_fixture_sentinel", 17).commit())
                throw new IllegalStateException("Synthetic retained choices could not be saved.");
            result.putString("status", "PASS");
            result.putString("scope", "Synthetic completed source startup, not legacy migration.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finish("PASS".equals(result.getString("status")) ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
}
