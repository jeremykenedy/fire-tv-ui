package com.jeremykenedy.firetv.ui;

public final class MenuBackgroundMotionTest {
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    public static void main(String[] args) {
        MenuBackgroundClock clock = new MenuBackgroundClock();
        check(!clock.frame(1000000000L, 50));
        clock.resumed(true);
        check(clock.frame(1000000000L, 50) && clock.phase() == 0);
        clock.frame(1010000000L, 50);
        check(Math.abs(clock.phase() - .001) < 1e-12);
        clock.resumed(true);
        clock.frame(1020000000L, 100);
        check(Math.abs(clock.phase() - .003) < 1e-12);
        clock.resumed(false);
        check(!clock.frame(5000000000L, 50) && Math.abs(clock.phase() - .003) < 1e-12);
        clock.resumed(true);
        clock.frame(10000000000L, 50);
        check(Math.abs(clock.phase() - .003) < 1e-12);
        clock.frame(11000000000L, 25);
        check(Math.abs(clock.phase() - .008) < 1e-12);
        clock.frame(10000000000L, 25);
        clock.frame(11000000000L, 25);
        check(Math.abs(clock.phase() - .008) < 1e-12);
        for (int i = 0; i < 160; i++) {
            for (int salt = 1; salt <= 14; salt++) {
                float value = MenuBackgroundMotion.seed(i, salt);
                check(value >= 0 && value < 1);
                check(value == MenuBackgroundMotion.seed(i, salt));
            }
        }
        check(MenuBackgroundMotion.seed(3, 2) != MenuBackgroundMotion.seed(4, 2));
        check(MenuBackgroundMotion.seed(3, 2) != MenuBackgroundMotion.seed(3, 3));
        check(Math.abs(MenuBackgroundMotion.wrap(-.2) - .8) < .000001);
        check(MenuBackgroundMotion.wrap(1) == 0);
        check(MenuBackgroundMotion.wrap(0) == 0);
        check(Math.abs(MenuBackgroundMotion.wrap(12345678.25) - .25) < .000001);
        check(MenuBackgroundMotion.envelope(0) == 0 && MenuBackgroundMotion.envelope(1) == 0);
        check(MenuBackgroundMotion.envelope(.5f) == 1);
        check(MenuBackgroundMotion.envelope(-.1f) == 0);
        check(MenuBackgroundMotion.envelope(1.1f) == 0);
        for (int i = 0; i <= 100; i++) {
            float a = i / 100f;
            check(Math.abs(MenuBackgroundMotion.envelope(a)
                    - MenuBackgroundMotion.envelope(1 - a)) < .000001);
        }
        for (int band = 0; band < 30; band++) {
            for (double time : new double[]{0, 3.7, 10000}) {
                for (int step = 0; step <= 32; step++) {
                    double position = step / 32.0, epsilon = .001;
                    double finite = (MenuBackgroundMotion.wave(position + epsilon, time, band)
                            - MenuBackgroundMotion.wave(position - epsilon, time, band)) / (2 * epsilon);
                    check(Math.abs(finite - MenuBackgroundMotion.waveSlope(position, time, band)) < .0005);
                    check(Math.abs(MenuBackgroundMotion.wave(position, time, band)) <= 1.000001);
                }
            }
        }
        String[] legacy = {"off", "particles", "aurora", "constellation", "prism"};
        for (int i = 0; i < legacy.length; i++) check(MenuBackgroundOptions.style(legacy[i]) == i);
        check(MenuBackgroundOptions.style(null) == 0);
        check(MenuBackgroundOptions.style("unknown") == 0);
        check(MenuBackgroundOptions.STYLES.length == MenuBackgroundOptions.LABELS.length);
        check(!MenuBackgroundOptions.animated(0));
        for (String value : new String[]{"still_midnight", "still_graphite", "still_forest", "still_plum"}) {
            int selected = MenuBackgroundOptions.style(value);
            check(selected > 0 && !MenuBackgroundOptions.animated(selected));
        }
        for (String value : new String[]{"particles", "aurora", "constellation", "prism",
                "silk", "bokeh", "stars", "embers", "waves", "rain", "orbits", "rays"}) {
            check(MenuBackgroundOptions.animated(MenuBackgroundOptions.style(value)));
        }
        for (int i = 0; i < MenuBackgroundOptions.STYLES.length; i++) {
            check(MenuBackgroundOptions.style(MenuBackgroundOptions.STYLES[i]) == i);
            check(!MenuBackgroundOptions.LABELS[i].isEmpty());
        }
        System.out.println("Paused scene time, independent motion, fade continuity, smooth curves and legacy choices passed.");
    }
}
