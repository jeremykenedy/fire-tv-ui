package com.jeremykenedy.firetv.ui.nativeapp.widget.status;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

/** Reads OS status and exercises refresh cancellation without changing any device setting. */
public final class StatusInstrumentation extends Instrumentation {
    private final StatusWidgetView[] views = new StatusWidgetView[4];
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicInteger reads = new AtomicInteger();
        try {
            final Context context = getTargetContext();
            final NetworkStatusWidgetProvider network = new NetworkStatusWidgetProvider();
            final TvAudioStatusWidgetProvider audio = new TvAudioStatusWidgetProvider();
            check(network.canRender(WidgetProvider.State.EMPTY) && network.canRender(WidgetProvider.State.UNAVAILABLE)
                    && audio.canRender(WidgetProvider.State.EMPTY) && audio.canRender(WidgetProvider.State.UNAVAILABLE),
                    "Status views remain refreshable while offline or temporarily unavailable");
            check(!network.defaultVisible() && !audio.defaultVisible(), "New widgets must be opt-in");
            check(network.item().key().equals("widget:network-status") && audio.item().key().equals("widget:tv-audio-status"), "Stable status identities");
            StatusReport observed = TvAudioStatus.read(context);
            check(observed.details.contains(TvAudioStatus.LIMITS), "Actual audio snapshot states its observation limits");
            StatusReport connection = NetworkStatus.read(context);
            check(connection.details.length() > 0 && connection.availability != null, "Network result is explicit even when offline or denied");
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    views[0] = (StatusWidgetView) network.createView(context);
                    views[1] = (StatusWidgetView) audio.createView(context);
                    network.onStart(views[0]); network.onStart(views[0]);
                    audio.onStart(views[1]); audio.onStart(views[1]);
                }
            });
            waitForReport(views[0]); waitForReport(views[1]);
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    check(!text(views[1]).contains(TvAudioStatus.LIMITS), "Home uses concise scope labels; details retain limitations");
                    network.onStop(views[0]); network.onStop(views[0]);
                    audio.onStop(views[1]); audio.onStop(views[1]);
                    check(!views[0].isRefreshing() && !views[1].isRefreshing(), "Repeated stop ends refresh");
                    views[2] = new StatusWidgetView(context, "Cancellation fixture", new StatusWidgetView.Reader() {
                        @Override public StatusReport read(Context ignored) {
                            reads.incrementAndGet();
                            entered.countDown();
                            boolean done = false;
                            while (!done) try { release.await(); done = true; } catch (InterruptedException cancelled) { }
                            return new StatusReport(StatusReport.Availability.READY, "late-result-must-not-render");
                        }
                    });
                    views[2].start();
                }
            });
            check(entered.await(3, TimeUnit.SECONDS), "Off-main snapshot started");
            runOnMainSync(new Runnable() { @Override public void run() { views[2].start(); views[2].start(); views[2].stop(); } });
            release.countDown();
            SystemClock.sleep(150);
            waitForIdleSync();
            runOnMainSync(new Runnable() {
                @Override public void run() {
                    check(reads.get() == 1, "Repeated start must keep a single in-flight OS read");
                    check(!text(views[2]).contains("late-result-must-not-render"), "Stopped view rejects late uncancellable OS read");
                }
            });
            final AtomicInteger refreshes = new AtomicInteger();
            runOnMainSync(new Runnable() { @Override public void run() {
                views[3] = new StatusWidgetView(context, "Recovery fixture", new StatusWidgetView.Reader() {
                    @Override public StatusReport read(Context ignored) {
                        return refreshes.incrementAndGet() == 1
                                ? new StatusReport(StatusReport.Availability.EMPTY, "No visible network")
                                : new StatusReport(StatusReport.Availability.READY, "Connection recovered");
                    }
                });
                views[3].start();
            }});
            awaitText(views[3], "No visible network", 3000);
            awaitText(views[3], "Connection recovered", 18000);
            result.putString("result", "PASS: observed reports, explicit limitations, opt-in identities, repeated lifecycle and late-result rejection");
            result.putString("preservation", "Read-only: no settings or preferences were modified");
        } catch (Throwable error) { result.putString("failure", error.toString()); }
        finally {
            release.countDown();
            runOnMainSync(new Runnable() {
                @Override public void run() { for (StatusWidgetView view : views) if (view != null) view.stop(); }
            });
        }
        finish(result.containsKey("failure") ? 1 : 0, result);
    }
    private void awaitText(final StatusWidgetView view, final String expected, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] found = new boolean[1];
            runOnMainSync(new Runnable() { @Override public void run() { found[0] = text(view).contains(expected); }});
            if (found[0]) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError("Status did not refresh to " + expected);
    }
    private void waitForReport(final StatusWidgetView view) {
        final boolean[] ready = new boolean[1];
        for (int attempt = 0; attempt < 60 && !ready[0]; attempt++) {
            runOnMainSync(new Runnable() {
                @Override public void run() { ready[0] = !text(view).contains("Reading a fresh snapshot"); }
            });
            if (!ready[0]) SystemClock.sleep(50);
        }
        check(ready[0], "A bounded status read must render a result");
    }
    private static String text(View view) {
        StringBuilder out = new StringBuilder();
        if (view instanceof TextView) out.append(((TextView) view).getText());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) out.append(text(group.getChildAt(i))).append('\n');
        }
        return out.toString();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
