package com.jeremykenedy.firetv.ui.nativeapp.providers.discovery;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Collections;

public final class DiscoveryPreferences {
    private DiscoveryPreferences() {}
    public static DiscoveryOptions load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("source_discovery", 0);
        try {
            return new DiscoveryOptions(preferences.getStringSet("included", Collections.<String>emptySet()),
                    preferences.getStringSet("excluded", Collections.<String>emptySet()),
                    preferences.getStringSet("libraries", Collections.<String>emptySet()),
                    preferences.getStringSet("dismissed", Collections.<String>emptySet()),
                    preferences.getStringSet("liked", Collections.<String>emptySet()),
                    preferences.getBoolean("movies", true), preferences.getBoolean("shows", true),
                    preferences.getInt("minutes", 0), Double.parseDouble(preferences.getString("score", "0")),
                    preferences.getLong("count", 0));
        } catch (RuntimeException invalid) { throw new IllegalStateException("Saved discovery filters could not be read."); }
    }
    public static void save(Context context, DiscoveryOptions options) {
        if (!context.getSharedPreferences("source_discovery", 0).edit()
                .putStringSet("included", options.includedGenres).putStringSet("excluded", options.excludedGenres)
                .putStringSet("libraries", options.libraries).putStringSet("dismissed", options.notInterested)
                .putStringSet("liked", options.likedTitles)
                .putBoolean("movies", options.movies).putBoolean("shows", options.shows)
                .putInt("minutes", options.maximumMinutes).putString("score", Double.toString(options.minimumScore))
                .putLong("count", options.minimumRatingCount).commit())
            throw new IllegalStateException("Discovery filters could not be saved.");
    }
}
