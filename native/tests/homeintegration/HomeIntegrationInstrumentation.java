package com.jeremykenedy.firetv.ui.nativeapp.homeintegration;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeTilePreferences;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptions;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputEntry;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputRepository;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Use only with disposable preview fixtures: Home discovery can write preferences. */
public final class HomeIntegrationInstrumentation extends Instrumentation {
    private Activity activity;
    private Bundle arguments;
    private final List<String> rendered = new ArrayList<>();
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args == null ? new Bundle() : new Bundle(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String caseId = arguments.getString("state_guard_case_id");
            String manifest = arguments.getString("state_guard_manifest_sha256");
            check(caseId != null && manifest != null, "A durable pre-case guard is required");
            GuardStore.Result guard = new GuardStore(getTargetContext().getDataDir()).verify(caseId,
                    new java.util.LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), manifest);
            check(guard.matches, "Private state must match the durable guard before fixture writes");
            Context context = getTargetContext();
            check(context.getSharedPreferences(FolderPreferences.STORE, 0).getAll().isEmpty()
                    && context.getSharedPreferences(InputPreferences.FILE, 0).getAll().isEmpty()
                    && context.getSharedPreferences("native_app_grid", 0).getAll().isEmpty(),
                    "Requires a fresh disposable preview without existing Home settings");
            List<String> apps = AppInventory.keys(AppInventory.load(context));
            check(apps.size() >= 2, "Two available launcher apps are required");
            String member = apps.get(0), visibleApp = apps.get(1);
            FolderOptions folders = FolderOptions.empty().create("mixed-proof", "Fixture folder").addApp("mixed-proof", member);
            FolderPreferences.save(context, folders);
            String inputId = "fixture.missing/.InputService/HDMI1";
            InputPreferences inputPrefs = new InputPreferences(context);
            inputPrefs.remember(inputId, "Fixture HDMI"); inputPrefs.rename(inputId, "Retained HDMI");
            InputEntry retained = null;
            for (InputEntry item : InputRepository.load(context).visibleInputs) if (inputId.equals(item.id)) retained = item;
            check(retained != null && retained.state == InputEntry.State.MISSING && !retained.canOpen(),
                    "Retained missing input must remain visible and unavailable");
            String inputKey = retained.artworkKey(), folderKey = "folder:mixed-proof";
            check(context.getSharedPreferences("native_app_grid", 0).edit().putString(HomeTilePreferences.KEY,
                    folderKey + "\n" + inputKey + "\n" + visibleApp).commit(), "Save fixture Home order");
            for (String id : Arrays.asList("daily-astronomy", "daily-art", "word-of-day", "daily-trivia",
                    "daily-inspiration", "daily-horoscope", "daily-comic"))
                check(HomeSlots.widget("widget:" + id) != null, "Missing daily provider: " + id);
            activity = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            List<String> expected = HomeTilePreferences.load(getTargetContext()).visible(
                    AppGridPreferences.load(getTargetContext()), AppInventory.keys(AppInventory.load(getTargetContext())),
                    FolderPreferences.load(getTargetContext()), InputRepository.load(getTargetContext()).visibleInputs);
            boolean app = false, folder = false, input = false;
            for (String key : expected) {
                if (key.startsWith("folder:")) folder = true;
                else if (key.startsWith("input:")) input = true;
                else app = true;
            }
            check(app && folder && input, "Fixture needs a visible app, folder and input");
            boolean matched = false;
            for (int i = 0; i < 50; i++) {
                runOnMainSync(new Runnable() { @Override public void run() {
                    rendered.clear(); collect(activity.getWindow().getDecorView());
                }});
                if (rendered.equals(expected)) { matched = true; break; }
                SystemClock.sleep(100);
            }
            check(matched, "Rendered Home order differs from saved available tile order");
            check(rendered.size() >= 3 && folderKey.equals(rendered.get(0)) && inputKey.equals(rendered.get(1))
                    && visibleApp.equals(rendered.get(2)), "Mixed folder, missing input and app order must be rendered");
            check(!rendered.contains(member), "Folder member must not also appear as a Home app");
            final boolean[] placeholder = {false};
            runOnMainSync(new Runnable() { @Override public void run() {
                View tile = activity.getWindow().getDecorView().findViewWithTag(inputKey);
                placeholder[0] = tile != null && tile.getContentDescription() != null
                        && tile.getContentDescription().toString().contains("Retained HDMI")
                        && tile.getContentDescription().toString().contains("no longer available");
            }});
            check(placeholder[0], "Missing input tile must disclose its retained name and unavailable state");
            result.putBoolean("mixed_order_and_folder_member_exclusion", true);
            result.putBoolean("missing_input_placeholder", true);
            result.putString("limits", "Retained missing input only; connected HDMI switching is untested.");
            result.putString("status", "PASS");
            result.putInt("home_tile_count", rendered.size());
            result.putBoolean("daily_providers_registered", true);
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", failure.toString());
        } finally {
            if (activity != null) runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); } });
        }
        finish(0, result);
    }
    private void collect(View view) {
        if (view instanceof GridLayout) {
            ViewGroup grid = (ViewGroup) view;
            for (int i = 0; i < grid.getChildCount(); i++) {
                Object tag = grid.getChildAt(i).getTag();
                if (tag instanceof String) rendered.add((String) tag);
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i));
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
