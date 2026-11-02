package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;
import com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidPresentationInventory;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import java.util.Map;
import java.util.Collections;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local setup management using standard TV focusable controls. */
public final class SavedSetupsActivity extends Activity {
    private boolean startupBlocked;
    private SavedSetupManager manager;
    private LinearLayout rows;
    private boolean includeIcons = true;
    private boolean destroyed;
    private AlertDialog pending;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    public void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; }
        destroyed = true;
        if (pending != null) pending.dismiss();
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        manager = new SavedSetupManager(this);
        refresh();
    }

    private void refresh() {
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(48, 32, 48, 32);
        scroll.addView(rows);
        setContentView(scroll);
        TextView title = new TextView(this);
        title.setText("Saved setups");
        title.setTextSize(26);
        rows.addView(title);
        TextView detail = new TextView(this);
        detail.setText(
                "Save your appearance, app arrangement, folders and artwork on this TV. Account"
                        + " connections and TV controls are excluded.");
        rows.addView(detail);
        CheckBox artwork = new CheckBox(this);
        artwork.setText("Include app icon choices when applying");
        artwork.setChecked(includeIcons);
        artwork.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        includeIcons = checked;
                    }
                });
        rows.addView(artwork);
        button(
                "Save current setup",
                new Runnable() {
                    @Override
                    public void run() {
                        name(null, null, false);
                    }
                });
        if (PresentationState.hasRecovery(this))
            button(
                    "Restore interrupted setup change",
                    new Runnable() {
                        @Override
                        public void run() {
                            runAction(
                                    new Action() {
                                        @Override
                                        public void run() throws Exception {
                                            PresentationState.recover(SavedSetupsActivity.this);
                                        }
                                    },
                                    "Previous presentation restored");
                        }
                    });
        try {
            for (JSONObject item : manager.list()) {
                final String id = item.getString("id"), name = item.getString("name");
                button(
                        name,
                        new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(SavedSetupsActivity.this)
                                        .setTitle(name)
                                        .setItems(
                                                new String[] {
                                                    "Apply",
                                                    "Update from current setup",
                                                    "Rename",
                                                    "Delete"
                                                },
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            DialogInterface dialog, int index) {
                                                        if (index == 0)
                                                            runAction(
                                                                    new Action() {
                                                                        @Override
                                                                        public void run()
                                                                                throws Exception {
                                                                            PresentationMappingContext identities = PresentationMappingContext.empty();
                                                                            if (!item.has("native")) {
                                                                                Map<String, Object> legacy = LegacySetupSettings.validate(item.getJSONObject("settings"));
                                                                                legacy.values().removeAll(Collections.singleton(null));
                                                                                identities = AndroidPresentationInventory.read(SavedSetupsActivity.this, new PreferenceSnapshot(legacy));
                                                                            }
                                                                            manager.apply(id, includeIcons, identities);
                                                                        }
                                                                    },
                                                                    "Setup applied");
                                                        else if (index == 1)
                                                            confirm(
                                                                    "Replace this saved setup with"
                                                                            + " your current"
                                                                            + " presentation?",
                                                                    "Update",
                                                                    new Action() {
                                                                        @Override
                                                                        public void run()
                                                                                throws Exception {
                                                                            manager.save(name, id);
                                                                        }
                                                                    });
                                                        else if (index == 2) name(id, name, true);
                                                        else
                                                            confirm(
                                                                    "Delete " + name + "?",
                                                                    "Delete",
                                                                    new Action() {
                                                                        @Override
                                                                        public void run()
                                                                                throws Exception {
                                                                            manager.delete(id);
                                                                        }
                                                                    });
                                                    }
                                                })
                                        .show();
                            }
                        });
            }
        } catch (Exception failure) {
            error(failure);
        }
    }

    private void confirm(String message, String label, final Action action) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(
                        label,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                runAction(action, "Saved setups updated");
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void button(String label, final Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        action.run();
                    }
                });
        rows.addView(button);
    }

    private void name(final String id, String previous, final boolean rename) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Setup name");
        if (previous != null) input.setText(previous);
        final AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(rename ? "Rename setup" : "Save current setup")
                        .setView(input)
                        .setPositiveButton("Save", null)
                        .setNegativeButton("Cancel", null)
                        .create();
        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface ignored) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                final String chosen = input.getText().toString();
                                                perform(
                                                        new Action() {
                                                            @Override
                                                            public void run() throws Exception {
                                                                if (rename)
                                                                    manager.rename(id, chosen);
                                                                else manager.save(chosen, id);
                                                            }
                                                        },
                                                        new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                dialog.dismiss();
                                                                refresh();
                                                            }
                                                        });
                                            }
                                        });
                    }
                });
        dialog.show();
    }

    private interface Action {
        void run() throws Exception;
    }

    private void runAction(Action action, final String message) {
        perform(
                action,
                new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SavedSetupsActivity.this, message, Toast.LENGTH_SHORT)
                                .show();
                        refresh();
                    }
                });
    }

    private void perform(final Action action, final Runnable success) {
        final AlertDialog progress =
                new AlertDialog.Builder(this).setMessage("Saving presentation...").create();
        pending = progress;
        progress.setCancelable(false);
        progress.show();
        worker.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        Exception failed = null;
                        try {
                            action.run();
                        } catch (Exception failure) {
                            failed = failure;
                        }
                        final Exception result = failed;
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (destroyed) return;
                                        progress.dismiss();
                                        if (result == null) success.run();
                                        else error(result);
                                    }
                                });
                    }
                });
    }

    private void error(Exception failure) {
        new AlertDialog.Builder(this)
                .setTitle("Setup unavailable")
                .setMessage(failure.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }
}
