package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.TextClock;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView;
import java.util.Locale;
import java.util.UUID;

/** Exercises clock format choices with a private disposable preference file. */
public final class ClockFormatInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        final String file = "clock-proof-" + UUID.randomUUID();
        final Context target = getTargetContext();
        final SharedPreferences prefs = target.getSharedPreferences(file, Context.MODE_PRIVATE);
        final Context isolated = new ContextWrapper(target) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return "launcher_prefs".equals(name) ? prefs : super.getSharedPreferences(name, mode);
            }
        };
        try {
            checkFormat(isolated, null, null);
            check(prefs.edit().putBoolean("use_24h_clock", true).commit(), "Could not set fixture");
            String hour24 = DateFormat.getBestDateTimePattern(Locale.getDefault(), "Hm");
            checkFormat(isolated, hour24, hour24);
            check(prefs.edit().putBoolean("use_24h_clock", false).commit(), "Could not set fixture");
            String hour12 = DateFormat.getBestDateTimePattern(Locale.getDefault(), "hm");
            checkFormat(isolated, hour12, hour12);
            check(prefs.edit().putString("use_24h_clock", "invalid").commit(), "Could not set malformed fixture");
            checkFormat(isolated, null, null);
            check("invalid".equals(prefs.getString("use_24h_clock", null)), "Malformed value must remain intact");
            result.putString("status", "PASS");
        } catch (Throwable failure) {
            result.putString("status", "FAIL"); result.putString("reason", failure.toString());
        } finally {
            result.putBoolean("fixture_removed", target.deleteSharedPreferences(file));
        }
        finish(0, result);
    }
    private void checkFormat(final Context context, final String format12, final String format24) {
        final Throwable[] failure = {null};
        runOnMainSync(new Runnable() { @Override public void run() {
            try {
                TextClock clock = (TextClock) new ClockWidgetView(context).getChildAt(0);
                check(equal(format12, clock.getFormat12Hour()) && equal(format24, clock.getFormat24Hour()),
                        "Clock format did not reflect saved choice");
            } catch (Throwable error) { failure[0] = error; }
        }});
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private static boolean equal(String expected, CharSequence actual) {
        return expected == null ? actual == null : expected.contentEquals(actual == null ? "" : actual);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
