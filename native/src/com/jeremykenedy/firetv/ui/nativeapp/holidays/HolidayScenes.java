package com.jeremykenedy.firetv.ui.nativeapp.holidays;

public final class HolidayScenes {
    public static final class Scene {
        public final String id, name, resourceName, overlay;
        public final String animatedBackgroundResourceName, characterResourceName;

        private Scene(String id, String name, String overlay) {
            this.id = id;
            this.name = name;
            this.resourceName = "holiday_scene_" + id;
            this.overlay = overlay;
            this.animatedBackgroundResourceName = "halloween".equals(id) ? "holiday_scene_halloween_clean" : "";
            this.characterResourceName = "halloween".equals(id) ? "holiday_characters_halloween_walk" : "";
        }
    }

    public static final Scene[] ALL = {
        new Scene("halloween", "Halloween", "leaves"),
        new Scene("christmas", "Christmas", "snowflakes"),
        new Scene("winter", "Winter landscape", "snow")
    };

    private HolidayScenes() {}

    public static Scene find(String id) {
        for (Scene scene : ALL) if (scene.id.equals(id)) return scene;
        return null;
    }
}
