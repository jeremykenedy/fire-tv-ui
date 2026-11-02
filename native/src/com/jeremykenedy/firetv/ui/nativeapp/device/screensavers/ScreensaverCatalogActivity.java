package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.updates.ReleaseTransport;
import com.jeremykenedy.firetv.ui.nativeapp.updates.UpdateCoordinator;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads only on request, then opens the ordinary installer only after a separate explicit action. */
public final class ScreensaverCatalogActivity extends Activity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private UpdateCoordinator.Cancellation cancellation = new UpdateCoordinator.Cancellation();
    private TextView status;
    private File verified;
    private ScreensaverInstallCatalog.Entry selected;
    private boolean busy, destroyed, installerOpen;
    private String focusPackage = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) focusPackage = state.getString("focus", "");
        String pendingName = getPreferences(0).getString("installer_file", "");
        String pendingPackage = getPreferences(0).getString("installer_package", "");
        if (pendingName.matches("verified-catalog-[A-Za-z0-9_-]+\\.apk")) {
            for (ScreensaverInstallCatalog.Entry entry : ScreensaverInstallCatalog.ENTRIES) {
                if (entry.packageName.equals(pendingPackage)) { selected = entry; verified = new File(getCacheDir(), pendingName); installerOpen = true; break; }
            }
        }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Install screensavers", 28));
        page.addView(Pages.text(this, "Reviewed releases. Downloads and installation require separate choices. Installing never selects an active saver.", 16));
        status = Pages.text(this, "Choose a saver to review its release before downloading.", 16);
        status.setTag("screensaver:catalog-status");
        page.addView(status);
        if (installerOpen) status.setText("An installer handoff is pending. Return from the installer or check the installed result.");
        page.addView(Pages.button(this, "Check installation result", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (verified == null || busy) { status.setText("No staged installation result to check."); return; }
                onActivityResult(610, RESULT_CANCELED, null);
            }
        }));
        page.addView(Pages.button(this, "Cancel download", new View.OnClickListener() {
            @Override public void onClick(View view) { cancellation.cancel(); status.setText("Cancelling download. Return to reopen the catalog before trying again."); }
        }));
        ScrollView scroll = new ScrollView(this);
        LinearLayout entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entries);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        for (final ScreensaverInstallCatalog.Entry entry : ScreensaverInstallCatalog.ENTRIES) {
            View row = Pages.button(this, entry.name + " | " + entry.tag, new View.OnClickListener() {
                @Override public void onClick(View view) { focusPackage = entry.packageName; details(entry); }
            });
            row.setTag("catalog:" + entry.packageName);
            entries.addView(row);
        }
        View focus = page.findViewWithTag("catalog:" + focusPackage);
        if (focus != null) focus.requestFocus();
    }

    private void details(final ScreensaverInstallCatalog.Entry entry) {
        if (busy || installerOpen) { status.setText("Finish or cancel the current operation first."); return; }
        new AlertDialog.Builder(this).setTitle(entry.name).setMessage(entry.description + "\n\n" + entry.repository
                + "\nRelease " + entry.tag + "\n" + ScreensaverMemory.size(entry.bytes)
                + "\nPackage " + entry.packageName + "\nSHA-256 " + entry.sha256)
                .setPositiveButton("Download and verify", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { download(entry); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void download(final ScreensaverInstallCatalog.Entry entry) {
        discard(); selected = entry; busy = true; cancellation = new UpdateCoordinator.Cancellation();
        status.setText("Downloading and verifying " + entry.name + "...");
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final File artifact = CatalogArtifact.download(getCacheDir(), entry,
                            new ReleaseTransport().open(entry.url, entry.bytes, cancellation), new CatalogPackages(ScreensaverCatalogActivity.this), cancellation);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            if (destroyed) { artifact.delete(); return; }
                            verified = artifact;
                            status.setText("Release bytes, package, version and signing identity verified. Nothing has been installed.");
                            new AlertDialog.Builder(ScreensaverCatalogActivity.this).setTitle("Install " + entry.name + "?")
                                    .setMessage("Open the TV installer for this verified release? Your active saver remains unchanged.")
                                    .setPositiveButton("Open installer", new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface dialog, int which) { install(); }
                                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface dialog, int which) { discard(); }
                                    }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                                        @Override public void onCancel(DialogInterface dialog) { discard(); }
                                    }).show();
                        }
                    });
                } catch (final Exception failed) { failure(failed); }
            }
        });
    }

    private void install() {
        if (verified == null || selected == null || busy) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            status.setText("Allow app installs from this source, then return and download again. Nothing was installed.");
            discard();
            try { startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
            catch (RuntimeException unavailable) { status.setText("Installation setup is unavailable. Use the TV's own settings."); }
            return;
        }
        busy = true;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    CatalogArtifact.verify(verified, selected, new CatalogPackages(ScreensaverCatalogActivity.this), cancellation);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            busy = false;
                            if (destroyed) { discard(); return; }
                            Uri uri = Uri.parse("content://" + getPackageName() + ".updates/" + verified.getName());
                            Intent installer = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            installer.setClipData(ClipData.newRawUri("Verified screensaver", uri));
                            try {
                                if (!getPreferences(0).edit().putString("installer_file", verified.getName())
                                        .putString("installer_package", selected.packageName).commit()) {
                                    throw new IllegalStateException("Could not preserve the verified installer handoff.");
                                }
                                installerOpen = true;
                                startActivityForResult(installer, 610);
                                status.setText("TV installer opened. Installation is not assumed.");
                            } catch (RuntimeException unavailable) {
                                installerOpen = false; discard();
                                status.setText("The TV installer is unavailable or restricted.");
                            }
                        }
                    });
                } catch (Exception failed) { failure(failed); }
            }
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (startupBlocked) { super.onActivityResult(request, result, data); return; }
        super.onActivityResult(request, result, data);
        if (request == 610) {
            installerOpen = false; busy = true;
            final File artifact = verified;
            final ScreensaverInstallCatalog.Entry entry = selected;
            verified = null;
            getPreferences(0).edit().clear().commit();
            status.setText("Checking installed package, version and signing certificate...");
            worker.execute(new Runnable() {
                @Override public void run() {
                    boolean confirmed = false;
                    try {
                        confirmed = artifact != null && entry != null && CatalogArtifact.installationConfirmed(artifact, entry,
                                new CatalogPackages(ScreensaverCatalogActivity.this), new UpdateCoordinator.Cancellation());
                    } catch (Exception unavailable) { }
                    finally { if (artifact != null) artifact.delete(); }
                    final boolean installed = confirmed;
                    runOnUiThread(new Runnable() { @Override public void run() {
                        busy = false;
                        if (!destroyed) status.setText(installed ? "Installed version and signing certificate verified. Active selection unchanged."
                                : "Installation was cancelled or could not be confirmed. Active selection unchanged.");
                    }});
                }
            });
        }
    }
    private void failure(final Exception failed) {
        runOnUiThread(new Runnable() { @Override public void run() {
            busy = false; discard();
            if (!destroyed) status.setText("Release could not be verified: " + failed.getMessage());
        }});
    }
    private void discard() {
        if (verified != null) { verified.delete(); verified = null; }
        getPreferences(0).edit().clear().commit();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; } state.putString("focus", focusPackage); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; }
        destroyed = true; cancellation.cancel(); worker.shutdownNow();
        if (!installerOpen) discard();
        super.onDestroy();
    }
}
