package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.view.View;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Named TV launcher setups, with remote-friendly actions and explicit restore scope. */
public final class SavedSetupsActivity extends CardActivity {
    private SavedSetupStore store;
    private LinearLayout body;
    private TextView status;
    private boolean busy;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public static void open(Context context) {
        context.startActivity(new Intent(context, SavedSetupsActivity.class));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new SavedSetupStore(this);
        title("Saved setups");
        TextView description = text("Save your appearance, layout, app arrangement and card presentation. Switch back whenever you like.", 17, MUTED);
        description.setPadding(0, 0, 0, dp(10));
        card.addView(description);
        status = text("", 15, MUTED);
        card.addView(status);
        body = scrollBody();
        load(null);
    }

    private void load(final String focusId) {
        execute(new Job() { public Runnable run() throws Exception {
            final List<JSONObject> items = store.list();
            return new Runnable() { public void run() { render(items, focusId); } };
        } });
    }

    private void render(List<JSONObject> items, String focusId) {
        body.removeAllViews();
        TextView save = button("Save current setup", new View.OnClickListener() { public void onClick(View v) { nameDialog(null, false); } });
        addButton(body, save);
        TextView focus = save;
        for (final JSONObject item : items) {
            String id = item.optString("id");
            String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.optLong("savedAt")));
            TextView row = button(item.optString("name") + "\n" + date, new View.OnClickListener() { public void onClick(View v) { actions(item); } });
            addButton(body, row);
            if (id.equals(focusId)) focus = row;
        }
        TextView scope = text("TV power, sleep, Home protection, account connections and settings inside other apps stay as they are. Still wallpaper and icon images are copied to private TV storage. Video wallpapers keep their source reference, so the original video must remain accessible. Installed icon packs must remain installed. Setups are removed when Fire TV UI app data is cleared or the app is uninstalled.", 14, MUTED);
        scope.setPadding(0, dp(14), 0, 0);
        body.addView(scope);
        status.setText(items.size() + " of " + SavedSetupStore.MAX_SETUPS + " setups saved");
        focus.requestFocus();
    }

    private void actions(final JSONObject item) {
        final String id = item.optString("id");
        new AlertDialog.Builder(this).setTitle(item.optString("name"))
                .setItems(new String[]{"Apply setup", "Rename", "Update with current setup", "Delete"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int selected) {
                        if (selected == 0) applyDialog(item);
                        else if (selected == 1) nameDialog(item, true);
                        else if (selected == 2) confirm("Update saved setup?", "Replace this saved setup with your current UI settings?", "Update", new Job() {
                            public Runnable run() throws Exception {
                                store.save(item.getString("name"), id);
                                return new Runnable() { public void run() { load(id); } };
                            }
                        });
                        else confirm("Delete saved setup?", "Delete " + item.optString("name") + "? Your current TV setup will stay as it is.", "Delete", new Job() {
                            public Runnable run() throws Exception {
                                store.delete(id);
                                return new Runnable() { public void run() { load(null); } };
                            }
                        });
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void nameDialog(final JSONObject item, final boolean rename) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Setup name");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        if (item != null) input.setText(item.optString("name"));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(rename ? "Rename setup" : "Save current setup")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface shown) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View view) {
                final String name = input.getText().toString();
                execute(new Job() { public Runnable run() throws Exception {
                    final String id;
                    if (rename) { id = item.getString("id"); store.rename(id, name); }
                    else id = store.save(name, null);
                    return new Runnable() { public void run() { dialog.dismiss(); load(id); } };
                } });
            } });
        } });
        dialog.show();
        input.requestFocus();
    }

    private void applyDialog(final JSONObject item) {
        final CheckBox icons = new CheckBox(this);
        icons.setText("Apply saved icons too");
        icons.setChecked(true);
        new AlertDialog.Builder(this).setTitle("Apply " + item.optString("name") + "?")
                .setMessage("This replaces your current UI setup. Save your current setup first if you want to return to it.")
                .setView(icons).setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
                    final boolean includeIcons = icons.isChecked();
                    execute(new Job() { public Runnable run() throws Exception {
                        store.apply(item.getString("id"), includeIcons);
                        return new Runnable() { public void run() {
                            Intent home = getPackageManager().getLaunchIntentForPackage(getPackageName());
                            if (home != null) {
                                home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(home);
                                finish();
                            } else { refreshMenuBackground(); load(item.optString("id")); }
                        } };
                    } });
                } }).show();
    }

    private void confirm(String heading, String message, String action, final Job job) {
        new AlertDialog.Builder(this).setTitle(heading).setMessage(message)
                .setNegativeButton("Cancel", null).setPositiveButton(action, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { execute(job); }
                }).show();
    }

    private interface Job { Runnable run() throws Exception; }

    private void execute(final Job job) {
        if (busy) return;
        busy = true;
        enableActions(false);
        status.setText("Saving or loading your setup...");
        worker.execute(new Runnable() { public void run() {
            try {
                final Runnable complete = job.run();
                runOnUiThread(new Runnable() { public void run() {
                    busy = false;
                    enableActions(true);
                    if (!isFinishing() && !isDestroyed()) complete.run();
                } });
            } catch (final Exception failure) {
                runOnUiThread(new Runnable() { public void run() {
                    busy = false;
                    enableActions(true);
                    if (!isFinishing() && !isDestroyed()) { status.setText("Could not complete the change."); error(failure); }
                } });
            }
        } });
    }

    private void enableActions(boolean enabled) {
        for (int i = 0; i < body.getChildCount(); i++) {
            View child = body.getChildAt(i);
            if (child.isClickable()) { child.setEnabled(enabled); child.setFocusable(enabled); }
        }
    }

    @Override public void onBackPressed() {
        if (busy) { status.setText("Finishing your setup change..."); return; }
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
