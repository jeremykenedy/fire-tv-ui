package com.jeremykenedy.firetv.ui.nativeapp.migration;

import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridOptions;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeTileOrder;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusOptions;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputEntry;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputOptions;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Produces validated native store changes without reading files or applying settings. */
public final class LegacyPresentationMapper {
    public static final String GRID = "native_app_grid", FOCUS = "native_focus", LAYOUT = "native_layout",
            CARDS = "native_cards", ICONS = "native_icons", INPUTS = "native_inputs", APPEARANCE = "source_appearance", ICON_PACKS = "native_icon_packs", FOLDERS = "native_folders";
    public static final String VERSION_KEY = "source_ui_presentation_mapping_version";
    public static final List<String> FILES = Collections.unmodifiableList(Arrays.asList(
            LauncherMigration.PREFERENCES, GRID, FOCUS, LAYOUT, CARDS, ICONS, INPUTS, APPEARANCE, ICON_PACKS, FOLDERS));
    private static final String FEED_GROUP = digest("preview\nfire-tv-ui\nprovider:top-level-feeds");

    public static final class Plan {
        public final Map<String, PreferenceSnapshot> before, after, patches;
        public final Set<String> mappedLegacyKeys, unmappedLegacyKeys;
        private Plan(Map<String, PreferenceSnapshot> before, Map<String, PreferenceSnapshot> after,
                Set<String> mapped, Set<String> unmapped, Map<String, PreferenceSnapshot> patches) {
            this.before = Collections.unmodifiableMap(new LinkedHashMap<>(before));
            this.after = Collections.unmodifiableMap(new LinkedHashMap<>(after));
            this.patches = Collections.unmodifiableMap(new LinkedHashMap<>(patches));
            mappedLegacyKeys = Collections.unmodifiableSet(new LinkedHashSet<>(mapped));
            unmappedLegacyKeys = Collections.unmodifiableSet(new LinkedHashSet<>(unmapped));
        }
    }
    private final Map<String, Object> legacy;
    private final PresentationMappingContext identities;
    private LegacyFolderMigration.Plan folders;
    private boolean completeLegacySnapshot;
    private final Map<String, PreferenceSnapshot> before = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> patches = new LinkedHashMap<>();
    private final Set<String> mapped = new LinkedHashSet<>();

    private LegacyPresentationMapper(PreferenceSnapshot legacy, PresentationMappingContext identities,
            Map<String, PreferenceSnapshot> nativeBefore) {
        if (legacy == null || identities == null || nativeBefore == null) throw invalid();
        this.legacy = legacy.values(); this.identities = identities;
        before.put(LauncherMigration.PREFERENCES, legacy);
        for (Map.Entry<String, PreferenceSnapshot> entry : nativeBefore.entrySet()) {
            if (!FILES.contains(entry.getKey()) || LauncherMigration.PREFERENCES.equals(entry.getKey()) || entry.getValue() == null)
                throw invalid();
        }
        for (String file : FILES) {
            if (LauncherMigration.PREFERENCES.equals(file)) continue;
            before.put(file, nativeBefore.containsKey(file) ? nativeBefore.get(file) : new PreferenceSnapshot(Collections.emptyMap()));
            patches.put(file, new LinkedHashMap<String, Object>());
        }
    }
    public static Plan plan(PreferenceSnapshot legacy, PresentationMappingContext identities,
            Map<String, PreferenceSnapshot> nativeBefore) {
        return plan(legacy, identities, nativeBefore, null, false);
    }
    static Plan plan(PreferenceSnapshot legacy, PresentationMappingContext identities,
            Map<String, PreferenceSnapshot> nativeBefore, LegacyFolderMigration.Plan folders, boolean privateLocksVerified) {
        return plan(legacy, identities, nativeBefore, folders, privateLocksVerified, false);
    }
    static Plan plan(PreferenceSnapshot legacy, PresentationMappingContext identities,
            Map<String, PreferenceSnapshot> nativeBefore, LegacyFolderMigration.Plan folders, boolean privateLocksVerified, boolean completeLegacySnapshot) {
        LegacyPresentationMapper mapper = new LegacyPresentationMapper(legacy, identities, nativeBefore);
        mapper.completeLegacySnapshot = completeLegacySnapshot;
        if (folders != null) {
            if (!legacy.values().containsKey("folders") || !folders.matches(legacy) || (folders.pinPresent && !privateLocksVerified)) throw invalid();
            mapper.folders = folders;
            mapper.put(FOLDERS, "schema_version", 1);
            mapper.put(FOLDERS, "folders", LegacyFolderMigration.encode(folders.folders));
            mapper.put(FOLDERS, "legacy_migration_version", 1);
            mapper.mapped.add("folders");
            if (folders.pinPresent) mapper.mapped.add("folder_lock_pin");
        }
        mapper.grid(); mapper.focus(); mapper.cards(); mapper.iconsAndVisibility(); mapper.inputs(); mapper.appearance(); mapper.contentMode();
        return mapper.finish();
    }
    public static Set<String> directlyConsumedPresentationKeys(Map<String, ?> legacy) {
        Set<String> accepted = new LinkedHashSet<>();
        Object transition = legacy.get("menu_transition_style");
        if (transition instanceof String && Arrays.asList("default", "none", "fade", "slide").contains(transition))
            accepted.add("menu_transition_style");
        for (String key : new String[]{"use_24h_clock", "featured_artwork_fit", "icon_shadow",
                "mini_mode", "blur_on_scroll", "keep_dock_bottom", "override_back_button",
                "blur_favorites_background"})
            if (legacy.get(key) instanceof Boolean) accepted.add(key);
        Object radius = legacy.get("icon_radius");
        if (radius instanceof Integer && (Integer) radius >= 0 && (Integer) radius <= 20) accepted.add("icon_radius");
        if (legacy.get("color_back_color_argb") instanceof Integer) accepted.add("color_back_color_argb");
        Object opacity = legacy.get("color_back_color_opacity");
        if (opacity instanceof Integer && (Integer) opacity >= 0 && (Integer) opacity <= 100)
            accepted.add("color_back_color_opacity");
        if (legacy.get("minimal_status_bar") instanceof Boolean) {
            accepted.add("minimal_status_bar");
            for (String key : new String[]{"hide_status_bar_unless_focused", "tinted_status_bar"})
                if (legacy.get(key) instanceof Boolean) accepted.add(key);
            Object position = legacy.get("status_bar_position");
            if ("Left".equals(position) || "Right".equals(position)) accepted.add("status_bar_position");
        }
        return accepted;
    }
    public static Map<String, Set<String>> ownedNativeKeys(Set<String> legacyKeys, PresentationMappingContext identities) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String file : FILES) if (!file.equals(LauncherMigration.PREFERENCES)) result.put(file, new LinkedHashSet<String>());
        for (String key : legacyKeys) {
            if (LegacyAppearanceMapper.keys().containsKey(key)) result.get(APPEARANCE).add(LegacyAppearanceMapper.keys().get(key));
            if (key.equals("mode")) { result.get(APPEARANCE).add("wallpaper_mode"); result.get(APPEARANCE).add("base"); result.get(APPEARANCE).add("scene"); }
            if (key.equals("custom_gradient")) result.get(APPEARANCE).add("legacy_gradient");
            if (key.equals("icon_pack_package")) result.get(ICON_PACKS).add("package");
            if (key.equals("apps_per_row")) result.get(GRID).add("columns");
            if (key.equals("app_max_rows")) result.get(GRID).add("rows");
            if (key.equals("app_scroll_direction")) result.get(GRID).add("horizontal");
            if (key.equals("display_order")) { result.get(GRID).add("order"); result.get(GRID).add("home_order"); result.get(INPUTS).add("order"); }
            if (key.equals("app_visibility")) {
                result.get(GRID).add("hidden");
                result.get(CARDS).add("hidden_packages");
                for (String id : identities.inputs.values()) result.get(INPUTS).add("hidden:" + id);
            }
            if (key.equals("show_all_apps_button")) result.get(GRID).add("show_all_apps_button");
            if (key.equals("white_border") || key.equals("icon_focus_style")) {
                result.get(FOCUS).add("border_enabled");
                result.get(FOCUS).add(FocusOptions.LEGACY_APP_DEFAULT);
            }
            if (key.equals("icon_focus_style")) result.get(FOCUS).add("fill_enabled");
            if (key.equals("icon_focus_background")) result.get(FOCUS).add("fill_rgb");
            if (key.equals("icon_focus_border")) result.get(FOCUS).add("border_rgb");
            if (key.equals("featured_rotation_seconds")) result.get(CARDS).add("global_seconds");
            if (key.equals("featured_feed_rotation_seconds")) result.get(CARDS).add("feed_seconds");
            if (key.equals("hidden_featured_cards")) result.get(CARDS).addAll(Arrays.asList("hidden_items", "hidden_series"));
            if (key.equals("featured_card_order")) result.get(CARDS).add("item_order");
            if (key.equals("featured_feed_order")) result.get(LAYOUT).add("order");
            if (key.equals("hide_watch_next") || key.equals("enable_widgets")) result.get(LAYOUT).add("mode");
            if (key.equals("app_visibility") || key.equals("factory_icon_packages")) {
                result.get(ICONS).addAll(identities.components.values());
                for (String id : identities.inputs.values()) result.get(ICONS).add("input:" + id);
            }
            if (key.equals("input_names")) for (String id : identities.inputs.values()) result.get(INPUTS).add("name:" + id);
        }
        Map<String, Set<String>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : result.entrySet()) immutable.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        return Collections.unmodifiableMap(immutable);
    }
    private void contentMode() {
        if (legacy.containsKey("hide_watch_next")) bool("hide_watch_next");
        if (legacy.containsKey("enable_widgets")) bool("enable_widgets");
        // Without both explicit choices the inherited widget default cannot be inferred from its stored type.
        if (!legacy.containsKey("hide_watch_next") || !legacy.containsKey("enable_widgets")) return;
        boolean cards = !bool("hide_watch_next"), widgets = bool("enable_widgets");
        // Widget visibility with a visible recommendation feed remains unverified.
        if (widgets) return;
        put(LAYOUT, "mode", cards ? "CARDS_ONLY" : "NONE");
        mapped.add("hide_watch_next"); mapped.add("enable_widgets");
    }
    private void appearance() {
        for (Map.Entry<String, Object> entry : LegacyAppearanceMapper.map(legacy, mapped, identities).entrySet()) put(APPEARANCE, entry.getKey(), entry.getValue());
    }
    private void inputs() {
        if (!present("input_names")) return;
        Map<String, Object> names = LegacyJson.object(string("input_names"));
        if (names.size() > 64) throw invalid();
        for (Map.Entry<String, Object> entry : names.entrySet()) {
            String id = identities.inputs.get(entry.getKey());
            if (id == null) throw missingInput();
            if (!(entry.getValue() instanceof String)) throw invalid();
            String name = (String) entry.getValue();
            if (name.isEmpty() || name.length() > 60 || !name.trim().equals(name)) throw invalid();
            for (int index = 0; index < name.length(); index++) if (Character.isISOControl(name.charAt(index))) throw invalid();
            put(INPUTS, "name:" + id, name);
            put(INPUTS, "known:" + id, true);
        }
    }
    private void grid() {
        if (present("show_all_apps_button")) put(GRID, "show_all_apps_button", bool("show_all_apps_button"));
        if (present("apps_per_row")) put(GRID, "columns", number("apps_per_row", 1, 20));
        if (present("app_max_rows")) put(GRID, "rows", number("app_max_rows", 0, 6));
        if (present("app_scroll_direction")) {
            String value = string("app_scroll_direction");
            if (!Arrays.asList("vertical", "horizontal").contains(value)) throw invalid();
            put(GRID, "horizontal", value.equals("horizontal"));
        }
        if (legacy.containsKey("display_order")) {
            List<String> ordered = new ArrayList<>(), inputOrder = new ArrayList<>(), homeOrder = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String value = string("display_order");
            if (!value.isEmpty()) for (String key : value.split(",", -1)) {
                if (key.startsWith("folder://")) {
                    String id = key.substring("folder://".length());
                    if (folders == null || folders.folders.find(id) == null || !seen.add("folder:" + id)) throw invalid();
                    homeOrder.add("folder:" + id);
                } else if (key.startsWith("tvinput_name:")) {
                    String id = identities.inputs.get(key);
                    if (id == null) throw missingInput();
                    if (!seen.add("input:" + id)) throw invalid();
                    inputOrder.add(id); homeOrder.add("input:" + id);
                } else {
                    String component = identities.component(key);
                    if (!seen.add("app:" + component)) throw invalid();
                    ordered.add(component); homeOrder.add(component);
                }
            }
            if (ordered.size() > 2048) throw invalid();
            if (!ordered.isEmpty() || value.isEmpty()) put(GRID, "order", String.join("\n", ordered));
            if (!inputOrder.isEmpty()) put(INPUTS, "order", new InputOptions(inputOrder, Collections.<String>emptySet()).savedOrder());
            put(GRID, "home_order", String.join("\n", homeOrder));
            mapped.add("display_order");
        }
    }
    private void focus() {
        String style = legacy.containsKey("icon_focus_style") ? string("icon_focus_style") : "default";
        if (!Arrays.asList("default", "border", "fill").contains(style)) throw invalid();
        if (legacy.containsKey("icon_focus_border")) color("icon_focus_border");
        if (legacy.containsKey("icon_focus_background")) color("icon_focus_background");
        if (completeLegacySnapshot && !legacy.isEmpty() && style.equals("default") && !legacy.containsKey("white_border")
                && !legacy.containsKey("icon_focus_border") && !legacy.containsKey("icon_focus_background")
                && !FocusOptions.hasExplicitSettings(before.get(FOCUS).values())) {
            put(FOCUS, FocusOptions.LEGACY_APP_DEFAULT, true);
            if (legacy.containsKey("icon_focus_style")) mapped.add("icon_focus_style");
        }
        if (!style.equals("default")) {
            mapped.add("icon_focus_style");
            put(FOCUS, "border_enabled", true);
            put(FOCUS, "fill_enabled", style.equals("fill"));
            put(FOCUS, "border_rgb", present("icon_focus_border") ? color("icon_focus_border") : 0xffffff);
            put(FOCUS, "fill_rgb", present("icon_focus_background") ? color("icon_focus_background") : 0x263a5c);
            put(FOCUS, "border_opacity", 100);
            put(FOCUS, "fill_opacity", 100);
        }
        // Unverified default combinations and their custom colors remain in legacy recovery.
        if (legacy.containsKey("white_border")) {
            boolean border = bool("white_border"); mapped.add("white_border");
            if (style.equals("default")) put(FOCUS, "border_enabled", border);
        }
    }
    private void cards() {
        CardOptions options = currentCards();
        if (present("featured_rotation_seconds")) {
            options = options.withGlobalSeconds(number("featured_rotation_seconds", 0, 86400));
            put(CARDS, "global_seconds", options.globalSeconds);
        }
        if (present("featured_feed_rotation_seconds")) {
            Set<String> seen = new HashSet<>();
            Set<String> timers = strings("featured_feed_rotation_seconds", 256);
            CardOptions target = CardOptions.defaults();
            for (String timer : sorted(timers)) {
                if (!timer.matches("[a-f0-9]{64}:(0|[1-9][0-9]{0,4})")) throw invalid();
                String hash = timer.substring(0, 64);
                if (!seen.add(hash)) throw invalid();
                PresentationMappingContext.Feed feed = identities.feed(hash);
                target = target.withFeedSeconds(feed.nativeKey, Integer.parseInt(timer.substring(65)));
            }
            put(CARDS, "feed_seconds", target.backupValues().get("feed_seconds"));
        }
        if (present("hidden_featured_cards")) {
            CardOptions hidden = CardOptions.defaults();
            for (String hash : sorted(strings("hidden_featured_cards", 512))) {
                if (!PresentationMappingContext.hash(hash)) throw invalid();
                PresentationMappingContext.Item item = identities.item(hash);
                hidden = item.series ? hidden.hideSeries(item.source, item.nativeKey, item.title)
                        : hidden.hide(item.source, item.nativeKey, item.title);
                if (item.aliasSource != null) hidden = hidden.hide(item.aliasSource, item.aliasKey, item.title);
            }
            put(CARDS, "hidden_items", hidden.backupValues().get("hidden_items"));
            put(CARDS, "hidden_series", hidden.backupValues().get("hidden_series"));
            put(CARDS, "schema_version", 2);
        }
        if (present("featured_feed_order")) {
            Map<Integer, String> ranks = ranked(strings("featured_feed_order", 4096), FEED_GROUP);
            List<String> order = new ArrayList<>();
            for (String hash : ranks.values()) order.add("channel:" + identities.feed(hash).nativeKey);
            for (String key : lines(before.get(LAYOUT).values().get("order"), 512)) if (!order.contains(key)) order.add(key);
            put(LAYOUT, "order", String.join("\n", order));
        }
        if (present("featured_card_order")) {
            Map<String, Set<String>> records = new TreeMap<>();
            for (String record : strings("featured_card_order", 4096)) {
                if (!validRank(record)) throw invalid();
                String feed = record.substring(0, 64);
                if (!records.containsKey(feed)) records.put(feed, new LinkedHashSet<String>());
                records.get(feed).add(record);
            }
            CardOptions ordered = CardOptions.defaults();
            for (Map.Entry<String, Set<String>> group : records.entrySet()) {
                PresentationMappingContext.Feed feed = identities.feed(group.getKey());
                List<String> keys = new ArrayList<>();
                for (String hash : ranked(group.getValue(), group.getKey()).values()) {
                    PresentationMappingContext.Item item = identities.item(hash);
                    if (item.series || !item.source.equals(feed.source)) throw invalid();
                    keys.add(item.nativeKey);
                }
                ordered = ordered.withItemOrder(feed.source, feed.nativeKey, keys);
            }
            put(CARDS, "item_order", ordered.backupValues().get("item_order"));
            put(CARDS, "schema_version", 2);
        }
    }
    private void iconsAndVisibility() {
        if (present("icon_pack_package")) {
            String pack = string("icon_pack_package");
            if (pack.trim().isEmpty()) put(ICON_PACKS, "package", "original");
            else if (pack.equals("builtin:clean_line")) put(ICON_PACKS, "package", "clean-line");
            else if (pack.equals("builtin:muted_solid")) put(ICON_PACKS, "package", "muted-solid");
            else {
                if (!identities.advertisedIconPacks.contains(pack)) {
                    throw new IllegalArgumentException("The selected legacy icon pack is not verified as supported and available.");
                }
                put(ICON_PACKS, "package", pack);
            }
        }
        Set<String> factories = legacy.containsKey("factory_icon_packages") ? strings("factory_icon_packages", 2048)
                : Collections.<String>emptySet();
        Map<String, String> customIcons = new LinkedHashMap<>();
        Set<String> hidden = new LinkedHashSet<>(lines(before.get(GRID).values().get("hidden"), 2048));
        boolean changedHidden = false, changedCardPackages = false, completeVisibility = true;
        Set<String> hiddenCardPackages = new LinkedHashSet<>(currentCards().hiddenPackages);
        Map<String, Boolean> resolvedCardPackages = new LinkedHashMap<>();
        Map<String, Boolean> resolvedVisibility = new LinkedHashMap<>();
        if (legacy.containsKey("app_visibility")) {
            Map<String, Object> visibility = LegacyJson.object(string("app_visibility"));
            if (visibility.size() > 2048) throw invalid();
            for (Map.Entry<String, Object> entry : visibility.entrySet()) {
                if (!(entry.getValue() instanceof Map)) throw invalid();
                @SuppressWarnings("unchecked") Map<String, Object> choices = (Map<String, Object>) entry.getValue();
                for (Map.Entry<String, Object> choice : choices.entrySet()) {
                    if (choice.getKey().equals("hidden")) {
                        if (!(choice.getValue() instanceof Boolean)) throw invalid();
                        if (entry.getKey().startsWith("tvinput_name:")) {
                            String id = identities.inputs.get(entry.getKey());
                            if (id == null) throw missingInput();
                            put(INPUTS, "hidden:" + id, choice.getValue());
                            put(INPUTS, "known:" + id, true);
                        } else {
                            String component = identities.component(entry.getKey());
                            if (resolvedVisibility.containsKey(component)
                                    && !resolvedVisibility.get(component).equals(choice.getValue())) throw invalid();
                            resolvedVisibility.put(component, (Boolean) choice.getValue());
                            if ((Boolean) choice.getValue()) hidden.add(component); else hidden.remove(component);
                            changedHidden = true;
                        }
                    } else if (choice.getKey().equals("hideWatchNext") && !entry.getKey().startsWith("tvinput_name:")) {
                        if (!(choice.getValue() instanceof Boolean)) throw invalid();
                        String source = cardPackage(entry.getKey());
                        if (resolvedCardPackages.containsKey(source)
                                && !resolvedCardPackages.get(source).equals(choice.getValue())) throw invalid();
                        resolvedCardPackages.put(source, (Boolean) choice.getValue());
                        if ((Boolean) choice.getValue()) hiddenCardPackages.add(source); else hiddenCardPackages.remove(source);
                        changedCardPackages = true;
                    } else if (choice.getKey().equals("customIconUri")) {
                        if (!(choice.getValue() instanceof String)) throw invalid();
                        String uri = (String) choice.getValue();
                        String selected = identities.icons.get(uri);
                        if (selected == null) throw new IllegalArgumentException("A custom icon needs a verified retained or imported native image.");
                        if (factories.contains(entry.getKey())) throw invalid();
                        String component = identities.tile(entry.getKey());
                        if (customIcons.put(component, selected) != null) throw invalid();
                    } else {
                        // Unknown properties remain in legacy recovery until their behavior is supported.
                        completeVisibility = false;
                    }
                }
            }
            if (completeVisibility) mapped.add("app_visibility");
        }
        if (changedHidden) put(GRID, "hidden", String.join("\n", hidden));
        if (changedCardPackages) put(CARDS, "hidden_packages", CardOptions.encodePackages(hiddenCardPackages));
        for (Map.Entry<String, String> entry : customIcons.entrySet()) put(ICONS, entry.getKey(), entry.getValue());
        if (present("factory_icon_packages")) {
            Set<String> seen = new HashSet<>();
            for (String key : sorted(factories)) {
                String component = identities.tile(key);
                if (!seen.add(component) || customIcons.containsKey(component)) throw invalid();
                put(ICONS, component, "factory");
            }
        }
    }
    private Plan finish() {
        Map<String, PreferenceSnapshot> after = new LinkedHashMap<>();
        for (String file : FILES) {
            Map<String, Object> next = new LinkedHashMap<>(before.get(file).values());
            if (file.equals(LauncherMigration.PREFERENCES)) {
                Object version = next.get(VERSION_KEY);
                if (version != null && (!(version instanceof Integer) || (Integer) version < 0 || (Integer) version > 1)) throw invalid();
                next.put(VERSION_KEY, 1);
            } else {
                for (Map.Entry<String, Object> entry : patches.get(file).entrySet()) {
                    if (next.containsKey(entry.getKey()) && !next.get(entry.getKey()).equals(entry.getValue())) {
                        throw new IllegalArgumentException("A native presentation setting conflicts with its legacy mapping.");
                    }
                    next.put(entry.getKey(), entry.getValue());
                }
            }
            after.put(file, new PreferenceSnapshot(next));
        }
        validateTargets(after);
        Set<String> unmapped = new LinkedHashSet<>(legacy.keySet());
        unmapped.removeAll(mapped); unmapped.remove(VERSION_KEY);
        Map<String, PreferenceSnapshot> patchSnapshots = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : patches.entrySet()) {
            if (!entry.getValue().isEmpty()) patchSnapshots.put(entry.getKey(), new PreferenceSnapshot(entry.getValue()));
        }
        return new Plan(before, after, mapped, unmapped, patchSnapshots);
    }
    private void validateTargets(Map<String, PreferenceSnapshot> values) {
        Map<String, Object> grid = values.get(GRID).values();
        new AppGridOptions(nativeInt(grid, "columns", 5), nativeInt(grid, "rows", 0), nativeBool(grid, "horizontal", false),
                lines(grid.get("order"), 2048), new LinkedHashSet<>(lines(grid.get("hidden"), 2048)),
                new LinkedHashSet<>(lines(grid.get("favorites"), 2048)));
        new HomeTileOrder(lines(grid.get("home_order"), 4096));
        Map<String, Object> focus = values.get(FOCUS).values();
        FocusOptions defaults = FocusOptions.defaults();
        nativeBool(focus, FocusOptions.LEGACY_APP_DEFAULT, false);
        new FocusOptions(nativeInt(focus, "duration_millis", defaults.durationMillis), nativeInt(focus, "growth_percent", defaults.growthPercent),
                nativeBool(focus, "border_enabled", defaults.borderEnabled), nativeInt(focus, "border_rgb", defaults.borderRgb),
                nativeInt(focus, "border_opacity", defaults.borderOpacity), nativeBool(focus, "fill_enabled", defaults.fillEnabled),
                nativeInt(focus, "fill_rgb", defaults.fillRgb), nativeInt(focus, "fill_opacity", defaults.fillOpacity));
        Map<String, Object> layout = new LinkedHashMap<>(LayoutOptions.defaults(320, 180).backupValues());
        for (String key : new ArrayList<>(layout.keySet())) if (values.get(LAYOUT).values().containsKey(key)) layout.put(key, values.get(LAYOUT).values().get(key));
        LayoutOptions.fromBackup(layout);
        cardOptions(values.get(CARDS));
        LegacyAppearanceMapper.validateNative(values.get(APPEARANCE).values());
        nativeBool(grid, "show_all_apps_button", false);
        validateInputs(values.get(INPUTS).values());
    }
    private static void validateInputs(Map<String, Object> values) {
        Object storedOrder = values.get("order");
        if (storedOrder != null && !(storedOrder instanceof String)) throw invalid();
        List<String> order = InputOptions.parseOrder((String) storedOrder);
        Set<String> hidden = new LinkedHashSet<>(), known = new LinkedHashSet<>(order);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String prefix = null;
            for (String candidate : new String[]{"hidden:", "known:", "name:", "factory:"}) if (entry.getKey().startsWith(candidate)) prefix = candidate;
            if (prefix == null) continue;
            String id = entry.getKey().substring(prefix.length());
            if (!InputEntry.validId(id)) throw invalid();
            known.add(id);
            if (prefix.equals("hidden:") || prefix.equals("known:")) {
                if (!(entry.getValue() instanceof Boolean)) throw invalid();
                if (prefix.equals("hidden:") && (Boolean) entry.getValue()) hidden.add(id);
            } else if (!(entry.getValue() instanceof String)) throw invalid();
        }
        new InputOptions(order, hidden);
        if (known.size() > InputOptions.MAX_INPUTS) throw invalid();
    }
    private static String cardPackage(String identity) {
        int slash = identity.indexOf('/');
        if (slash >= 0) {
            if (!com.jeremykenedy.firetv.ui.nativeapp.folders.FolderOptions.validComponent(identity)) throw invalid();
            identity = identity.substring(0, slash);
        }
        CardOptions.validatePackage(identity);
        return identity;
    }
    private CardOptions currentCards() { return cardOptions(before.get(CARDS)); }
    private static CardOptions cardOptions(PreferenceSnapshot snapshot) {
        Map<String, Object> values = new LinkedHashMap<>(CardOptions.defaults().backupValues());
        for (String key : new ArrayList<>(values.keySet())) if (snapshot.values().containsKey(key)) values.put(key, snapshot.values().get(key));
        return CardOptions.fromBackup(values);
    }
    private static IllegalArgumentException missingInput() {
        return new IllegalArgumentException("A saved input has no unique verified platform identity. Its original settings were kept. Restore input discovery before upgrading.");
    }
    private boolean present(String key) { if (!legacy.containsKey(key)) return false; mapped.add(key); return true; }
    private String string(String key) { Object value = legacy.get(key); if (!(value instanceof String)) throw invalid(); return (String) value; }
    private boolean bool(String key) { Object value = legacy.get(key); if (!(value instanceof Boolean)) throw invalid(); return (Boolean) value; }
    private int color(String key) { Object value = legacy.get(key); if (!(value instanceof Integer)) throw invalid(); return (Integer) value & 0xffffff; }
    private int number(String key, int min, int max) {
        Object value = legacy.get(key);
        if (!(value instanceof Integer) || (Integer) value < min || (Integer) value > max) throw invalid();
        return (Integer) value;
    }
    private Set<String> strings(String key, int max) {
        Object value = legacy.get(key);
        if (!(value instanceof Set) || ((Set<?>) value).size() > max) throw invalid();
        Set<String> result = new LinkedHashSet<>();
        for (Object item : (Set<?>) value) { if (!(item instanceof String)) throw invalid(); result.add((String) item); }
        return result;
    }
    private void put(String file, String key, Object value) {
        Map<String, Object> values = patches.get(file);
        if (values.containsKey(key) && !values.get(key).equals(value)) throw invalid();
        values.put(key, value);
    }
    private static List<String> lines(Object value, int max) {
        if (value == null) return Collections.emptyList();
        if (!(value instanceof String) || ((String) value).length() > max * 513) throw invalid();
        if (((String) value).isEmpty()) return Collections.emptyList();
        List<String> result = Arrays.asList(((String) value).split("\n", -1));
        if (result.size() > max || new HashSet<>(result).size() != result.size()) throw invalid();
        return result;
    }
    private static Map<Integer, String> ranked(Set<String> values, String group) {
        Map<Integer, String> ranks = new TreeMap<>(); Set<String> items = new HashSet<>();
        for (String record : values) {
            if (!validRank(record) || !record.substring(0, 64).equals(group)) throw invalid();
            int rank = Integer.parseInt(record.substring(65, 69));
            String hash = record.substring(70);
            if (ranks.put(rank, hash) != null || !items.add(hash)) throw invalid();
        }
        return ranks;
    }
    private static boolean validRank(String value) {
        return value != null && value.matches("[a-f0-9]{64}:[0-9]{4}:[a-f0-9]{64}")
                && Integer.parseInt(value.substring(65, 69)) < 4096;
    }
    private static List<String> sorted(Set<String> values) { List<String> result = new ArrayList<>(values); Collections.sort(result); return result; }
    private static int nativeInt(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key); if (value == null) return fallback; if (!(value instanceof Integer)) throw invalid(); return (Integer) value;
    }
    private static boolean nativeBool(Map<String, Object> values, String key, boolean fallback) {
        Object value = values.get(key); if (value == null) return fallback; if (!(value instanceof Boolean)) throw invalid(); return (Boolean) value;
    }
    private static String digest(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte item : bytes) value.append(String.format(Locale.ROOT, "%02x", item & 255));
            return value.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Unsupported or malformed legacy presentation mapping. No values were applied."); }
}
