package com.jeremykenedy.firetv.ui.nativeapp.backup;

import android.content.Context;
import com.jeremykenedy.firetv.ui.nativeapp.widget.notes.NotesOptions;
import com.jeremykenedy.firetv.ui.nativeapp.widget.notes.NotesPreferences;
import com.jeremykenedy.firetv.ui.nativeapp.widget.reminders.RemindersOptions;
import com.jeremykenedy.firetv.ui.nativeapp.widget.reminders.RemindersPreferences;
import java.util.*;

/** Consent is local. An imported document cannot enable export, details or reminders. */
public final class HouseholdContent {
    public static final String NOTES = "notes", REMINDERS = "reminders";
    private HouseholdContent() {}
    public static Map<String,String> capture(Context context, boolean notes, boolean reminders) {
        Map<String,String> result = new LinkedHashMap<>();
        if (notes) result.put(NOTES, NotesPreferences.portableDocument(context));
        if (reminders) result.put(REMINDERS, RemindersPreferences.portableDocument(context));
        return sanitize(result);
    }
    public static Map<String,String> sanitize(Map<String,String> documents) {
        if (documents == null || documents.size() > 2) throw new IllegalArgumentException("Unsupported household content.");
        Map<String,String> result = new LinkedHashMap<>();
        for (Map.Entry<String,String> entry : documents.entrySet()) {
            if (NOTES.equals(entry.getKey())) {
                NotesOptions value = NotesPreferences.decode(entry.getValue());
                result.put(NOTES, NotesPreferences.encode(new NotesOptions(value.configured, true, false, value.items)));
            } else if (REMINDERS.equals(entry.getKey())) {
                RemindersOptions value = RemindersPreferences.decode(entry.getValue());
                result.put(REMINDERS, RemindersPreferences.encode(new RemindersOptions(false, true, false, value.items)));
            } else throw new IllegalArgumentException("Unsupported household content.");
        }
        return Collections.unmodifiableMap(result);
    }
    static String store(String name) {
        if (NOTES.equals(name)) return NotesPreferences.STORE;
        if (REMINDERS.equals(name)) return RemindersPreferences.STORE;
        throw new IllegalArgumentException("Unsupported household content.");
    }
}
