package com.jeremykenedy.firetv.ui;

/** Optional decorative palettes, not religious symbols or an automatic calendar. */
public final class HolidayThemes {
    public static final class Theme {
        public final String id, name, family, motif;
        public final int dark, middle, light, accent;

        Theme(String id, String name, String family, String motif, int dark, int middle,
                int light, int accent) {
            this.id = id; this.name = name; this.family = family; this.motif = motif;
            this.dark = dark; this.middle = middle; this.light = light; this.accent = accent;
        }
    }

    private static Theme theme(String id, String name, String family, String motif,
            int dark, int middle, int light, int accent) {
        return new Theme(id, name, family, motif, dark, middle, light, accent);
    }

    public static final Theme[] ALL = {
        theme("new_year", "New Year", "Seasonal and cultural", "stars", 0xff121323, 0xff303453, 0xfff2dfb4, 0xffd9b96e),
        theme("lunar_new_year", "Lunar New Year", "Seasonal and cultural", "lanterns", 0xff230f17, 0xff642f35, 0xfff0d4a2, 0xffdfb967),
        theme("valentine", "Valentine's Day", "Seasonal and cultural", "hearts_floating", 0xff21121c, 0xff513042, 0xfff2c4ce, 0xffd38b9f),
        theme("st_patrick", "St. Patrick's Day", "Christian and cultural", "particles", 0xff0d211a, 0xff28513c, 0xffd7e8b8, 0xffb9c887),
        theme("easter", "Easter", "Christian", "petals", 0xff172125, 0xff3b5052, 0xfff1dbe0, 0xffcba9ca),
        theme("christmas", "Christmas", "Christian", "snowflakes", 0xff11201d, 0xff294640, 0xfff0e6d2, 0xffd2b17c),
        theme("epiphany", "Epiphany", "Christian", "stars", 0xff15192c, 0xff343d62, 0xffe5e0cb, 0xffd3bb81),
        theme("ramadan", "Ramadan", "Muslim", "lanterns", 0xff101d29, 0xff29495a, 0xffeae1c4, 0xffd5bd84),
        theme("eid_fitr", "Eid al-Fitr", "Muslim", "bokeh", 0xff12251f, 0xff31584a, 0xffe8e4cb, 0xffbfbd7e),
        theme("eid_adha", "Eid al-Adha", "Muslim", "lanterns", 0xff1b192b, 0xff443b59, 0xffe5d9bc, 0xffcbb084),
        theme("hanukkah", "Hanukkah", "Jewish", "lanterns", 0xff101b2e, 0xff2b4671, 0xffe3e9f2, 0xffacbfdc),
        theme("passover", "Passover", "Jewish", "petals", 0xff18241e, 0xff3d5140, 0xffeee5d1, 0xffc8bb8a),
        theme("rosh_hashanah", "Rosh Hashanah", "Jewish", "leaves", 0xff241a16, 0xff5a3f2a, 0xfff1ddb1, 0xffd4b16b),
        theme("yom_kippur", "Yom Kippur", "Jewish", "silk", 0xff1b2129, 0xff3f4a59, 0xffe7e9ed, 0xffbdc8d7),
        theme("sukkot", "Sukkot", "Jewish", "leaves", 0xff16251d, 0xff3b5740, 0xffe9deba, 0xffc1bb80),
        theme("purim", "Purim", "Jewish", "particles", 0xff25162c, 0xff51365f, 0xffe9c6dc, 0xffd7accd),
        theme("diwali", "Diwali", "Hindu, Jain and Sikh celebrations", "lanterns", 0xff24151e, 0xff653e39, 0xfff5d5a4, 0xffe0ae6a),
        theme("holi", "Holi", "Hindu", "petals", 0xff23162d, 0xff4d365d, 0xffeec5df, 0xffdb9fca),
        theme("navratri", "Navratri", "Hindu", "silk", 0xff211a2e, 0xff544266, 0xffe6d5f2, 0xffc7a3dd),
        theme("janmashtami", "Janmashtami", "Hindu", "petals", 0xff122629, 0xff2c565b, 0xffe6deb0, 0xffc8bc6e),
        theme("ganesh_chaturthi", "Ganesh Chaturthi", "Hindu", "lanterns", 0xff251a17, 0xff614237, 0xffefdbb9, 0xffd5a777),
        theme("vaisakhi", "Vaisakhi", "Sikh", "petals", 0xff261f15, 0xff625133, 0xfff0dfb1, 0xffdfb86c),
        theme("gurpurab", "Gurpurab", "Sikh", "lanterns", 0xff192131, 0xff3a4b6c, 0xffe9d7b9, 0xffcdb27e),
        theme("vesak", "Vesak", "Buddhist", "petals", 0xff18251f, 0xff3a584b, 0xffe8dfc8, 0xffb9c397),
        theme("losar", "Losar", "Tibetan Buddhist and cultural", "snowflakes", 0xff172439, 0xff384f6f, 0xffdbe7ee, 0xffa8c6d7),
        theme("obon", "Obon", "Japanese Buddhist and cultural", "lanterns", 0xff201923, 0xff4e394a, 0xffeed6bf, 0xffd2ab89),
        theme("mahavir_jayanti", "Mahavir Jayanti", "Jain", "petals", 0xff20231c, 0xff4f5745, 0xffeee4ca, 0xffc3c39a),
        theme("paryushan", "Paryushan", "Jain", "silk", 0xff1a2226, 0xff3d4b50, 0xffe1e6e3, 0xffb7c8c1),
        theme("naw_ruz", "Naw-Ruz", "Baha'i", "petals", 0xff142521, 0xff34564c, 0xffe6dec2, 0xffbdc69d),
        theme("ridvan", "Ridvan", "Baha'i", "petals", 0xff211b27, 0xff4c3f57, 0xffedcfdc, 0xffd1abbf),
        theme("nowruz", "Nowruz", "Persian, Zoroastrian and cultural", "petals", 0xff11251f, 0xff345745, 0xffe9dfbf, 0xffc7c48f),
        theme("mehrgan", "Mehregan", "Zoroastrian and Persian", "leaves", 0xff261c17, 0xff5c422e, 0xffedd3a6, 0xffd8b078),
        theme("kwanzaa", "Kwanzaa", "African American and Pan-African", "lanterns", 0xff17211c, 0xff3d4e35, 0xffe8d4b3, 0xffccaa7b),
        theme("halloween", "Halloween", "Seasonal and cultural", "leaves", 0xff201927, 0xff49324d, 0xffe3bd92, 0xffd89e61),
        theme("thanksgiving", "Thanksgiving", "Seasonal and cultural", "leaves", 0xff231b16, 0xff584334, 0xffecd7b4, 0xffcba476),
        theme("independence", "Independence Day", "United States", "stars", 0xff131e31, 0xff334969, 0xffe9e0d7, 0xffcaa9a5),
        theme("juneteenth", "Juneteenth", "United States", "stars", 0xff1d1b2a, 0xff473a52, 0xffe6ccd1, 0xffcf9caa),
        theme("winter", "Winter solstice", "Seasonal and cultural", "snow", 0xff11202b, 0xff2e4b5e, 0xffdce9ed, 0xffa9cbd8),
        theme("spring", "Spring equinox", "Seasonal and cultural", "petals", 0xff19251f, 0xff405745, 0xffeddfe1, 0xffc2bfa5),
        theme("summer", "Summer solstice", "Seasonal and cultural", "bokeh", 0xff1f2429, 0xff4b5253, 0xffe6dbb5, 0xffd2bf83),
        theme("autumn", "Autumn equinox", "Seasonal and cultural", "leaves", 0xff271c17, 0xff624432, 0xffeed9b6, 0xffd1a97b)
    };

    private HolidayThemes() {}

    public static Theme find(String id) {
        for (Theme theme : ALL) if (theme.id.equals(id)) return theme;
        return null;
    }

    public static String[] names() {
        String[] names = new String[ALL.length];
        for (int i = 0; i < names.length; i++) names[i] = ALL[i].name + " (" + ALL[i].family + ")";
        return names;
    }
}
