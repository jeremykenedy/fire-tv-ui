package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class QuickActionSettings {
    private QuickActionSettings() {}

    public static void show(final Activity activity) {
        try {
            final List<QuickActions.Entry> entries = new QuickActionStore(activity).read().entries;
            String[] names = new String[entries.size() + 1];
            names[0] = "Add app or content link";
            for (int i = 0; i < entries.size(); i++) names[i + 1] = entries.get(i).label
                    + (QuickActionLaunch.available(activity, entries.get(i)) ? "" : " (unavailable)");
            AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Quick actions")
                    .setItems(names, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) chooseApp(activity);
                            else actions(activity, entries.get(which - 1));
                        }
                    }).setNegativeButton("Done", null).show();
            dialog.getListView().requestFocus(); dialog.getListView().setSelection(0);
        } catch (Exception failed) { unavailable(activity); }
    }

    private static void actions(final Activity activity, final QuickActions.Entry entry) {
        final boolean available = QuickActionLaunch.available(activity, entry);
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(entry.label).setItems(new String[] {
            available ? "Open now" : "Unavailable: app or link is not supported",
            "Edit name or content link", "Move earlier", "Move later", "Remove action"
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    if (available) QuickActionLaunch.open(activity, entry);
                    else new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Action unavailable")
                            .setMessage("The selected app is missing, disabled, or cannot open this HTTPS content link. Your saved action is retained. Edit it or remove it below.")
                            .setPositiveButton("Back", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface ignored, int button) { actions(activity, entry); }
                            }).show();
                } else if (which == 1) edit(activity, entry, false);
                else if (which == 4) new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Remove " + entry.label + "?")
                        .setMessage("This removes only the saved quick action.").setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface ignored, int button) {
                                try { new QuickActionStore(activity).remove(entry.id); show(activity); }
                                catch (Exception failed) { unavailable(activity); }
                            }
                        }).show();
                else try { new QuickActionStore(activity).move(entry.id, which == 2 ? -1 : 1); show(activity); }
                    catch (Exception failed) { unavailable(activity); }
            }
        }).setNegativeButton("Back", new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { show(activity); }
        }).show();
    }

    private static void chooseApp(final Activity activity) {
        Toast.makeText(activity, "Loading installed TV apps…", Toast.LENGTH_SHORT).show();
        final ExecutorService loader = Executors.newSingleThreadExecutor();
        loader.execute(new Runnable() {
            @Override public void run() {
                try {
                    final List<AppInventory.App> apps = AppInventory.load(activity.getApplicationContext());
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (activity.isDestroyed() || activity.isFinishing()) return;
                            if (apps.isEmpty()) { unavailable(activity); return; }
                            String[] labels = new String[apps.size()];
                            for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).label;
                            new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Choose the app that owns this action").setItems(labels,
                                    new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface dialog, int which) {
                                            AppInventory.App app = apps.get(which);
                                            edit(activity, QuickActions.Entry.create(defaultName(app.label), app.key(), ""), true);
                                        }
                                    }).setNegativeButton("Cancel", null).show();
                        }
                    });
                } catch (RuntimeException failed) {
                    activity.runOnUiThread(new Runnable() { @Override public void run() { if (!activity.isDestroyed()) unavailable(activity); } });
                } finally { loader.shutdown(); }
            }
        });
    }

    private static void edit(final Activity activity, final QuickActions.Entry entry, final boolean add) {
        LinearLayout fields = new LinearLayout(activity); fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);
        final EditText name = new EditText(activity); name.setHint("Name"); name.setText(entry.label);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(40)}); name.setSingleLine();
        final EditText link = new EditText(activity); link.setHint("HTTPS content link (optional)"); link.setText(entry.url);
        link.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2048)});
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); link.setSingleLine();
        link.setSaveEnabled(false); link.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        fields.addView(name); fields.addView(link);
        final AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(add ? "Add quick action" : "Edit quick action")
                .setMessage("Leave the link blank to open the app. Only HTTPS content links handled by the selected app are supported. Other schemes and system actions are unavailable. Links stay on this device; do not enter passwords or access tokens. Saving never opens the app.")
                .setView(fields).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                name.requestFocus(); name.selectAll();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            QuickActions.Entry edited = entry.edit(name.getText().toString(), link.getText().toString().trim());
                            if (!edited.url.isEmpty() && !QuickActionLaunch.available(activity, edited)) {
                                link.setError("The selected app cannot open this content link."); return;
                            }
                            new QuickActionStore(activity).put(edited, add); dialog.dismiss(); show(activity);
                        } catch (IllegalArgumentException invalid) { name.setError(invalid.getMessage()); }
                        catch (Exception failed) { unavailable(activity); }
                    }
                });
            }
        });
        dialog.show();
    }

    private static void unavailable(Activity activity) {
        Toast.makeText(activity, "Quick actions are unavailable. Saved actions were not reset.", Toast.LENGTH_LONG).show();
    }

    private static String defaultName(String label) {
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < label.length() && cleaned.length() < 40; i++)
            if (!Character.isISOControl(label.charAt(i))) cleaned.append(label.charAt(i));
        return cleaned.toString().trim().isEmpty() ? "App" : cleaned.toString().trim();
    }
}
