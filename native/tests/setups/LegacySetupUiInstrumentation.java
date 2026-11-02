package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

/** Disposable preview only. The operator must restore and resolve the durable guard afterward. */
public final class LegacySetupUiInstrumentation extends Instrumentation {
    private static final String ACTIVITY = "com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupsActivity";
    private Bundle args;
    private Activity screen;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); args = new Bundle(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            Context context = getTargetContext();
            check("com.jeremykenedy.firetv.ui.preview".equals(context.getPackageName()), "Preview package required");
            String id = args.getString("state_guard_case_id"), hash = args.getString("state_guard_manifest_sha256");
            check(id != null && hash != null, "Durable guard required");
            File active = new File(context.getDataDir(), "no_backup/native-test-state-guard-v1/active");
            check(new File(active, "manifest.bin").isFile() && !new File(active, "restore.pending").exists(), "Guard not ready");
            GuardStore.Result guard = new GuardStore(context.getDataDir()).verify(id,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash);
            check(guard.matches, "Guard does not match");
            SharedPreferences prefs = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE);
            check(prefs.edit().putBoolean("use_24h_clock", false).putBoolean("featured_artwork_fit", true)
                    .putBoolean("minimal_status_bar", false).putBoolean("tinted_status_bar", true)
                    .putString("status_bar_position", "Right").putBoolean("hide_status_bar_unless_focused", true)
                    .putString("setup_proof_unrelated", "preserve").commit(), "Could not prepare preferences");
            JSONObject valid = settings(new String[]{"use_24h_clock", "featured_artwork_fit", "minimal_status_bar",
                    "status_bar_position", "hide_status_bar_unless_focused"}, new JSONObject()
                    .put("use_24h_clock", true).put("minimal_status_bar", true)
                    .put("status_bar_position", "Left").put("hide_status_bar_unless_focused", false));
            JSONObject unsupported = settings(new String[]{"poster_mode"}, new JSONObject().put("poster_mode", true));
            writeIndex(context, valid, unsupported);
            open(context);
            click("Legacy proof"); click("Apply");
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (SystemClock.uptimeMillis() < deadline && !Boolean.TRUE.equals(prefs.getAll().get("use_24h_clock"))) SystemClock.sleep(50);
            Map<String, ?> values = prefs.getAll();
            check(Boolean.TRUE.equals(values.get("use_24h_clock")) && !values.containsKey("featured_artwork_fit")
                    && Boolean.TRUE.equals(values.get("minimal_status_bar")) && "Left".equals(values.get("status_bar_position"))
                    && Boolean.FALSE.equals(values.get("hide_status_bar_unless_focused"))
                    && Boolean.TRUE.equals(values.get("tinted_status_bar"))
                    && "preserve".equals(values.get("setup_proof_unrelated")), "Legacy setup values differ");
            verifyConsumers(context);
            result.putBoolean("valid_setup_applied", true);
            click("Unsupported proof"); click("Apply");
            waitFor("Setup unavailable");
            check(values.equals(prefs.getAll()), "Unsupported setup changed preferences");
            click("OK"); close();
            JSONObject malformed = settings(new String[]{"use_24h_clock"}, new JSONObject().put("use_24h_clock", "true"));
            writeIndex(context, malformed, unsupported);
            open(context); waitFor("Setup unavailable");
            check(values.equals(prefs.getAll()), "Malformed setup changed preferences");
            result.putBoolean("unsupported_and_malformed_preserved", true);
            code = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("failure", failure.toString());
        } finally {
            try { close(); } catch (Throwable ignored) { result.putString("close", "FAILED"); code = Activity.RESULT_CANCELED; }
            result.putString("restoration", "Guard retained. Stop target, restore, verify and resolve before reuse.");
        }
        finish(code, result);
    }
    private void verifyConsumers(final Context context) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> statusType = loader.loadClass("com.jeremykenedy.firetv.ui.nativeapp.statusbar.StatusBarOptions");
        Object status = statusType.getMethod("load", Context.class).invoke(null, context);
        check(statusType.getField("configured").getBoolean(status) && statusType.getField("minimal").getBoolean(status)
                && "Left".equals(statusType.getField("position").get(status))
                && !statusType.getField("hideUnlessFocused").getBoolean(status)
                && statusType.getField("tinted").getBoolean(status), "Status consumer differs");
        Class<?> cards = loader.loadClass("com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences");
        check(Boolean.TRUE.equals(cards.getMethod("artworkFits", Context.class).invoke(null, context)),
                "Artwork reset did not reach its default consumer");
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() { @Override public void run() {
            try {
                Class<?> type = context.getClassLoader().loadClass("com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView");
                android.view.ViewGroup clock = (android.view.ViewGroup) type.getConstructor(Context.class).newInstance(context);
                android.widget.TextClock time = (android.widget.TextClock) clock.getChildAt(0);
                String expected = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "Hm");
                check(expected.contentEquals(time.getFormat12Hour()) && expected.contentEquals(time.getFormat24Hour()),
                        "Clock consumer did not use the saved 24-hour format");
            } catch (Throwable error) { failure[0] = error; }
        }});
        if (failure[0] != null) throw new IllegalStateException("Clock consumer failed", failure[0]);
    }
    private static JSONObject settings(String[] keys, JSONObject values) throws Exception {
        return new JSONObject().put("keys", new JSONArray(Arrays.asList(keys))).put("values", values);
    }
    private static JSONObject item(String id, String name, JSONObject settings) throws Exception {
        return new JSONObject().put("id", id).put("name", name).put("savedAt", 1).put("settings", settings);
    }
    private static void writeIndex(Context context, JSONObject first, JSONObject second) throws Exception {
        File directory = new File(context.getFilesDir(), "saved-setups");
        check(directory.isDirectory() || directory.mkdirs(), "Fixture directory unavailable");
        File backup = new File(directory, "setups.json.bak");
        check(!backup.exists() || backup.delete(), "Fixture backup unavailable");
        JSONObject index = new JSONObject().put("version", 1).put("setups", new JSONArray()
                .put(item("11111111-1111-1111-1111-111111111111", "Legacy proof", first))
                .put(item("22222222-2222-2222-2222-222222222222", "Unsupported proof", second)));
        try (FileOutputStream output = new FileOutputStream(new File(directory, "setups.json"))) {
            output.write(index.toString().getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
        }
    }
    private void open(Context context) {
        ActivityMonitor monitor = addMonitor(ACTIVITY, null, false);
        context.startActivity(new Intent().setClassName(context, ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        screen = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
        check(screen != null, "Saved setups did not open");
    }
    private void close() {
        if (screen == null) return;
        final Activity closing = screen;
        runOnMainSync(new Runnable() { @Override public void run() { closing.finish(); } });
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!closing.isDestroyed() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        check(closing.isDestroyed(), "Saved setup activity did not close"); screen = null;
    }
    private AccessibilityNodeInfo waitFor(String text) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text))
                if (node.getText() != null && text.equalsIgnoreCase(node.getText().toString())) return node;
            SystemClock.sleep(50);
        }
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        StringBuilder detail = new StringBuilder();
        describe(root, detail, 0);
        throw new AssertionError("UI text missing: " + text + "; window=" + detail);
    }
    private static void describe(AccessibilityNodeInfo node, StringBuilder output, int depth) {
        if (node == null || depth > 12 || output.length() > 5000) return;
        if (node.getText() != null) output.append('[').append(node.getClassName()).append(':').append(node.getText()).append(']');
        for (int index = 0; index < node.getChildCount(); index++) describe(node.getChild(index), output, depth + 1);
    }
    private void click(String text) {
        AccessibilityNodeInfo node = waitFor(text);
        while (node != null && !node.isClickable()) node = node.getParent();
        check(node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Unable to select " + text);
        waitForIdleSync();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
