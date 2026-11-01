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
            new Entry("jbl", "JBL", "Audio", R.drawable.icon_jbl),
            new Entry("sonos", "Sonos", "Audio", R.drawable.icon_sonos),
            new Entry("stereo-receiver", "Stereo receiver", "Audio", R.drawable.icon_stereo_receiver),
            new Entry("laptop", "Laptop", "Computers", R.drawable.icon_laptop),
            new Entry("pc", "PC", "Computers", R.drawable.icon_pc),
            new Entry("lg", "LG", "TV and video", R.drawable.icon_lg),
            new Entry("samsung", "Samsung", "TV and video", R.drawable.icon_samsung),
            new Entry("vcr", "VCR", "TV and video", R.drawable.icon_vcr)));

    private IconCatalog() {}

    public static Entry find(String id) {
        for (Entry entry : ALL) if (entry.id.equals(id)) return entry;
        return null;
    }
}
