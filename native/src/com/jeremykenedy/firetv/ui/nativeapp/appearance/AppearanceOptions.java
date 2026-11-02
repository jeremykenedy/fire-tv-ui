package com.jeremykenedy.firetv.ui.nativeapp.appearance;

public final class AppearanceOptions {
    public static final String[] BASES = {"off", "particles", "aurora", "constellation", "prism", "silk", "bokeh", "stars", "embers", "waves", "rain", "orbits", "rays", "still_midnight", "still_graphite", "still_forest", "still_plum", "still_legacy_gradient", "still_light", "still_dark", "snow", "snowflakes", "leaves", "hearts_falling", "hearts_floating", "petals", "lanterns"};
    public static final String[] BASE_LABELS = {"Off: use my wallpaper", "Quiet particles", "Aurora ribbons", "Connected lights", "Prism light", "Flowing silk", "Soft bokeh", "Drifting stars", "Warm embers", "Ocean ribbons", "Gentle rain", "Orbital arcs", "Light rays", "Still: Midnight blue", "Still: Warm graphite", "Still: Forest dusk", "Still: Plum velvet", "Still: Saved gradient", "Light", "Dark", "Falling snow", "Drifting snowflakes", "Falling leaves", "Falling hearts", "Floating hearts", "Drifting petals", "Floating lights"};
    public static final String[] OVERLAYS = {"off", "snow", "snowflakes", "leaves", "hearts_falling", "hearts_floating", "petals", "lanterns", "particles", "aurora", "constellation", "prism", "silk", "bokeh", "stars", "embers", "waves", "rain", "orbits", "rays"};
    public static final String[] OVERLAY_LABELS = {"Off", "Falling snow", "Drifting snowflakes", "Falling leaves", "Falling hearts", "Floating hearts", "Drifting petals", "Floating lights", "Quiet particles", "Aurora ribbons", "Connected lights", "Prism light", "Flowing silk", "Soft bokeh", "Drifting stars", "Warm embers", "Ocean ribbons", "Gentle rain", "Orbital arcs", "Light rays"};
    public final String base, overlay, scene, imageUri, videoUri, basePalette, overlayPalette, legacyGradient;
    public final boolean baseFrozen, overlayFrozen;
    public final int baseSpeed, baseIntensity, brightness, overlaySpeed, overlayIntensity, overlayOpacity;
    public AppearanceOptions(String base, boolean baseFrozen, int baseSpeed, int baseIntensity, int brightness,
            String overlay, boolean overlayFrozen, int overlaySpeed, int overlayIntensity, int overlayOpacity,
            String scene, String imageUri, String videoUri) {
        this(base, baseFrozen, baseSpeed, baseIntensity, brightness, overlay, overlayFrozen,
                overlaySpeed, overlayIntensity, overlayOpacity, scene, imageUri, videoUri, "", "");
    }
    public AppearanceOptions(String base, boolean baseFrozen, int baseSpeed, int baseIntensity, int brightness,
            String overlay, boolean overlayFrozen, int overlaySpeed, int overlayIntensity, int overlayOpacity,
            String scene, String imageUri, String videoUri, String basePalette, String overlayPalette) {
        this(base, baseFrozen, baseSpeed, baseIntensity, brightness, overlay, overlayFrozen, overlaySpeed, overlayIntensity, overlayOpacity, scene, imageUri, videoUri, basePalette, overlayPalette, "");
    }
    public AppearanceOptions(String base, boolean baseFrozen, int baseSpeed, int baseIntensity, int brightness,
            String overlay, boolean overlayFrozen, int overlaySpeed, int overlayIntensity, int overlayOpacity,
            String scene, String imageUri, String videoUri, String basePalette, String overlayPalette, String legacyGradient) {
        this.legacyGradient = safe(legacyGradient);
        this.basePalette = safe(basePalette); this.overlayPalette = safe(overlayPalette);
        this.base = known(BASES, base); this.baseFrozen = baseFrozen;
        this.baseSpeed = level(baseSpeed); this.baseIntensity = level(baseIntensity); this.brightness = level(brightness);
        this.overlay = known(OVERLAYS, overlay); this.overlayFrozen = overlayFrozen;
        this.overlaySpeed = level(overlaySpeed); this.overlayIntensity = level(overlayIntensity); this.overlayOpacity = level(overlayOpacity);
        this.scene = (com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidayScenes.find(scene) != null) ? scene : ""; this.imageUri = safe(imageUri); this.videoUri = safe(videoUri);
    }
    public static AppearanceOptions defaults() { return new AppearanceOptions("off", false, 50, 50, 50, "off", false, 50, 50, 50, "", "", ""); }
    public boolean baseAnimated() { return !baseFrozen && baseIntensity > 0 && !base.equals("off") && !base.startsWith("still_") && scene.isEmpty(); }
    public boolean overlayAnimated() { return !overlayFrozen && !overlay.equals("off") && overlayIntensity > 0 && overlayOpacity > 0; }
    public static String known(String[] choices, String value) { for (String choice : choices) if (choice.equals(value)) return value; return "off"; }
    private static int level(int value) { return value >= 0 && value <= 100 ? value : 50; }
    private static String safe(String value) { return value == null ? "" : value; }
}
