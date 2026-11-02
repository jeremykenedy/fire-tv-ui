package com.jeremykenedy.firetv.ui.nativeapp.widget.notes;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NotesTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IllegalArgumentException | IllegalStateException expected) {}
    }
    public static void main(String[] args) {
        Context context = new Context();
        NotesOptions initial = NotesPreferences.load(context);
        check(!initial.configured && initial.summaryOnly && !initial.allowExport && initial.items.isEmpty(), "Private empty default");
        rejects(() -> NotesPreferences.portableDocument(context));
        NotesOptions added = initial.add("Private\n\"note\" \\ text 😀", false).add("Household checklist", true);
        String note = added.items.get(0).id, task = added.items.get(1).id;
        check(added.configured && added.items.size() == 2 && !note.equals(task), "Created stable separate identities");
        check(!added.homeText().contains("Private") && !added.homeText().contains("Household checklist"), "Home summary redacts text");
        NotesOptions edited = added.edit(note, "Edited text").toggle(task).move(task, -1);
        check(edited.items.get(0).id.equals(task) && edited.find(task).checked && edited.find(note).text.equals("Edited text"), "Edit, check and reorder preserve identity");
        check(!edited.toggle(task).find(task).checked, "Uncheck");
        rejects(() -> edited.toggle(note));
        NotesPreferences.save(context, edited);
        NotesOptions loaded = NotesPreferences.load(context);
        check(NotesPreferences.encode(loaded).equals(NotesPreferences.encode(edited)), "Exact reload preserves item order, identities and completion");
        check(NotesPreferences.decode(NotesPreferences.encode(added)).find(note).text.equals(added.find(note).text), "Unicode, multiline, quotes and slashes round trip");
        NotesOptions visible = loaded.privacy(false, true);
        check(visible.homeText().contains("Edited text"), "Explicit Home detail opt-in");
        NotesPreferences.save(context, visible);
        check(NotesPreferences.portableDocument(context).contains("Edited text"), "Explicit portable content opt-in");
        String before = NotesPreferences.encode(visible);
        context.prefs.failNext = true;
        rejects(() -> NotesPreferences.save(context, visible.remove(note)));
        check(NotesPreferences.encode(NotesPreferences.load(context)).equals(before), "Failed save restores prior in-memory state");
        NotesOptions empty = visible.remove(note).remove(task);
        check(empty.configured && empty.items.isEmpty(), "Last delete remains configured empty");
        rejects(() -> initial.add(" ", false)); rejects(() -> initial.add(new String(new char[513]).replace('\0', 'a'), false));
        rejects(() -> initial.add("bad\u0000text", false)); rejects(() -> initial.add("bad\ud800text", false));
        rejects(() -> new NotesOptions.Item("a", "note", false, true));
        rejects(() -> new NotesOptions(true, true, false, Arrays.asList(added.items.get(0), added.items.get(0))));
        rejects(() -> initial.move("missing", 1));
        NotesOptions full = initial;
        for (int i = 0; i < NotesOptions.MAX_ITEMS; i++) full = full.add("Entry " + i, true);
        final NotesOptions atLimit = full; rejects(() -> atLimit.add("One too many", false));
        check(NotesPreferences.decode(NotesPreferences.encode(full)).items.size() == 64, "Maximum collection roundtrip");
        final String valid = NotesPreferences.encode(added);
        rejects(() -> NotesPreferences.decode(valid.replace("\"summaryOnly\":true", "\"summaryOnly\":\"true\"")));
        rejects(() -> NotesPreferences.decode(valid.replace("\"checked\":false", "\"checked\":0")));
        rejects(() -> NotesPreferences.decode(valid.replace("\"version\":1", "\"version\":2")));
        rejects(() -> NotesPreferences.decode(valid.replace("\"version\":1", "\"version\":1,\"version\":1")));
        rejects(() -> NotesPreferences.decode(valid.replace("\"version\":1", "\"extra\":true,\"version\":1")));
        context.prefs.values.put(NotesPreferences.KEY, Boolean.TRUE);
        rejects(() -> NotesPreferences.load(context));
        check(Boolean.TRUE.equals(context.prefs.values.get(NotesPreferences.KEY)), "Unreadable storage preserved");
        System.out.println("Notes behavior, privacy, bounds, exact types and save recovery pass");
    }
}
