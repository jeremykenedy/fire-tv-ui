package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Requires an explicitly granted emulator listener lease and fixture POST_NOTIFICATIONS permission. */
public final class NotificationInstrumentation extends Instrumentation {
    private static final String PRODUCER = "com.jeremykenedy.firetv.ui.providerfixture";
    private Activity foreground;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Context context = getTargetContext();
        SharedPreferences preferences = context.getSharedPreferences("native_notification_recommendations", Context.MODE_PRIVATE);
        boolean present = preferences.contains("enabled"), before = preferences.getBoolean("enabled", false);
        try {
            NotificationRecommendations.setEnabled(context, false);
            check(NotificationRecommendations.read(context, null).status.state == TvRecommendations.SourceStatus.State.DISABLED,
                    "Opted-out notifications must remain disabled even when Android access exists.");
            check(NotificationRecommendations.accessGranted(context), "Grant emulator listener access explicitly before this suite.");
            foregroundSources();
            NotificationRecommendations.setEnabled(context, true);
            awaitListener();
            fixture("notification-publish", null);
            TvRecommendations.Program program = awaitProgram(true);
            check(program != null && program.notificationKey != null && program.seriesKey == null,
                    "Real notification cards retain source identity without inventing series.");
            long deadline;
            check(RecommendationActions.inspect(context, program).title, "Source-owned notification action must be available.");
            check(RecommendationActions.launchTitle(foreground, program) == RecommendationActions.Outcome.OPENED,
                    "Current source-owned PendingIntent must be accepted.");
            deadline = SystemClock.uptimeMillis() + 5000;
            boolean received = false;
            while (SystemClock.uptimeMillis() < deadline) {
                if ("fixture://notification/1".equals(fixture("status", null).getString("opened"))) { received = true; break; }
                SystemClock.sleep(50);
            }
            check(received, "Producer must receive its own title handoff.");
            PendingIntent foreign = PendingIntent.getActivity(context, 43,
                    new Intent().setClassName(context, "com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationSourcesActivity"),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            fixture("notification-foreign", foreign);
            awaitProgram(false);
            check(!RecommendationActions.inspect(context, program).title,
                    "Replacing a notification with another app's action invalidates the old handoff.");
            fixture("notification-clear", null);
            check(RecommendationActions.launchTitle(foreground, program) == RecommendationActions.Outcome.UNAVAILABLE,
                    "Withdrawn notifications cannot launch stale actions.");
            result.putString("status", "PASS");
            result.putBoolean("cross_app_notification_read", true);
            result.putBoolean("creator_package_and_uid_validation", true);
            result.putBoolean("source_owned_handoff_receipt", true);
            result.putBoolean("disabled_and_withdrawn_states", true);
        } catch (Throwable failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        } finally {
            try { fixture("notification-clear", null); result.putBoolean("fixture_notification_removed", true); }
            catch (Throwable failure) { result.putString("cleanup_error", failure.toString()); result.putString("status", "FAIL"); }
            if (foreground != null) runOnMainSync(new Runnable() {
                @Override public void run() { foreground.finish(); }
            });
            SharedPreferences.Editor restore = preferences.edit();
            if (present) restore.putBoolean("enabled", before); else restore.remove("enabled");
            result.putBoolean("notification_choice_restored", restore.commit());
        }
        finish(0, result);
    }
    private void foregroundSources() {
        foreground = startActivitySync(new Intent().setClassName(getTargetContext(),
                "com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationSourcesActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!foreground.hasWindowFocus() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        check(foreground.hasWindowFocus(), "Notification opt-in must run from its foreground source screen.");
    }
    private void awaitListener() {
        long deadline = SystemClock.uptimeMillis() + 15000;
        NotificationRecommendations.Result last = null;
        while (SystemClock.uptimeMillis() < deadline) {
            last = NotificationRecommendations.read(getTargetContext(), null);
            if (last.status.state == TvRecommendations.SourceStatus.State.READY
                    || last.status.state == TvRecommendations.SourceStatus.State.EMPTY) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Notification listener did not become ready after foreground opt-in; state="
                + (last == null ? "none" : last.status.state) + "; message=" + (last == null ? "none" : last.status.message));
    }
    private TvRecommendations.Program awaitProgram(boolean expected) throws Exception {
        NotificationRecommendations.Result last = null;
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            TvRecommendations.Program found = null;
            last = NotificationRecommendations.read(getTargetContext(), null);
            for (TvRecommendations.Feed feed : last.feeds)
                if (PRODUCER.equals(feed.source) && !feed.programs.isEmpty()) found = feed.programs.get(0);
            if ((found != null) == expected) return found;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Notification snapshot did not reach expected fixture state; expected_present=" + expected
                + "; state=" + (last == null ? "none" : last.status.state)
                + "; message=" + (last == null ? "none" : last.status.message)
                + "; access=" + NotificationRecommendations.accessGranted(getTargetContext())
                + "; tv_app_visible=" + (getTargetContext().getPackageManager().getLeanbackLaunchIntentForPackage(PRODUCER) != null)
                + "; fixture=" + fixture("notification-diagnostics", null));
    }
    private Bundle fixture(String operation, PendingIntent foreign) throws Exception {
        final CountDownLatch done = new CountDownLatch(1);
        final Bundle[] received = new Bundle[1];
        Intent request = new Intent().setComponent(new ComponentName(PRODUCER, PRODUCER + ".FixtureReceiver"))
                .putExtra("operation", operation);
        if (foreign != null) request.putExtra("foreign_action", foreign);
        getTargetContext().sendOrderedBroadcast(request, null, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { received[0] = getResultExtras(false); done.countDown(); }
        }, null, 0, null, null);
        check(done.await(10, TimeUnit.SECONDS) && received[0] != null && "OK".equals(received[0].getString("status")),
                "Notification fixture failed: " + received[0]);
        return received[0];
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
