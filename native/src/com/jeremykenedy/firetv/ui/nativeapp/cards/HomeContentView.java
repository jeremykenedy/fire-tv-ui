package com.jeremykenedy.firetv.ui.nativeapp.cards;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.HomeSlots;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroups;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsStore;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsView;
import com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetGroupsEditor;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Reconciles changing payloads inside stable mixed Home slots. All calls use the UI thread. */
public final class HomeContentView extends HorizontalScrollView {
    public interface Listener {
        void onSlotFocused(String key);
        void onUnavailable(LayoutItem item);
        default void onArtworkChanged(ChannelCardView card, android.graphics.Bitmap artwork) {}
        default void onGroupedWidgetsVisibilityChanged(boolean visible) {}
    }

    private final Activity activity;
    private final ChannelCardView.Listener cardListener;
    private final Listener listener;
    private final LinearLayout row;
    private final Map<String, Slot> slots = new LinkedHashMap<>();
    private final Map<String, String> selections = new LinkedHashMap<>();
    private final Map<String, String> titles = new LinkedHashMap<>();
    private final List<TvRecommendations.Feed> feeds = new ArrayList<>();
    private TvRecommendations.Status status = TvRecommendations.Status.EMPTY;
    private boolean resumed;
    private boolean widgetReadability;
    private boolean groupedWidgetsVisible;
    private boolean checking = true;
    private LayoutOptions dashboardLayout;
    private boolean preserveDashboardSlots;
    private long generation;
    private String focusedKey;
    private String restoreFocus;
    private String restoreChild;
    private int restoreScroll;
    private boolean restorePending;
    private String widgetColors;
    private final Runnable widgetRefresh = new Runnable() {
        @Override public void run() { if (resumed) refreshPreferences(); }
    };

    public HomeContentView(Activity activity, ChannelCardView.Listener cardListener, Listener listener) {
        super(activity);
        this.activity = activity;
        this.cardListener = cardListener;
        this.listener = listener;
        setTag("home:content");
        setClipChildren(false);
        setClipToPadding(false);
        setFillViewport(false);
        setFocusable(false);
        row = new LinearLayout(activity);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
    }

    public void setWidgetReadability(boolean enabled) {
        if (widgetReadability == enabled) return;
        widgetReadability = enabled;
        for (Slot slot : slots.values()) slot.updateWidgetBacking();
    }

    public long beginRefresh() {
        checking = true;
        return ++generation;
    }

    /** Returns false for a superseded or paused request without touching existing slots. */
    public boolean applyResult(long request, TvRecommendations.Result result) {
        if (request != generation || !resumed || result == null) return false;
        feeds.clear();
        feeds.addAll(result.feeds);
        status = result.status;
        checking = false;
        refreshPreferences();
        return true;
    }

    /** A null layout restores the ordinary Home layout without changing saved preferences. */
    public void setDashboardLayout(LayoutOptions layout) {
        dashboardLayout = layout;
        if (layout != null) preserveDashboardSlots = true;
        if (resumed) refreshPreferences();
    }

    public void refreshPreferences() {
        LayoutOptions previous = LayoutPreferences.load(activity, HomeSlots.defaults());
        boolean hasGroups = activity.getSharedPreferences("native_layout", android.content.Context.MODE_PRIVATE)
                .contains("widget_groups");
        LayoutOptions layout = HomeSlots.discover(previous, feeds);
        if (hasGroups) layout = layout.discover(Collections.singletonList(WidgetGroupsView.ITEM));
        if (dashboardLayout == null && (!previous.order.equals(layout.order) || !previous.hidden.equals(layout.hidden)))
            LayoutPreferences.saveDiscovery(activity, previous, layout);
        if (dashboardLayout != null) {
            layout = HomeSlots.discover(dashboardLayout, feeds);
            if (hasGroups) layout = layout.discover(Collections.singletonList(WidgetGroupsView.ITEM));
        }
        CardOptions options = CardPreferences.load(activity);
        Map<String, TvRecommendations.Feed> byKey = new LinkedHashMap<>();
        for (TvRecommendations.Feed feed : feeds) byKey.put(feed.item.key(), feed);
        List<TvRecommendations.Feed> knownFeeds = new ArrayList<>(feeds);
        for (Slot slot : slots.values()) if (slot.card != null) knownFeeds.add(slot.card.feed());
        java.util.Set<String> packageHidden = CardPreferences.hiddenFeedKeys(activity, knownFeeds);
        List<LayoutItem> ordered = new ArrayList<>();
        for (String key : layout.order) {
            LayoutItem item = item(key);
            if (layout.includes(item.kind) && !layout.hidden.contains(key) && !packageHidden.contains(key)) ordered.add(item);
        }
        LinkedHashSet<String> retained = new LinkedHashSet<>();
        Slot previouslyFocused = focusedSlot();
        View previousView = findFocus();
        Object previousTag = previousView == null ? null : previousView.getTag();
        for (int index = 0; index < ordered.size(); index++) {
            LayoutItem item = ordered.get(index);
            String key = item.key();
            retained.add(key);
            Slot slot = slots.get(key);
            if (slot == null) {
                slot = new Slot(item);
                slots.put(key, slot);
            }
            slot.update(byKey.get(key), options);
            int width = slot.groups != null ? Math.max(212, slot.groupColumns * 228 - 16)
                    : layout.widthDp(item, slot.provider == null ? 320 : slot.provider.widthDp());
            int height = layout.heightDp(item, slot.groups != null ? 270 : slot.provider == null ? 160 : slot.provider.heightDp());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(width), dp(height));
            if (slot.getParent() == null) row.addView(slot, Math.min(index, row.getChildCount()), params);
            else if (row.indexOfChild(slot) != index) {
                if (slot.groups != null) slot.stop();
                row.removeView(slot);
                row.addView(slot, Math.min(index, row.getChildCount()), params);
            } else slot.setLayoutParams(params);
            slot.start();
        }
        for (Slot slot : slots.values()) {
            if (!retained.contains(slot.item.key())) {
                slot.rememberSelection();
                slot.stop();
                if (slot.getParent() == row) row.removeView(slot);
            }
        }
        // Dashboard snapshots can temporarily omit slots that another snapshot will restore.
        if (!preserveDashboardSlots) {
            for (String key : new ArrayList<>(slots.keySet())) {
                if (!layout.order.contains(key)) { slots.remove(key); selections.remove(key); }
            }
            selections.keySet().retainAll(layout.order);
            titles.keySet().retainAll(layout.order);
        }
        if (previouslyFocused != null && previouslyFocused.getParent() == row) {
            View target = previousTag instanceof String ? previouslyFocused.findViewWithTag(previousTag) : null;
            if (target != null && target.isFocusable()) target.requestFocus(); else previouslyFocused.requestFocus();
        }
        else if (previouslyFocused != null && row.getChildCount() > 0) row.getChildAt(0).requestFocus();
        Slot grouped = slots.get(WidgetGroupsView.ITEM.key());
        boolean visible = grouped != null && grouped.groups != null && grouped.getParent() == row;
        if (groupedWidgetsVisible != visible) {
            groupedWidgetsVisible = visible;
            setPadding(0, visible ? dp(20) : 0, 0, 0);
            FrameLayout.LayoutParams rowBounds = (FrameLayout.LayoutParams) row.getLayoutParams();
            rowBounds.gravity = visible ? Gravity.CENTER_VERTICAL : Gravity.TOP;
            row.setLayoutParams(rowBounds);
            if (listener != null) listener.onGroupedWidgetsVisibilityChanged(visible);
        }
        requestLayout();
    }

    public ChannelCardView findCard(String key) {
        Slot slot = slots.get(key);
        return slot == null ? null : slot.card;
    }

    public boolean showFocusedCardActions() {
        Slot slot = focusedSlot();
        if (slot == null || !slot.ready || slot.card == null || cardListener == null) return false;
        cardListener.onLongPress(slot.card.feed(), slot.card.currentProgram());
        return true;
    }

    public List<ChannelCardView> cards() {
        List<ChannelCardView> result = new ArrayList<>();
        for (Slot slot : slots.values()) if (slot.card != null) result.add(slot.card);
        return Collections.unmodifiableList(result);
    }

    public void onHostResume() {
        resumed = true;
        java.util.Map<String, ?> preferences = activity.getSharedPreferences("launcher_prefs", android.content.Context.MODE_PRIVATE).getAll();
        String colors = String.valueOf(preferences.get("color_widget_bg_color_argb")) + ":"
                + preferences.get("color_widget_bg_color_opacity") + ":"
                + preferences.get("color_widget_text_color_argb") + ":" + preferences.get("color_widget_text_color_opacity");
        if (!colors.equals(widgetColors)) {
            for (Slot slot : slots.values()) {
                if (slot.groups != null) slot.groupsRaw = null;
                if (slot.widget instanceof com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView
                        || slot.widget instanceof com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView) {
                    slot.stopContent(); slot.removeView(slot.widget); slot.widget = null;
                }
            }
            widgetColors = colors;
        }
        refreshPreferences();
    }

    public void onHostPause() {
        resumed = false;
        generation++;
        removeCallbacks(widgetRefresh);
        for (Slot slot : slots.values()) slot.stop();
    }

    public void saveState(Bundle state) {
        Bundle saved = new Bundle();
        for (Slot slot : slots.values()) slot.rememberSelection();
        Bundle programs = new Bundle();
        for (Map.Entry<String, String> entry : selections.entrySet()) programs.putString(entry.getKey(), entry.getValue());
        saved.putBundle("programs", programs);
        Bundle labels = new Bundle();
        for (Slot slot : slots.values()) labels.putString(slot.item.key(), slot.title);
        saved.putBundle("titles", labels);
        saved.putString("focus", hasFocus() ? focusedKey : null);
        View focused = findFocus();
        if (hasFocus() && focused != null && focused.getTag() instanceof String)
            saved.putString("focus_child", (String) focused.getTag());
        saved.putInt("scroll", getScrollX());
        state.putBundle("home_content", saved);
    }

    public void restoreState(Bundle state) {
        Bundle saved = state == null ? null : state.getBundle("home_content");
        if (saved == null) return;
        restoreFocus = saved.getString("focus");
        restoreChild = saved.getString("focus_child");
        restoreScroll = Math.max(0, saved.getInt("scroll"));
        restorePending = true;
        Bundle labels = saved.getBundle("titles");
        if (labels != null && labels.size() <= LayoutOptions.MAX_ITEMS) {
            for (String key : labels.keySet()) {
                String title = labels.getString(key);
                if (title != null && title.length() <= 512) titles.put(key, title);
            }
        }
        Bundle programs = saved.getBundle("programs");
        if (programs != null && programs.size() <= LayoutOptions.MAX_ITEMS) {
            for (String key : programs.keySet()) selections.put(key, programs.getString(key));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (restorePending && row.getChildCount() > 0) {
            Slot slot = slots.get(restoreFocus);
            if (slot != null && slot.getParent() == row) {
                View child = restoreChild == null ? null : slot.findViewWithTag(restoreChild);
                if (child != null && child.isFocusable()) child.requestFocus(); else slot.requestFocus();
            }
            scrollTo(restoreScroll, 0);
            restorePending = false;
            restoreFocus = null;
            restoreChild = null;
        }
        syncVisibleSlots();
    }

    @Override protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);
        if (row != null) syncVisibleSlots();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && resumed) refreshPreferences();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncVisibleSlots();
    }

    private void syncVisibleSlots() {
        for (Slot slot : slots.values()) {
            if (slot.nearViewport()) slot.start();
            else slot.stop();
        }
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(widgetRefresh);
        for (Slot slot : slots.values()) slot.stop();
        super.onDetachedFromWindow();
    }

    @Override public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        Slot slot = focusedSlot();
        if (slot != null && slot.item.key().equals(WidgetGroupsView.ITEM.key())) {
            focusedKey = slot.item.key();
            if (listener != null) listener.onSlotFocused(focusedKey);
        }
    }

    private Slot focusedSlot() {
        View focused = findFocus();
        while (focused != null && focused != this) {
            if (focused instanceof Slot) return (Slot) focused;
            android.view.ViewParent parent = focused.getParent();
            focused = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static LayoutItem item(String key) {
        return new LayoutItem(key.substring(key.indexOf(':') + 1),
                key.startsWith("channel:") ? LayoutItem.Kind.CHANNEL_CARD : LayoutItem.Kind.WIDGET);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class Slot extends FrameLayout {
        final LayoutItem item;
        final WidgetProvider provider;
        final FrameLayout placeholder;
        final FocusTileView placeholderFocus;
        final TextView placeholderTitle;
        final TextView placeholderMessage;
        ChannelCardView card;
        View widget;
        boolean ready;
        WidgetGroupsView groups;
        String groupsRaw;
        int groupColumns;
        boolean started;
        boolean observing;
        AutoCloseable observation;
        String title;

        Slot(final LayoutItem item) {
            super(activity);
            this.item = item;
            provider = HomeSlots.widget(item.key());
            updateWidgetBacking();
            title = titles.containsKey(item.key()) ? titles.get(item.key()) : HomeSlots.title(item.key());
            setTag("slot:" + item.key());
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
            setDescendantFocusability(item.key().equals(WidgetGroupsView.ITEM.key())
                    ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
            placeholder = new FrameLayout(activity);
            placeholderFocus = new FocusTileView(activity);
            placeholderFocus.setFocusable(false);
            placeholderFocus.setClickable(false);
            placeholderFocus.setText("");
            placeholder.addView(placeholderFocus, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER);
            labels.setPadding(dp(16), dp(8), dp(16), dp(8));
            placeholderTitle = new TextView(activity);
            placeholderTitle.setTextColor(0xffffffff);
            placeholderTitle.setTextSize(20);
            placeholderTitle.setMaxLines(2);
            labels.addView(placeholderTitle);
            placeholderMessage = new TextView(activity);
            placeholderMessage.setTextColor(0xffcec7df);
            placeholderMessage.setTextSize(16);
            placeholderMessage.setMaxLines(3);
            labels.addView(placeholderMessage);
            placeholder.addView(labels, new FrameLayout.LayoutParams(-1, -1));
            placeholder.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) { select(false); }
            });
            setOnLongClickListener(new OnLongClickListener() {
                @Override public boolean onLongClick(View view) { select(true); return true; }
            });
        }

        void updateWidgetBacking() {
            if (provider != null && widgetReadability
                    && !item.key().equals("widget:clock") && !item.key().equals("widget:weather")) {
                android.graphics.drawable.GradientDrawable surface = new android.graphics.drawable.GradientDrawable();
                surface.setColor(0xd9101523);
                surface.setCornerRadius(dp(14));
                setBackground(surface);
            } else setBackground(null);
        }

        void update(TvRecommendations.Feed feed, CardOptions options) {
            if (item.key().equals(WidgetGroupsView.ITEM.key())) {
                updateGroups();
            } else if (item.kind == LayoutItem.Kind.CHANNEL_CARD && feed != null) {
                title = feed.title;
                List<TvRecommendations.Program> visible = CardPreferences.visiblePrograms(options, feed);
                if (card == null) {
                    card = new ChannelCardView(activity, feed, cardListener);
                    card.setArtworkListener(new ChannelCardView.ArtworkListener() {
                        @Override public void onArtworkChanged(ChannelCardView card, android.graphics.Bitmap artwork) {
                            if (listener != null) listener.onArtworkChanged(card, artwork);
                        }
                    });
                    card.setFocusable(false);
                    card.setClickable(false);
                    card.setLongClickable(false);
                    card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    addView(card, new FrameLayout.LayoutParams(-1, -1));
                    card.updateFeed(feed, visible);
                    card.restoreProgramKey(selections.get(item.key()));
                } else card.updateFeed(feed, visible);
                card.setRotationIntervalMillis(options.timerSeconds(feed.item.id) * 1000L);
                ready = true;
            } else if (provider != null) {
                title = provider.title();
                ready = provider.canRender(provider.state(activity));
                if (ready && widget == null) {
                    widget = provider.createView(activity);
                    if (widget instanceof com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView
                            || widget instanceof com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView)
                        com.jeremykenedy.firetv.ui.nativeapp.widgetgroups.WidgetAppearance.apply(widget);
                    widget.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    addView(widget, new FrameLayout.LayoutParams(-1, -1));
                }
            } else ready = false;
            if (!ready) stopContent();
            if (card != null) card.setVisibility(ready ? VISIBLE : GONE);
            if (widget != null) widget.setVisibility(ready ? VISIBLE : GONE);
            if (groups != null) groups.setVisibility(ready ? VISIBLE : GONE);
            if (widget != null && ready && started) provider.onStart(widget);
            placeholder.setVisibility(ready ? GONE : VISIBLE);
            placeholderTitle.setText(title);
            String message = item.key().equals(WidgetGroupsView.ITEM.key())
                    ? (groupsRaw != null && groupColumns == 0 ? "No widget columns. Select to add one."
                            : "Saved widget groups are unavailable. Open Home layout to review.")
                    : provider == null ? channelMessage() : provider.message(activity);
            placeholderMessage.setText(message);
            setContentDescription(ready && card != null ? card.getContentDescription()
                    : title + ": " + (ready ? "Select to configure" : message));
            focusVisual();
        }

        private void updateGroups() {
            title = "Grouped widgets";
            try {
                WidgetGroupsStore store = new WidgetGroupsStore(activity);
                String raw = store.raw();
                WidgetGroups data = store.load();
                if (data == null) { ready = false; return; }
                groupColumns = data.groups.size();
                if (groupColumns == 0) { groupsRaw = raw; ready = false; return; }
                if (groups == null || !raw.equals(groupsRaw)) {
                    View focus = findFocus();
                    final Object tag = focus == null ? null : focus.getTag();
                    if (groups != null) { groups.stop(); removeView(groups); }
                    groups = new WidgetGroupsView(activity, data,
                            new WidgetGroupsEditor(activity, new Runnable() {
                                @Override public void run() { refreshPreferences(); }
                            }), 212, 270);
                    groupsRaw = raw;
                    addView(groups, new FrameLayout.LayoutParams(-1, -1));
                    if (tag instanceof String) {
                        View target = groups.findViewWithTag(tag);
                        if (target != null) target.requestFocus();
                    }
                    if (started) groups.start();
                }
                ready = true;
            } catch (IllegalStateException unavailable) { groupsRaw = null; ready = false; }
        }

        private String channelMessage() {
            if (item.kind == LayoutItem.Kind.WIDGET) return "Widget unavailable. Select to manage Home layout.";
            if (checking) return "Checking shared items…";
            if (status == TvRecommendations.Status.PERMISSION_REQUIRED) return "TV listings access required. Select for card settings.";
            if (status == TvRecommendations.Status.EMPTY) return "No items shared by this channel. Select for card settings.";
            return "Channel unavailable. Select for card settings.";
        }

        void rememberSelection() {
            if (card != null && card.selectedProgramKey() != null) selections.put(item.key(), card.selectedProgramKey());
        }

        boolean nearViewport() {
            return getParent() == row && getWidth() > 0 && getRight() > HomeContentView.this.getScrollX() - HomeContentView.this.getWidth()
                    && getLeft() < HomeContentView.this.getScrollX() + 2 * HomeContentView.this.getWidth();
        }

        void start() {
            if (!resumed || !isAttachedToWindow() || !nearViewport()) return;
            if (groups != null && ready) groups.start();
            if (provider != null && !observing) {
                observing = true;
                observation = provider.observe(activity, new Runnable() {
                    @Override public void run() {
                        if (!observing || !resumed) return;
                        removeCallbacks(widgetRefresh);
                        post(widgetRefresh);
                    }
                });
            }
            if (!ready || started) return;
            if (card != null) card.onHostResume();
            if (widget != null) provider.onStart(widget);
            started = true;
        }

        void stop() {
            observing = false;
            if (observation != null) {
                try { observation.close(); }
                catch (Exception unavailable) { /* A source failure must not interrupt other slots' cleanup. */ }
                observation = null;
            }
            stopContent();
        }

        private void stopContent() {
            rememberSelection();
            if (groups != null) groups.stop();
            if (card != null && started) card.onHostPause();
            if (widget != null && started) provider.onStop(widget);
            started = false;
        }

        private void select(boolean longPress) {
            if (ready && card != null && cardListener != null) {
                if (longPress) cardListener.onLongPress(card.feed(), card.currentProgram());
                else cardListener.onSelect(card.feed(), card.currentProgram());
            } else if (provider != null) {
                if (longPress) provider.configure(activity);
                else provider.activate(activity, widget);
            }
            else if (listener != null) listener.onUnavailable(item);
        }

        @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
            super.onFocusChanged(focused, direction, previous);
            focusVisual();
            if (focused) {
                focusedKey = item.key();
                if (listener != null) listener.onSlotFocused(item.key());
            }
        }

        private void focusVisual() {
            if (placeholderFocus != null) placeholderFocus.setPreviewFocused(isFocused());
            if (card != null) card.setHostFocused(isFocused());
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            boolean compact = MeasureSpec.getSize(heightSpec) < dp(112);
            placeholderMessage.setVisibility(compact ? GONE : VISIBLE);
            placeholderTitle.setTextSize(compact ? 17 : 20);
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
