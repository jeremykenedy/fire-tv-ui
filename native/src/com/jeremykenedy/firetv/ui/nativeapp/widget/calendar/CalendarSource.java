package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.CalendarContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class CalendarSource {
    public static final String PERMISSION = Manifest.permission.READ_CALENDAR;
    public static final int MAX_CALENDARS = 128;
    private static final String[] CALENDARS = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL};
    private static final String[] INSTANCES = {CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.STATUS};
    private CalendarSource() {}
    public interface Query {
        Cursor query(Uri uri, String[] projection, String selection, String[] args, String order, CancellationSignal signal);
    }
    public static boolean permitted(Context context) { return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED; }
    public static boolean available(Context context) { return context.getPackageManager().resolveContentProvider(CalendarContract.AUTHORITY, 0) != null; }
    public static CalendarData.Result read(Context context, CalendarOptions options, long now, CancellationSignal signal) {
        final ContentResolver resolver = context.getContentResolver();
        return read(new Query() {
            @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order, CancellationSignal cancellation) {
                return resolver.query(uri, projection, selection, args, order, cancellation);
            }
        }, permitted(context), options, now, signal);
    }
    public static CalendarData.Result read(Query query, boolean permission, CalendarOptions options, long now, CancellationSignal signal) {
        List<CalendarData.Calendar> calendars = new ArrayList<>(); List<CalendarData.Event> events = new ArrayList<>();
        if (!permission) return result(CalendarData.State.PERMISSION_REQUIRED, calendars, events, now);
        boolean partial = false;
        try {
            signal.throwIfCanceled();
            HashSet<Long> known = new HashSet<>(); int scanned = 0;
            try (Cursor rows = query.query(CalendarContract.Calendars.CONTENT_URI, CALENDARS, null, null,
                    CalendarContract.Calendars._ID + " ASC", signal)) {
                if (rows == null) return result(CalendarData.State.UNAVAILABLE, calendars, events, now);
                int id = rows.getColumnIndexOrThrow(CalendarContract.Calendars._ID);
                int name = rows.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
                int access = rows.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL);
                while (rows.moveToNext()) {
                    signal.throwIfCanceled();
                    if (++scanned > CalendarData.MAX_SCANNED || calendars.size() >= MAX_CALENDARS) { partial = true; break; }
                    if (rows.getType(id) != Cursor.FIELD_TYPE_INTEGER || rows.isNull(access) || rows.getInt(access) < CalendarContract.Calendars.CAL_ACCESS_READ) continue;
                    long key = rows.getLong(id); if (key < 0 || !known.add(key)) { partial = true; continue; }
                    calendars.add(new CalendarData.Calendar(key, rows.getString(name)));
                }
            }
            if (options.selected.isEmpty()) return result(CalendarData.State.SETUP_REQUIRED, calendars, events, now);
            List<Long> selected = new ArrayList<>();
            for (Long id : options.selected) { if (known.contains(id)) selected.add(id); else partial = true; }
            if (selected.isEmpty()) return result(CalendarData.State.PARTIAL, calendars, events, now);
            Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri, Math.max(0, now - 2 * 86400000L));
            ContentUris.appendId(uri, CalendarData.endOfWindow(now, options.zone) + 2 * 86400000L);
            StringBuilder selection = new StringBuilder(CalendarContract.Instances.CALENDAR_ID).append(" IN (");
            String[] args = new String[selected.size()];
            for (int i = 0; i < selected.size(); i++) { if (i > 0) selection.append(','); selection.append('?'); args[i] = Long.toString(selected.get(i)); }
            selection.append(')');
            List<String> projection = new ArrayList<>(); Collections.addAll(projection, INSTANCES);
            if (!options.summaryOnly) projection.add(CalendarContract.Instances.TITLE);
            scanned = 0;
            try (Cursor rows = query.query(uri.build(), projection.toArray(new String[projection.size()]), selection.toString(), args,
                    CalendarContract.Instances.BEGIN + " ASC", signal)) {
                if (rows == null) return result(CalendarData.State.UNAVAILABLE, calendars, events, now);
                int calendar = rows.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID);
                int event = rows.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID);
                int begin = rows.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN);
                int end = rows.getColumnIndexOrThrow(CalendarContract.Instances.END);
                int day = rows.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY);
                int status = rows.getColumnIndexOrThrow(CalendarContract.Instances.STATUS);
                int title = options.summaryOnly ? -1 : rows.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
                while (rows.moveToNext()) {
                    signal.throwIfCanceled();
                    if (++scanned > CalendarData.MAX_SCANNED) { partial = true; break; }
                    if (!rows.isNull(status) && rows.getInt(status) == CalendarContract.Events.STATUS_CANCELED) continue;
                    if (rows.getType(calendar) != Cursor.FIELD_TYPE_INTEGER || rows.getType(event) != Cursor.FIELD_TYPE_INTEGER
                            || rows.getType(begin) != Cursor.FIELD_TYPE_INTEGER || rows.getType(end) != Cursor.FIELD_TYPE_INTEGER
                            || rows.getType(day) != Cursor.FIELD_TYPE_INTEGER || rows.getInt(day) < 0 || rows.getInt(day) > 1) { partial = true; continue; }
                    long calendarId = rows.getLong(calendar);
                    if (!selected.contains(calendarId)) continue;
                    try {
                        CalendarData.Event item = new CalendarData.Event(calendarId, rows.getLong(event), rows.getLong(begin), rows.getLong(end),
                                rows.getInt(day) == 1, title < 0 ? "" : rows.getString(title));
                        if (item.overlaps(now, options)) events.add(item);
                    } catch (IllegalArgumentException malformed) { partial = true; }
                }
            }
            List<CalendarData.Event> visible = CalendarData.visible(events, options, now);
            if (visible.size() > CalendarData.MAX_EVENTS) { partial = true; visible = new ArrayList<>(visible.subList(0, CalendarData.MAX_EVENTS)); }
            return result(partial ? CalendarData.State.PARTIAL : visible.isEmpty() ? CalendarData.State.EMPTY : CalendarData.State.READY, calendars, visible, now);
        } catch (OperationCanceledException canceled) { throw canceled; }
        catch (SecurityException denied) { return result(CalendarData.State.PERMISSION_REQUIRED, Collections.<CalendarData.Calendar>emptyList(), Collections.<CalendarData.Event>emptyList(), now); }
        catch (RuntimeException unavailable) { return result(CalendarData.State.UNAVAILABLE, Collections.<CalendarData.Calendar>emptyList(), Collections.<CalendarData.Event>emptyList(), now); }
    }
    static CalendarData.Result result(CalendarData.State state, List<CalendarData.Calendar> calendars, List<CalendarData.Event> events, long now) {
        return new CalendarData.Result(state, calendars, events, now);
    }
}
