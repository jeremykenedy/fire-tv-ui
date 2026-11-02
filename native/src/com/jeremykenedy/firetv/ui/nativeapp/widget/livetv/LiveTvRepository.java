package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ordinary read-only TV provider queries. Invoke off the UI thread. */
public final class LiveTvRepository {
    public static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    public enum Status { READY, EMPTY, PERMISSION_REQUIRED, UNAVAILABLE, PARTIAL }
    public static final class Channels {
        public final Status status;
        public final List<LiveGuide.Channel> values;
        Channels(Status status, List<LiveGuide.Channel> values) { this.status = status; this.values = Collections.unmodifiableList(values); }
        public String message() {
            switch (status) {
                case PERMISSION_REQUIRED: return "Allow ordinary TV listings access to read channel guides.";
                case UNAVAILABLE: return "The TV channel provider is unavailable or restricted on this device.";
                case EMPTY: return "No configured live channels were found. Set up channels and an EPG in your TV source app.";
                case PARTIAL: return "Only part of the channel guide could be read. Some channels may be missing.";
                default: return "Uses the guide published by your configured TV sources.";
            }
        }
    }
    public static Channels channels(Context context, CancellationSignal cancellation) {
        List<LiveGuide.Channel> values = new ArrayList<>();
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return new Channels(Status.PERMISSION_REQUIRED, values);
        if (context.getPackageManager().resolveContentProvider(TvContract.AUTHORITY, 0) == null) return new Channels(Status.UNAVAILABLE, values);
        boolean partial = false;
        try (Cursor rows = context.getContentResolver().query(TvContract.Channels.CONTENT_URI,
                new String[]{"_id", "package_name", "display_name", "display_number", "type", "app_link_intent_uri", "input_id", "internal_provider_id"}, null, null, null, cancellation)) {
            if (rows == null) return new Channels(Status.UNAVAILABLE, values);
            int count = 0;
            while (rows.moveToNext()) {
                if (cancellation != null) cancellation.throwIfCanceled();
                if (count++ >= 1024) { partial = true; break; }
                if (TvContract.Channels.TYPE_PREVIEW.equals(text(rows, "type", 100))) continue;
                try {
                    String name = text(rows, "display_name", 160);
                    String number = text(rows, "display_number", 40);
                    values.add(new LiveGuide.Channel(rows.getLong(rows.getColumnIndexOrThrow("_id")), text(rows, "package_name", 200),
                            number.isEmpty() ? name : number + " " + name, text(rows, "app_link_intent_uri", 16384), text(rows, "input_id", 400), text(rows, "internal_provider_id", 400)));
                } catch (IllegalArgumentException invalid) { partial = true; }
            }
        } catch (OperationCanceledException cancelled) { throw cancelled; }
        catch (RuntimeException unavailable) { return new Channels(Status.UNAVAILABLE, values); }
        return new Channels(partial ? Status.PARTIAL : values.isEmpty() ? Status.EMPTY : Status.READY, values);
    }
    public static LiveGuide.Listing listing(Context context, LiveGuide.Channel channel, long now, CancellationSignal cancellation) {
        List<LiveGuide.Program> programs = new ArrayList<>();
        try (Cursor rows = context.getContentResolver().query(TvContract.buildProgramsUriForChannel(channel.id,
                Math.max(0, now - 7L * 86400000), now + 2L * 86400000),
                new String[]{"channel_id", "package_name", "title", "start_time_utc_millis", "end_time_utc_millis"},
                null, null, "start_time_utc_millis ASC", cancellation)) {
            if (rows == null) return unavailable(channel);
            int count = 0;
            while (rows.moveToNext()) {
                if (cancellation != null) cancellation.throwIfCanceled();
                if (count++ >= 512) return unavailable(channel);
                if (rows.getLong(rows.getColumnIndexOrThrow("channel_id")) != channel.id
                        || !channel.publisher.equals(text(rows, "package_name", 200))) continue;
                try {
                    programs.add(new LiveGuide.Program(text(rows, "title", 240), rows.getLong(rows.getColumnIndexOrThrow("start_time_utc_millis")),
                            rows.getLong(rows.getColumnIndexOrThrow("end_time_utc_millis"))));
                } catch (IllegalArgumentException malformed) { }
            }
        } catch (OperationCanceledException cancelled) { throw cancelled; }
        catch (RuntimeException inaccessible) { return unavailable(channel); }
        return LiveGuide.select(channel, programs, now);
    }
    private static LiveGuide.Listing unavailable(LiveGuide.Channel channel) { return new LiveGuide.Listing(channel, LiveGuide.State.UNAVAILABLE, null, null); }
    private static String text(Cursor rows, String column, int max) {
        String value = rows.getString(rows.getColumnIndexOrThrow(column));
        return value == null || value.length() > max ? "" : value;
    }
    private LiveTvRepository() {}
}
