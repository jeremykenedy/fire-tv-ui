package com.jeremykenedy.firetv.ui;

/** Chooses only the selected dream's supported launch paths. */
public final class IdleDispatch {
    public static final String SYSTEM_STARTER =
            "com.android.systemui/com.android.systemui.Somnambulator";

    private IdleDispatch() {
    }

    public static String[] targets(int sdk, boolean enabled, String selected,
            String registeredDream, String preview) {
        String dream = Screensavers.fullComponent(selected);
        if (!enabled || !valid(dream)) {
            return new String[0];
        }
        String fallback = Screensavers.fullComponent(preview);
        String pkg = dream.substring(0, dream.indexOf('/'));
        boolean mapped = dream.equals(Screensavers.fullComponent(registeredDream))
                && valid(fallback) && fallback.startsWith(pkg + "/");
        if (sdk < 34) {
            return mapped ? new String[] {SYSTEM_STARTER, fallback}
                    : new String[] {SYSTEM_STARTER};
        }
        return mapped ? new String[] {fallback} : new String[0];
    }

    private static boolean valid(String value) {
        return value.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+");
    }
}
