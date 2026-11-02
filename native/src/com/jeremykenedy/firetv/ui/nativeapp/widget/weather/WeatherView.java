package com.jeremykenedy.firetv.ui.nativeapp.widget.weather;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class WeatherView extends LinearLayout {
    private WeatherLoader loader;
    private final TextView title;
    private final TextView status;
    private final LinearLayout periods;
    private final TextView source;
    public WeatherView(Context context) {
        super(context); setOrientation(VERTICAL);
        int pad = Math.round(14 * context.getResources().getDisplayMetrics().density); setPadding(pad, pad, pad, pad);
        title = text(18); title.setMaxLines(2); addView(title);
        status = text(15); status.setMaxLines(2); addView(status);
        periods = new LinearLayout(context); periods.setOrientation(VERTICAL); addView(periods, new LayoutParams(-1, 0, 1));
        source = text(13); source.setMaxLines(4); addView(source);
        title.setText("Hourly weather"); status.setText("Open settings to choose a supported U.S. location.");
        source.setText("Data: National Weather Service");
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
        }); loader.start();
    }
    void render(WeatherOptions options, WeatherForecast forecast) {
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
    void stop() { if (loader != null) { loader.close(); loader = null; } }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
