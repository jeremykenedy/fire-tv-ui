package com.jeremykenedy.firetv.ui.nativeapp.widgetgroups;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import android.widget.HorizontalScrollView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.widget.ClockWidgetView;
import com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherOptions;
import com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherView;
import com.jeremykenedy.firetv.ui.nativeapp.widget.weather.WeatherWidgetProvider;
import java.util.ArrayList;
import java.util.List;

/** A column per saved group, with independently owned and focusable widget instances. */
public final class WidgetGroupsView extends HorizontalScrollView {
    public static final LayoutItem ITEM = new LayoutItem("grouped-widgets", LayoutItem.Kind.WIDGET);
    public interface Editor {
        void open(int groupIndex, int entryIndex, WidgetGroups.Entry entry);
    }
    private static final class LiveWidget {
        final WidgetProvider provider;
        final View view;
        boolean active;
        LiveWidget(WidgetProvider provider, View view) { this.provider = provider; this.view = view; }
    }
    private final Activity activity;
    private final Editor editor;
    private final List<LiveWidget> live = new ArrayList<>();
    private boolean started;
    private final ViewTreeObserver.OnScrollChangedListener scrollChanged = new ViewTreeObserver.OnScrollChangedListener() {
        @Override public void onScrollChanged() { syncVisible(); }
    };
    private final ViewTreeObserver.OnGlobalLayoutListener layoutChanged = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override public void onGlobalLayout() { syncVisible(); }
    };

    public WidgetGroupsView(Activity activity, WidgetGroups groups, Editor editor, int widthDp, int heightDp) {
        super(activity);
        if (groups == null || editor == null || widthDp < 64 || widthDp > 1024 || heightDp < 48 || heightDp > 1024)
            throw new IllegalArgumentException("Invalid widget group display.");
        this.activity = activity; this.editor = editor;
        setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        int width = dp(widthDp), height = dp(heightDp), gap = dp(16);
        for (int index = 0; index < groups.groups.size(); index++) {
            List<WidgetGroups.Entry> entries = groups.groups.get(index);
            LinearLayout column = new LinearLayout(activity); column.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(width, height);
            if (index > 0) columnParams.leftMargin = gap;
            row.addView(column, columnParams);
            if (entries.isEmpty()) {
                column.addView(empty(index, 0), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
            } else {
                for (int position = 0; position < entries.size(); position++) {
                    if (position > 0) {
                        View spacer = new View(activity);
                        column.addView(spacer, new LinearLayout.LayoutParams(1, gap));
                    }
                    column.addView(entry(index, position, entries.get(position)), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
                }
                if (entries.size() == 1 && entries.get(0).size.equals("Small")) {
                    View spacer = new View(activity); column.addView(spacer, new LinearLayout.LayoutParams(1, gap));
                    column.addView(empty(index, 1), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
                }
            }
        }
    }

    private View entry(final int group, final int index, final WidgetGroups.Entry record) {
        WidgetProvider provider = WidgetGroupProviders.resolve(record);
        View content = null;
        try {
            if (record.type.equals("Weather")) {
                WeatherOptions options = WeatherGroupOptions.read(record);
                WeatherWidgetProvider weather = new WeatherWidgetProvider();
                content = weather.createView(activity, options);
                ((WeatherView) content).setGroupedSize(record.size.equals("Small")); provider = weather;
            } else if (provider != null && provider.state(activity) == WidgetProvider.State.READY) {
                content = provider.createView(activity);
            }
        } catch (RuntimeException unavailable) {
            // Keep the original record visible and editable if a provider cannot render it.
        }
        boolean unavailable = content == null;
        if (unavailable) content = label(record.type + " unavailable. Saved widget data was kept.");
        else live.add(new LiveWidget(provider, content));
        WidgetAppearance.applyText(content, WidgetAppearance.groupColor(activity, true));
        if (content instanceof ClockWidgetView && record.size.equals("Small"))
            ((ClockWidgetView) content).setGroupedSmall();
        View tile = tile(content, record.id, unavailable
                ? record.type + " unavailable. Saved widget data was kept." : record.type + " widget");
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { editor.open(group, index, record); }
        });
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View view) { editor.open(group, index, record); return true; }
        });
        return tile;
    }
    private View empty(final int group, final int index) {
        TextView view = label("+ Add Widget");
        View tile = tile(view, "group:empty:" + group + ":" + index, "Add widget");
        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) { editor.open(group, index, null); }
        });
        return tile;
    }
    private View tile(View content, String tag, String description) {
        FrameLayout frame = new FrameLayout(activity);
        frame.setTag(tag);
        frame.setFocusable(true);
        frame.setContentDescription(description);
        frame.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                CharSequence detail = widgetSummary(content);
                if (detail != null && detail.length() > 0)
                    info.setContentDescription(description + ". " + detail);
            }
        });
        frame.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        final FocusTileView background = new FocusTileView(activity);
        background.useTransparentSurface();
        background.setFocusable(false);
        background.setClickable(false);
        background.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        background.setText("");
        View color = new View(activity);
        android.graphics.drawable.GradientDrawable fill = new android.graphics.drawable.GradientDrawable();
        fill.setColor(WidgetAppearance.groupColor(activity, false));
        fill.setCornerRadius(dp(22));
        color.setBackground(fill);
        color.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams colorBounds = new FrameLayout.LayoutParams(-1, -1);
        frame.addView(color, colorBounds);
        frame.addView(background, new FrameLayout.LayoutParams(-1, -1));
        content.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int inset = dp(8);
        FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(-1, -1);
        bounds.setMargins(inset, inset, inset, inset);
        frame.addView(content, bounds);
        frame.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { background.setPreviewFocused(focused); }
        });
        return frame;
    }
    private CharSequence widgetSummary(View content) {
        if (content instanceof WeatherView) return ((WeatherView) content).accessibilitySummary();
        if (content instanceof LegacyClockView) return ((LegacyClockView) content).timeText();
        if (content instanceof ClockWidgetView) {
            ClockWidgetView clock = (ClockWidgetView) content;
            CharSequence time = clock.timeText(), date = clock.dateText();
            if (time == null || time.length() == 0) return date;
            return date == null || date.length() == 0 ? time : time + ". " + date;
        }
        if (content instanceof LegacyYearProgressView)
            return "Year " + LegacyYearProgressModel.at(java.time.LocalDate.now()).percent + "%";
        return null;
    }
    private TextView label(String text) {
        TextView view = new TextView(activity); view.setText(text); view.setTextColor(WidgetAppearance.groupColor(activity, true));
        view.setTextSize(16); view.setGravity(Gravity.CENTER); view.setPadding(dp(12), dp(12), dp(12), dp(12));
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    public void start() {
        if (started) return;
        started = true;
        getViewTreeObserver().addOnScrollChangedListener(scrollChanged);
        getViewTreeObserver().addOnGlobalLayoutListener(layoutChanged);
        syncVisible();
    }
    private void syncVisible() {
        if (!started) return;
        for (LiveWidget widget : live) {
            boolean visible = widget.view.isShown() && widget.view.getGlobalVisibleRect(new android.graphics.Rect());
            if (visible && !widget.active) { widget.provider.onStart(widget.view); widget.active = true; }
            else if (!visible && widget.active) { widget.provider.onStop(widget.view); widget.active = false; }
        }
    }
    public void stop() {
        if (!started) return;
        started = false;
        if (getViewTreeObserver().isAlive()) {
            getViewTreeObserver().removeOnScrollChangedListener(scrollChanged);
            getViewTreeObserver().removeOnGlobalLayoutListener(layoutChanged);
        }
        for (LiveWidget widget : live) if (widget.active) {
            widget.provider.onStop(widget.view); widget.active = false;
        }
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
