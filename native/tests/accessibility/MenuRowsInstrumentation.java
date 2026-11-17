package com.jeremykenedy.firetv.ui.nativeapp.accessibility;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Measures real shared menu rows on an exclusively owned, guarded emulator. */
public final class MenuRowsInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Activity activity;
    private LinearLayout panel;
    private Button shortRow, longRow, nextRow;
    private int clicks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            String caseId = arguments.getString("state_guard_case_id"), hash = arguments.getString("state_guard_manifest_sha256");
            check(caseId != null && hash != null && new GuardStore(context.getDataDir()).verify(caseId,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches,
                    "Completed durable StateGuard required before fixture writes.");
            final CountDownLatch ready = new CountDownLatch(1);
            runOnMainSync(new Runnable() { @Override public void run() { SourceStartup.start(context, new Runnable() {
                @Override public void run() { ready.countDown(); }
            }); }});
            check(ready.await(10, TimeUnit.SECONDS), "Preview startup must finish before opening the native host.");
            AccessibilityPreferences.save(context, 100, false);
            activity = startActivitySync(new Intent(context, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(!activity.isFinishing(), "Native host must remain visible.");
            runOnMainSync(new Runnable() { @Override public void run() {
                panel = Pages.sidePanel(activity); panel.addView(Pages.text(activity, "UI settings", 26));
                shortRow = Pages.menuButton(activity, "Apps", null); shortRow.setId(0x20e1); panel.addView(shortRow);
                longRow = Pages.menuButton(activity, "App scrolling: Vertical (Default)", new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        clicks++;
                        new android.app.AlertDialog.Builder(activity).setTitle("App scrolling")
                                .setSingleChoiceItems(new String[]{"Vertical (Default)", "Horizontal"}, 0, null)
                                .setNegativeButton("Cancel", null).show();
                    }
                }); longRow.setId(0x20e2); panel.addView(longRow);
                nextRow = Pages.menuButton(activity, "Keep favorites at bottom", null); nextRow.setId(0x20e3); panel.addView(nextRow);
                shortRow.requestFocus();
            }});
            awaitLayout();
            final JSONObject metrics = new JSONObject();
            runOnMainSync(new Runnable() { @Override public void run() {
                check(shortRow.getHeight() == Pages.dp(activity, 44), "Default short row must retain its 44dp height.");
                check(shortRow.getCurrentTextColor() == android.graphics.Color.BLACK, "Default focus retains black text on its light surface.");
                try { metrics.put("defaultShortHeightPx", shortRow.getHeight()); }
                catch (org.json.JSONException invalid) { throw new AssertionError(invalid); }
            }});
            screenshot(arguments.getString("stage", "menu-rows") + "-default");
            runOnMainSync(new Runnable() { @Override public void run() {
                AccessibilityPreferences.save(context, 120, false); AccessibilityStyle.apply(activity, panel);
                check(longRow.requestFocus(), "Long native row must accept remote focus.");
            }});
            awaitLayout();
            final boolean[] readable = {false};
            runOnMainSync(new Runnable() { @Override public void run() {
                Layout text = longRow.getLayout();
                readable[0] = readable(longRow);
                try {
                    metrics.put("systemFontScale", activity.getResources().getConfiguration().fontScale);
                    metrics.put("textPercent", AccessibilityPreferences.textPercent(context));
                    metrics.put("longRowHeightPx", longRow.getHeight()); metrics.put("textHeightPx", text.getHeight());
                    metrics.put("lineCount", text.getLineCount()); metrics.put("lastLineEnd", text.getLineEnd(text.getLineCount() - 1));
                    metrics.put("labelLength", longRow.length()); metrics.put("readable", readable[0]);
                } catch (org.json.JSONException invalid) { throw new AssertionError(invalid); }
            }});
            result.putString("measurements", metrics.toString());
            screenshot(arguments.getString("stage", "menu-rows"));
            if ("clipped".equals(arguments.getString("expect"))) check(!readable[0], "Baseline must reproduce clipped large menu text.");
            else check(readable[0], "All lines of the large native menu label must fit its row: " + metrics);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
            check(clicks == 1, "Remote Select must invoke the focused row once.");
            boolean chooser = false;
            for (int attempt = 0; attempt < 50 && !chooser; attempt++) {
                android.view.accessibility.AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                chooser = root != null && !root.findAccessibilityNodeInfosByText("App scrolling").isEmpty()
                        && !root.findAccessibilityNodeInfosByText("Horizontal").isEmpty();
                if (!chooser) android.os.SystemClock.sleep(40);
            }
            check(chooser, "Remote Select opens the native row chooser.");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            boolean returned = false;
            for (int attempt = 0; attempt < 50 && !returned; attempt++) {
                returned = activity.hasWindowFocus();
                if (!returned) android.os.SystemClock.sleep(40);
            }
            check(returned, "Native menu regains window focus after Back.");
            runOnMainSync(new Runnable() { @Override public void run() { check(longRow.hasFocus(), "Back returns focus to the invoking menu row."); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            runOnMainSync(new Runnable() { @Override public void run() { check(nextRow.hasFocus(), "Remote Down moves to the next menu row."); }});
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP); waitForIdleSync();
            runOnMainSync(new Runnable() { @Override public void run() {
                check(longRow.hasFocus() && longRow.getCurrentTextColor() == android.graphics.Color.BLACK, "Remote Up restores the existing focus treatment.");
                if (!"clipped".equals(arguments.getString("expect"))) check(readable(longRow) && readable(nextRow) && readable(shortRow), "Rows stay readable after remote focus changes.");
            }});
            screenshot(arguments.getString("stage", "menu-rows") + "-after-back");
            result.putString("status", "PASS");
        } catch (Throwable failed) { result.putString("status", "FAIL"); result.putString("reason", failed.toString()); }
        finally { if (activity != null) runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); }}); }
        finish(0, result);
    }
    private void awaitLayout() {
        for (int attempt = 0; attempt < 50; attempt++) {
            waitForIdleSync(); final boolean[] measured = {false};
            runOnMainSync(new Runnable() { @Override public void run() { measured[0] = longRow.isLaidOut() && !longRow.isLayoutRequested()
                    && longRow.getLayout() != null && longRow.getWidth() > 0 && longRow.getHeight() > 0; }});
            if (measured[0]) return;
            android.os.SystemClock.sleep(40);
        }
        throw new AssertionError("Native menu row did not finish layout.");
    }
    private static boolean readable(Button button) {
        Layout text = button.getLayout();
        if (text == null || text.getHeight() + button.getCompoundPaddingTop() + button.getCompoundPaddingBottom() > button.getHeight()) return false;
        if (text.getLineEnd(text.getLineCount() - 1) < button.length()) return false;
        for (int line = 0; line < text.getLineCount(); line++) if (text.getEllipsisCount(line) != 0) return false;
        return true;
    }
    private void screenshot(String name) throws Exception {
        check(name.matches("[a-z0-9-]{1,80}"), "Bounded proof screenshot name required.");
        getUiAutomation().waitForIdle(500, 5000); waitForIdleSync();
        Bitmap bitmap = getUiAutomation().takeScreenshot(); check(bitmap != null, "Native screenshot required.");
        File file = new File(getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output), "Save native menu screenshot."); }
        finally { bitmap.recycle(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
