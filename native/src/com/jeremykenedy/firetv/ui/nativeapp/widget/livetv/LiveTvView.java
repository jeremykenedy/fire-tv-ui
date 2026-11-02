package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.tv.TvContract;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LiveTvView extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout rows;
    private final TextView status;
    private final ScrollView scroll;
    private ExecutorService worker;
    private CancellationSignal cancellation;
    private boolean running, loading;
    private Runnable timeout;
    private int generation;
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (running) { refresh(); handler.postDelayed(this, 30000); } }
    };
    private final ContentObserver changes = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) { if (running) { handler.removeCallbacks(reload); handler.postDelayed(reload, 200); } }
    };
    private final Runnable reload = new Runnable() { @Override public void run() { refresh(); } };
    private final BroadcastReceiver clock = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (running) refresh(); }
    };
    public LiveTvView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        TextView title = text("Live TV favorites now/next", 22); addView(title);
        status = text("Configure favorite channels in Widgets settings.", 16); status.setTag("livetv:guide-status"); addView(status);
        scroll = new ScrollView(context);
        rows = new LinearLayout(context); rows.setOrientation(VERTICAL); scroll.addView(rows);
        addView(scroll, new LayoutParams(-1, 0, 1));
    }
    public void start() {
        if (running) return;
        stop(); running = true; worker = Executors.newSingleThreadExecutor();
        try {
            getContext().getContentResolver().registerContentObserver(TvContract.Channels.CONTENT_URI, true, changes);
            getContext().getContentResolver().registerContentObserver(TvContract.Programs.CONTENT_URI, true, changes);
        } catch (RuntimeException unavailable) { }
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED); filter.addAction(Intent.ACTION_LOCALE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(clock, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(clock, filter);
        tick.run();
    }
    public void stop() {
        generation++; loading = false;
        if (timeout != null) handler.removeCallbacks(timeout);
        handler.removeCallbacks(tick); handler.removeCallbacks(reload);
        if (cancellation != null) cancellation.cancel();
        if (worker != null) { worker.shutdownNow(); worker = null; }
        if (running) {
            try { getContext().getContentResolver().unregisterContentObserver(changes); } catch (RuntimeException ignored) { }
            try { getContext().unregisterReceiver(clock); } catch (RuntimeException ignored) { }
        }
        running = false;
    }
    public boolean isRefreshing() { return running; }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    private void refresh() {
        if (!running || worker == null || loading) return;
        loading = true;
        if (cancellation != null) cancellation.cancel();
        final CancellationSignal signal = new CancellationSignal(); cancellation = signal;
        final int request = ++generation;
        timeout = new Runnable() { @Override public void run() {
            if (running && loading && request == generation) {
                signal.cancel();
                status.setText("Guide refresh timed out. Any last displayed guide may be stale.");
            }
        }};
        handler.postDelayed(timeout, 15000);
        worker.execute(new Runnable() { @Override public void run() {
            try {
                final LiveTvPreferences prefs = LiveTvPreferences.read(getContext());
                final LiveTvRepository.Channels channels = LiveTvRepository.channels(getContext(), signal);
                final List<LiveGuide.Listing> listings = new ArrayList<>();
                final List<String> missing = new ArrayList<>();
                final long now = System.currentTimeMillis();
                for (String key : prefs.favorites) {
                    LiveGuide.Channel match = null;
                    for (LiveGuide.Channel channel : channels.values) if (key.equals(channel.key())) { match = channel; break; }
                    if (match == null) missing.add(key);
                    else listings.add(LiveTvRepository.listing(getContext(), match, now, signal));
                }
                handler.post(new Runnable() { @Override public void run() {
                    if (!running || signal.isCanceled() || request != generation) return;
                    render(prefs, channels, listings, missing, now);
                }});
            } catch (final RuntimeException unavailable) {
                if (signal.isCanceled()) return;
                handler.post(new Runnable() { @Override public void run() {
                    if (running && request == generation) { rows.removeAllViews(); status.setText("Live guide unavailable. Saved favorites were kept."); }
                }});
            } finally {
                handler.post(new Runnable() { @Override public void run() {
                    if (request == generation) {
                        loading = false;
                        if (timeout != null) handler.removeCallbacks(timeout);
                    }
                }});
            }
        }});
    }
    private void render(LiveTvPreferences prefs, LiveTvRepository.Channels channels, List<LiveGuide.Listing> listings, List<String> missing, long now) {
        View focused = findFocus();
        Object focusTag = focused == null ? null : focused.getTag();
        final int previousScroll = scroll.getScrollY();
        rows.removeAllViews();
        ZoneId zone = prefs.zone.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(prefs.zone);
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        DateTimeFormatter format = DateTimeFormatter.ofPattern(android.text.format.DateFormat.is24HourFormat(getContext())
                ? "EEE HH:mm z" : "EEE h:mm a z", locale).withZone(zone);
        status.setText(channels.status == LiveTvRepository.Status.READY || channels.status == LiveTvRepository.Status.PARTIAL
                ? (prefs.favorites.isEmpty() ? "Choose favorite channels in Widgets settings." : "Guide read " + format.format(Instant.ofEpochMilli(now))
                    + " | " + zone.getId() + (channels.status == LiveTvRepository.Status.PARTIAL ? " | Partial provider data" : ""))
                : channels.message());
        for (final LiveGuide.Listing listing : listings) {
            rows.addView(text(listing.channel.name, 20));
            TextView guide = text(description(listing, format), 16); guide.setTag("livetv:listing:" + listing.channel.key()); rows.addView(guide);
            final boolean supported = LiveTvActions.available(getContext(), listing.channel);
            Button open = new Button(getContext()); open.setAllCaps(false);
            open.setText(supported ? "Open channel source link" : "Channel source link unavailable");
            open.setTag("livetv:open:" + listing.channel.key());
            open.setOnClickListener(new OnClickListener() { @Override public void onClick(View view) {
                if (!supported) { status.setText("This source has no supported publisher-owned channel link. Direct tuning is unavailable."); return; }
                RecommendationActions.Outcome result = LiveTvActions.open(getContext(), listing.channel);
                status.setText(result == RecommendationActions.Outcome.OPENED ? "Opened the publisher's channel link. Playback is not assumed."
                        : result == RecommendationActions.Outcome.PENDING ? "Waiting for the app unlock choice." : "The publisher's channel link is unavailable.");
            }});
            rows.addView(open);
        }
        for (String key : missing) rows.addView(text("Saved channel unavailable: " + key + ". Check its source app or edit favorites.", 16));
        if (focusTag != null) {
            final View target = findViewWithTag(focusTag);
            if (target != null) scroll.post(new Runnable() { @Override public void run() {
                if (running && target.isAttachedToWindow()) { target.requestFocus(); scroll.scrollTo(0, previousScroll); }
            }});
        }
    }
    static String description(LiveGuide.Listing listing, DateTimeFormatter format) {
        if (listing.state == LiveGuide.State.UNAVAILABLE) return "Guide unavailable or restricted for this channel.";
        if (listing.state == LiveGuide.State.STALE) return "Guide is stale: only ended programs are available. Refresh the EPG in the source app.";
        if (listing.state == LiveGuide.State.MISSING) return "Guide missing: no usable EPG programs are published.";
        String now = listing.now == null ? "Now: guide gap" : "Now: " + listing.now.title + " | " + format.format(Instant.ofEpochMilli(listing.now.start))
                + " to " + format.format(Instant.ofEpochMilli(listing.now.end));
        return now + "\n" + (listing.next == null ? "Next: guide unavailable" : "Next: " + listing.next.title + " | " + format.format(Instant.ofEpochMilli(listing.next.start)));
    }
    private TextView text(String value, int size) {
        TextView text = new TextView(getContext()); text.setText(value); text.setTextSize(size); text.setTextColor(0xffffffff);
        text.setPadding(0, 4, 0, 8); return text;
    }
}
