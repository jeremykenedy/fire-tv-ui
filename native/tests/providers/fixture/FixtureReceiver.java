package com.jeremykenedy.firetv.ui.providerfixture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/** Emulator-only producer. Deletes only this package's rows, under TvProvider's caller ownership checks. */
public final class FixtureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Bundle result = new Bundle();
        try {
            ContentResolver resolver = context.getContentResolver();
            String action = intent.getStringExtra("operation");
            if ("clear".equals(action) || "publish".equals(action)) {
                clearOwned(context, TvContract.PreviewPrograms.CONTENT_URI);
                clearOwned(context, TvContract.WatchNextPrograms.CONTENT_URI);
                clearOwned(context, TvContract.Channels.CONTENT_URI);
                context.getSharedPreferences("fixture", Context.MODE_PRIVATE).edit().clear().commit();
            }
            if ("publish".equals(action)) {
                ContentValues channel = new ContentValues();
                channel.put("type", TvContract.Channels.TYPE_PREVIEW);
                channel.put("input_id", context.getPackageName() + "/fixture");
                channel.put("display_name", "Fixture channel");
                long id = ContentUris.parseId(resolver.insert(TvContract.Channels.CONTENT_URI, channel));
                for (int i = 1; i <= 2; i++) {
                    ContentValues program = program("Fixture title " + i, "preview-" + i, "fixture://title/" + i);
                    program.put("channel_id", id);
                    if (android.os.Build.VERSION.SDK_INT >= 29) program.put("series_id", "fixture-series-1");
                    resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, program);
                }
                ContentValues next = program("Fixture next", "next-1", "fixture://next/1");
                next.put("watch_next_type", 0);
                next.put("last_engagement_time_utc_millis", System.currentTimeMillis());
                resolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, next);
                result.putLong("channel", id);
            }
            if ("add-episode".equals(action)) {
                long channel = -1;
                try (Cursor rows = resolver.query(TvContract.Channels.CONTENT_URI,
                        new String[]{"_id", "package_name", "display_name"}, null, null, null)) {
                    while (rows != null && rows.moveToNext()) {
                        if (context.getPackageName().equals(rows.getString(1)) && "Fixture channel".equals(rows.getString(2))) {
                            channel = rows.getLong(0);
                            break;
                        }
                    }
                }
                if (channel <= 0) throw new IllegalStateException("Publish fixture before adding an episode");
                ContentValues episode = program("Fixture title 3", "preview-3", "fixture://title/3");
                episode.put("channel_id", channel);
                if (android.os.Build.VERSION.SDK_INT >= 29) episode.put("series_id", "fixture-series-1");
                resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, episode);
            }
            if ("remove-added-episode".equals(action)) {
                List<Long> ids = new ArrayList<>();
                try (Cursor rows = resolver.query(TvContract.PreviewPrograms.CONTENT_URI,
                        new String[]{"_id", "package_name", "internal_provider_id"}, null, null, null)) {
                    while (rows != null && rows.moveToNext())
                        if (context.getPackageName().equals(rows.getString(1)) && "preview-3".equals(rows.getString(2)))
                            ids.add(rows.getLong(0));
                }
                for (long id : ids) resolver.delete(ContentUris.withAppendedId(TvContract.PreviewPrograms.CONTENT_URI, id), null, null);
            }
            if ("notification-publish".equals(action) || "notification-foreign".equals(action)) {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.createNotificationChannel(new NotificationChannel("fixture", "Fixture recommendations", NotificationManager.IMPORTANCE_LOW));
                PendingIntent pending = intent.getParcelableExtra("foreign_action");
                if (pending == null) pending = PendingIntent.getActivity(context, 42,
                        new Intent(context, FixtureActivity.class).setAction(Intent.ACTION_VIEW)
                        .setData(Uri.parse("fixture://notification/1")), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                manager.notify(42, new Notification.Builder(context, "fixture")
                        .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Fixture notification title")
                        .setCategory(Notification.CATEGORY_RECOMMENDATION).setContentIntent(pending).build());
            }
            if ("notification-clear".equals(action)) {
                NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                manager.cancel(42);
            }
            result.putString("opened", context.getSharedPreferences("fixture", Context.MODE_PRIVATE).getString("opened", ""));
            result.putString("status", "OK");
        } catch (RuntimeException failure) {
            result.putString("status", "FAIL");
            result.putString("reason", failure.toString());
        }
        setResultExtras(result);
        setResultCode(-1);
    }

    private void clearOwned(Context context, Uri table) {
        List<Long> ids = new ArrayList<>();
        try (Cursor rows = context.getContentResolver().query(table,
                new String[]{"_id", "package_name"}, null, null, null)) {
            if (rows == null) throw new IllegalStateException("Fixture cleanup query unavailable");
            while (rows.moveToNext()) {
                if (context.getPackageName().equals(rows.getString(1))) ids.add(rows.getLong(0));
            }
        }
        for (long id : ids)
            context.getContentResolver().delete(ContentUris.withAppendedId(table, id), null, null);
    }

    private ContentValues program(String title, String id, String link) {
        ContentValues values = new ContentValues();
        values.put("type", 0);
        values.put("title", title);
        values.put("internal_provider_id", id);
        values.put("intent_uri", link);
        values.put("poster_art_uri", "android.resource://com.jeremykenedy.firetv.ui.providerfixture/17301516");
        return values;
    }
}
