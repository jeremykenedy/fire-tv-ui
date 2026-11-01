package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only access to recommendations published by installed TV apps. Call off the UI thread. */
public final class TvRecommendations {
    public static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final int MAX_ROWS = 2048;
    private static final int MAX_FEEDS = 64;
    private static final int MAX_PROGRAMS = 24;
    public enum Status { READY, EMPTY, PERMISSION_REQUIRED, UNAVAILABLE, PARTIAL }

    public static final class Program {
        public final String key;
        public final String source;
        public final String title;
        public final Uri artwork;
        public final String intentUri;
        Program(String key, String source, String title, Uri artwork, String intentUri) {
            this.key = key;
            this.source = source;
            this.title = title;
            this.artwork = artwork;
            this.intentUri = intentUri;
        }
    }

    public static final class Feed {
        public final LayoutItem item;
        public final String title;
        public final String source;
        public final List<Program> programs;
        Feed(String id, String title, String source, List<Program> programs) {
            item = new LayoutItem(id, LayoutItem.Kind.CHANNEL_CARD);
            this.title = title;
            this.source = source;
            this.programs = Collections.unmodifiableList(new ArrayList<>(programs));
        }
    }

    public static final class Result {
        public final Status status;
        public final List<Feed> feeds;
        Result(Status status, List<Feed> feeds) {
            this.status = status;
            this.feeds = Collections.unmodifiableList(new ArrayList<>(feeds));
        }
        public String message() {
            switch (status) {
                case PERMISSION_REQUIRED: return "Allow TV listings access to show cards shared by your apps.";
                case EMPTY: return "No apps are sharing TV recommendations.";
                case PARTIAL: return "Some TV recommendations could not be read.";
                case UNAVAILABLE: return "TV recommendations are unavailable on this device.";
                default: return feeds.size() + (feeds.size() == 1 ? " channel card available." : " channel cards available.");
            }
        }
    }

    private static final class Channel {
        final String source;
        final String title;
        Channel(String source, String title) { this.source = source; this.title = title; }
    }

    private TvRecommendations() {}

    public static Result read(Context context) {
        try {
            if (context.getPackageManager().resolveContentProvider(TvContract.AUTHORITY, 0) == null)
                return new Result(Status.UNAVAILABLE, Collections.<Feed>emptyList());
            if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED)
                return new Result(Status.PERMISSION_REQUIRED, Collections.<Feed>emptyList());
        } catch (RuntimeException unavailable) {
            return new Result(Status.UNAVAILABLE, Collections.<Feed>emptyList());
        }
        Map<Long, Channel> channels = new LinkedHashMap<>();
        boolean partial = false;
        try (Cursor rows = context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{"_id", "package_name", "display_name"}, null, null, null)) {
            if (rows == null) partial = true;
            else {
                int count = 0;
                while (count++ < MAX_ROWS && rows.moveToNext()) {
                    String source = text(rows, "package_name", 200);
                    if (!RecommendationKeys.validPackage(source)) continue;
                    channels.put(rows.getLong(rows.getColumnIndexOrThrow("_id")),
                            new Channel(source, text(rows, "display_name", 200)));
                }
            }
        } catch (RuntimeException inaccessible) { partial = true; }
        List<Feed> feeds = new ArrayList<>();
        partial |= readPrograms(context, TvContract.PreviewPrograms.CONTENT_URI, false, channels, feeds);
        partial |= readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, true, channels, feeds);
        return new Result(partial ? (feeds.isEmpty() ? Status.UNAVAILABLE : Status.PARTIAL)
                : (feeds.isEmpty() ? Status.EMPTY : Status.READY), feeds);
    }

    private static boolean readPrograms(Context context, Uri uri, boolean watchNext,
            Map<Long, Channel> channels, List<Feed> output) {
        String[] columns = watchNext
                ? new String[]{"_id", "package_name", "title", "poster_art_uri", "intent_uri", "internal_provider_id"}
                : new String[]{"_id", "package_name", "title", "poster_art_uri", "intent_uri", "internal_provider_id", "channel_id"};
        Map<String, List<Program>> groups = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        try (Cursor rows = context.getContentResolver().query(uri, columns, null, null, null)) {
            if (rows == null) return true;
            int count = 0;
            while (count++ < MAX_ROWS && rows.moveToNext()) {
                String source = text(rows, "package_name", 200);
                if (!RecommendationKeys.validPackage(source)) continue;
                long rowId = rows.getLong(rows.getColumnIndexOrThrow("_id"));
                if (rowId <= 0) continue;
                long channelId = watchNext ? 0 : rows.getLong(rows.getColumnIndexOrThrow("channel_id"));
                Channel channel = channels.get(channelId);
                if (!watchNext && (channel == null || !source.equals(channel.source))) continue;
                String feedId = RecommendationKeys.feed(source, watchNext, channelId);
                if (!groups.containsKey(feedId)) {
                    if (output.size() + groups.size() >= MAX_FEEDS) continue;
                    groups.put(feedId, new ArrayList<Program>());
                    String label = appLabel(context, source);
                    titles.put(feedId, watchNext ? label + " Watch Next"
                            : (channel.title.isEmpty() ? label : channel.title));
                    sources.put(feedId, source);
                }
                List<Program> programs = groups.get(feedId);
                if (programs.size() >= MAX_PROGRAMS) continue;
                String title = text(rows, "title", 512);
                String id = text(rows, "internal_provider_id", 2048);
                String key = RecommendationKeys.program(feedId, rowId, id);
                boolean duplicate = false;
                for (Program program : programs) if (program.key.equals(key)) duplicate = true;
                if (!duplicate) programs.add(new Program(key, source, title.isEmpty() ? "Untitled item" : title,
                        artwork(text(rows, "poster_art_uri", 8192)), text(rows, "intent_uri", 16384)));
            }
        } catch (RuntimeException inaccessible) { return true; }
        for (Map.Entry<String, List<Program>> group : groups.entrySet()) {
            if (!group.getValue().isEmpty()) output.add(new Feed(group.getKey(), titles.get(group.getKey()),
                    sources.get(group.getKey()), group.getValue()));
        }
        return false;
    }

    private static String appLabel(Context context, String source) {
        try { return context.getPackageManager().getApplicationLabel(
                context.getPackageManager().getApplicationInfo(source, 0)).toString(); }
        catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return source; }
    }

    private static String text(Cursor rows, String column, int limit) {
        int index = rows.getColumnIndex(column);
        if (index < 0 || rows.isNull(index)) return "";
        String value = rows.getString(index);
        return value == null || value.length() > limit ? "" : value.trim();
    }

    private static Uri artwork(String value) {
        if (value.isEmpty()) return null;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        return "https".equals(scheme) || "content".equals(scheme) || "android.resource".equals(scheme) ? uri : null;
    }
}
