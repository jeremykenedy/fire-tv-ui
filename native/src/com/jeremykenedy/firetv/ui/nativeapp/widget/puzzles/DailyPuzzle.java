package com.jeremykenedy.firetv.ui.nativeapp.widget.puzzles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Original local daily boards are made by legal moves from the solved board. */
public final class DailyPuzzle {
    public static final int SIZE = 3;
    public static final int MAX_MOVES = 1000000;
    public final LocalDate date;
    public final int board;
    public final int moves;
    public final boolean hintsUsed;

    public DailyPuzzle(LocalDate date, int board, int moves, boolean hintsUsed) {
        if (date == null || board < 0 || board > 511 || moves < 0 || moves > MAX_MOVES) {
            throw new IllegalArgumentException("Invalid saved puzzle progress.");
        }
        this.date = date; this.board = board; this.moves = moves; this.hintsUsed = hintsUsed;
    }

    public static DailyPuzzle forDate(LocalDate date) {
        long seed = date.toEpochDay() + 0x6a09e667f3bcc909L;
        seed = (seed ^ seed >>> 30) * 0xbf58476d1ce4e5b9L;
        seed = (seed ^ seed >>> 27) * 0x94d049bb133111ebL;
        int presses = (int) (seed ^ seed >>> 31) & 511;
        if (presses == 0) presses = 1;
        int board = 0;
        for (int cell = 0; cell < 9; cell++) if ((presses & 1 << cell) != 0) board ^= mask(cell);
        if (board == 0) board = mask(4);
        return new DailyPuzzle(date, board, 0, false);
    }

    public boolean lit(int cell) { validateCell(cell); return (board & 1 << cell) != 0; }
    public boolean solved() { return board == 0; }

    public DailyPuzzle press(int cell) {
        validateCell(cell);
        if (solved()) return this;
        if (moves == MAX_MOVES) throw new IllegalArgumentException("Move limit reached. Reset this puzzle to continue.");
        return new DailyPuzzle(date, board ^ mask(cell), moves + 1, hintsUsed);
    }

    public DailyPuzzle withHint() { return new DailyPuzzle(date, board, moves, true); }

    /** Breadth first search validates a shortest sequence against the current board, at most 512 states. */
    public List<Integer> solution() {
        if (solved()) return Collections.emptyList();
        int[] parent = new int[512];
        int[] move = new int[512];
        int[] queue = new int[512];
        Arrays.fill(parent, -1);
        parent[board] = board;
        queue[0] = board;
        int head = 0, tail = 1;
        while (head < tail && parent[0] < 0) {
            int state = queue[head++];
            for (int cell = 0; cell < 9; cell++) {
                int next = state ^ mask(cell);
                if (parent[next] >= 0) continue;
                parent[next] = state; move[next] = cell; queue[tail++] = next;
            }
        }
        if (parent[0] < 0) throw new IllegalArgumentException("This board has no solution.");
        List<Integer> result = new ArrayList<>();
        for (int state = 0; state != board; state = parent[state]) result.add(move[state]);
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    public static int mask(int cell) {
        validateCell(cell);
        int row = cell / SIZE, column = cell % SIZE, mask = 1 << cell;
        if (row > 0) mask |= 1 << (cell - SIZE);
        if (row < SIZE - 1) mask |= 1 << (cell + SIZE);
        if (column > 0) mask |= 1 << (cell - 1);
        if (column < SIZE - 1) mask |= 1 << (cell + 1);
        return mask;
    }

    private static void validateCell(int cell) { if (cell < 0 || cell >= 9) throw new IllegalArgumentException("Choose a puzzle cell."); }
}
