package com.jeremykenedy.firetv.ui.nativeapp.widget.yearprogress;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Overlay-only test. It does not save settings or change the system clock. */
public final class YearProgressInstrumentation extends Instrumentation {
    private Activity activity;
    private YearProgressView view;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            activity = startActivitySync(new Intent().setClassName(getTargetContext(),
                    "com.jeremykenedy.firetv.ui.nativeapp.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(new Runnable() { @Override public void run() {
                YearProgressWidgetProvider provider = new YearProgressWidgetProvider();
                check(!provider.defaultVisible(), "Widget must start hidden");
                view = (YearProgressView) provider.createView(activity);
                activity.addContentView(view, new ViewGroup.LayoutParams(600, 300));
                check(!view.isStarted(), "Constructor must not start refresh work");
                provider.onStart(view); provider.onStart(view);
                check(view.isStarted(), "Start must be idempotent");
                check(!text("year-progress:year").isEmpty(), "Local year must render");
                check(text("year-progress:percentage").contains("completed"), "Percentage must render");
                check(text("year-progress:remaining").contains("remaining"), "Remaining days must render");
                provider.onStop(view); provider.onStop(view);
                check(!view.isStarted(), "Stop must cancel refresh work");
                provider.onStart(view);
                ((ViewGroup) view.getParent()).removeView(view);
                check(!view.isStarted(), "Detach must stop refresh work");
            }});
            result.putString("status", "PASS");
            result.putString("limits", "Direct widget overlay only; no Home registration, physical TV, or system clock changes.");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finally {
            if (activity != null) runOnMainSync(new Runnable() { @Override public void run() {
                if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
                activity.finish();
            }});
        }
        finish(0, result);
    }
    private String text(String tag) {
        View found = view.findViewWithTag(tag);
        check(found instanceof TextView, "Missing text: " + tag);
        return ((TextView) found).getText().toString();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
