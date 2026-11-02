package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class WeatherView extends LinearLayout {
    private WeatherLoader loader;
    private boolean grouped;
    private boolean groupedSmall;
    private final WeatherOptions instanceOptions;
    private final TextView title;
    private final TextView status;
    private final LinearLayout periods;
    private final TextView source;
    public WeatherView(Context context) { this(context, null); }
    public WeatherView(Context context, WeatherOptions instanceOptions) {
        super(context); this.instanceOptions = instanceOptions; setOrientation(VERTICAL);
        int pad = Math.round(14 * context.getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad);
        title = text(18); title.setMaxLines(2); addView(title);
        status = text(15); status.setMaxLines(2); addView(status);
        periods = new LinearLayout(context); periods.setOrientation(VERTICAL); addView(periods, new LayoutParams(-1, 0, 1));
        source = text(13); source.setMaxLines(4); addView(source);
        title.setText("Hourly weather"); status.setText("Open settings to choose a supported U.S. location.");
        source.setText("Data: National Weather Service");
    }
    public void setGroupedSize(boolean small) {
        grouped = true; groupedSmall = small;
        int horizontal = Math.round(12 * getResources().getDisplayMetrics().density);
        int vertical = Math.round(6 * getResources().getDisplayMetrics().density);
        setPadding(horizontal, vertical, horizontal, vertical);
        title.setTextSize(15); title.setSingleLine(true);
        status.setTextSize(14); status.setSingleLine(true);
        periods.setLayoutParams(new LayoutParams(-1, LayoutParams.WRAP_CONTENT));
        source.setTextSize(12); source.setSingleLine(true);
        title.setText("Hourly weather"); status.setText("Checking forecast...");
    }
    private TextView text(int size) {
        TextView view = new TextView(getContext()); view.setTextColor(Color.WHITE); view.setTextSize(size);
        view.setEllipsize(TextUtils.TruncateAt.END); return view;
    }
    void start() {
        if (loader != null) return;
        status.setText("Checking the saved location...");
        loader = new WeatherLoader(getContext(), new WeatherLoader.Listener() {
            @Override public void onForecast(WeatherOptions options, WeatherForecast forecast) { render(options, forecast); }
        }, new WeatherRepository(new java.io.File(getContext().getCacheDir(), "weather-hourly")), instanceOptions); loader.start();
    }
    void render(WeatherOptions options, WeatherForecast forecast) {
        if (grouped) { renderGrouped(options, forecast); return; }
        periods.removeAllViews();
        title.setText(options == null || options.label.isEmpty() ? "Hourly weather" : options.label + " hourly forecast");
        if (options == null || (forecast.state != WeatherForecast.State.READY && forecast.state != WeatherForecast.State.EMPTY)) {
            status.setText(forecast.message); source.setText("Data: National Weather Service"); return;
        }
        status.setText(forecast.stale ? "Stale saved forecast. Offline or refresh failed." : forecast.state == WeatherForecast.State.EMPTY ? forecast.message : "Hourly forecast");
        int shown = 0; long now = System.currentTimeMillis();
        for (WeatherForecast.Hour hour : forecast.hours) {
            if (hour.end <= now || shown >= 4) continue;
            TextView row = text(16); row.setSingleLine(true);
            row.setText(forecast.hourLine(hour, options, DateFormat.is24HourFormat(getContext()))); periods.addView(row); shown++;
        }
        if (shown == 0) { TextView empty = text(16); empty.setText("No upcoming hourly periods."); periods.addView(empty); }
        source.setText(forecast.sourceText(options));
    }
    private void renderGrouped(WeatherOptions options, WeatherForecast forecast) {
        periods.removeAllViews();
        WeatherOptions location = options == null ? instanceOptions : options;
        title.setText(location == null || location.label.isEmpty() ? "Hourly weather" : location.label);
        if (options == null || (forecast.state != WeatherForecast.State.READY && forecast.state != WeatherForecast.State.EMPTY)) {
            status.setVisibility(VISIBLE);
            status.setText(forecast.state == WeatherForecast.State.SETUP_REQUIRED ? "Choose exact location" : "Forecast unavailable");
            source.setText("National Weather Service");
            return;
        }
        status.setVisibility(forecast.state == WeatherForecast.State.EMPTY ? VISIBLE : GONE);
        if (forecast.state == WeatherForecast.State.EMPTY) status.setText("No upcoming forecast");
        int shown = 0; long now = System.currentTimeMillis();
        for (WeatherForecast.Hour hour : forecast.hours) {
            if (hour.end <= now || shown >= (groupedSmall ? 1 : 2)) continue;
            TextView row = text(14); row.setSingleLine(true);
            row.setText(forecast.hourLine(hour, options, DateFormat.is24HourFormat(getContext())));
            periods.addView(row); shown++;
        }
        if (shown == 0 && forecast.state == WeatherForecast.State.READY) {
            status.setVisibility(VISIBLE); status.setText("No upcoming forecast");
        }
        source.setText("NWS" + (forecast.stale ? " | Stale | " : " | ") + forecast.updatedText(options));
    }
    public CharSequence accessibilitySummary() {
        StringBuilder summary = new StringBuilder();
        appendSummary(summary, title);
        if (status.getVisibility() == VISIBLE) appendSummary(summary, status);
        for (int i = 0; i < periods.getChildCount(); i++) {
            if (periods.getChildAt(i) instanceof TextView)
                appendSummary(summary, (TextView) periods.getChildAt(i));
        }
        appendSummary(summary, source);
        return summary.toString();
    }
    private static void appendSummary(StringBuilder summary, TextView text) {
        CharSequence value = text.getText();
        if (value == null || value.length() == 0) return;
        if (summary.length() > 0) summary.append(". ");
        summary.append(value);
    }
    void stop() { if (loader != null) { loader.close(); loader = null; } }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
