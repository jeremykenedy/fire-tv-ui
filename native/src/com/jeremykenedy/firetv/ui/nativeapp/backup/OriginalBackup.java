package com.jeremykenedy.firetv.ui.nativeapp.backup;

import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original flat documents are reviewed as a whole before applying any included choice. */
public final class OriginalBackup {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final Map<String,String> TYPES = new LinkedHashMap<>();
    static {
        add("String", "app_order app_visibility channel_visibility custom_gradient custom_wallpaper_uri custom_video_uri display_order favorites_bg_mode folders icon_pack_package mode size status_bar_position status_bar_weather_loc status_bar_weather_unit subreddit_wallpaper_name widgets_config menu_animation menu_transition_style app_scroll_direction icon_focus_style menu_background_palette menu_overlay menu_overlay_palette input_names");
        add("Boolean", "blur_favorites_background blur_on_scroll enable_widgets featured_artwork_fit hide_favorites_background hide_status_bar_unless_focused hide_watch_next hide_watch_next_titles icon_shadow keep_dock_bottom mini_mode minimal_status_bar poster_mode show_all_apps_button text_outside_cards tinted_status_bar use_24h_clock white_border menu_animation_frozen menu_overlay_frozen");
        add("Int", "apps_per_row app_max_rows featured_rotation_seconds icon_radius menu_animation_speed menu_animation_intensity menu_animation_brightness icon_focus_background icon_focus_border menu_overlay_speed menu_overlay_intensity menu_overlay_opacity");
        add("StringSet", "featured_card_order featured_feed_order hidden_featured_cards featured_feed_rotation_seconds factory_icon_packages");
    }
    public final PreferenceSnapshot settings;
    public final Set<String> unsupported;
    private OriginalBackup(Map<String,Object> values,Set<String> unsupported) {
        settings=new PreferenceSnapshot(values); this.unsupported=Collections.unmodifiableSet(unsupported);
    }
    private static void add(String type,String keys) { for(String key:keys.split(" ")) TYPES.put(key,type); }
    public static OriginalBackup decode(byte[] bytes) {
        if(bytes==null||bytes.length>MAX_BYTES)throw invalid();
        final String text;
        try {text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(Exception bad){throw invalid();}
        Map<String,Object> root=LegacyJson.object(text,MAX_BYTES);
        if(root.containsKey("format")||root.containsKey("stores"))return null;
        if(root.isEmpty()||root.size()>512)throw invalid();
        Map<String,Object> values=new LinkedHashMap<>();Set<String> unsupported=new TreeSet<>();
        for(Map.Entry<String,Object> entry:root.entrySet()) {
            String key=entry.getKey();
            if(!key.matches("[A-Za-z0-9_]{1,128}"))throw invalid();
            String type=key.matches("color_[A-Za-z0-9_]{1,100}")?"Int":TYPES.get(key);
            if(type==null) {unsupported.add(key);continue;}
            Object value=entry.getValue();
            if("Int".equals(type)&&value instanceof BigDecimal) {
                try {value=((BigDecimal)value).intValueExact();}catch(ArithmeticException bad){throw invalid();}
            } else if("StringSet".equals(type)&&value instanceof List) {
                Set<String> set=new LinkedHashSet<>();
                if(((List<?>)value).size()>2048)throw invalid();
                for(Object item:(List<?>)value)if(!(item instanceof String)||!set.add((String)item))throw invalid();
                value=set;
            }
            if(!("Int".equals(type)&&value instanceof Integer||"Boolean".equals(type)&&value instanceof Boolean
                    ||"String".equals(type)&&value instanceof String||"StringSet".equals(type)&&value instanceof Set))throw invalid();
            values.put(key,value);
        }
        return new OriginalBackup(values,unsupported);
    }
    public String summary() {
        StringBuilder text=new StringBuilder("Original launcher backup for this TV.\n\nIncluded presentation choices: ");
        append(text,settings.values().keySet());
        if(!unsupported.isEmpty()) {text.append("\n\nUnsupported settings: ");append(text,unsupported);
            text.append("\n\nNothing can be imported from this file while these settings are included. Your current settings are unchanged.");}
        else text.append("\n\nOnly the included choices will change. Other local choices, accounts, passwords, locks and device settings remain. Personal artwork must already be available and selected on this TV. No media files or access permissions are imported.");
        return text.toString();
    }
    private static void append(StringBuilder out,Collection<String> keys) {
        if(keys.isEmpty()){out.append("none");return;}
        int count=0;for(String key:keys){if(count++>0)out.append(", ");out.append(key.replace('_',' '));}
    }
    public static byte[] read(InputStream input) throws IOException {
        if(input==null)throw new IOException("The selected document could not be opened.");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(bytes.size()+count>MAX_BYTES)throw new IOException("This original backup exceeds the 8 MB limit.");
            if(count==0){int next=input.read();if(next<0)break;if(bytes.size()==MAX_BYTES)throw new IOException("This original backup exceeds the 8 MB limit.");bytes.write(next);}
            else bytes.write(buffer,0,count);}
        return bytes.toByteArray();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Malformed original launcher backup. No settings were changed.");}
}
