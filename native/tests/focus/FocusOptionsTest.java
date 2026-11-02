package com.jeremykenedy.firetv.ui.nativeapp.focus;

public final class FocusOptionsTest {
    public static void main(String[] args) {
        FocusOptions defaults = FocusOptions.defaults();
        check(defaults.durationMillis == 100, "Fast is the initial transition speed.");
        check(defaults.scale(1f) == 1.06f, "Default growth is six percent.");
        check(defaults.fillEnabled && defaults.fillRgb == 0x3c3251 && defaults.fillOpacity == 100,
                "Existing focus fill remains the default.");
        check(defaults.surfaceRgb(0f) == 0xff251f39 && defaults.surfaceRgb(1f) == 0xff3c3251,
                "Default fill preserves the previous surface colors.");
        FocusOptions borderOnly = new FocusOptions(0, 7, true, 0x123456, 75, false, 0xffffff, 100);
        check(borderOnly.surfaceRgb(0f) == borderOnly.surfaceRgb(1f),
                "Border-only focus never brightens the surface.");
        check(borderOnly.scale(1f) == 1.07f, "Custom growth accepts values between presets.");
        FocusOptions fill = new FocusOptions(0, 0, false, 0x123456, 75, true, 0xffffff, 50);
        check(fill.surfaceRgb(1f) == 0xff928f9c, "Fill opacity blends only the surface behind artwork.");
        check(fill.surfaceRgb(-1f) == 0xff251f39 && fill.surfaceRgb(2f) == fill.surfaceRgb(1f),
                "Fill animation progress stays bounded.");
        for (int[] invalidFill : new int[][]{{-1, 0}, {0x1000000, 0}, {0, -1}, {0, 101}}) {
            try {
                new FocusOptions(100, 6, true, 0xffffff, 100, true, invalidFill[0], invalidFill[1]);
                throw new AssertionError("Invalid fill accepted.");
            } catch (IllegalArgumentException expected) {}
        }
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
