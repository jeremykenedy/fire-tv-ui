package com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public final class PuzzleWidgetView extends LinearLayout {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView status;
    private final TextView date;
    private boolean started;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if (started) { render(); handler.postDelayed(this, 30000); } }
    };
    public PuzzleWidgetView(Context context) {
        super(context);
        setOrientation(VERTICAL); setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding, padding, padding);
        setTag("puzzle:summary");
        text("Daily lights puzzle", 24, 0xffffffff);
        status = text("", 21, 0xffd5e7e9);
        date = text("", 16, 0xffbed4da);
        text("Select to play", 16, 0xffbed4da);
        render();
    }
    private TextView text(String label, int size, int color) {
        TextView text = new TextView(getContext()); text.setText(label); text.setTextSize(size); text.setTextColor(color);
        text.setMaxLines(2); addView(text, new LayoutParams(-1, -2)); return text;
    }
    private void render() {
        try {
            DailyPuzzle puzzle = PuzzlePreferences.read(getContext(), PuzzlePreferences.today());
            status.setText(puzzle.solved() ? "Solved in " + puzzle.moves + " moves" : puzzle.moves == 0 ? "Today's puzzle is ready" : puzzle.moves + " moves so far");
            date.setText(puzzle.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
        } catch (IllegalArgumentException error) { status.setText("Saved puzzle needs attention"); date.setText("Select to reset today's puzzle"); }
    }
    public void start() { stop(); started = true; refresh.run(); }
    public void stop() { started = false; handler.removeCallbacks(refresh); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
