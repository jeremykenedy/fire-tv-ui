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
        new Scene("ramadan", "Ramadan evening", "lanterns"),
        new Scene("new_year", "New Year", "stars"),
        new Scene("epiphany", "Epiphany", "stars"),
        new Scene("eid_adha", "Eid al-Adha", "lanterns"),
        new Scene("rosh_hashanah", "Rosh Hashanah", "leaves"),
        new Scene("yom_kippur", "Yom Kippur", "silk"),
        new Scene("sukkot", "Sukkot", "leaves"),
        new Scene("purim", "Purim", "particles"),
        new Scene("holi", "Holi", "petals"),
        new Scene("navratri", "Navratri", "silk"),
        new Scene("janmashtami", "Janmashtami", "petals"),
        new Scene("ganesh_chaturthi", "Ganesh Chaturthi", "lanterns"),
        new Scene("vaisakhi", "Vaisakhi", "petals"),
        new Scene("gurpurab", "Gurpurab", "lanterns"),
        new Scene("losar", "Losar", "snowflakes"),
        new Scene("obon", "Obon", "lanterns"),
        new Scene("mahavir_jayanti", "Mahavir Jayanti", "petals"),
        new Scene("paryushan", "Paryushan", "silk"),
        new Scene("naw_ruz", "Naw-Ruz", "petals"),
        new Scene("ridvan", "Ridvan", "petals"),
        new Scene("nowruz", "Nowruz", "petals"),
        new Scene("mehrgan", "Mehregan", "leaves"),
        new Scene("kwanzaa", "Kwanzaa", "lanterns"),
        new Scene("independence", "Independence Day", "stars"),
        new Scene("spring", "Spring equinox", "petals"),
        new Scene("summer", "Summer solstice", "bokeh"),
        new Scene("autumn", "Autumn equinox", "leaves")
    };

    private HolidayScenes() {}

    public static Scene find(String id) {
        for (Scene scene : ALL) if (scene.id.equals(id)) return scene;
        return null;
    }
}
