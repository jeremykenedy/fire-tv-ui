package com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.ZoneId;

final class DailyContentView extends LinearLayout {
    private final DailyContent.Kind kind;
    private final boolean detail;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView headline;
    private final TextView body;
    private final TextView footer;
    private final DailyIllustration illustration;
    private DailyContent current;
    private boolean revealed;
    private boolean started;
    private boolean receiverRegistered;
    private final BroadcastReceiver dateChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (started) { refresh(); handler.postDelayed(this, 30000); } }
    };
    DailyContentView(Context context, DailyContent.Kind kind, boolean detail) {
        super(context); this.kind = kind; this.detail = detail;
        setOrientation(VERTICAL); setGravity(Gravity.CENTER_VERTICAL);
        int padding = dp(16); setPadding(padding, padding, padding, padding);
        setTag("daily-content:" + kind.id);
        if (!detail) text(kind.title, 20, 1);
        headline = text("", 23, detail ? Integer.MAX_VALUE : 1);
        if (kind == DailyContent.Kind.ART || kind == DailyContent.Kind.COMIC) {
            illustration = new DailyIllustration(context); addView(illustration, new LayoutParams(-1, dp(detail ? 240 : 110)));
        } else illustration = null;
        body = text("", 19, detail ? Integer.MAX_VALUE : 4);
        footer = text("", 14, detail ? Integer.MAX_VALUE : 2);
        footer.setTextColor(0xffb8d2da);
        refresh();
    }
    private TextView text(String value, int size, int maxLines) {
        TextView text = new TextView(getContext()); text.setText(value); text.setTextSize(size);
        text.setTextColor(0xfff1f6f8); text.setMaxLines(maxLines); text.setPadding(0, dp(3), 0, dp(3));
        addView(text, new LayoutParams(-1, -2)); return text;
    }
    void refresh() {
        try {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate date = DailyContent.date(System.currentTimeMillis(), zone);
            DailyContent next = DailyContent.forDate(kind, date, kind == DailyContent.Kind.HOROSCOPE ? DailyContentWidgets.sign(getContext()) : 0);
            if (current == null || !current.date.equals(next.date)) revealed = false;
            current = next;
            headline.setText(next.headline);
            String text = next.text;
            if (kind == DailyContent.Kind.COMIC) text += "\n" + next.answer;
            if (kind == DailyContent.Kind.TRIVIA) text += revealed ? "\n\n" + next.answer : "\nSelect to reveal the answer.";
            if (kind == DailyContent.Kind.HOROSCOPE) text = "Entertainment only\n" + text;
            body.setText(text);
            String edition = next.date + " · " + zone.getId();
            footer.setText(edition + "\n" + (detail ? next.attribution : kind == DailyContent.Kind.ASTRONOMY ? "NASA facts · Original summaries" : "Original collection · MIT License"));
            if (illustration != null) illustration.setContent(next);
        } catch (RuntimeException error) {
            current = null; headline.setText("Daily content unavailable");
            body.setText("Review the TV date and saved zodiac sign."); footer.setText("Stored settings have been kept.");
            if (illustration != null) illustration.setContent(null);
        }
    }
    void reveal() { revealed = true; refresh(); }
    void start() {
        stop(); started = true;
        IntentFilter filter = new IntentFilter(); filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) getContext().registerReceiver(dateChanged, filter, Context.RECEIVER_NOT_EXPORTED);
        else getContext().registerReceiver(dateChanged, filter);
        receiverRegistered = true; refresh.run();
    }
    void stop() {
        started = false; handler.removeCallbacks(refresh);
        if (receiverRegistered) { getContext().unregisterReceiver(dateChanged); receiverRegistered = false; }
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
