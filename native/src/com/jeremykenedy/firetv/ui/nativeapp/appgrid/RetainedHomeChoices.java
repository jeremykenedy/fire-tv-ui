package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputEntry;
import com.jeremykenedy.firetv.ui.nativeapp.inputs.InputOptions;
import com.jeremykenedy.firetv.ui.nativeapp.migration.LegacyJson;
import java.math.BigDecimal;
import java.util.*;

/** Unavailable legacy references remain choices, not invented platform identities. */
public final class RetainedHomeChoices {
    public static final String KEY = "retained_choices";
    public static final String GRID = "native_app_grid", INPUTS = "native_inputs", ICONS = "native_icons";
    private static final String APP = "legacy-app:", INPUT = "legacy-input:";
    private RetainedHomeChoices() {}

    public static boolean packageName(String value) {
        return value != null && value.length() <= 200 && value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
    }

    public static boolean component(String value) {
        return value != null && value.length() <= 512 && value.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");
    }

    public static boolean inputAlias(String value) {
        if (value == null || !value.startsWith("tvinput_name:") || value.length() <= 13 || value.length() > 512) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }

    public static String appReference(String packageName) {
        if (!packageName(packageName)) throw invalid();
        return APP + packageName;
    }

    public static String inputReference(String alias) {
        if (!inputAlias(alias)) throw invalid();
        return INPUT + alias.substring(13);
    }

    public static Map<String, String> appIdentities(Collection<String> activities) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Set<String>> packages = new LinkedHashMap<>();
        for (String activity : activities) {
            if (!component(activity)) throw invalid();
            int slash = activity.indexOf('/');
            String name = activity.substring(0, slash), type = activity.substring(slash + 1);
            String canonical = name + "/" + (type.startsWith(".") ? name + type : type);
            result.put(activity, canonical); result.put(canonical, canonical);
            if (!packages.containsKey(name)) packages.put(name, new LinkedHashSet<String>());
            packages.get(name).add(canonical);
        }
        for (Map.Entry<String, Set<String>> entry : packages.entrySet())
            if (entry.getValue().size() == 1) result.put(entry.getKey(), entry.getValue().iterator().next());
        return result;
    }

    public static Map<String, String> inputIdentities(Map<String, String> labels) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
            String alias = "tvinput_name:" + entry.getValue();
            if (!inputAlias(alias) || !InputEntry.validId(entry.getKey())) throw invalid();
            if (result.containsKey(alias)) ambiguous.add(alias); else result.put(alias, entry.getKey());
        }
        for (String alias : ambiguous) result.remove(alias);
        return result;
    }

    public static Map<String, Object> read(String file, Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (!(value instanceof String) || ((String) value).length() > 1024 * 1024) throw invalid();
        Map<String, Object> values = new LinkedHashMap<>(LegacyJson.object((String) value));
        if (!BigDecimal.ONE.equals(values.remove("version"))) throw invalid();
        validate(file, values);
        return values;
    }

    public static String write(String file, Map<String, Object> values) {
        validate(file, values);
        StringBuilder json = new StringBuilder("{\"version\":1");
        for (Map.Entry<String, Object> entry : new TreeMap<>(values).entrySet()) {
            json.append(',').append(quote(entry.getKey())).append(':');
            Object value = entry.getValue();
            json.append(value instanceof Boolean ? value.toString() : quote((String) value));
        }
        json.append('}');
        if (json.length() > 1024 * 1024) throw invalid();
        return json.toString();
    }

    public static void validate(String file, Map<String, Object> values) {
        if (!Arrays.asList(GRID, INPUTS, ICONS).contains(file) || values == null || values.size() > 4095) throw invalid();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if ((key.equals("order") || key.equals("home_order")) && !file.equals(ICONS)) {
                if (!(value instanceof String) || key.equals("home_order") && !file.equals(GRID)) throw invalid();
                List<String> order = lines((String) value);
                if (new HashSet<>(order).size() != order.size()) throw invalid();
                for (String reference : order) {
                    if (reference.startsWith(APP)) {
                        if (!file.equals(GRID) || !packageName(reference.substring(APP.length()))) throw invalid();
                    } else if (reference.startsWith(INPUT)) {
                        if (key.equals("order") && file.equals(GRID)
                                || !inputAlias("tvinput_name:" + reference.substring(INPUT.length()))) throw invalid();
                    } else if (file.equals(INPUTS)) {
                        if (!reference.startsWith("input:") || !InputEntry.validId(reference.substring(6))) throw invalid();
                    } else if (key.equals("order") ? !component(reference) : !HomeTileOrder.validKey(reference)) throw invalid();
                }
            } else if (key.startsWith("hidden:") && !file.equals(ICONS)) {
                String reference = key.substring(7);
                if (!(value instanceof Boolean) || (file.equals(GRID)
                        ? !packageName(reference) && !component(reference) : !inputAlias(reference))) throw invalid();
            } else if (key.startsWith("name:") && file.equals(INPUTS)) {
                if (!inputAlias(key.substring(5)) || !(value instanceof String)) throw invalid();
                InputEntry.name((String) value);
            } else if (key.startsWith("icon:") && file.equals(ICONS)) {
                String reference = key.substring(5);
                if (!packageName(reference) && !component(reference) && !inputAlias(reference)
                        || !(value instanceof String) || !icon((String) value)) throw invalid();
            } else throw invalid();
        }
    }

    public static Map<String, Object> promoteGrid(Map<String, ?> saved, Map<String, String> resolutions) {
        Map<String, Object> result = copy(saved), pending = read(GRID, saved.get(KEY));
        Set<String> hidden = new LinkedHashSet<>(lines(text(saved, "hidden")));
        Set<String> owned = new LinkedHashSet<>(lines(text(saved, "order")));
        owned.addAll(lines(text(saved, "home_order"))); owned.addAll(hidden);
        for (String field : new String[]{"order", "home_order"}) {
            if (!pending.containsKey(field)) continue;
            List<String> current = lines(text(saved, field));
            List<String> references = lines((String) pending.get(field));
            List<String> changed = merge(current, references, resolutions);
            if (!changed.equals(current)) result.put(field, String.join("\n", changed));
            updateOrder(pending, field, references, resolutions);
        }
        for (String key : new ArrayList<>(pending.keySet())) {
            if (!key.startsWith("hidden:")) continue;
            String target = resolutions.get(key.substring(7));
            if (target == null || !component(target)) continue;
            if (!owned.contains(target)) {
                if ((Boolean) pending.get(key)) hidden.add(target); else hidden.remove(target);
                result.put("hidden", String.join("\n", hidden));
            }
            pending.remove(key);
        }
        finish(result, GRID, pending);
        return result;
    }

    public static Map<String, Object> promoteInputs(Map<String, ?> saved, Map<String, String> resolutions) {
        Map<String, Object> result = copy(saved), pending = read(INPUTS, saved.get(KEY));
        Set<String> owned = new LinkedHashSet<>(InputOptions.parseOrder(text(saved, "order")));
        for (String key : saved.keySet()) for (String prefix : new String[]{"known:", "factory:", "name:", "hidden:"})
            if (key.startsWith(prefix)) owned.add(key.substring(prefix.length()));
        if (pending.containsKey("order")) {
            List<String> current = new ArrayList<>();
            for (String id : InputOptions.parseOrder(text(saved, "order"))) current.add("input:" + id);
            List<String> references = lines((String) pending.get("order"));
            List<String> changed = new ArrayList<>();
            for (String key : merge(current, references, resolutions)) changed.add(key.substring(6));
            if (!changed.equals(InputOptions.parseOrder(text(saved, "order"))))
                result.put("order", new InputOptions(changed, Collections.<String>emptySet()).savedOrder());
            updateOrder(pending, "order", references, resolutions);
        }
        for (String key : new ArrayList<>(pending.keySet())) {
            String prefix = key.startsWith("hidden:") ? "hidden:" : key.startsWith("name:") ? "name:" : null;
            if (prefix == null) continue;
            String target = resolutions.get(key.substring(prefix.length()));
            if (target == null || !target.startsWith("input:")) continue;
            String id = target.substring(6);
            if (!InputEntry.validId(id)) throw invalid();
            if (!owned.contains(id)) result.put(prefix + id, pending.get(key));
            result.put("known:" + id, true);
            pending.remove(key);
        }
        finish(result, INPUTS, pending);
        return result;
    }

    public static Map<String, Object> promoteIcons(Map<String, ?> saved, Map<String, String> resolutions) {
        Map<String, Object> result = copy(saved), pending = read(ICONS, saved.get(KEY));
        for (String key : new ArrayList<>(pending.keySet())) {
            String target = resolutions.get(key.substring(5));
            if (target == null) continue;
            if (!component(target) && !(target.startsWith("input:") && InputEntry.validId(target.substring(6)))) throw invalid();
            if (!saved.containsKey(target) && !saved.containsKey("edited:" + target)) result.put(target, pending.get(key));
            pending.remove(key);
        }
        finish(result, ICONS, pending);
        return result;
    }

    private static void updateOrder(Map<String, Object> pending, String field, List<String> references, Map<String, String> resolutions) {
        List<String> updated = new ArrayList<>(); boolean unavailable = false;
        for (String reference : references) {
            String target = resolve(reference, resolutions);
            String retained = target == null ? reference : target;
            if (!updated.contains(retained)) updated.add(retained);
            if (target == null) unavailable = true;
        }
        if (unavailable) pending.put(field, String.join("\n", updated)); else pending.remove(field);
    }

    private static List<String> merge(List<String> current, List<String> references, Map<String, String> resolutions) {
        List<String> result = new ArrayList<>(current);
        for (int index = 0; index < references.size(); index++) {
            String reference = references.get(index), target = resolve(reference, resolutions);
            if (!(reference.startsWith(APP) || reference.startsWith(INPUT)) || target == null || result.contains(target)) continue;
            int position = -1;
            for (int next = index + 1; next < references.size() && position < 0; next++) {
                String anchor = resolve(references.get(next), resolutions);
                if (anchor != null && result.contains(anchor)) position = result.indexOf(anchor);
            }
            for (int previous = index - 1; previous >= 0 && position < 0; previous--) {
                String anchor = resolve(references.get(previous), resolutions);
                if (anchor != null && result.contains(anchor)) position = result.indexOf(anchor) + 1;
            }
            result.add(position < 0 ? result.size() : position, target);
        }
        return result;
    }

    private static String resolve(String reference, Map<String, String> resolutions) {
        if (reference.startsWith(APP)) return resolutions.get(reference.substring(APP.length()));
        if (reference.startsWith(INPUT)) return resolutions.get("tvinput_name:" + reference.substring(INPUT.length()));
        return reference;
    }

    private static void finish(Map<String, Object> result, String file, Map<String, Object> pending) {
        if (pending.isEmpty()) result.remove(KEY); else result.put(KEY, write(file, pending));
    }
    private static Map<String, Object> copy(Map<String, ?> values) { return new LinkedHashMap<String, Object>(values); }
    private static String text(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return "";
        if (!(value instanceof String)) throw invalid();
        return (String) value;
    }
    private static List<String> lines(String text) {
        List<String> result = text.isEmpty() ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        if (result.size() > 4096) throw invalid();
        return result;
    }
    private static boolean icon(String value) {
        return value.equals("factory") || value.equals("theme")
                || value.matches("image:[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}\\.png")
                || Arrays.asList("catalog:jbl", "catalog:sonos", "catalog:stereo-receiver", "catalog:laptop",
                        "catalog:pc", "catalog:lg", "catalog:samsung", "catalog:vcr").contains(value);
    }
    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c < 32) result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Saved unavailable Home choices are invalid. Original choices were kept."); }
}
