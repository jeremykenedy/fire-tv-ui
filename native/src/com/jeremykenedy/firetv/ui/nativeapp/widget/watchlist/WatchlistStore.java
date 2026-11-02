package com.jeremykenedy.firetv.ui.nativeapp.widget.watchlist;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

/** A private local document, excluded from portable backups. */
public final class WatchlistStore {
    public static final String FILE = "native_tonight_watchlist", KEY = "document";
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;
    public WatchlistStore(Context context) { preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public String raw() {
        Map<String, ?> all = preferences.getAll(); Object value = all.get(KEY);
        if (!all.containsKey(KEY)) return null;
        if (!(value instanceof String)) throw new IllegalStateException("Watchlist data is unavailable. Saved data was kept.");
        return (String) value;
    }
    public Watchlist load() {
        try { String value = raw(); return value == null ? Watchlist.empty() : Watchlist.decode(value); }
        catch (IllegalArgumentException bad) { throw new IllegalStateException("Watchlist data is unavailable. Saved data was kept.", bad); }
    }
    public void save(String expected, Watchlist next) {
        synchronized (LOCK) {
            String old = raw();
            if (!(old == null ? expected == null : old.equals(expected))) throw new IllegalStateException("Watchlist changed. Reopen and try again.");
            String value = next.encode();
            if (preferences.edit().putString(KEY, value).commit()) return;
            if (value.equals(raw())) {
                SharedPreferences.Editor rollback = preferences.edit();
                if (old == null) rollback.remove(KEY); else rollback.putString(KEY, old);
                if (!rollback.commit() || !(old == null ? raw() == null : old.equals(raw())))
                    throw new IllegalStateException("Watchlist save recovery could not be verified.");
            }
            throw new IllegalStateException("Watchlist could not be saved. Please try again.");
        }
    }
}
