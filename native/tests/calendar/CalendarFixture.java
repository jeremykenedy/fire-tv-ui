package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.CalendarContract;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Read-only in-memory query fixture. It never installs, seeds or writes a Calendar provider. */
final class CalendarFixture implements CalendarSource.Query {
    private final long now;
    int queries;
    boolean denied;
    boolean missing;
    boolean many;
    boolean malformed;
    boolean titleRequested;
    String lastSelection;
    String[] lastArgs;
    Uri lastUri;
    final List<TrackedCursor> cursors = new ArrayList<>();
    CalendarFixture(long now) { this.now = now; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order, CancellationSignal signal) {
        signal.throwIfCanceled(); queries++;
        if (denied) throw new SecurityException("Fixture denied");
        if (missing) return null;
        TrackedCursor cursor = new TrackedCursor(projection); cursors.add(cursor);
        if (uri.equals(CalendarContract.Calendars.CONTENT_URI)) {
            addCalendar(cursor, projection, 7L, "Fixture calendar", CalendarContract.Calendars.CAL_ACCESS_READ);
            addCalendar(cursor, projection, 8L, "Unselected fixture calendar", CalendarContract.Calendars.CAL_ACCESS_READ);
            addCalendar(cursor, projection, 9L, "Free/busy only", CalendarContract.Calendars.CAL_ACCESS_FREEBUSY);
        } else {
            lastSelection = selection; lastArgs = args; lastUri = uri;
            for (String column : projection) if (column.equals(CalendarContract.Instances.TITLE)) titleRequested = true;
            long start = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            addEvent(cursor, projection, 7L, 1L, now + 3600000, now + 7200000, false, 1, "Private fixture meeting");
            addEvent(cursor, projection, 7L, 1L, now + 3600000, now + 7200000, false, 1, "Duplicate meeting");
            addEvent(cursor, projection, 7L, 2L, start, start + 2 * 86400000L, true, 1, "Private all-day fixture");
            addEvent(cursor, projection, 7L, 3L, now + 3600000, now + 7200000, false, CalendarContract.Events.STATUS_CANCELED, "Cancelled fixture");
            addEvent(cursor, projection, 8L, 4L, now + 3600000, now + 7200000, false, 1, "Unselected private fixture");
            if (malformed) addEvent(cursor, projection, 7L, 5L, now + 7200000, now + 3600000, false, 1, "Malformed fixture");
            if (many) for (int i = 0; i < CalendarData.MAX_SCANNED + 20; i++) addEvent(cursor, projection, 7L, 100L + i,
                    now + 3600000 + i, now + 7200000 + i, false, 1, "Bounded fixture");
        }
        return cursor;
    }
    private void addCalendar(MatrixCursor cursor, String[] columns, long id, String label, int access) {
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(CalendarContract.Calendars._ID)) row[i] = id;
            else if (columns[i].equals(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)) row[i] = label;
            else if (columns[i].equals(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)) row[i] = access;
            else throw new AssertionError("Unexpected calendar projection");
        } cursor.addRow(row);
    }
    private void addEvent(MatrixCursor cursor, String[] columns, long calendar, long id, long begin, long end, boolean day, int status, String title) {
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            String column = columns[i];
            if (column.equals(CalendarContract.Instances.CALENDAR_ID)) row[i] = calendar;
            else if (column.equals(CalendarContract.Instances.EVENT_ID)) row[i] = id;
            else if (column.equals(CalendarContract.Instances.BEGIN)) row[i] = begin;
            else if (column.equals(CalendarContract.Instances.END)) row[i] = end;
            else if (column.equals(CalendarContract.Instances.ALL_DAY)) row[i] = day ? 1 : 0;
            else if (column.equals(CalendarContract.Instances.STATUS)) row[i] = status;
            else if (column.equals(CalendarContract.Instances.TITLE)) row[i] = title;
            else throw new AssertionError("Unexpected event projection");
        } cursor.addRow(row);
    }
    boolean allClosed() { for (TrackedCursor cursor : cursors) if (!cursor.closed) return false; return true; }
    static final class TrackedCursor extends MatrixCursor {
        boolean closed;
        TrackedCursor(String[] projection) { super(projection); }
        @Override public void close() { closed = true; super.close(); }
    }
}
