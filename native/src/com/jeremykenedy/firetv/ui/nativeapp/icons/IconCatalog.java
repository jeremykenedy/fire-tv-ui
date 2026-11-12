package com.jeremykenedy.firetv.ui.nativeapp.icons;

import com.jeremykenedy.firetv.ui.preview.R;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Explicitly reviewed bundled artwork, independent of the device photo library. */
public final class IconCatalog {
    public static final class Entry {
        public final String id, label, category;
        public final int drawable;
        Entry(String id, String label, String category, int drawable) {
            this.id = id;
            this.label = label;
            this.category = category;
            this.drawable = drawable;
        }
    }

    public static final List<Entry> ALL = Collections.unmodifiableList(Arrays.asList(
            new Entry("bose", "Bose", "Audio", R.drawable.icon_bose),
            new Entry("denon", "Denon", "Audio", R.drawable.icon_denon),
            new Entry("harman-kardon", "Harman Kardon", "Audio", R.drawable.icon_harman_kardon),
            new Entry("insignia", "Insignia", "Audio", R.drawable.icon_insignia),
            new Entry("jbl", "JBL", "Audio", R.drawable.icon_jbl),
            new Entry("kenwood", "Kenwood", "Audio", R.drawable.icon_kenwood),
            new Entry("klipsch", "Klipsch", "Audio", R.drawable.icon_klipsch),
            new Entry("marantz", "Marantz", "Audio", R.drawable.icon_marantz),
            new Entry("onkyo", "Onkyo", "Audio", R.drawable.icon_onkyo),
            new Entry("pioneer", "Pioneer", "Audio", R.drawable.icon_pioneer),
            new Entry("sonos", "Sonos", "Audio", R.drawable.icon_sonos),
            new Entry("sony", "Sony", "Audio", R.drawable.icon_sony),
            new Entry("stereo-receiver", "Stereo Receiver", "Audio", R.drawable.icon_stereo_receiver),
            new Entry("yamaha", "Yamaha", "Audio", R.drawable.icon_yamaha),
            new Entry("laptop", "Laptop", "Computers", R.drawable.icon_laptop),
            new Entry("macbook", "MacBook", "Computers", R.drawable.icon_macbook),
            new Entry("pc", "PC", "Computers", R.drawable.icon_pc),
            new Entry("dreamcast", "Dreamcast", "Game consoles", R.drawable.icon_dreamcast),
            new Entry("nes", "NES", "Game consoles", R.drawable.icon_nes),
            new Entry("nintendo-64", "Nintendo 64", "Game consoles", R.drawable.icon_nintendo_64),
            new Entry("gamecube", "Nintendo GameCube", "Game consoles", R.drawable.icon_gamecube),
            new Entry("switch", "Nintendo Switch", "Game consoles", R.drawable.icon_switch),
            new Entry("switch-2", "Nintendo Switch 2", "Game consoles", R.drawable.icon_switch_2),
            new Entry("switch-lite", "Nintendo Switch Lite", "Game consoles", R.drawable.icon_switch_lite),
            new Entry("playstation-1", "PlayStation 1", "Game consoles", R.drawable.icon_playstation_1),
            new Entry("playstation-2", "PlayStation 2", "Game consoles", R.drawable.icon_playstation_2),
            new Entry("playstation-3", "PlayStation 3", "Game consoles", R.drawable.icon_playstation_3),
            new Entry("playstation-4", "PlayStation 4", "Game consoles", R.drawable.icon_playstation_4),
            new Entry("playstation-5", "PlayStation 5", "Game consoles", R.drawable.icon_playstation_5),
            new Entry("sega-genesis", "Sega Genesis", "Game consoles", R.drawable.icon_sega_genesis),
            new Entry("sega-master-system", "Sega Master System", "Game consoles", R.drawable.icon_sega_master_system),
            new Entry("sega-saturn", "Sega Saturn", "Game consoles", R.drawable.icon_sega_saturn),
            new Entry("snes", "SNES", "Game consoles", R.drawable.icon_snes),
            new Entry("wii", "Wii", "Game consoles", R.drawable.icon_wii),
            new Entry("wii-u", "Wii U", "Game consoles", R.drawable.icon_wii_u),
            new Entry("xbox", "Xbox", "Game consoles", R.drawable.icon_xbox),
            new Entry("xbox-360", "Xbox 360", "Game consoles", R.drawable.icon_xbox_360),
            new Entry("xbox-one", "Xbox One", "Game consoles", R.drawable.icon_xbox_one),
            new Entry("xbox-series-x", "Xbox Series X", "Game consoles", R.drawable.icon_xbox_series_x),
            new Entry("blu-ray", "Blu-ray", "TV and video", R.drawable.icon_blu_ray),
            new Entry("dvd", "DVD", "TV and video", R.drawable.icon_dvd),
            new Entry("laserdisc", "LaserDisc", "TV and video", R.drawable.icon_laserdisc),
            new Entry("lg", "LG", "TV and video", R.drawable.icon_lg),
            new Entry("samsung", "Samsung", "TV and video", R.drawable.icon_samsung),
            new Entry("vcr", "VCR", "TV and video", R.drawable.icon_vcr),
            new Entry("vizio", "Vizio", "TV and video", R.drawable.icon_vizio),
            new Entry("meta-quest", "Meta Quest", "VR", R.drawable.icon_meta_quest),
            new Entry("meta-vr", "Meta VR", "VR", R.drawable.icon_meta_vr)));

    private IconCatalog() {}

    public static Entry find(String id) {
        for (Entry entry : ALL) if (entry.id.equals(id)) return entry;
        return null;
    }
}
