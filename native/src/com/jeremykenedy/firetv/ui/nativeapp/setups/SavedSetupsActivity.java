package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.migration.AndroidPresentationInventory;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PresentationMappingContext;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import java.util.Map;
import java.util.Collections;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local setup management using standard TV focusable controls. */
public final class SavedSetupsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private SavedSetupManager manager;
    private LinearLayout rows;
    private ScrollView scroll;
    private boolean includeIcons = true;
    private boolean destroyed;
    private AlertDialog pending;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("includeIcons", includeIcons);
        super.onSaveInstanceState(state);
    }

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
        if (state != null) includeIcons = state.getBoolean("includeIcons", true);
        manager = new SavedSetupManager(this);
        LinearLayout page = Pages.sidePanel(this);
        TextView heading = new TextView(this);
        heading.setText("Saved setups");
        heading.setTextSize(26);
        heading.setTextColor(0xff8d8d8d);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, Pages.dp(this, 4), 0, Pages.dp(this, 26));
        page.addView(heading);
        scroll = new ScrollView(this);
        scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false); rows.setClipToPadding(false);
        rows.setPadding(0, Pages.dp(this, 4), 0, Pages.dp(this, 8));
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        refresh();
    }

    private void refresh() {
        final int previousScroll = scroll.getScrollY();
        View focused = getCurrentFocus();
        final Object previousTag = focused == null ? null : focused.getTag();
        final int previousIndex = focused == null ? 0 : Math.max(0, rows.indexOfChild(focused));
        rows.removeAllViews();
        rows.addView(Pages.text(this,
                "Save your appearance, app arrangement, folders and artwork on this TV. Account"
                        + " connections and TV controls are excluded.", 16));
        CheckBox artwork = new CheckBox(this);
        artwork.setText("Include app icon choices when applying");
        artwork.setTextColor(android.graphics.Color.WHITE);
        artwork.setTag("artwork");
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
                "Save current setup", "save",
                new Runnable() {
                    @Override
                    public void run() {
                        name(null, null, false);
                    }
                });
        if (PresentationState.hasRecovery(this))
            button(
                    "Restore interrupted setup change", "recovery",
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
                        name, "setup:" + id,
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
        View target = previousTag == null ? null : rows.findViewWithTag(previousTag);
        if (target == null && previousTag != null) {
            for (int i = Math.min(previousIndex, rows.getChildCount() - 1); i >= 0; i--) {
                if (rows.getChildAt(i).isFocusable()) { target = rows.getChildAt(i); break; }
            }
        }
        if (target == null) target = rows.findViewWithTag("save");
        if (target != null) target.requestFocus();
        if (previousTag != null) scroll.post(new Runnable() {
            @Override public void run() { scroll.scrollTo(0, previousScroll); }
        });
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

    private void button(String label, String tag, final Runnable action) {
        Button button = Pages.menuButton(this, label,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        action.run();
                    }
                });
        button.setTag(tag);
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
