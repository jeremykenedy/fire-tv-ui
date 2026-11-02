package com.jeremykenedy.firetv.ui.nativeapp.migration;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import android.util.AtomicFile;
import java.io.*;
import java.util.*;

/** Self-targeted fixture only. A real process stop leaves the journal for the next invocation. */
public final class MigrationCrashInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { this.arguments = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (!"com.jeremykenedy.firetv.migrationproof".equals(context.getPackageName())) throw new AssertionError("Unexpected fixture package.");
            String phase = arguments.getString("phase", "");
            if (phase.equals("interrupt")) interrupt(context, Integer.parseInt(arguments.getString("after", "1")));
            else if (phase.equals("recover")) recover(context, result);
            else throw new AssertionError("Use interrupt or recover phase.");
            finish(Activity.RESULT_OK, result);
        } catch (Exception | AssertionError failed) {
            result.putString("failure", failed.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
    private static void interrupt(Context context, final int after) throws IOException {
        if (after < 1 || after > 3) throw new AssertionError("The fixture changes exactly three stores.");
        for (String file : LegacyPresentationMapper.FILES) {
            if (!context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()) throw new IOException("Fixture reset failed.");
        }
        File recovery = new File(context.getNoBackupFilesDir(), "presentation-migration-v1.snapshot");
        for (String suffix : new String[]{"", ".bak", ".new"}) {
            File file = new File(recovery.getPath() + suffix);
            if (file.exists() && !file.delete()) throw new IOException("Fixture recovery reset failed.");
        }
        SharedPreferences original = context.getSharedPreferences(LauncherMigration.PREFERENCES, Context.MODE_PRIVATE);
        if (!original.edit().putInt("apps_per_row", 7).putInt("app_max_rows", 0)
                .putString("icon_focus_style", "border").putInt("icon_focus_border", 0xff123456)
                .putLong("unknown_future", 9000000000L).putFloat("unknown_float", -0.0f)
                .putBoolean("white_border", false).putStringSet("unknown_set", new LinkedHashSet<>(Arrays.asList("fixture-a", "fixture-b")))
                .commit()) throw new IOException("Fixture seed failed.");
        final AndroidPresentationStore actual = new AndroidPresentationStore(context);
        PresentationSnapshot before = actual.read();
        Map<String, PreferenceSnapshot> nativeBefore = new LinkedHashMap<>(before.stores);
        nativeBefore.remove(LauncherMigration.PREFERENCES);
        LegacyPresentationMapper.Plan plan = LegacyPresentationMapper.plan(before.stores.get(LauncherMigration.PREFERENCES),
                PresentationMappingContext.empty(), nativeBefore);
        AtomicFile expected = new AtomicFile(new File(context.getNoBackupFilesDir(), "fixture-expected.snapshot"));
        FileOutputStream output = expected.startWrite();
        try { output.write(before.encode()); expected.finishWrite(output); }
        catch (IOException failed) { expected.failWrite(output); throw failed; }
        if (!context.getSharedPreferences("fixture_control", Context.MODE_PRIVATE).edit().putInt("pid", Process.myPid())
                .putInt("after", after).commit()) throw new IOException("Fixture control save failed.");
        PresentationTransaction.Store interrupted = new PresentationTransaction.Store() {
            private int writes;
            @Override public Object lock() { return actual.lock(); }
            @Override public PresentationSnapshot read() throws IOException { return actual.read(); }
            @Override public void saveRecovery(PresentationSnapshot snapshot) throws IOException { actual.saveRecovery(snapshot); }
            @Override public boolean replace(String file, PreferenceSnapshot snapshot) throws IOException {
                boolean committed = actual.replace(file, snapshot);
                if (committed && ++writes == after) {
                    Process.killProcess(Process.myPid());
                    throw new AssertionError("Fixture process termination returned.");
                }
                return committed;
            }
        };
        PresentationTransaction.apply(interrupted, plan);
        throw new AssertionError("Fixture interruption did not occur.");
    }
    private static void recover(Context context, Bundle result) throws IOException {
        SharedPreferences control = context.getSharedPreferences("fixture_control", Context.MODE_PRIVATE);
        int priorPid = control.getInt("pid", -1);
        if (priorPid < 0 || priorPid == Process.myPid()) throw new AssertionError("A new fixture process is required.");
        byte[] bytes;
        try (InputStream input = new AtomicFile(new File(context.getNoBackupFilesDir(), "fixture-expected.snapshot")).openRead()) {
            ByteArrayOutputStream data = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int size;
            while ((size = input.read(buffer)) != -1) {
                if ((long) data.size() + size > PresentationSnapshot.MAX_BYTES) throw new IOException("Fixture data too large.");
                data.write(buffer, 0, size);
            }
            bytes = data.toByteArray();
        }
        PresentationSnapshot expected = PresentationSnapshot.decode(bytes);
        AndroidPresentationStore store = new AndroidPresentationStore(context);
        PresentationSnapshot journal = store.readRecovery();
        if (!expected.sameValues(journal)) throw new AssertionError("Unexpected fixture journal.");
        PresentationTransaction.recover(store, journal);
        if (!expected.sameValues(store.read())) throw new AssertionError("Exact typed recovery failed.");
        result.putInt("previous_pid", priorPid); result.putInt("current_pid", Process.myPid());
        result.putInt("interrupted_after", control.getInt("after", -1)); result.putBoolean("restored", true);
    }
}
