package com.jeremykenedy.firetv.ui;

final class MenuMotionOptions {
    static final String SETTING = "menu_transition_style";
    static final String[] VALUES = {"default", "none", "fade", "slide"};
    static final String[] LABELS = {"Default", "None", "Fade", "Short slide"};

    private MenuMotionOptions() {}

    static int index(String value) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(value)) return i;
        }
        return 0;
    }

    static boolean valid(Object value) {
        return value instanceof String && VALUES[index((String) value)].equals(value);
    }

    static String[] animations(String value, boolean opening) {
        if ("fade".equals(value)) return new String[] {"menu_fade_in", "menu_fade_out"};
        if ("slide".equals(value)) return opening
                ? new String[] {"menu_slide_in_right", "menu_slide_out_left"}
                : new String[] {"menu_slide_in_left", "menu_slide_out_right"};
        return new String[] {"", ""};
    }
}
