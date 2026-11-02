package com.jeremykenedy.firetv.ui.nativeapp.widget.reminders;

import android.content.Context;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;

public final class RemindersTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException | IllegalStateException expected) {}
    }
    private static long instant(String value) { return Instant.parse(value).toEpochMilli(); }
    private static RemindersOptions.Item item(String date, String time, RemindersOptions.Repeat repeat) {
        return new RemindersOptions.Item("fixture", "Private reminder", LocalDate.parse(date), LocalTime.parse(time), "America/Los_Angeles", repeat, false, 0);
    }
    public static void main(String[] args) {
        Context context = new Context();
        RemindersOptions initial = RemindersPreferences.load(context);
        check(!initial.enabled && initial.summaryOnly && !initial.allowExport && initial.items.isEmpty(), "Disabled private defaults");
        rejects(() -> RemindersPreferences.portableDocument(context));
        RemindersOptions added = initial.add("Private plant reminder 😀", LocalDate.of(2026, 3, 7), LocalTime.of(9, 0), "America/Los_Angeles", RemindersOptions.Repeat.DAILY);
        String id = added.items.get(0).id;
        long now = instant("2026-03-07T17:00:00Z");
        check(!added.homeText(now).contains("Private"), "Disabled Home does not expose label");
        RemindersOptions enabled = added.settings(true, true, false);
        check(enabled.dueCount(now - 1) == 0 && enabled.dueCount(now) == 1, "Exact due boundary");
        check(enabled.homeText(now).contains("1 due") && !enabled.homeText(now).contains("Private"), "Counts-only Home");
        check(enabled.settings(true, false, false).homeText(now).contains("Private plant"), "Explicit label display");
        RemindersOptions snoozed = enabled.snooze(id, now);
        check(snoozed.find(id).dueAt() == now + 3600000 && snoozed.dueCount(now) == 0, "Snooze one actual hour");
        check(snoozed.find(id).status(now).startsWith("Snoozed until"), "Honest snoozed state");
        rejects(() -> snoozed.snooze(id, now));
        RemindersOptions completed = snoozed.complete(id, now + 3600000);
        check(completed.find(id).date.equals(LocalDate.of(2026, 3, 8)) && completed.find(id).time.equals(LocalTime.of(9, 0)) && completed.find(id).snoozeUntil == 0, "Completion advances local recurrence and clears snooze");
        check(completed.find(id).scheduledAt() - enabled.find(id).scheduledAt() == 23 * 3600000L, "Daily repeat across DST keeps wall clock, not 24-hour duration");
        check(completed.find(id).id.equals(id), "Completion preserves stable identity");
        RemindersOptions.Item gap = item("2026-03-08", "02:30", RemindersOptions.Repeat.DAILY);
        check(gap.scheduledAt() == instant("2026-03-08T10:30:00Z"), "DST gap shifts forward to 03:30 PDT");
        check(gap.complete(gap.scheduledAt()).scheduledAt() == instant("2026-03-09T09:30:00Z"), "After gap, daily repeat returns to intended 02:30");
        RemindersOptions.Item overlap = item("2026-11-01", "01:30", RemindersOptions.Repeat.WEEKLY);
        check(overlap.scheduledAt() == instant("2026-11-01T08:30:00Z"), "DST overlap chooses earlier offset");
        check(overlap.complete(overlap.scheduledAt()).scheduledAt() - overlap.scheduledAt() == 169 * 3600000L, "Weekly recurrence across fall DST keeps local clock");
        RemindersOptions.Item old = item("2020-01-01", "09:00", RemindersOptions.Repeat.WEEKLY);
        long late = instant("2026-11-01T23:00:00Z");
        RemindersOptions.Item next = old.complete(late);
        check(next.scheduledAt() > late && next.date.getDayOfWeek() == old.date.getDayOfWeek(), "Missed weeks skipped without losing selected weekday");
        check(java.time.temporal.ChronoUnit.DAYS.between(old.date, next.date) % 7 == 0, "Weekly cadence preserved");
        RemindersOptions.Item once = item("2026-03-07", "09:00", RemindersOptions.Repeat.ONCE).complete(now);
        check(once.completed && !once.due(now + 99999999), "One-time completion stays complete");
        rejects(() -> once.snooze(now));
        RemindersOptions edited = enabled.edit(id, "Changed label", LocalDate.of(2026, 5, 1), LocalTime.of(7, 30), "Europe/London", RemindersOptions.Repeat.WEEKLY);
        check(edited.items.get(0).id.equals(id) && edited.items.get(0).zone.getId().equals("Europe/London"), "Edit preserves identity and explicit zone");
        check(edited.items.get(0).scheduledAt() == instant("2026-05-01T06:30:00Z"), "Selected timezone determines actual instant");
        RemindersPreferences.save(context, snoozed);
        check(RemindersPreferences.encode(RemindersPreferences.load(context)).equals(RemindersPreferences.encode(snoozed)), "Exact persistence with active snooze");
        RemindersOptions export = snoozed.settings(true, false, true);
        RemindersPreferences.save(context, export);
        check(RemindersPreferences.portableDocument(context).contains("Private plant"), "Explicit export consent");
        String before = RemindersPreferences.encode(export);
        context.prefs.failNext = true;
        rejects(() -> RemindersPreferences.save(context, export.remove(id)));
        check(RemindersPreferences.encode(RemindersPreferences.load(context)).equals(before), "Failed commit restores prior memory state");
        check(export.remove(id).items.isEmpty(), "Delete leaves configured empty state");
        rejects(() -> new RemindersOptions.Item("x", " ", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), "UTC", RemindersOptions.Repeat.ONCE, false, 0));
        rejects(() -> initial.add("test", LocalDate.of(2101, 1, 1), LocalTime.of(9, 0), "UTC", RemindersOptions.Repeat.ONCE));
        rejects(() -> initial.add("test", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0, 1), "UTC", RemindersOptions.Repeat.ONCE));
        rejects(() -> initial.add("test", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), "unknown/zone", RemindersOptions.Repeat.ONCE));
        rejects(() -> initial.add("bad\nlabel", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), "UTC", RemindersOptions.Repeat.ONCE));
        rejects(() -> new RemindersOptions(true, true, false, Arrays.asList(enabled.items.get(0), enabled.items.get(0))));
        RemindersOptions many = initial;
        for (int i = 0; i < 64; i++) many = many.add("Reminder " + i, LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), "UTC", RemindersOptions.Repeat.ONCE);
        final RemindersOptions full = many;
        rejects(() -> full.add("Too many", LocalDate.of(2026, 1, 1), LocalTime.of(9, 0), "UTC", RemindersOptions.Repeat.ONCE));
        check(RemindersPreferences.decode(RemindersPreferences.encode(full)).items.size() == 64, "Bounded maximum collection roundtrip");
        final String valid = RemindersPreferences.encode(snoozed);
        rejects(() -> RemindersPreferences.decode(valid.replace("\"enabled\":true", "\"enabled\":1")));
        rejects(() -> RemindersPreferences.decode(valid.replace("\"completed\":false", "\"completed\":\"false\"")));
        rejects(() -> RemindersPreferences.decode(valid.replace("\"version\":1", "\"version\":1,\"version\":1")));
        rejects(() -> RemindersPreferences.decode(valid.replace("\"version\":1", "\"version\":2")));
        rejects(() -> RemindersPreferences.decode(valid.replace("\"repeat\":\"DAILY\"", "\"repeat\":\"MONTHLY\"")));
        rejects(() -> RemindersPreferences.decode(valid.replace("\"snoozeUntil\":" + snoozed.find(id).snoozeUntil, "\"snoozeUntil\":1.5")));
        context.prefs.values.put(RemindersPreferences.KEY, Boolean.TRUE);
        rejects(() -> RemindersPreferences.load(context));
        check(Boolean.TRUE.equals(context.prefs.values.get(RemindersPreferences.KEY)), "Unreadable data retained");
        System.out.println("Reminders recurrence, DST, due/snooze, privacy, persistence and bounds pass");
    }
}
