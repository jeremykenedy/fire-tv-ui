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
        new Scene("winter", "Winter landscape", "snow"),
        new Scene("hanukkah", "Hanukkah candlelight", "snowflakes"),
        new Scene("diwali", "Diwali courtyard", "lanterns"),
        new Scene("eid_fitr", "Eid al-Fitr courtyard", "lanterns"),
        new Scene("lunar_new_year", "Lunar New Year garden", "petals"),
        new Scene("valentine", "Valentine's garden", "hearts_floating"),
        new Scene("st_patrick", "St. Patrick's valley", "particles"),
        new Scene("easter", "Easter garden", "petals"),
        new Scene("thanksgiving", "Thanksgiving harvest", "leaves"),
        new Scene("passover", "Passover Seder", "petals"),
        new Scene("vesak", "Vesak lantern garden", "petals"),
        new Scene("juneteenth", "Juneteenth evening", "stars"),
        new Scene("ramadan", "Ramadan evening", "lanterns")
    };

    private HolidayScenes() {}

    public static Scene find(String id) {
        for (Scene scene : ALL) if (scene.id.equals(id)) return scene;
        return null;
    }
}
