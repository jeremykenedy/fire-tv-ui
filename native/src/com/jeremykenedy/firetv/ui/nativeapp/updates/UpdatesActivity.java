package com.jeremykenedy.firetv.ui.nativeapp.updates;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Source preview never treats the legacy production release as a compatible update. */
public class UpdatesActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private UpdateCoordinator coordinator;
    private StagedUpdateStore staged;
    private UpdateCoordinator.Cancellation cancellation = new UpdateCoordinator.Cancellation();
    private UpdateCoordinator.Release release;
    private UpdateCoordinator.Verified verified;
    private TextView status, details;
    private ProgressBar progress;
    private Button check, download, install, cancel;
    private boolean busy, destroyed, installerOpen, awaitingInstallerReturn;

    /** Fixture hosts can inject reviewed metadata; no intent extra can change this trust boundary. */
    protected UpdateCoordinator.Source releaseSource() {
        try {
            UpdateCoordinator.Identity installed = new AndroidPackages(this).installed(getPackageName());
            if (installed == null) throw new IllegalStateException("Installed package unavailable.");
            return new GitHubReleaseSource(getPackageName(), installed.version);
        } catch (Exception failed) {
            throw new IllegalStateException("Installed package unavailable.", failed);
        }
    }
    protected UpdateCoordinator.Transport releaseTransport() { return new ReleaseTransport(); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        coordinator = new UpdateCoordinator(getPackageName(), getCacheDir(), new AndroidPackages(this));
        staged = new StagedUpdateStore(getCacheDir());
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Updates", 30));
        status = Pages.text(this, "Check for a compatible source release. Installation always requires your confirmation.", 18);
        status.setTag("updates:status");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        page.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setVisibility(View.GONE); page.addView(progress);
        check = Pages.button(this, "Check for updates", new View.OnClickListener() {
            @Override public void onClick(View view) { checkRelease(); }
        });
        check.setTag("updates:check"); page.addView(check);
        download = Pages.button(this, "Download and verify", new View.OnClickListener() {
            @Override public void onClick(View view) { downloadRelease(); }
        });
        download.setTag("updates:download"); page.addView(download);
        install = Pages.button(this, "Install verified update", new View.OnClickListener() {
            @Override public void onClick(View view) { installRelease(); }
        });
        install.setTag("updates:install"); page.addView(install);
        cancel = Pages.button(this, "Cancel", new View.OnClickListener() {
            @Override public void onClick(View view) { cancellation.cancel(); status.setText("Cancelling update. Waiting for the current network read to finish."); }
        });
        cancel.setTag("updates:cancel"); page.addView(cancel);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        details = Pages.text(this, "Check GitHub for source releases compatible with this installed package.", 16);
        details.setTag("updates:release"); scroll.addView(details);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        render(); check.requestFocus();
        if (staged.hasPending()) restoreStaged(); else staged.cleanup(null);
    }

    private void restoreStaged() {
        begin("Rechecking the staged update...");
        final UpdateCoordinator.Cancellation request = cancellation;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final UpdateCoordinator.Verified recovered = staged.restore(coordinator, request);
                    staged.cleanup(recovered);
                    final boolean installed = recovered != null && coordinator.installationConfirmed(recovered);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed) return;
                            verified = recovered; release = recovered == null ? null : recovered.release;
                            busy = false;
                            if (installed) {
                                discard(); release = null;
                                status.setText("Installed version and signing certificate verified. Update installed.");
                            } else if (awaitingInstallerReturn) {
                                discard(); release = null;
                                status.setText("No verified installation was observed. Check for updates to retry.");
                            } else if (recovered != null) {
                                status.setText("Staged APK reverified. Select Install to open the TV installer. No installation is assumed.");
                                details.setText(release.versionName + "\n" + release.releaseUrl() + "\n\n" + release.notes);
                            }
                            render(); (verified == null ? check : install).requestFocus();
                        }
                    });
                } catch (InterruptedException cancelled) { if (!destroyed) failure(cancelled); }
                catch (Exception failed) { staged.cleanup(null); failure(failed); }
            }
        });
    }

    private void render() {
        check.setEnabled(!busy && !installerOpen);
        download.setVisibility(release != null && verified == null ? View.VISIBLE : View.GONE);
        download.setEnabled(!busy && !installerOpen);
        install.setVisibility(verified != null ? View.VISIBLE : View.GONE);
        install.setEnabled(!busy && !installerOpen);
        cancel.setVisibility(busy ? View.VISIBLE : View.GONE);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }
    private void begin(String message) {
        busy = true; cancellation = new UpdateCoordinator.Cancellation();
        progress.setProgress(0); status.setText(message); render(); cancel.requestFocus();
    }
    private void discard() { if (verified != null) verified.discard(); verified = null; staged.clear(); }
    private void checkRelease() {
        discard(); release = null; begin("Checking compatible source releases...");
        final UpdateCoordinator.Cancellation request = cancellation;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final UpdateCoordinator.Release found = coordinator.check(releaseSource(), request);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed) return;
                            busy = false; release = found;
                            status.setText(found == null ? "No compatible source release is available. This preview was not changed."
                                    : "A reviewed update is available. Read the release information before downloading.");
                            if (found != null) details.setText(found.versionName + " (" + found.version + ")\n" + found.bytes
                                    + " bytes\n" + found.releaseUrl() + "\n\n" + found.notes);
                            render(); (found == null ? check : download).requestFocus();
                        }
                    });
                } catch (Exception failed) { failure(failed); }
            }
        });
    }
    private void downloadRelease() {
        if (release == null || busy) return;
        begin("Downloading reviewed update...");
        final UpdateCoordinator.Cancellation request = cancellation;
        final UpdateCoordinator.Release candidate = release;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final UpdateCoordinator.Verified artifact = coordinator.download(candidate, releaseTransport(), request,
                            new UpdateCoordinator.Progress() {
                                @Override public void update(final int percent) {
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            if (!destroyed && request == cancellation) {
                                                progress.setProgress(percent);
                                                status.setText("Downloading: " + percent + "%. Package and signature verification follows.");
                                            }
                                        }
                                    });
                                }
                            });
                    try { staged.save(artifact); }
                    catch (Exception failed) { artifact.discard(); throw failed; }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed) return;
                            try { request.check(); }
                            catch (InterruptedException cancelled) { artifact.discard(); failure(cancelled); return; }
                            verified = artifact; busy = false;
                            status.setText("Download verified. Select Install to open your TV's installer. Nothing has been installed yet.");
                            render(); install.requestFocus();
                        }
                    });
                } catch (Exception failed) { failure(failed); }
            }
        });
    }
    private void installRelease() {
        if (verified == null || busy || installerOpen) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), 40);
                status.setText("Allow this app to request installation, then select Install again.");
            } catch (Exception unavailable) { status.setText("This device cannot open install permission settings. No update was installed."); }
            return;
        }
        begin("Rechecking the verified APK before opening the installer...");
        final UpdateCoordinator.Cancellation request = cancellation;
        final UpdateCoordinator.Verified candidate = verified;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final File file = coordinator.prepareInstall(candidate, request);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed) return;
                            try {
                                request.check();
                                Uri uri = new Uri.Builder().scheme("content").authority(getPackageName() + ".updates").appendPath(file.getName()).build();
                                startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true), 41);
                                installerOpen = true; busy = false;
                                status.setText("Confirm installation on your TV. Opening the installer does not confirm an update.");
                                render();
                            } catch (Exception failed) { failure(failed); }
                        }
                    });
                } catch (Exception failed) { failure(failed); }
            }
        });
    }
    private void failure(final Exception failed) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (destroyed) return;
                busy = false; discard();
                status.setText("Update not installed. " + (cancellationMessage(failed) ? "Cancelled." : "Verification or download failed. Check the release and retry."));
                render(); (release == null ? check : download).requestFocus();
            }
        });
    }
    private boolean cancellationMessage(Exception failed) {
        return failed instanceof InterruptedException || failed.getCause() instanceof InterruptedException;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        if (startupBlocked) { super.onActivityResult(request, result, data); return; }
        super.onActivityResult(request, result, data);
        if (request == 41) {
            installerOpen = false;
            if (busy && verified == null) { awaitingInstallerReturn = true; return; }
            boolean confirmed = verified != null && coordinator.installationConfirmed(verified);
            discard(); release = null;
            status.setText(confirmed ? "Installed version and signing certificate verified. Update installed."
                    : "No verified installation was observed. Check for updates to retry.");
            render(); check.requestFocus();
        } else if (request == 40) {
            status.setText("Select Install to retry after granting installation permission.");
            render(); install.requestFocus();
        }
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; }
        destroyed = true; cancellation.cancel(); worker.shutdownNow();
        // An installer may still be reading its granted file when this activity is destroyed.
        // Retain the private verified journal for rotation, process recreation or installer return.
        super.onDestroy();
    }
}
