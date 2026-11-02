package com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles;

import java.time.LocalDate;
import java.util.List;

public final class DailyPuzzleTest {
    public static void main(String[] args) {
        LocalDate date = LocalDate.of(2026, 11, 1);
        DailyPuzzle puzzle = DailyPuzzle.forDate(date);
        check(puzzle.board == DailyPuzzle.forDate(date).board && !puzzle.solved(), "Daily board is stable and starts unsolved.");
        check(Integer.bitCount(DailyPuzzle.mask(0)) == 3 && Integer.bitCount(DailyPuzzle.mask(4)) == 5,
                "Corner and center toggle only orthogonal neighbors.");
        check((DailyPuzzle.mask(2) & 1 << 3) == 0, "Moves cannot wrap across rows.");
        check(puzzle.press(4).press(4).board == puzzle.board && puzzle.press(4).press(4).moves == 2,
                "Repeating a press reverses lights and retains actual move count.");
        DailyPuzzle hinted = puzzle.withHint();
        check(hinted.hintsUsed && hinted.board == puzzle.board && hinted.moves == 0, "Hint marks assistance without playing a move.");
        for (int board = 0; board < 512; board++) {
            DailyPuzzle state = new DailyPuzzle(date, board, 0, false);
            List<Integer> solution = state.solution();
            for (int cell : solution) state = state.press(cell);
            check(state.solved(), "Every accepted board's returned solution actually reaches zero.");
            check(state.moves == solution.size(), "Validated solution move count matches actual presses.");
        }
        for (int day = 0; day < 366; day++) {
            DailyPuzzle state = DailyPuzzle.forDate(date.plusDays(day));
            check(!state.solved() && state.moves == 0, "Daily generation starts playable without recycled progress.");
            for (int cell : state.solution()) state = state.press(cell);
            check(state.solved(), "Each generated daily puzzle has a validated solution.");
        }
        DailyPuzzle solved = new DailyPuzzle(date, 0, 7, false);
        check(solved.press(0) == solved && solved.solution().isEmpty(), "Solved state stays solved until explicit reset.");
        check(DailyPuzzle.forDate(date).moves == 0 && !DailyPuzzle.forDate(date).hintsUsed, "Reset creates today's original board without progress.");
        reject(new Runnable() { @Override public void run() { DailyPuzzle.forDate(LocalDate.now()).press(9); } });
        reject(new Runnable() { @Override public void run() { new DailyPuzzle(date, 512, 0, false); } });
        reject(new Runnable() { @Override public void run() { new DailyPuzzle(date, 1, DailyPuzzle.MAX_MOVES, false).press(0); } });
        System.out.println("DailyPuzzleTest: exhaustive solution and daily progress checks passed");
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid puzzle accepted"); } catch (IllegalArgumentException expected) {}
    }
}
