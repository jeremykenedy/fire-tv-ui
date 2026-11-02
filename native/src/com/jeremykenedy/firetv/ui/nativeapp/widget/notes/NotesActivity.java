package com.jeremykenedy.firetv.ui.nativeapp.widget.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;

public final class NotesActivity extends Activity {
    private boolean startupBlocked;
    private LinearLayout body;
    private String focusKey = "add-note";
    private View desiredFocus;
    private String renderFocus;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) focusKey = state.getString("focus", "add-note");
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        super.onSaveInstanceState(state); state.putString("focus", focusKey);
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); render(); }
    private void render() {
        desiredFocus = null; renderFocus = focusKey;
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Household notes and lists", 30));
        page.addView(Pages.text(this, "Stored only in this launcher on this TV. Anyone using the launcher can open this screen. No accounts, sharing or sync.", 16));
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        final NotesOptions options;
        try { options = NotesPreferences.load(this); }
        catch (IllegalStateException error) { body.addView(Pages.text(this, error.getMessage(), 20)); return; }
        if (!options.configured) body.addView(Pages.text(this, "Add a note or list item to get started. Home hides details by default. This widget starts hidden in Home layout.", 18));
        add("add-note", "Add note", new Runnable() { @Override public void run() { edit(null, false); } });
        add("add-item", "Add checklist item", new Runnable() { @Override public void run() { edit(null, true); } });
        add("privacy", "Home details: " + (options.summaryOnly ? "Hidden (summary only)" : "Visible"), new Runnable() {
            @Override public void run() {
                if (options.summaryOnly) new AlertDialog.Builder(NotesActivity.this).setTitle("Show notes on Home?")
                        .setMessage("Up to three item previews will be visible to anyone looking at this TV.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Show details", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { privacy(false, options.allowExport); }
                        }).show();
                else privacy(true, options.allowExport);
            }
        });
        add("export", "Portable backups: " + (options.allowExport ? "Private notes allowed" : "Private notes excluded"), new Runnable() {
            @Override public void run() {
                if (options.allowExport) privacy(options.summaryOnly, false);
                else new AlertDialog.Builder(NotesActivity.this).setTitle("Allow private notes in backups?")
                        .setMessage("A portable backup may contain every note and checklist item as readable text. Anyone with that file can read them. This does not send a backup or enable sync.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Allow in backups", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { privacy(options.summaryOnly, true); }
                        }).show();
            }
        });
        if (options.items.isEmpty()) body.addView(Pages.text(this, "No notes or checklist items yet.", 20));
        else body.addView(Pages.text(this, options.summary() + ". Select a checklist item to check or uncheck it. Hold any item for controls.", 16));
        for (final NotesOptions.Item item : options.items) {
            Button button = add(item.id, (item.checklist ? (item.checked ? "[x] " : "[ ] ") : "Note: ") + item.text, new Runnable() {
                @Override public void run() { if (item.checklist) mutate(item.id, 0); else actions(item); }
            });
            button.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) { focusKey = item.id; actions(item); return true; }
            });
        }
        if (desiredFocus != null) desiredFocus.requestFocus(); else body.getChildAt(0).requestFocus();
    }
    private void privacy(boolean summary, boolean export) {
        try { save(NotesPreferences.load(this).privacy(summary, export)); }
        catch (IllegalArgumentException | IllegalStateException error) { toast(error); }
    }
    private Button add(final String key, String label, final Runnable action) {
        Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; action.run(); }
        });
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { if (focused) focusKey = key; }
        });
        button.setTag("notes:" + key);
        body.addView(button); if (key.equals(renderFocus)) desiredFocus = button; return button;
    }
    private void actions(final NotesOptions.Item item) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(item.checklist ? "Checklist item controls" : "Note controls")
                .setItems(new String[] {"Edit text", "Move earlier", "Move later", "Delete"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) edit(item, item.checklist);
                        else if (which == 3) new AlertDialog.Builder(NotesActivity.this).setTitle("Delete this item?")
                                .setMessage("This removes the item from this TV.").setNegativeButton("Cancel", null)
                                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) { mutate(item.id, 3); }
                                }).show();
                        else mutate(item.id, which);
                    }
                }).setNegativeButton("Cancel", null).show();
        dialog.getListView().requestFocus(); dialog.getListView().setSelection(0);
    }
    private void mutate(String id, int action) {
        try {
            NotesOptions old = NotesPreferences.load(this);
            NotesOptions next = action == 0 ? old.toggle(id) : action == 3 ? old.remove(id) : old.move(id, action == 1 ? -1 : 1);
            if (action == 3) {
                int oldIndex = 0;
                for (int i = 0; i < old.items.size(); i++) if (old.items.get(i).id.equals(id)) oldIndex = i;
                focusKey = next.items.isEmpty() ? "add-note" : next.items.get(Math.min(oldIndex, next.items.size() - 1)).id;
            }
            save(next);
        } catch (IllegalArgumentException | IllegalStateException error) { toast(error); }
    }
    private void edit(final NotesOptions.Item item, final boolean checklist) {
        final EditText input = new EditText(this); input.setSingleLine(false); input.setMaxLines(5);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(NotesOptions.MAX_TEXT)});
        input.setHint("1 to 512 characters");
        if (item != null) { input.setText(item.text); input.selectAll(); }
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(item != null ? "Edit text" : checklist ? "Add checklist item" : "Add note")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                input.requestFocus();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            NotesOptions old = NotesPreferences.load(NotesActivity.this);
                            NotesOptions next = item == null ? old.add(input.getText().toString(), checklist) : old.edit(item.id, input.getText().toString());
                            NotesPreferences.save(NotesActivity.this, next);
                            focusKey = item == null ? next.items.get(next.items.size() - 1).id : item.id;
                            dialog.dismiss(); render();
                        } catch (IllegalArgumentException | IllegalStateException error) { input.setError(error.getMessage()); }
                    }
                });
            }
        }); dialog.show();
    }
    private void save(NotesOptions options) { NotesPreferences.save(this, options); render(); }
    private void toast(Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
}
