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

/** Explicit document selection only. No production preferences or external directory scanning. */
public final class BackupActivity extends Activity {
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
        scroll.addView(body); page.addView(scroll);
        body.addView(Pages.text(this, "Backup and import", 28));
        body.addView(Pages.text(this, "Source preview presentation only. Export saves reviewed layout and appearance choices in a document you select. Import replaces those choices after validation and confirmation. Account credentials, device settings, lock PINs and encrypted connection files stay local.", 18));
        body.addView(Pages.text(this, "Legacy backups are recognized but require verified migration before they can be applied. Personal image files are not included in portable documents.", 16));
        notesOption = new CheckBox(this); notesOption.setTextColor(android.graphics.Color.WHITE); notesOption.setText("Include notes in this export (requires local notes export permission)");
        remindersOption = new CheckBox(this); remindersOption.setTextColor(android.graphics.Color.WHITE); remindersOption.setText("Include reminders in this export (requires local reminders export permission)");
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
        if (!preview()) {
            exportButton.setEnabled(false); importButton.setEnabled(false); recoveryButton.setEnabled(false);
            status.setText("Production backup and import remain unavailable until migration and recovery are verified.");
        } else exportButton.requestFocus();
    }
    private boolean hasRecovery() { return BackupTransaction.hasRecovery(this) || PresentationState.hasRecovery(this); }
    private boolean preview() { return getPackageName().endsWith(".preview"); }
    private void choose(int request) {
        if (busy || !preview()) return;
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
        if ((request != EXPORT && request != IMPORT) || !preview()) return;
        if (result != RESULT_OK) { finishWork("Canceled. Presentation settings were not changed."); return; }
        Uri uri = data == null ? null : data.getData();
        if (uri == null || !"content".equals(uri.getScheme())) {
            finishWork("Choose a document from an Android document provider."); return;
        }
        if (request == EXPORT) export(uri); else inspect(uri);
    }
    private void export(final Uri uri) {
        final boolean notes = notesOption.isChecked(), reminders = remindersOption.isChecked();
        worker.execute(new Runnable() {
            @Override public void run() {
                String message;
                try {
                    byte[] bytes = new BackupPayload(PresentationState.capture(BackupActivity.this),
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
                    final BackupPayload payload;
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        payload = BackupPayload.decode(PortableBackup.read(input), VALIDATOR);
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() { if (!destroyed) confirmImport(payload); }
                    });
                } catch (Exception failed) {
                    report(failed instanceof IllegalArgumentException ? failed.getMessage()
                            : "The selected document could not be read. Nothing was changed.");
                }
            }
        });
    }
    private void confirmImport(final BackupPayload payload) {
        new AlertDialog.Builder(this).setTitle("Import presentation backup?")
                .setMessage("This replaces the reviewed preview presentation settings. Missing choices return to defaults. "
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
        if (!preview()) return;
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
        if (busy || !preview()) return;
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
        busy = value; exportButton.setEnabled(!value); importButton.setEnabled(!value); recoveryButton.setEnabled(!value);
        notesOption.setEnabled(!value); remindersOption.setEnabled(!value);
        if (value) status.setText("Working with the selected document...");
    }
    private void finishWork(String message) {
        setBusy(false); status.setText(message);
        recoveryButton.setVisibility(hasRecovery() ? View.VISIBLE : View.GONE);
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } destroyed = true; worker.shutdown(); super.onDestroy(); }
}
