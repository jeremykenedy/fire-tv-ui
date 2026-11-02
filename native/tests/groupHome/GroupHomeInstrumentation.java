package com.jeremykenedy.firetv.ui.nativeapp.grouphome;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import com.jeremykenedy.firetv.ui.nativeapp.cards.HomeContentView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;

/** Run only on a disposable preview protected by an operator's durable StateGuard. */
public final class GroupHomeInstrumentation extends Instrumentation {
    private static final String A = "11111111-1111-4111-8111-111111111111";
    private static final String B = "22222222-2222-4222-8222-222222222222";
    private static final String C = "33333333-3333-4333-8333-333333333333";
    private static final String KEY = "widget:grouped-widgets";
    private Bundle args;
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String id = args.getString("state_guard_case_id"), hash = args.getString("state_guard_manifest_sha256");
            check(id != null && hash != null, "Durable StateGuard required");
            check(new GuardStore(getTargetContext().getDataDir()).verify(id,
                    new LinkedHashSet<String>(Arrays.asList("shared_prefs", "files", "no_backup")), hash).matches,
                    "StateGuard must match before writes");
            final Context context = getTargetContext();
            final String raw = "[[{\"id\":\"" + A + "\",\"type\":\"YearProgress\",\"size\":\"Big\",\"data\":{}}],[{\"id\":\"" + B + "\",\"type\":\"DigitalClock\",\"size\":\"Small\",\"data\":{}}],[],[{\"id\":\"" + C + "\",\"type\":\"FutureWidget\",\"size\":\"Big\",\"data\":{\"opaque\":\"keep\"},\"future\":42}]]";
            check(context.getSharedPreferences("native_layout", 0).edit().putString("widget_groups", raw).commit(), "Save groups");
            LayoutPreferences.save(context, new LayoutOptions(320, 180, Arrays.asList("channel:fixture-before", KEY, "channel:fixture-after"), Collections.<String>emptyList()));
            activity = startActivitySync(new Intent().setClassName(context, "com.jeremykenedy.firetv.ui.nativeapp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            getUiAutomation().injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_DOWN), true);
            getUiAutomation().injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN), true);
            final boolean[] loaded = {false};
            for (int attempt = 0; attempt < 50 && !loaded[0]; attempt++) {
                runOnMainSync(new Runnable() { @Override public void run() {
                    HomeContentView view = findHome(activity.getWindow().getDecorView());
                    loaded[0] = view != null && view.findViewWithTag(A) != null;
                }});
                if (!loaded[0]) android.os.SystemClock.sleep(100);
            }
            check(loaded[0], "Grouped Home content did not load");
            final boolean[] measured = {false};
            for (int attempt = 0; attempt < 50 && !measured[0]; attempt++) {
                runOnMainSync(new Runnable() { @Override public void run() {
                    HomeContentView view = findHome(activity.getWindow().getDecorView());
                    View tile = view == null ? null : view.findViewWithTag(B);
                    View clock = tile == null ? null : childOfType(tile, "ClockWidgetView");
                    measured[0] = clock instanceof ViewGroup && clock.isLaidOut() && clock.getHeight() > 0
                            && ((ViewGroup) clock).getChildAt(1).isLaidOut();
                }});
                if (!measured[0]) android.os.SystemClock.sleep(100);
            }
            check(measured[0], "Small clock did not complete layout");
            runOnMainSync(new Runnable() { @Override public void run() {
                HomeContentView home = findHome(activity.getWindow().getDecorView());
                check(home != null, "Home content must exist");
                home.refreshPreferences();
                View slot = home.findViewWithTag("slot:" + KEY);
                check(slot != null && home.findViewWithTag(A) != null && home.findViewWithTag(B) != null && home.findViewWithTag(C) != null, "All saved records must render");
                check(home.findViewWithTag("group:empty:2:0") != null, "Empty group must remain editable");
                float density = activity.getResources().getDisplayMetrics().density;
                check(slot.getLayoutParams().width == Math.round(1304 * density), "Four groups need four full width columns");
                check(home.findViewWithTag(B).requestFocus(), "Record must take native focus");
                home.refreshPreferences();
                check(home.findViewWithTag(B).isFocused(), "Refresh must retain record focus");
                LayoutOptions saved = LayoutPreferences.load(context, new LayoutOptions(320, 180, Collections.<String>emptyList(), Collections.<String>emptyList()));
                check(saved.order.indexOf("channel:fixture-before") < saved.order.indexOf(KEY) && saved.order.indexOf(KEY) < saved.order.indexOf("channel:fixture-after"), "Channel order and collection position must survive discovery");
            }});
            final HomeContentView[] home = new HomeContentView[1];
            final View[] year = new View[1];
            final View[] clock = new View[1];
            runOnMainSync(new Runnable() { @Override public void run() {
                home[0] = findHome(activity.getWindow().getDecorView());
                year[0] = childOfType(home[0].findViewWithTag(A), "LegacyYearProgressView");
                check(year[0] != null, "Year provider content must be inside its focus frame");
                clock[0] = childOfType(home[0].findViewWithTag(B), "ClockWidgetView");
                check(clock[0] instanceof ViewGroup, "Small clock must have its own focus frame");
                ViewGroup clockView = (ViewGroup) clock[0];
                check(clockView.getChildCount() == 2 && clockView.getChildAt(1).getVisibility() == View.VISIBLE,
                        "Small clock must retain both time and date");
                check(clockView.getChildAt(0).getBottom() <= clockView.getHeight()
                        && clockView.getChildAt(1).getBottom() <= clockView.getHeight()
                        && clockView.getChildAt(0).getHeight() >= ((android.widget.TextView) clockView.getChildAt(0)).getLineHeight()
                        && clockView.getChildAt(1).getHeight() >= ((android.widget.TextView) clockView.getChildAt(1)).getLineHeight(), "Small clock labels must fit their bounds: clock=" + clockView.getWidth() + "x" + clockView.getHeight()
                                + ", time=" + clockView.getChildAt(0).getHeight() + "/" + ((android.widget.TextView) clockView.getChildAt(0)).getLineHeight()
                                + ", date=" + clockView.getChildAt(1).getHeight() + "/" + ((android.widget.TextView) clockView.getChildAt(1)).getLineHeight()
                                + ", date bottom=" + clockView.getChildAt(1).getBottom());
                check(childOfType(home[0].findViewWithTag(B), "FocusTileView") != null, "Record must use the shared focus visual");
                home[0].scrollTo(Math.round(320 * activity.getResources().getDisplayMetrics().density), 0);
            }});
            waitForIdleSync();
            check(awaitCallback(year[0], true), "Visible year provider must schedule its next refresh");
            check(awaitObserving(clock[0], true), "Visible clock must observe format changes");
            runOnMainSync(new Runnable() { @Override public void run() { home[0].onHostPause(); }});
            check(!callbackPending(year[0]), "Home pause must cancel the year refresh callback");
            check(!(Boolean) field(clock[0], "observing"), "Home pause must remove the clock format listener");
            checkReceiverStopped(year[0]);
            runOnMainSync(new Runnable() { @Override public void run() { home[0].onHostResume(); }});
            waitForIdleSync();
            check(awaitCallback(year[0], true), "Home resume must restart the visible year provider");
            check(awaitObserving(clock[0], true), "Home resume must restore the clock format listener");
            final android.widget.TextClock ticking = (android.widget.TextClock) ((ViewGroup) clock[0]).getChildAt(0);
            runOnMainSync(new Runnable() { @Override public void run() { ticking.setFormat12Hour("ss"); ticking.setFormat24Hour("ss"); }});
            String visibleTime = clockText(ticking);
            android.os.SystemClock.sleep(2200);
            check(!visibleTime.equals(clockText(ticking)), "Visible native clock must tick");
            Activity cover = startActivitySync(new Intent().setClassName(context,
                    "com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            android.os.SystemClock.sleep(1500);
            String hiddenTime = clockText(ticking);
            android.os.SystemClock.sleep(2200);
            check(hiddenTime.equals(clockText(ticking)), "Clock must stop ticking while its Home window is hidden");
            final Activity closeCover = cover;
            runOnMainSync(new Runnable() { @Override public void run() { closeCover.finish(); }});
            waitForIdleSync();
            runOnMainSync(new Runnable() { @Override public void run() { ticking.setFormat12Hour("ss"); ticking.setFormat24Hour("ss"); }});
            String resumedTime = clockText(ticking);
            android.os.SystemClock.sleep(2200);
            check(!resumedTime.equals(clockText(ticking)), "Clock must resume ticking when Home returns");
            openRecord(B);
            choose("Replace with year progress");
            JSONArray changed = new JSONArray(context.getSharedPreferences("native_layout", 0).getString("widget_groups", ""));
            final String replacement = changed.getJSONArray(1).getJSONObject(0).getString("id");
            check(!replacement.equals(B) && A.equals(changed.getJSONArray(0).getJSONObject(0).getString("id")), "Replacement changes only the selected identity");
            openRecord("group:empty:1:1");
            choose("Add small clock");
            changed = new JSONArray(context.getSharedPreferences("native_layout", 0).getString("widget_groups", ""));
            final String added = changed.getJSONArray(1).getJSONObject(1).getString("id");
            check("Small".equals(changed.getJSONArray(1).getJSONObject(1).getString("size")), "Second widget must be small");
            openRecord(added);
            choose("Move column earlier");
            changed = new JSONArray(context.getSharedPreferences("native_layout", 0).getString("widget_groups", ""));
            check(replacement.equals(changed.getJSONArray(0).getJSONObject(0).getString("id"))
                    && added.equals(changed.getJSONArray(0).getJSONObject(1).getString("id"))
                    && A.equals(changed.getJSONArray(1).getJSONObject(0).getString("id"))
                    && C.equals(changed.getJSONArray(3).getJSONObject(0).getString("id")), "Moving retains record identities and unknown data");
            check("FutureWidget".equals(changed.getJSONArray(3).getJSONObject(0).getString("type"))
                    && "keep".equals(changed.getJSONArray(3).getJSONObject(0).getJSONObject("data").getString("opaque"))
                    && changed.getJSONArray(3).getJSONObject(0).getInt("future") == 42, "Unknown type and data must survive edits");
            final String edited = context.getSharedPreferences("native_layout", 0).getString("widget_groups", null);
            openRecord(added);
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            waitForIdleSync();
            check(edited.equals(context.getSharedPreferences("native_layout", 0).getString("widget_groups", null)),
                    "Reopening and backing out must not change groups");
            runOnMainSync(new Runnable() { @Override public void run() {
                LayoutOptions saved = LayoutPreferences.load(context, new LayoutOptions(320, 180, Collections.<String>emptyList(), Collections.<String>emptyList()));
                LayoutPreferences.save(context, saved.withMode(LayoutOptions.Mode.CARDS_ONLY));
                home[0].refreshPreferences();
                check(home[0].findViewWithTag("slot:" + KEY) == null, "Cards only must hide grouped widgets");
                check(edited.equals(context.getSharedPreferences("native_layout", 0).getString("widget_groups", null)), "Disabled groups retain exact data");
                LayoutPreferences.save(context, saved.withMode(LayoutOptions.Mode.BOTH));
                home[0].refreshPreferences();
                check(home[0].findViewWithTag(added) != null && home[0].findViewWithTag(C) != null, "Reenabled groups retain edited and unknown records");
                check(edited.equals(context.getSharedPreferences("native_layout", 0).getString("widget_groups", null)), "Reenabling preserves exact data");
            }});
            result.putString("status", "PASS");
            result.putString("limits", "Synthetic saved records; upgrade and physical TV need separate proof.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finally { if (activity != null) runOnMainSync(new Runnable() { @Override public void run() { activity.finish(); }}); }
        finish(0, result);
    }
    private String clockText(final android.widget.TextClock clock) {
        final String[] value = new String[1];
        runOnMainSync(new Runnable() { @Override public void run() { value[0] = clock.getText().toString(); }});
        return value[0];
    }
    private void openRecord(final String tag) {
        runOnMainSync(new Runnable() { @Override public void run() {
            HomeContentView home = findHome(activity.getWindow().getDecorView());
            View target = home.findViewWithTag(tag);
            check(target != null && target.performClick(), "Open editor for " + tag);
        }});
        waitForIdleSync();
    }
    private void choose(String label) {
        for (int attempt = 0; attempt < 50; attempt++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                java.util.List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(label);
                for (AccessibilityNodeInfo node : matches) if (label.contentEquals(node.getText())) {
                    if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { waitForIdleSync(); return; }
                }
            }
            android.os.SystemClock.sleep(100);
        }
        throw new AssertionError("Dialog action unavailable: " + label);
    }
    private static Object field(Object object, String name) throws Exception {
        java.lang.reflect.Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static boolean callbackPending(View year) throws Exception {
        return ((Handler) field(year, "handler")).hasCallbacks((Runnable) field(year, "nextDay"));
    }
    private static boolean awaitCallback(View year, boolean expected) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (callbackPending(year) == expected) return true;
            android.os.SystemClock.sleep(100);
        }
        return false;
    }
    private static boolean awaitObserving(View clock, boolean expected) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            if ((Boolean) field(clock, "observing") == expected) return true;
            android.os.SystemClock.sleep(100);
        }
        return false;
    }
    private static void checkReceiverStopped(View year) throws Exception {
        android.content.BroadcastReceiver receiver = (android.content.BroadcastReceiver) field(year, "changes");
        try { year.getContext().unregisterReceiver(receiver); }
        catch (IllegalArgumentException expected) { return; }
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED); filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) year.getContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else year.getContext().registerReceiver(receiver, filter);
        throw new AssertionError("Home pause left the year date receiver registered");
    }
    private static View childOfType(View view, String type) {
        if (view.getClass().getSimpleName().equals(type)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = childOfType(((ViewGroup) view).getChildAt(i), type);
            if (found != null) return found;
        }
        return null;
    }
    private static HomeContentView findHome(View view) {
        if (view instanceof HomeContentView) return (HomeContentView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            HomeContentView result = findHome(((ViewGroup) view).getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
