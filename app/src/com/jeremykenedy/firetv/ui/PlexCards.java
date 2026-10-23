package com.jeremykenedy.firetv.ui;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

final class PlexCards {
    private static final String CONTINUE = "fire-tv-ui-plex-continue";
    private static final String RECENT = "fire-tv-ui-plex-recent";

    private PlexCards() {}

    static int publish(
            Context context,
            PlexApi api,
            PlexConnection connection,
            List<PlexLibrary.Item> continuing,
            List<PlexLibrary.Item> recent,
            long request)
            throws Exception {
        List<ContentValues> first = values(context, api, connection, continuing, request);
        List<ContentValues> second = values(context, api, connection, recent, request);
        if (!PlexSession.current(request)) return -1;
        final int count = first.size() + second.size();
        boolean committed =
                PlexSession.commit(
                        request,
                        new PlexSession.Change() {
                            @Override
                            public void apply() throws Exception {
                                ArrayList<ContentProviderOperation> changes = new ArrayList<>();
                                replace(
                                        context,
                                        CONTINUE,
                                        "Plex Continue Watching",
                                        first,
                                        changes);
                                replace(context, RECENT, "Plex Recently Added", second, changes);
                                if (!changes.isEmpty())
                                    context.getContentResolver()
                                            .applyBatch(TvContract.AUTHORITY, changes);
                                connection.save(context);
                                java.util.Set<String> referenced = new java.util.HashSet<>();
                                for (ContentValues row : first)
                                    referenced.add(row.getAsString("poster_art_uri"));
                                for (ContentValues row : second)
                                    referenced.add(row.getAsString("poster_art_uri"));
                                PlexArtwork.prune(context, referenced);
                            }
                        });
        return committed ? count : -1;
    }

    private static List<ContentValues> values(
            Context context,
            PlexApi api,
            PlexConnection connection,
            List<PlexLibrary.Item> items,
            long request)
            throws Exception {
        List<ContentValues> values = new ArrayList<>();
        for (PlexLibrary.Item item : items) {
            if (!PlexSession.current(request)) break;
            Uri art = PlexArtwork.cache(context, api, connection, item, request);
            if (!PlexSession.current(request)) break;
            ContentValues row = new ContentValues();
            row.put(
                    "internal_provider_id",
                    PlexValues.providerItemId(connection.sourceKey(), item.id));
            row.put("title", item.title);
            row.put("short_description", item.summary);
            row.put(
                    "type",
                    "movie".equals(item.type)
                            ? TvContract.PreviewPrograms.TYPE_MOVIE
                            : TvContract.PreviewPrograms.TYPE_TV_EPISODE);
            row.put(
                    "poster_art_aspect_ratio",
                    item.landscape
                            ? TvContract.PreviewPrograms.ASPECT_RATIO_16_9
                            : TvContract.PreviewPrograms.ASPECT_RATIO_2_3);
            if (art != null) row.put("poster_art_uri", art.toString());
            Intent launch =
                    new Intent(
                            Intent.ACTION_VIEW,
                            new Uri.Builder()
                                    .scheme("firetvui")
                                    .authority("plex")
                                    .appendPath("item")
                                    .appendPath(connection.sourceKey())
                                    .appendPath(item.id)
                                    .build());
            launch.setClassName(context, context.getPackageName() + ".PlexItemActivity");
            row.put("intent_uri", launch.toUri(Intent.URI_INTENT_SCHEME));
            row.put("weight", items.size() - values.size());
            values.add(row);
        }
        return values;
    }

    private static long channel(Context context, String source, String label) throws Exception {
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                TvContract.Channels.CONTENT_URI,
                                new String[] {"_id", "package_name", "internal_provider_id"},
                                null,
                                null,
                                null)) {
            if (cursor == null)
                throw new java.io.IOException("Plex card channels could not be read.");
            while (cursor.moveToNext()) {
                if (context.getPackageName().equals(cursor.getString(1))
                        && source.equals(cursor.getString(2))) return cursor.getLong(0);
            }
        }
        ContentValues values = new ContentValues();
        values.put("type", TvContract.Channels.TYPE_PREVIEW);
        values.put("display_name", label);
        values.put("internal_provider_id", source);
        Intent intent =
                context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null)
            values.put("app_link_intent_uri", intent.toUri(Intent.URI_INTENT_SCHEME));
        Uri inserted = context.getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
        if (inserted == null)
            throw new java.io.IOException("Plex card channels could not be created.");
        return ContentUris.parseId(inserted);
    }

    private static void replace(
            Context context,
            String source,
            String label,
            List<ContentValues> values,
            ArrayList<ContentProviderOperation> changes)
            throws Exception {
        long channel = channel(context, source, label);
        Uri programs =
                TvContract.PreviewPrograms.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter("channel", Long.toString(channel))
                        .build();
        try (Cursor cursor =
                context.getContentResolver()
                        .query(programs, new String[] {"_id", "package_name"}, null, null, null)) {
            if (cursor == null)
                throw new java.io.IOException("Existing Plex cards could not be read.");
            while (cursor.moveToNext()) {
                if (context.getPackageName().equals(cursor.getString(1))) {
                    changes.add(
                            ContentProviderOperation.newDelete(
                                            ContentUris.withAppendedId(
                                                    TvContract.PreviewPrograms.CONTENT_URI,
                                                    cursor.getLong(0)))
                                    .build());
                }
            }
        }
        for (ContentValues row : values) {
            row.put("channel_id", channel);
            changes.add(
                    ContentProviderOperation.newInsert(TvContract.PreviewPrograms.CONTENT_URI)
                            .withValues(row)
                            .build());
        }
    }

    static void remove(Context context) throws Exception {
        List<Long> channels = new ArrayList<>();
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                TvContract.Channels.CONTENT_URI,
                                new String[] {"_id", "package_name", "internal_provider_id"},
                                null,
                                null,
                                null)) {
            if (cursor == null) throw new java.io.IOException("Plex channels could not be read.");
            while (cursor.moveToNext()) {
                if (context.getPackageName().equals(cursor.getString(1))
                        && (CONTINUE.equals(cursor.getString(2))
                                || RECENT.equals(cursor.getString(2))))
                    channels.add(cursor.getLong(0));
            }
        }
        for (long channel : channels)
            context.getContentResolver()
                    .delete(
                            ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channel),
                            null,
                            null);
        java.io.File[] images = PlexArtwork.directory(context).listFiles();
        if (images != null) for (java.io.File image : images) image.delete();
    }
}
