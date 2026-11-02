package com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.util.List;

final class PuzzleDialog {
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Button[] cells = new Button[9];
    private DailyPuzzle puzzle;
    private TextView status;
    private final Runnable dateCheck = new Runnable() {
        @Override public void run() { refreshDate(); handler.postDelayed(this, 30000); }
    };

    private PuzzleDialog(Activity activity, DailyPuzzle puzzle) { this.activity = activity; this.puzzle = puzzle; }

    static void show(final Activity activity) {
        try { new PuzzleDialog(activity, PuzzlePreferences.read(activity, PuzzlePreferences.today())).showBoard(); }
        catch (IllegalArgumentException error) {
            new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Daily puzzle").setMessage(error.getMessage())
                    .setPositiveButton("Reset today's puzzle", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            if (PuzzlePreferences.save(activity, DailyPuzzle.forDate(PuzzlePreferences.today()))) show(activity);
                            else failed(activity);
                        }
                    }).setNegativeButton("Back", null).show();
        }
    }

    private void showBoard() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16); content.setPadding(padding, padding, padding, padding);
        TextView rules = new TextView(activity);
        rules.setText("Turn every light off. Select toggles that light and its neighbors above, below, left and right. Use the D-pad to choose a light.");
        rules.setTextSize(17); content.addView(rules);
        status = new TextView(activity); status.setTextSize(19); status.setPadding(0, dp(8), 0, dp(8));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setTag("puzzle:status"); content.addView(status);
        for (int row = 0; row < 3; row++) {
            LinearLayout line = new LinearLayout(activity); line.setGravity(Gravity.CENTER);
            for (int column = 0; column < 3; column++) {
                final int cell = row * 3 + column;
                Button button = new Button(activity);
                button.setId(View.generateViewId()); button.setTag("puzzle:cell:" + cell);
                button.setTextSize(20); button.setPadding(dp(4), 0, dp(4), 0);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        if (refreshDate()) return;
                        try { save(puzzle.press(cell)); }
                        catch (IllegalArgumentException error) { Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show(); }
                    }
                });
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(100), dp(56));
                size.setMargins(dp(3), dp(3), dp(3), dp(3)); line.addView(button, size); cells[cell] = button;
            }
            content.addView(line);
        }
        for (int cell = 0; cell < 9; cell++) {
            cells[cell].setNextFocusLeftId(cells[cell % 3 == 0 ? cell : cell - 1].getId());
            cells[cell].setNextFocusRightId(cells[cell % 3 == 2 ? cell : cell + 1].getId());
            if (cell >= 3) cells[cell].setNextFocusUpId(cells[cell - 3].getId());
            if (cell < 6) cells[cell].setNextFocusDownId(cells[cell + 3].getId());
        }
        Button hint = new Button(activity); hint.setText("Hint"); hint.setTag("puzzle:hint");
        hint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (refreshDate() || puzzle.solved()) return;
                List<Integer> solution = puzzle.solution();
                if (save(puzzle.withHint())) {
                    int cell = solution.get(0);
                    status.setText("Try row " + (cell / 3 + 1) + ", column " + (cell % 3 + 1) + ". " + solution.size() + " moves can solve this board.");
                }
            }
        }); content.addView(hint);
        Button reset = new Button(activity); reset.setText("Reset today's puzzle"); reset.setTag("puzzle:reset");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Reset today's puzzle?").setMessage("Clear this puzzle's moves and hints and return to today's starting board?")
                        .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { save(DailyPuzzle.forDate(PuzzlePreferences.today())); }
                        }).setNegativeButton("Keep progress", null).show();
            }
        }); content.addView(reset);
        TextView privacy = new TextView(activity);
        privacy.setText("A new puzzle uses the TV's local date. Only the current date's progress is kept on this TV.");
        privacy.setTextSize(15); content.addView(privacy);
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        final AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Daily lights puzzle").setView(scroll)
                .setNegativeButton("Done", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface unused) { render(); cells[4].requestFocus(); handler.post(dateCheck); }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface unused) { handler.removeCallbacks(dateCheck); }
        }); dialog.show();
    }

    private boolean refreshDate() {
        LocalDate today = PuzzlePreferences.today();
        if (puzzle.date.equals(today)) return false;
        try { puzzle = PuzzlePreferences.read(activity, today); }
        catch (IllegalArgumentException error) { status.setText(error.getMessage()); return true; }
        render();
        status.setText("New local date: today's puzzle is ready."); return true;
    }

    private boolean save(DailyPuzzle next) {
        if (!PuzzlePreferences.save(activity, next)) { failed(activity); return false; }
        puzzle = next; render(); return true;
    }

    private void render() {
        status.setText(puzzle.solved() ? "Solved in " + puzzle.moves + " moves" + (puzzle.hintsUsed ? " with hints" : "")
                : puzzle.date + " · " + puzzle.moves + " moves");
        for (int cell = 0; cell < 9; cell++) {
            boolean lit = puzzle.lit(cell);
            Button button = cells[cell]; button.setText(lit ? "On" : "Off");
            button.setTextColor(lit ? 0xff10232b : 0xffedf7f8);
            button.setContentDescription("Row " + (cell / 3 + 1) + ", column " + (cell % 3 + 1) + ", " + (lit ? "on" : "off"));
            StateListDrawable background = new StateListDrawable();
            background.addState(new int[]{android.R.attr.state_focused}, tile(lit, true));
            background.addState(new int[]{android.R.attr.state_pressed}, tile(lit, true));
            background.addState(new int[]{}, tile(lit, false)); button.setBackground(background);
        }
    }

    private GradientDrawable tile(boolean lit, boolean focused) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(lit ? 0xff9bd4d5 : 0xff263c48);
        drawable.setCornerRadius(dp(8)); drawable.setStroke(dp(focused ? 4 : 1), focused ? 0xffffd279 : 0xff6e8d97); return drawable;
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private static void failed(Activity activity) { Toast.makeText(activity, "Could not save progress. Try again.", Toast.LENGTH_LONG).show(); }
}
