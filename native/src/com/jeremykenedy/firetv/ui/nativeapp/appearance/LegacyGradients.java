package com.jeremykenedy.firetv.ui.nativeapp.appearance;

/** Colors observed in two supported legacy gradient selections. */
public final class LegacyGradients {
    public static final String[] IDS = {"electric_indigo", "cloud_nine"};
    public static final String[] LABELS = {"Electric Indigo", "Cloud Nine"};
    private static final int[] TOP = {0xff1a0f3e, 0xffeceff1};
    private static final int[] BOTTOM = {0xff5633a1, 0xff90a4ae};

    private LegacyGradients() {}
    public static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }
    public static int top(int index) { return TOP[index]; }
    public static int bottom(int index) { return BOTTOM[index]; }
}
