package com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;

public final class PuzzlePreferences {
    public static final String FILE = "native_daily_puzzle";
    private PuzzlePreferences() {}
    public static LocalDate today() { return LocalDate.now(); }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    public static DailyPuzzle read(Context context, LocalDate today) {
        SharedPreferences prefs = prefs(context);
        try {
            if (!today.toString().equals(prefs.getString("date", ""))) return DailyPuzzle.forDate(today);
            return new DailyPuzzle(today, prefs.getInt("board", -1), prefs.getInt("moves", 0), prefs.getBoolean("hints", false));
        } catch (RuntimeException error) { throw new IllegalArgumentException("Saved puzzle could not be read. Reset today's puzzle to continue.", error); }
    }

    public static boolean save(Context context, DailyPuzzle puzzle) {
        return prefs(context).edit().putString("date", puzzle.date.toString()).putInt("board", puzzle.board)
                .putInt("moves", puzzle.moves).putBoolean("hints", puzzle.hintsUsed).commit();
    }
}
