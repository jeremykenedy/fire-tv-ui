package com.jeremykenedy.firetv.ui.livetvfixture;

import android.content.BroadcastReceiver;
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

/** Publishes and deletes only this fixture's own real Channels/Programs rows. */
public final class GuideReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Bundle result = new Bundle();
        try {
            String operation = intent.getStringExtra("operation");
            if ("publish".equals(operation) || "clear".equals(operation)) {
                clear(context, TvContract.Programs.CONTENT_URI);
                clear(context, TvContract.Channels.CONTENT_URI);
            }
            if ("publish".equals(operation)) {
                long now = System.currentTimeMillis();
                long current = channel(context, "Current fixture", "1");
                program(context, current, "Fixture now", now - 1800000, now + 1800000);
                program(context, current, "Fixture next", now + 1800000, now + 3600000);
                long stale = channel(context, "Stale fixture", "2");
                program(context, stale, "Ended fixture", now - 7200000, now - 3600000);
                channel(context, "Missing fixture", "3");
                long gap = channel(context, "Gap fixture", "4");
                program(context, gap, "Future fixture", now + 7200000, now + 10800000);
                result.putLong("current_channel", current);
                result.putLong("stale_channel", stale);
            }
            result.putString("status", "OK");
        } catch (RuntimeException failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        setResultExtras(result); setResultCode(-1);
    }
    private long channel(Context context, String name, String number) {
        ContentValues values = new ContentValues();
        values.put("input_id", context.getPackageName() + "/.GuideInput");
        values.put("type", TvContract.Channels.TYPE_OTHER);
        values.put("internal_provider_id", "fixture-channel-" + number);
        values.put("display_name", name); values.put("display_number", number);
        Intent link = new Intent(Intent.ACTION_VIEW).setPackage(context.getPackageName()).setData(Uri.parse("livetvfixture://channel/" + number));
        values.put("app_link_intent_uri", link.toUri(Intent.URI_INTENT_SCHEME));
        Uri inserted = context.getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
        if (inserted == null) throw new IllegalStateException("Fixture channel insert unavailable");
        return ContentUris.parseId(inserted);
    }
    private void program(Context context, long channel, String title, long start, long end) {
        ContentValues values = new ContentValues();
        values.put("channel_id", channel); values.put("title", title);
        values.put("start_time_utc_millis", start); values.put("end_time_utc_millis", end);
        if (context.getContentResolver().insert(TvContract.Programs.CONTENT_URI, values) == null)
            throw new IllegalStateException("Fixture program insert unavailable");
    }
    private void clear(Context context, Uri table) {
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(table, new String[]{"_id", "package_name"}, null, null, null)) {
            while (cursor != null && cursor.moveToNext()) if (context.getPackageName().equals(cursor.getString(1))) ids.add(cursor.getLong(0));
        }
        for (Long id : ids) context.getContentResolver().delete(ContentUris.withAppendedId(table, id), null, null);
    }
}
