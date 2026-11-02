package com.jeremykenedy.firetv.ui.nativeapp.widget.historyproof;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.stateguard.GuardStore;
import com.jeremykenedy.firetv.ui.nativeapp.widget.history.HistoryWidgetProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;

/** Guarded preview-only Home proof. The cached sentence is synthetic test content. */
public final class HistoryHomeInstrumentation extends Instrumentation {
    private static final String EVENT =
            "2001 – Synthetic fixture event: this deliberately long test description fills the"
                    + " available summary and continues with additional words to show that the full"
                    + " source text remains accessible in the detail dialog after the Home card has"
                    + " shortened its visible display.";
    private Bundle arguments;
    private volatile Intent observed;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args;
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        Activity home = null;
        ActivityMonitor monitor = null;
        int code = Activity.RESULT_CANCELED;
        StringBuilder focusTrace = new StringBuilder();
        try {
            Context context = getTargetContext();
            check(
                    "com.jeremykenedy.firetv.ui.preview".equals(context.getPackageName()),
                    "Preview target required");
            String id = arguments.getString("state_guard_case_id");
            String hash = arguments.getString("state_guard_manifest_sha256");
            check(
                    id != null
                            && hash != null
                            && new GuardStore(context.getDataDir())
                                    .verify(
                                            id,
                                            new LinkedHashSet<String>(
                                                    Arrays.asList(
                                                            "shared_prefs", "files", "no_backup")),
                                            hash)
                                    .matches,
                    "Verified durable guard required");
            final String historyKey = new HistoryWidgetProvider().item().key();
            check(
                    HomeSlots.widget(historyKey) instanceof HistoryWidgetProvider,
                    "History registered in Home slots");
            LocalDate date = LocalDate.now(ZoneId.systemDefault());
            String title = date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.US));
            String source = "https://en.wikipedia.org/wiki/" + title.replace(' ', '_');
            seedCache(context, date, source);
            LayoutPreferences.save(
                    context,
                    new LayoutOptions(
                            320,
                            180,
                            Arrays.asList(historyKey, HomeSlots.CLOCK.key()),
                            Collections.<String>emptyList(),
                            LayoutOptions.Mode.WIDGETS_ONLY));
            home =
                    startActivitySync(
                            new Intent()
                                    .setClassName(
                                            context,
                                            "com.jeremykenedy.firetv.ui.nativeapp.MainActivity")
                                    .addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final Activity screen = home;
            final View[] history = new View[1];
            final View[] clock = new View[1];
            for (int i = 0; i < 80 && (history[0] == null || clock[0] == null); i++) {
                onMain(
                        new Runnable() {
                            public void run() {
                                history[0] =
                                        screen.getWindow()
                                                .getDecorView()
                                                .findViewWithTag("slot:" + historyKey);
                                clock[0] =
                                        screen.getWindow()
                                                .getDecorView()
                                                .findViewWithTag("slot:" + HomeSlots.CLOCK.key());
                            }
                        });
                if (history[0] == null || clock[0] == null) SystemClock.sleep(100);
            }
            check(
                    history[0] != null && clock[0] != null,
                    "History and neighboring clock present on Home");
            final String[] visible = new String[1];
            onMain(
                    new Runnable() {
                        public void run() {
                            Rect bounds = new Rect();
                            check(
                                    history[0].isShown()
                                            && history[0].getGlobalVisibleRect(bounds)
                                            && bounds.width() > 0
                                            && bounds.height() > 0,
                                    "History slot visible and laid out");
                            visible[0] = text(history[0]);
                            check(clock[0].requestFocus(), "Neighboring clock accepts focus");
                        }
                    });
            for (int i = 0; i < 50 && !visible[0].contains("Synthetic fixture event"); i++) {
                SystemClock.sleep(100);
                onMain(
                        new Runnable() {
                            public void run() {
                                visible[0] = text(history[0]);
                            }
                        });
            }
            check(
                    visible[0].contains("Synthetic fixture event")
                            && visible[0].contains("Wikipedia contributors")
                            && visible[0].contains("CC BY-SA 4.0"),
                    "Home event and attribution present");
            final String[] geometry = new String[1];
            String previousGeometry = null;
            int stableSamples = 0;
            for (int i = 0; i < 50 && stableSamples < 3; i++) {
                onMain(
                        new Runnable() {
                            public void run() {
                                geometry[0] = geometry(history[0]);
                            }
                        });
                stableSamples = geometry[0].equals(previousGeometry) ? stableSamples + 1 : 0;
                previousGeometry = geometry[0];
                if (stableSamples < 3) SystemClock.sleep(150);
            }
            result.putString("settled_geometry", geometry[0]);
            check(stableSamples >= 3, "Home geometry settles");
            onMain(
                    new Runnable() {
                        public void run() {
                            checkSummary(history[0]);
                            checkVisibleText(history[0], "Wikipedia contributors");
                        }
                    });
            check(focused(clock[0]), "Clock has focus before D-pad navigation");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);
            waitForIdleSync();
            check(focused(history[0]), "D-pad moves focus to History");
            monitor =
                    new ActivityMonitor() {
                        @Override
                        public ActivityResult onStartActivity(Intent intent) {
                            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                                observed = new Intent(intent);
                                return new ActivityResult(Activity.RESULT_CANCELED, null);
                            }
                            return null;
                        }
                    };
            addMonitor(monitor);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
            waitForIdleSync();
            AccessibilityNodeInfo root = waitForText("Open source");
            check(
                    root != null && hasText(root, EVENT),
                    "Full original event available in detail dialog");
            check(
                    root != null
                            && hasText(root, "Wikipedia contributors")
                            && hasText(root, "CC BY-SA 4.0")
                            && hasText(root, "History and contributors"),
                    "Detail dialog exposes attribution and history");
            check(reachButton("Open source", focusTrace), "D-pad reaches Open source button");
            result.putString("source_route", verifyRoute(context, source));
            check(reachButton("Open license", focusTrace), "D-pad reaches Open license button");
            result.putString(
                    "license_route",
                    verifyRoute(context, "https://creativecommons.org/licenses/by-sa/4.0/"));
            result.putString(
                    "status", "PASS: Home layout, D-pad, dialog attribution and safe link routes");
            result.putString("source", source);
            code = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("failure", failure.toString());
        } finally {
            result.putString("dialog_focus_trace", focusTrace.toString());
            if (monitor != null) removeMonitor(monitor);
            if (home != null) {
                final Activity close = home;
                try {
                    onMain(
                            new Runnable() {
                                public void run() {
                                    close.finish();
                                }
                            });
                } catch (Throwable failure) {
                    result.putString("failure", failure.toString());
                    code = Activity.RESULT_CANCELED;
                }
            }
        }
        finish(code, result);
    }

    private String verifyRoute(Context context, String address) throws Exception {
        observed = null;
        ComponentName expected = expectedHandler(context, address);
        if (expected == null) {
            AccessibilityEvent toast =
                    getUiAutomation()
                            .executeAndWaitForEvent(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                                        }
                                    },
                                    new UiAutomation.AccessibilityEventFilter() {
                                        @Override
                                        public boolean accept(AccessibilityEvent event) {
                                            return event.getEventType()
                                                            == AccessibilityEvent
                                                                    .TYPE_NOTIFICATION_STATE_CHANGED
                                                    && event.getText()
                                                            .toString()
                                                            .contains(
                                                                    "A browser for this HTTPS link"
                                                                        + " is unavailable on this"
                                                                        + " TV.");
                                        }
                                    },
                                    5000);
            check(
                    toast != null && observed == null,
                    "No-handler message shown without opening a link");
            return "NO_HANDLER_MESSAGE";
        }
        sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        for (int i = 0; i < 30 && observed == null; i++) SystemClock.sleep(100);
        check(
                observed != null
                        && Intent.ACTION_VIEW.equals(observed.getAction())
                        && observed.getData() != null
                        && address.equals(observed.getData().toString())
                        && expected.equals(observed.getComponent())
                        && observed.hasCategory(Intent.CATEGORY_BROWSABLE),
                "Pinned HTTPS handler receives exact URL");
        return "PINNED_HANDLER_INTERCEPTED:" + expected.flattenToString();
    }

    private static ComponentName expectedHandler(Context context, String source) {
        Intent request =
                new Intent(Intent.ACTION_VIEW, Uri.parse(source))
                        .addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager packages = context.getPackageManager();
        ResolveInfo resolved = packages.resolveActivity(request, PackageManager.MATCH_DEFAULT_ONLY);
        ActivityInfo info = resolved == null ? null : resolved.activityInfo;
        if (info == null
                || !info.enabled
                || !info.exported
                || info.applicationInfo == null
                || !info.applicationInfo.enabled
                || (info.permission != null && !info.permission.isEmpty())
                || context.getPackageName().equals(info.packageName)) return null;
        for (ResolveInfo candidate :
                packages.queryIntentActivities(request, PackageManager.MATCH_DEFAULT_ONLY)) {
            if (candidate.activityInfo != null
                    && info.packageName.equals(candidate.activityInfo.packageName)
                    && info.name.equals(candidate.activityInfo.name))
                return new ComponentName(info.packageName, info.name);
        }
        return null;
    }

    private void seedCache(Context context, LocalDate date, String source) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Class<?> repository =
                Class.forName(
                        "com.jeremykenedy.firetv.ui.nativeapp.widget.history.HistoryRepository",
                        true,
                        loader);
        Class<?> event =
                Class.forName(
                        "com.jeremykenedy.firetv.ui.nativeapp.widget.history.HistoryRepository$Event",
                        true,
                        loader);
        Field shared = repository.getDeclaredField("SHARED");
        shared.setAccessible(true);
        Object instance = shared.get(null);
        Constructor<?> constructor =
                event.getDeclaredConstructor(LocalDate.class, String.class, String.class);
        constructor.setAccessible(true);
        Object value = constructor.newInstance(date, EVENT, source);
        long now = System.currentTimeMillis();
        set(repository, instance, "savedAt", now);
        set(repository, instance, "freshUntil", now + 600000L);
        set(repository, instance, "cached", value);
    }

    private static void set(Class<?> type, Object target, String name, Object value)
            throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private boolean reachButton(String label, StringBuilder trace) {
        for (int i = 0; i < 12; i++) {
            AccessibilityNodeInfo root = activeDialog();
            recordFocus(trace, "down-check", root);
            if (root == null) {
                SystemClock.sleep(120);
                continue;
            }
            if (focusedText(root, label)) return true;
            AccessibilityNodeInfo focused =
                    root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null
                    && focused.getClassName() != null
                    && focused.getClassName().toString().endsWith("Button")) break;
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            waitForIdleSync();
            SystemClock.sleep(120);
        }
        for (int i = 0; i < 16; i++) {
            AccessibilityNodeInfo root = activeDialog();
            recordFocus(trace, "horizontal-check", root);
            if (root == null) {
                SystemClock.sleep(120);
                continue;
            }
            if (focusedText(root, label)) return true;
            sendKeyDownUpSync(i < 8 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
            waitForIdleSync();
            SystemClock.sleep(120);
        }
        AccessibilityNodeInfo root = activeDialog();
        recordFocus(trace, "final-check", root);
        return focusedText(root, label);
    }

    private AccessibilityNodeInfo activeDialog() {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        return hasText(root, "Open source") && hasText(root, "Open license") ? root : null;
    }

    private static void recordFocus(StringBuilder trace, String phase, AccessibilityNodeInfo root) {
        if (trace.length() > 6000) return;
        AccessibilityNodeInfo focused =
                root == null ? null : root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        trace.append(phase).append(": ");
        if (focused == null) trace.append("none");
        else
            trace.append(focused.getClassName())
                    .append(" text=")
                    .append(focused.getText())
                    .append(" visible=")
                    .append(focused.isVisibleToUser())
                    .append(" window=")
                    .append(focused.getWindowId());
        trace.append('\n');
    }

    private boolean focused(final View view) throws Throwable {
        final boolean[] value = new boolean[1];
        onMain(
                new Runnable() {
                    public void run() {
                        value[0] = view.isFocused() && view.hasWindowFocus();
                    }
                });
        return value[0];
    }

    private void onMain(final Runnable work) throws Throwable {
        final Throwable[] error = new Throwable[1];
        runOnMainSync(
                new Runnable() {
                    public void run() {
                        try {
                            work.run();
                        } catch (Throwable caught) {
                            error[0] = caught;
                        }
                    }
                });
        if (error[0] != null) throw error[0];
    }

    private AccessibilityNodeInfo waitForText(String expected) {
        for (int i = 0; i < 50; i++) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (hasText(root, expected)) return root;
            SystemClock.sleep(100);
        }
        return null;
    }

    private static boolean hasText(AccessibilityNodeInfo root, String value) {
        return root != null && !root.findAccessibilityNodeInfosByText(value).isEmpty();
    }

    private static boolean focusedText(AccessibilityNodeInfo root, String value) {
        if (root == null) return false;
        for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(value))
            if (node.isFocused()
                    && node.isVisibleToUser()
                    && node.getWindowId() == root.getWindowId()) return true;
        return false;
    }

    private static String text(View view) {
        StringBuilder out = new StringBuilder();
        if (view instanceof TextView) out.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                out.append(text(((ViewGroup) view).getChildAt(i)));
        return out.toString();
    }

    private static TextView findText(View view, String expected) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(expected))
            return (TextView) view;
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                TextView found = findText(((ViewGroup) view).getChildAt(i), expected);
                if (found != null) return found;
            }
        return null;
    }

    private static String bounds(View view) {
        if (view == null) return "missing";
        Rect rect = new Rect();
        boolean visible = view.getGlobalVisibleRect(rect);
        return view.getClass().getSimpleName()
                + " size="
                + view.getWidth()
                + "x"
                + view.getHeight()
                + " visible="
                + visible
                + " rect="
                + rect.toShortString();
    }

    private static String geometry(View parent) {
        return "slot["
                + bounds(parent)
                + "] event["
                + bounds(findText(parent, "Synthetic fixture event"))
                + "] credit["
                + bounds(findText(parent, "Wikipedia contributors"))
                + "]";
    }

    private static void checkSummary(View parent) {
        TextView text = findText(parent, "Synthetic fixture event");
        Rect rect = new Rect();
        check(
                text != null
                        && text.isShown()
                        && text.getGlobalVisibleRect(rect)
                        && rect.height() > 0
                        && text.getLayout() != null
                        && text.getLineCount() <= 2,
                "Bounded visible event summary");
        boolean ellipsized = false;
        for (int line = 0; line < text.getLayout().getLineCount(); line++)
            ellipsized |= text.getLayout().getEllipsisCount(line) > 0;
        check(ellipsized, "Long event summary is ellipsized");
    }

    private static void checkVisibleText(View parent, String expected) {
        TextView text = findText(parent, expected);
        Rect rect = new Rect();
        check(
                text != null
                        && text.isShown()
                        && text.getGlobalVisibleRect(rect)
                        && rect.width() >= text.getWidth() - 1
                        && rect.height() >= text.getHeight() - 1
                        && text.getLayout() != null,
                "Fully visible text layout: " + expected);
        for (int line = 0; line < text.getLayout().getLineCount(); line++)
            check(
                    text.getLayout().getEllipsisCount(line) == 0,
                    "Credit not ellipsized: " + expected);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
