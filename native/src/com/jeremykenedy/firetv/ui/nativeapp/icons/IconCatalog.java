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
            new Entry("denon", "Denon", "Audio", R.drawable.icon_denon),
            new Entry("harman-kardon", "Harman Kardon", "Audio", R.drawable.icon_harman_kardon),
            new Entry("jbl", "JBL", "Audio", R.drawable.icon_jbl),
            new Entry("kenwood", "Kenwood", "Audio", R.drawable.icon_kenwood),
            new Entry("pioneer", "Pioneer", "Audio", R.drawable.icon_pioneer),
            new Entry("sonos", "Sonos", "Audio", R.drawable.icon_sonos),
            new Entry("sony", "Sony", "Audio", R.drawable.icon_sony),
            new Entry("stereo-receiver", "Stereo Receiver", "Audio", R.drawable.icon_stereo_receiver),
            new Entry("laptop", "Laptop", "Computers", R.drawable.icon_laptop),
            new Entry("macbook", "MacBook", "Computers", R.drawable.icon_macbook),
            new Entry("pc", "PC", "Computers", R.drawable.icon_pc),
            new Entry("switch-2", "Nintendo Switch 2", "Game consoles", R.drawable.icon_switch_2),
            new Entry("switch-lite", "Nintendo Switch Lite", "Game consoles", R.drawable.icon_switch_lite),
            new Entry("xbox-series-x", "Xbox Series X", "Game consoles", R.drawable.icon_xbox_series_x),
            new Entry("blu-ray", "Blu-ray", "TV and video", R.drawable.icon_blu_ray),
            new Entry("dvd", "DVD", "TV and video", R.drawable.icon_dvd),
            new Entry("laserdisc", "LaserDisc", "TV and video", R.drawable.icon_laserdisc),
            new Entry("lg", "LG", "TV and video", R.drawable.icon_lg),
            new Entry("samsung", "Samsung", "TV and video", R.drawable.icon_samsung),
            new Entry("vcr", "VCR", "TV and video", R.drawable.icon_vcr),
            new Entry("meta-quest", "Meta Quest", "VR", R.drawable.icon_meta_quest),
            new Entry("meta-vr", "Meta VR", "VR", R.drawable.icon_meta_vr)));

    private IconCatalog() {}

    public static Entry find(String id) {
        for (Entry entry : ALL) if (entry.id.equals(id)) return entry;
        return null;
    }
}
