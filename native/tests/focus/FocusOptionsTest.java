package com.jeremykenedy.firetv.ui.nativeapp.focus;

public final class FocusOptionsTest {
    public static void main(String[] args) {
        FocusOptions defaults = FocusOptions.defaults();
        check(defaults.durationMillis == 100, "Fast is the initial transition speed.");
        check(defaults.scale(1f) == 1.06f, "Default growth is six percent.");
        for (int opacity : new int[]{0, 1, 25, 50, 99, 100}) {
            FocusOptions options = new FocusOptions(0, 0, true, 0x12abef, opacity);
            check((options.borderArgb() & 0xffffff) == 0x12abef, "Opacity never changes RGB.");
            check((options.borderArgb() >>> 24) == Math.round(opacity * 255f / 100f),
                    "Border opacity has its own actual alpha.");
            check(options.scale(1f) == 1f && options.scale(0f) == 1f,
                    "None growth does not resize artwork.");
        }
        for (int width : new int[]{80, 160, 320, 640, 1920}) {
            int inset = FocusOptions.growthInset(width);
            check((width - 2 * inset) * 1.1f <= width + 0.01f,
                    "Maximum growth stays inside any card width.");
        }
        int[][] invalid = {{-1,6,0xffffff,100},{501,6,0xffffff,100},{100,-1,0xffffff,100},
                {100,11,0xffffff,100},{100,6,-1,100},{100,6,0x1000000,100},
                {100,6,0xffffff,-1},{100,6,0xffffff,101}};
        for (int[] values : invalid) {
            try {
                new FocusOptions(values[0], values[1], true, values[2], values[3]);
                throw new AssertionError("Invalid focus setting accepted.");
            } catch (IllegalArgumentException expected) {}
        }
        System.out.println("Focus defaults, independent alpha, no-growth and dimension bounds passed.");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
