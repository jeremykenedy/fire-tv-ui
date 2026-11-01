package com.jeremykenedy.firetv.ui;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Iterator;

final class InputNameValues {
    static final String SETTING = "input_names";
    static final int MAX_LENGTH = 60;

    private InputNameValues() {}

    static boolean input(String key) {
        return key != null && key.startsWith("tvinput_name:")
                && key.length() > 13 && key.length() <= 512;
    }

    static String name(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Enter an input name of 1 to 60 characters.");
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                throw new IllegalArgumentException("Use a single line for the input name.");
            }
        }
        return name;
    }

    static JSONObject parse(String saved) throws JSONException {
        JSONObject names = new JSONObject(saved);
        if (names.length() > 64) throw new JSONException("Too many input names.");
        for (Iterator<String> keys = names.keys(); keys.hasNext();) {
            String key = keys.next();
            Object value = names.get(key);
            if (!input(key) || !(value instanceof String)) throw new JSONException("Invalid input name.");
            try {
                if (!name((String) value).equals(value)) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                throw new JSONException("Invalid input name.");
            }
        }
        return names;
    }

    static String display(String saved, String key, String original) {
        if (!input(key)) return original;
        try {
            return parse(saved).optString(key, original);
        } catch (Exception invalid) {
            return original;
        }
    }

    static String change(String saved, String key, String label) throws JSONException {
        if (!input(key)) throw new IllegalArgumentException("Select a TV input.");
        JSONObject names = parse(saved);
        if (label == null) names.remove(key);
        else names.put(key, name(label));
        if (names.length() > 64) throw new JSONException("Too many input names.");
        return names.toString();
    }
}
