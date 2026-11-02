package com.jeremykenedy.firetv.ui.nativeapp.folders;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class FolderPreferences {
    public static final String STORE = "native_folders";
    private FolderPreferences() {}

    public static FolderOptions load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        try {
            if (prefs.getInt("schema_version", 1) != 1) throw new IllegalArgumentException("Unknown folder version.");
            return decode(prefs.getString("folders", "[]"));
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException("Saved folders could not be read. Their stored data has been kept.", unreadable);
        }
    }
    public static void save(Context context, FolderOptions value) {
        if (!context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putInt("schema_version", 1).putString("folders", encode(value)).commit())
            throw new IllegalStateException("Folders could not be saved. Please try again.");
    }
    public static FolderOptions decode(String json) {
        try {
            if (json == null || json.length() > 262144) throw new IllegalArgumentException("Invalid folders data.");
            JSONArray array = new JSONArray(json);
            if (array.length() > FolderOptions.MAX_FOLDERS) throw new IllegalArgumentException("Too many folders.");
            List<FolderOptions.Folder> folders = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                JSONArray keys = item.getJSONArray("components");
                if (keys.length() > FolderOptions.MAX_APPS) throw new IllegalArgumentException("Too many apps.");
                List<String> components = new ArrayList<>();
                for (int j = 0; j < keys.length(); j++) components.add(string(keys.get(j)));
                folders.add(new FolderOptions.Folder(string(item.get("id")), string(item.get("name")), components));
            }
            return new FolderOptions(folders);
        } catch (JSONException invalid) { throw new IllegalArgumentException("Invalid folders data.", invalid); }
    }
    private static String string(Object value) {
        if (!(value instanceof String)) throw new IllegalArgumentException("Expected folder text.");
        return (String) value;
    }
    public static String encode(FolderOptions value) {
        try {
            JSONArray array = new JSONArray();
            for (FolderOptions.Folder folder : value.folders) {
                JSONArray components = new JSONArray();
                for (String key : folder.components) components.put(key);
                array.put(new JSONObject().put("id", folder.id).put("name", folder.name).put("components", components));
            }
            String json = array.toString();
            if (json.length() > 262144) throw new IllegalArgumentException("Folder data is too large.");
            return json;
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }
}
