package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
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
    private static final int MAX_PROGRAMS = 96;
    public enum Status { READY, EMPTY, PERMISSION_REQUIRED, UNAVAILABLE, PARTIAL }

    public static final class Program {
        public final String key;
        public final String compatibilityKey;
        public final String legacyKey;
        public final String source;
        public final String title;
        public final Uri artwork;
        public final String intentUri;
        public final String seriesKey;
        public final String seriesTitle;
        public final String detailsIntentUri;
        public final String playIntentUri;
        public final String notificationKey;
        public Program(String key, String source, String title, Uri artwork, String intentUri) {
            this(key, source, title, artwork, intentUri, null, null, null, null);
        }
        public Program(String key, String source, String title, Uri artwork, String intentUri,
                String seriesKey, String seriesTitle) {
            this(key, source, title, artwork, intentUri, seriesKey, seriesTitle, null, null);
        }
        public Program(String key, String source, String title, Uri artwork, String intentUri,
                String seriesKey, String seriesTitle, String detailsIntentUri, String playIntentUri) {
            this(key, source, title, artwork, intentUri, seriesKey, seriesTitle, detailsIntentUri, playIntentUri, null);
        }
        Program(String key, String source, String title, Uri artwork, String intentUri,
                String seriesKey, String seriesTitle, String detailsIntentUri, String playIntentUri, String notificationKey) {
            this(key, source, title, artwork, intentUri, seriesKey, seriesTitle, detailsIntentUri, playIntentUri, notificationKey, null);
        }
        Program(String key, String source, String title, Uri artwork, String intentUri,
                String seriesKey, String seriesTitle, String detailsIntentUri, String playIntentUri, String notificationKey, String compatibilityKey) {
            this(key, source, title, artwork, intentUri, seriesKey, seriesTitle, detailsIntentUri,
                    playIntentUri, notificationKey, compatibilityKey, null);
        }
        Program(String key, String source, String title, Uri artwork, String intentUri,
                String seriesKey, String seriesTitle, String detailsIntentUri, String playIntentUri,
                String notificationKey, String compatibilityKey, String legacyKey) {
            this.key = key;
            this.legacyKey = legacyKey;
            this.compatibilityKey = compatibilityKey;
            this.source = source;
            this.title = title;
            this.artwork = artwork;
            this.intentUri = intentUri;
            this.seriesKey = seriesKey;
            this.seriesTitle = seriesTitle;
            this.detailsIntentUri = detailsIntentUri;
            this.playIntentUri = playIntentUri;
            this.notificationKey = notificationKey;
        }
    }

    public static final class Feed {
        public final LayoutItem item;
        public final String title;
        public final String source;
        public final List<Program> programs;
        public Feed(String id, String title, String source, List<Program> programs) {
            item = new LayoutItem(id, LayoutItem.Kind.CHANNEL_CARD);
            this.title = title;
            this.source = source;
            this.programs = Collections.unmodifiableList(new ArrayList<>(programs));
        }
    }

    public static final class SourceStatus {
        public enum State { READY, EMPTY, DISABLED, SETUP_REQUIRED, UNAVAILABLE, PARTIAL }
        public final String id;
        public final String label;
        public final State state;
        public final String message;
        public SourceStatus(String id, String label, State state, String message) {
            this.id = id; this.label = label; this.state = state; this.message = message;
        }
    }

    public static final class Result {
        /** The TV provider's own status remains available even when optional sources have cards. */
        public final Status status;
        public final List<Feed> feeds;
        public final List<SourceStatus> sources;
        Result(Status status, List<Feed> feeds) { this(status, feeds, Collections.<SourceStatus>emptyList()); }
        Result(Status status, List<Feed> feeds, List<SourceStatus> sources) {
            this.status = status;
            this.feeds = Collections.unmodifiableList(new ArrayList<>(feeds));
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        }
        public String message() {
            if (!sources.isEmpty()) {
                StringBuilder summary = new StringBuilder(feeds.size() + (feeds.size() == 1 ? " channel card available." : " channel cards available."));
                for (SourceStatus source : sources) if (source.state == SourceStatus.State.SETUP_REQUIRED
                        || source.state == SourceStatus.State.UNAVAILABLE || source.state == SourceStatus.State.PARTIAL)
                    summary.append(' ').append(source.message);
                if (feeds.isEmpty() && sources.get(0).state == SourceStatus.State.EMPTY)
                    summary.append(" No apps are sharing TV recommendations.");
                return summary.toString();
            }
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

    public static Result read(Context context) { return read(context, null); }

    public static Result read(Context context, CancellationSignal cancellation) {
        if (cancellation != null) cancellation.throwIfCanceled();
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
                new String[]{"_id", "package_name", "display_name"}, null, null, null, cancellation)) {
            if (rows == null) partial = true;
            else {
                int count = 0;
                while (count++ < MAX_ROWS && rows.moveToNext()) {
                    if (cancellation != null) cancellation.throwIfCanceled();
                    String source = text(rows, "package_name", 200);
                    if (!RecommendationKeys.validPackage(source)) continue;
                    channels.put(rows.getLong(rows.getColumnIndexOrThrow("_id")),
                            new Channel(source, text(rows, "display_name", 200)));
                }
            }
        } catch (OperationCanceledException canceled) { throw canceled; }
        catch (RuntimeException inaccessible) { partial = true; }
        List<Feed> feeds = new ArrayList<>();
        partial |= readPrograms(context, TvContract.PreviewPrograms.CONTENT_URI, false, channels, feeds, cancellation);
        partial |= readPrograms(context, TvContract.WatchNextPrograms.CONTENT_URI, true, channels, feeds, cancellation);
        return new Result(partial ? (feeds.isEmpty() ? Status.UNAVAILABLE : Status.PARTIAL)
                : (feeds.isEmpty() ? Status.EMPTY : Status.READY), feeds);
    }

    private static boolean readPrograms(Context context, Uri uri, boolean watchNext,
            Map<Long, Channel> channels, List<Feed> output, CancellationSignal cancellation) {
        String[] columns = watchNext
                ? new String[]{"_id", "package_name", "title", "poster_art_uri", "intent_uri", "internal_provider_id", "content_id"}
                : new String[]{"_id", "package_name", "title", "poster_art_uri", "intent_uri", "internal_provider_id", "content_id", "channel_id"};
        if (Build.VERSION.SDK_INT >= 29) {
            String[] withSeries = java.util.Arrays.copyOf(columns, columns.length + 1);
            withSeries[columns.length] = "series_id";
            columns = withSeries;
        }
        boolean incomplete = false;
        Map<String, List<Program>> groups = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        try (Cursor rows = context.getContentResolver().query(uri, columns, null, null, null, cancellation)) {
            if (rows == null) return true;
            int count = 0;
            while (count++ < MAX_ROWS && rows.moveToNext()) {
                if (cancellation != null) cancellation.throwIfCanceled();
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
                String oldKey = RecommendationKeys.program(feedId, rowId, id);
                String intent = text(rows, "intent_uri", 16384);
                String plexId = OwnedPlexIdentity.canonical(context, rows, source);
                if (source.equals(context.getPackageName()) && plexId == null
                        && !OwnedPlexIdentity.notification(context, rows, source)) {
                    incomplete = true;
                    continue;
                }
                String key = plexId == null ? oldKey : "plex:" + plexId;
                boolean duplicate = false;
                for (Program program : programs) if (program.key.equals(key)) duplicate = true;
                if (!duplicate) programs.add(new Program(key, source, title.isEmpty() ? "Untitled item" : title,
                        artwork(context, source, text(rows, "poster_art_uri", 8192)), intent,
                        RecommendationKeys.series(source, Build.VERSION.SDK_INT >= 29 ? text(rows, "series_id", 2048) : ""),
                        null, null, null, null, plexId == null ? null : oldKey,
                        LegacyRecommendationIdentities.item(source, watchNext,
                                plexId == null ? rawText(rows, "internal_provider_id") : plexId,
                                rawText(rows, "content_id"), rowId)));
            }
        } catch (OperationCanceledException canceled) { throw canceled; }
        catch (RuntimeException inaccessible) { return true; }
        for (Map.Entry<String, List<Program>> group : groups.entrySet()) {
            if (!group.getValue().isEmpty()) output.add(new Feed(group.getKey(), titles.get(group.getKey()),
                    sources.get(group.getKey()), group.getValue()));
        }
        return incomplete;
    }

    private static String appLabel(Context context, String source) {
        try { return context.getPackageManager().getApplicationLabel(
                context.getPackageManager().getApplicationInfo(source, 0)).toString(); }
        catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return source; }
    }

    private static String rawText(Cursor rows, String column) {
        int index = rows.getColumnIndex(column);
        return index < 0 || rows.isNull(index) ? "" : rows.getString(index);
    }

    private static String text(Cursor rows, String column, int limit) {
        int index = rows.getColumnIndex(column);
        if (index < 0 || rows.isNull(index)) return "";
        String value = rows.getString(index);
        return value == null || value.length() > limit ? "" : value.trim();
    }

    private static Uri artwork(Context context, String source, String value) {
        if (value.isEmpty()) return null;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        if ("https".equals(scheme)) return uri;
        if ("android.resource".equals(scheme)) return source.equals(uri.getAuthority()) ? uri : null;
        if (!"content".equals(scheme) || uri.getAuthority() == null) return null;
        try {
            android.content.pm.ProviderInfo provider = context.getPackageManager().resolveContentProvider(uri.getAuthority(), 0);
            return provider != null && source.equals(provider.packageName) ? uri : null;
        } catch (RuntimeException unavailable) { return null; }
    }
}
