package com.jeremykenedy.firetv.ui.nativeapp.widget.feeds;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Home owns visibility; each visible view owns one cancellable, bounded feed request. */
public final class FeedView extends LinearLayout {
    private final FeedClient client;
    private final TextView heading, status;
    private static final int PAGE_SIZE = 2;
    private final Button[] items = new Button[PAGE_SIZE];
    private final Button refresh, cancel, next, restore;
    private java.util.Set<String> hidden = new java.util.LinkedHashSet<>();
    private FeedSource source;
    private FeedSnapshot snapshot = FeedSnapshot.empty();
    private List<FeedParser.Item> displayed = java.util.Collections.emptyList();
    private static final ThreadPoolExecutor REQUESTS = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1));
    static { REQUESTS.allowCoreThreadTimeOut(true); }
    private Future<?> task;
    private FeedParser.Cancellation cancellation;
    private int generation, page;
    private boolean started, busy;
    private final Runnable tick = new Runnable() {
        @Override public void run() { if (started) refresh(); }
    };
    public FeedView(Context context) { this(context, new FeedClient(new FeedClient.Https())); }
    public FeedView(Context context, FeedClient client) {
        super(context); this.client = client; setOrientation(VERTICAL);
        int pad = Math.round(12 * getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad);
        heading = text("Custom feed", 21); heading.setMaxLines(2); heading.setEllipsize(android.text.TextUtils.TruncateAt.END); addView(heading);
        status = text("Choose a source in Widgets settings.", 15); status.setTag("feed:status");
        status.setMaxLines(3); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); addView(status);
        for (int i = 0; i < items.length; i++) {
            final int slot = i;
            items[i] = button("", "feed:item:" + i, new OnClickListener() {
                @Override public void onClick(View view) {
                    int index = page * PAGE_SIZE + slot;
                    if (source != null && index < visibleItems().size() && getContext() instanceof Activity) {
                        final FeedParser.Item item = visibleItems().get(index);
                        FeedLinks.confirm((Activity) getContext(), source, item, new Runnable() {
                            @Override public void run() {
                                try {
                                    new FeedStore(getContext()).hide(source, item.id);
                                    hidden = new FeedStore(getContext()).hidden(source);
                                    render(); refresh.requestFocus();
                                } catch (java.io.IOException unavailable) { status.setText(unavailable.getMessage()); }
                            }
                        });
                    }
                }
            });
            items[i].setTextSize(17); items[i].setMaxLines(2); items[i].setEllipsize(android.text.TextUtils.TruncateAt.END); addView(items[i], new LayoutParams(-1, -2));
        }
        LinearLayout actions = new LinearLayout(context); actions.setOrientation(HORIZONTAL);
        refresh = button("Refresh", "feed:refresh", new OnClickListener() { @Override public void onClick(View view) { refresh(); } });
        cancel = button("Cancel", "feed:cancel", new OnClickListener() {
            @Override public void onClick(View view) {
                if (cancellation != null) { cancellation.cancel(); status.setText("Cancelling refresh. Waiting for the current read to finish."); }
            }
        });
        next = button("More", "feed:more", new OnClickListener() {
            @Override public void onClick(View view) { page = (page + 1) % Math.max(1, (visibleItems().size() + PAGE_SIZE - 1) / PAGE_SIZE); render(); }
        });
        actions.addView(refresh, new LayoutParams(0, -2, 1)); actions.addView(cancel, new LayoutParams(0, -2, 1));
        actions.addView(next, new LayoutParams(0, -2, 1)); addView(actions);
        restore = button("Restore hidden headlines", "feed:restore", new OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    new FeedStore(getContext()).restoreHidden(source); hidden.clear(); render(); refresh.requestFocus();
                } catch (java.io.IOException unavailable) { status.setText(unavailable.getMessage()); }
            }
        });
        addView(restore, new LayoutParams(-1, -2)); render();
    }
    public void start() {
        if (started) return;
        stop(); started = true;
        try {
            FeedSource selected = new FeedStore(getContext()).read();
            if (selected == null) { source = null; snapshot = FeedSnapshot.empty(); render(); return; }
            if (!selected.equals(source)) page = 0;
            source = selected; hidden = new FeedStore(getContext()).hidden(source); snapshot = FeedMemory.read(source); render();
            long age = System.currentTimeMillis() - snapshot.fetchedAt;
            if (snapshot.fetchedAt == 0 || snapshot.failed || age < 0 || age >= FeedSnapshot.REFRESH_MS) refresh();
            else postDelayed(tick, FeedSnapshot.REFRESH_MS - age);
        } catch (Exception unavailable) { source = null; render(); status.setText("Saved source unavailable. Review Custom feed settings."); }
    }
    public void stop() {
        started = false; generation++; busy = false; removeCallbacks(tick);
        if (cancellation != null) cancellation.cancel();
        if (task != null) { task.cancel(true); REQUESTS.remove((Runnable) task); task = null; }
    }
    public boolean isRefreshing() { return started && busy; }
    public boolean isStarted() { return started; }
    private void refresh() {
        if (!started || busy || source == null) return;
        try {
            if (!source.equals(new FeedStore(getContext()).read())) { stop(); start(); return; }
        } catch (Exception unavailable) { stop(); status.setText("Saved source unavailable. Review Custom feed settings."); return; }
        removeCallbacks(tick); busy = true;
        final int request = ++generation; final FeedSource selected = source;
        final FeedParser.Cancellation token = new FeedParser.Cancellation(); cancellation = token;
        render(); status.setText("Refreshing " + source.display() + "...");
        try { task = REQUESTS.submit(new Runnable() {
            @Override public void run() {
                List<FeedParser.Item> received = null; boolean failed = false, cancelled = false;
                try { received = client.fetch(selected, token); token.check(); }
                catch (Exception unavailable) {
                    failed = true;
                    try { token.check(); } catch (InterruptedException expected) { cancelled = true; }
                }
                final List<FeedParser.Item> result = received; final boolean error = failed, stopped = cancelled;
                post(new Runnable() {
                    @Override public void run() {
                        if (!started || request != generation || !selected.equals(source)) return;
                        try {
                            if (!selected.equals(new FeedStore(getContext()).read())) { stop(); start(); return; }
                        } catch (Exception unavailable) { stop(); status.setText("Saved source unavailable. Review Custom feed settings."); return; }
                        busy = false;
                        boolean cancelFocused = cancel.hasFocus();
                        String focusedId = null;
                        for (int i = 0; i < items.length; i++) {
                            int old = page * PAGE_SIZE + i;
                            if (items[i].hasFocus() && old < visibleItems().size()) focusedId = visibleItems().get(old).id;
                        }
                        if (!stopped) {
                            snapshot = error ? snapshot.failure() : snapshot.success(result, System.currentTimeMillis());
                            FeedMemory.write(source, snapshot);
                        }
                        render();
                        int selected = -1;
                        if (focusedId != null) for (int i = 0; i < visibleItems().size(); i++) {
                            if (focusedId.equals(visibleItems().get(i).id)) { selected = i; page = i / PAGE_SIZE; break; }
                        }
                        render();
                        if (focusedId != null) {
                            if (selected >= 0) items[selected % PAGE_SIZE].requestFocus(); else refresh.requestFocus();
                        } else if (cancelFocused) refresh.requestFocus();
                        if (stopped) status.setText("Refresh cancelled. " + snapshot.message(System.currentTimeMillis()));
                        postDelayed(tick, FeedSnapshot.REFRESH_MS);
                    }
                });
            }
        }); } catch (RejectedExecutionException pending) {
            busy = false; render(); status.setText("A previous feed request is finishing. Retry shortly.");
        }
    }
    private List<FeedParser.Item> visibleItems() { return displayed; }
    private void render() {
        displayed = snapshot.visible(hidden).items;
        heading.setText(source == null ? "Custom feed" : source.display());
        status.setText(source == null ? "Choose a source in Widgets settings." : snapshot.message(System.currentTimeMillis()));
        if (source != null && !snapshot.items.isEmpty() && visibleItems().isEmpty())
            status.setText("All saved headlines are hidden. Restore hidden headlines to show them again. " + snapshot.message(System.currentTimeMillis()));
        restore.setVisibility(source != null && !hidden.isEmpty() ? View.VISIBLE : View.GONE);
        int pages = Math.max(1, (visibleItems().size() + PAGE_SIZE - 1) / PAGE_SIZE); page = Math.min(page, pages - 1);
        for (int i = 0; i < items.length; i++) {
            int index = page * PAGE_SIZE + i; boolean exists = source != null && index < visibleItems().size();
            items[i].setVisibility(exists ? View.VISIBLE : View.GONE);
            if (exists) {
                FeedParser.Item item = visibleItems().get(index); items[i].setText(item.title);
                items[i].setContentDescription(item.title + ", source " + source.display());
            }
        }
        refresh.setEnabled(started && source != null && !busy);
        cancel.setVisibility(busy ? View.VISIBLE : View.GONE);
        next.setVisibility(pages > 1 ? View.VISIBLE : View.GONE);
    }
    private TextView text(String value, int size) { TextView view = new TextView(getContext()); view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE); return view; }
    private Button button(String label, String tag, OnClickListener listener) { Button button = new Button(getContext()); button.setText(label); button.setAllCaps(false); button.setTag(tag); button.setOnClickListener(listener); return button; }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
