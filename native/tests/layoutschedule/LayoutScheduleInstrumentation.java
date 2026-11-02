package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;

import java.io.File;
import java.util.*;

/** Native catalog proof in an isolated cache directory. Other preference reads are forbidden. */
public final class LayoutScheduleInstrumentation extends Instrumentation {
    private int checks;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int code = 0;
        final File root = new File(getTargetContext().getCacheDir(), "layout-schedule-proof");
        remove(root);
        Context isolated =
                new ContextWrapper(getTargetContext()) {
                    @Override
                    public File getFilesDir() {
                        root.mkdirs();
                        return root;
                    }

                    @Override
                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        throw new AssertionError(
                                "Layout schedule must not open provider, timer, household or"
                                    + " base-layout preferences.");
                    }
                };
        try {
            LayoutScheduleStore store = new LayoutScheduleStore(isolated);
            check(
                    store.resolve(1).status == LayoutSchedule.Status.BASE_LAYOUT,
                    "New policy must leave the existing layout alone.");
            LayoutOptions first =
                    new LayoutOptions(
                            400,
                            220,
                            Arrays.asList("channel:missing.source", "widget:clock"),
                            new LinkedHashSet<>(Arrays.asList("widget:clock")),
                            LayoutOptions.Mode.CARDS_ONLY);
            String one = store.save("Native morning", null, first),
                    two = store.save("Native evening", null, LayoutOptions.defaults(320, 180));
            check(
                    store.read().mode == LayoutSchedule.Mode.OFF,
                    "Saving profiles must not enable rotation.");
            store.rotation(Arrays.asList(one, two), 60, 1000);
            store.mode(LayoutSchedule.Mode.ROTATION);
            check(
                    store.resolve(1000).profileId.equals(one)
                            && store.resolve(61000).profileId.equals(two),
                    "Native stored rotation must preserve exact sequence and boundary.");
            store.selectManual(one);
            LayoutSchedule.Decision manual = new LayoutScheduleStore(isolated).resolve(99999999);
            check(
                    manual.reason == LayoutSchedule.Reason.MANUAL && manual.profileId.equals(one),
                    "Manual override must persist across store recreation and time changes.");
            check(
                    manual.profile.layout.backupValues().equals(first.backupValues()),
                    "Layout-only snapshot must preserve dimensions, mode, hidden and unavailable"
                        + " slot identity.");
            store.resumeAutomatic();
            check(
                    store.resolve(61000).profileId.equals(two),
                    "Only explicit Resume may restore automatic selection.");
            store.daily(
                    "UTC",
                    Arrays.asList(
                            new LayoutSchedule.Slot(0, one), new LayoutSchedule.Slot(720, two)));
            store.mode(LayoutSchedule.Mode.DAILY);
            check(
                    store.resolve(12 * 60 * 60 * 1000L).profileId.equals(two),
                    "Daily time zone and local rule must persist.");
            store.selectManual(one);
            store.delete(one);
            check(
                    store.resolve(12 * 60 * 60 * 1000L).status
                            == LayoutSchedule.Status.MISSING_PROFILE,
                    "Deleted selected profile must report missing instead of resetting provider"
                        + " state.");
            store.rename(two, "Native renamed");
            check(
                    new LayoutScheduleStore(isolated)
                            .read()
                            .profile(two)
                            .name
                            .equals("Native renamed"),
                    "Rename must persist atomically.");
            result.putString("result", "PASS: " + checks + " isolated native layout policy checks");
            code = -1;
        } catch (Throwable failure) {
            result.putString("result", "FAIL: " + failure.toString());
        } finally {
            remove(root);
        }
        finish(code, result);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }
}
