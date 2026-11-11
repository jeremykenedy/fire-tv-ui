package com.jeremykenedy.firetv.ui.nativeapp.backup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;
import com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit document selection with validated presentation and private rollback. */
public final class BackupActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private static final int EXPORT = 71, IMPORT = 72;
    static final PortableBackup.Validator VALIDATOR = new PortableBackup.Validator() {
        @Override public void validate(Map<String, PreferenceSnapshot> stores) throws Exception {
            PresentationState.validatePortable(stores);
        }
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button exportButton, importButton, recoveryButton;
    private CheckBox notesOption, remindersOption;
    private boolean busy, destroyed;

    public static void open(Context context) {
        Intent intent = new Intent(context, BackupActivity.class);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.page(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(Pages.text(this, "Backup and restore", 28));
        body.addView(Pages.text(this, "Export saves launcher layout, app organization, card choices and appearance in a document you select. Import replaces those choices after you confirm. Accounts, passwords, device settings and private connection files stay on this TV.", 18));
        body.addView(Pages.text(this, "Original launcher backups can restore supported presentation choices on this TV after review. Files containing unsupported settings are refused as a whole. Personal photos, videos, timers, location and quick actions stay local. Portable export requires built-in artwork. Notes and reminders are included only when selected below and allowed in their settings.", 16));
        notesOption = new CheckBox(this); notesOption.setTextColor(android.graphics.Color.WHITE); notesOption.setText("Include notes in this export (requires local notes export permission)");
        remindersOption = new CheckBox(this); remindersOption.setTextColor(android.graphics.Color.WHITE); remindersOption.setText("Include reminders in this export (requires local reminders export permission)");
        if (state != null) {
            notesOption.setChecked(state.getBoolean("includeNotes", false));
            remindersOption.setChecked(state.getBoolean("includeReminders", false));
        }
        body.addView(notesOption); body.addView(remindersOption);
        status = Pages.text(this, "Choose a document action. Press Back to return.", 16);
        body.addView(status);
        exportButton = Pages.button(this, "Export presentation backup", new View.OnClickListener() {
            @Override public void onClick(View view) { choose(EXPORT); }
        });
        importButton = Pages.button(this, "Import presentation backup", new View.OnClickListener() {
            @Override public void onClick(View view) { choose(IMPORT); }
        });
        body.addView(exportButton); body.addView(importButton);
        recoveryButton = Pages.button(this, "Restore interrupted presentation change", new View.OnClickListener() {
            @Override public void onClick(View view) { confirmRecovery(); }
        });
        body.addView(recoveryButton);
        recoveryButton.setVisibility(hasRecovery() ? View.VISIBLE : View.GONE);
        finishWork(hasRecovery() ? "Restore the interrupted presentation change before making another backup or import."
                : "Choose a document action. Press Back to return.");
        if (hasRecovery()) recoveryButton.requestFocus(); else exportButton.requestFocus();
    }
    private boolean hasRecovery() { return BackupTransaction.hasRecovery(this) || PresentationState.hasRecovery(this); }
    private boolean ready() { return com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(this); }
    private void choose(int request) {
        if (busy || !ready()) return;
        if (hasRecovery()) { finishWork("Restore the interrupted presentation change first."); return; }
        Intent intent = new Intent(request == EXPORT ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType(request == EXPORT ? "text/plain" : "*/*");
        if (request == EXPORT) intent.putExtra(Intent.EXTRA_TITLE, PortableBackup.BACKUP_NAME);
        try { setBusy(true); startActivityForResult(intent, request); }
        catch (ActivityNotFoundException unavailable) { finishWork("No document picker is available on this device."); }
        catch (SecurityException denied) { finishWork("The document picker could not be opened."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (startupBlocked) { super.onActivityResult(request, result, data); return; }
        super.onActivityResult(request, result, data);
        if ((request != EXPORT && request != IMPORT) || !ready()) return;
        if (result != RESULT_OK) { finishWork("Canceled. Presentation settings were not changed."); return; }
        Uri uri = data == null ? null : data.getData();
        if (uri == null || !"content".equals(uri.getScheme())) {
            finishWork("Choose a document from an Android document provider."); return;
        }
        if (request == EXPORT) export(uri); else inspect(uri);
    }
    private void export(final Uri uri) {
        final boolean notes = notesOption.isChecked(), reminders = remindersOption.isChecked();
        notesOption.setChecked(false); remindersOption.setChecked(false);
        worker.execute(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    if (!ready() || hasRecovery()) throw new java.io.IOException("Restore the interrupted presentation change first.");
                    byte[] bytes = new BackupPayload(PresentationState.capturePortable(BackupActivity.this),
                            HouseholdContent.capture(BackupActivity.this, notes, reminders)).encode(VALIDATOR);
                    try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException();
                        output.write(bytes); output.flush();
                    }
                    message = "Presentation backup exported to your selected document.";
                } catch (Exception failed) {
                    message = "Export failed. The selected document may be incomplete. No settings were changed. Personal images require a named local setup; household exports require local permission.";
                }
                report(message);
            }
        });
    }
    private void inspect(final Uri uri) {
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final byte[] bytes;
                    try (InputStream input = getContentResolver().openInputStream(uri)) { bytes = OriginalBackup.read(input); }
                    final OriginalBackup original = OriginalBackup.decode(bytes);
                    if (original != null) {
                        String problem = null;
                        if (original.unsupported.isEmpty()) {
                            try { new com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupManager(BackupActivity.this).prepareOriginalBackup(original.settings); }
                            catch (Exception unavailable) { problem = unavailable instanceof java.io.IOException ? unavailable.getMessage()
                                    : "Some presentation choices are invalid or unavailable on this TV. Nothing was changed."; }
                        }
                        final String reason = problem;
                        runOnUiThread(new Runnable() { @Override public void run() { if (!destroyed) confirmOriginal(original,reason); } });
                    } else {
                        final BackupPayload payload = BackupPayload.decode(bytes, VALIDATOR);
                        runOnUiThread(new Runnable() { @Override public void run() { if (!destroyed) confirmImport(payload); } });
                    }
                } catch (Exception failed) {
                    report(failed instanceof IllegalArgumentException ? failed.getMessage()
                            : "The selected document could not be read. Nothing was changed.");
                }
            }
        });
    }
    private void confirmOriginal(final OriginalBackup original, String problem) {
        boolean allowed = original.unsupported.isEmpty() && problem == null;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(allowed ? "Import original launcher backup?" : "Original backup cannot be imported")
                .setMessage(original.summary() + (problem == null ? "" : "\n\n" + problem))
                .setNegativeButton(allowed ? "Cancel" : "Close", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog,int which) { finishWork("Original backup review closed. No settings were changed."); }
                }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { finishWork("Original backup review closed. No settings were changed."); }
                });
        if (allowed) dialog.setPositiveButton("Import",new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog,int which) { applyOriginal(original); }
        });
        dialog.show();
    }
    private void applyOriginal(final OriginalBackup original) {
        if (!ready()) return;
        setBusy(true);
        worker.execute(new Runnable() { @Override public void run() {
            String message;
            try { BackupTransaction.applyOriginal(BackupActivity.this,original); message = "Original launcher presentation imported. Other local settings were preserved."; }
            catch (Exception failed) { message = hasRecovery() ? "Import failed. Restore the interrupted presentation change before importing again."
                    : "Original backup could not be applied. Previous presentation settings were retained or restored."; }
            report(message);
        }});
    }
    private void confirmImport(final BackupPayload payload) {
        new AlertDialog.Builder(this).setTitle("Import presentation backup?")
                .setMessage("This replaces launcher layout, app organization, cards and appearance. Missing choices return to defaults. "
                        + (payload.household.isEmpty() ? "Household content is unchanged. " : "Included household documents replace the selected notes or reminders. Imported details stay hidden, export permission stays off, and reminders stay disabled. ")
                        + "Accounts, device controls and other private files are preserved.")
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { finishWork("Canceled. Presentation settings were not changed."); }
                })
                .setPositiveButton("Import", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { apply(payload); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { finishWork("Canceled. Presentation settings were not changed."); }
                }).show();
    }
    private void apply(final BackupPayload payload) {
        if (!ready()) return;
        setBusy(true);
        worker.execute(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    BackupTransaction.apply(BackupActivity.this, payload);
                    message = "Presentation backup imported.";
                } catch (Exception failed) {
                    message = hasRecovery()
                            ? "Import failed. Restore the interrupted presentation change before importing again."
                            : "Import failed. Previous presentation settings were retained or restored.";
                }
                report(message);
            }
        });
    }
    private void confirmRecovery() {
        if (busy || !ready()) return;
        new AlertDialog.Builder(this).setTitle("Restore previous presentation?")
                .setMessage("This restores presentation choices saved before an interrupted import or setup change.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { recover(); }
                }).show();
    }
    private void recover() {
        setBusy(true);
        worker.execute(new Runnable() {
            @Override public void run() {
                String message;
                try { BackupTransaction.recover(BackupActivity.this); message = "Previous presentation restored."; }
                catch (Exception failed) { message = "Restoration could not be verified. Local recovery was kept."; }
                report(message);
            }
        });
    }
    private void report(final String message) {
        runOnUiThread(new Runnable() {
            @Override public void run() { if (!destroyed) finishWork(message); }
        });
    }
    private void setBusy(boolean value) {
        busy = value;
        boolean available = !value && ready();
        boolean recovery = hasRecovery();
        exportButton.setEnabled(available && !recovery); importButton.setEnabled(available && !recovery);
        recoveryButton.setEnabled(available && recovery);
        notesOption.setEnabled(!value); remindersOption.setEnabled(!value);
        if (value) status.setText("Working with the selected document...");
    }
    private void finishWork(String message) {
        setBusy(false); status.setText(message);
        recoveryButton.setVisibility(hasRecovery() ? View.VISIBLE : View.GONE);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (!startupBlocked) {
            state.putBoolean("includeNotes", notesOption.isChecked());
            state.putBoolean("includeReminders", remindersOption.isChecked());
        }
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } destroyed = true; worker.shutdown(); super.onDestroy(); }
}
