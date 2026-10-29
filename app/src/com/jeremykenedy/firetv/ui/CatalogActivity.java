package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ScrollView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CatalogActivity extends CardActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout rows;
    private TextView status;
    private ProgressBar progress;
    private ScreensaverCatalog.Entry pending;
    private File installing;
    private boolean downloading;
    private String focusPackage;
    private int scrollPosition;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Screensaver catalog");
        status = text("Install a new screensaver or configure one already installed. Your TV asks you to confirm installs and removals.", 16, MUTED);
        status.setPadding(0, 0, 0, dp(8));
        card.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        card.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        addButton(card, button("Check for updates to installed apps", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CatalogActivity.this, UpdatesActivity.class));
            }
        }));
        rows = scrollBody();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onPause() {
        if (rows != null) scrollPosition = ((ScrollView) rows.getParent()).getScrollY();
        super.onPause();
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    private void load() {
        if (rows.getChildCount() > 0) scrollPosition = ((ScrollView) rows.getParent()).getScrollY();
        rows.removeAllViews();
        View returnFocus = null;
        try {
            for (final ScreensaverCatalog.Entry entry : ScreensaverCatalog.load(this)) {
                boolean installed = installed(entry.pkg);
                String version = installed ? getPackageManager().getPackageInfo(entry.pkg, 0).versionName : entry.tag;
                TextView label = text(entry.name, 21, INK);
                label.setPadding(0, dp(12), 0, dp(4));
                rows.addView(label);
                rows.addView(text((installed ? "Installed: " : "Available: ") + version, 16, MUTED));
                if (!entry.description.isEmpty()) rows.addView(text(entry.description, 14, MUTED));
                rows.addView(text(installed ? PackageStorage.summary(this, entry.pkg)
                        : "Download " + PackageStorage.size(entry.bytes), 14, MUTED));
                TextView action = button(installed ? "Configure " + entry.name : "Install " + entry.name,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                focusPackage = entry.pkg;
                                if (installed(entry.pkg)) {
                                    try {
                                        Intent controls = ScreensaverControls.resolve(CatalogActivity.this, entry.dream);
                                        if (controls == null) throw new IllegalStateException("This screensaver does not provide accessible settings.");
                                        startActivity(controls);
                                    } catch (RuntimeException unavailable) {
                                        error(unavailable);
                                    }
                                } else {
                                    install(entry);
                                }
                            }
                        });
                addButton(rows, action);
                action.setEnabled(!downloading);
                if (entry.pkg.equals(focusPackage)) returnFocus = action;
                if (installed) {
                    TextView remove = button("Uninstall " + entry.name, new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            focusPackage = entry.pkg + ":uninstall";
                            uninstall(entry);
                        }
                    });
                    addButton(rows, remove);
                    remove.setEnabled(!downloading);
                    if ((entry.pkg + ":uninstall").equals(focusPackage)) returnFocus = remove;
                }
            }
            final View focus = returnFocus;
            final int position = scrollPosition;
            rows.post(new Runnable() {
                @Override public void run() {
                    if (focus != null) focus.requestFocus();
                    ((ScrollView) rows.getParent()).scrollTo(0, position);
                }
            });
        } catch (Exception failed) {
            error(failed);
        }
    }

    private void install(final ScreensaverCatalog.Entry entry) {
        if (downloading) {
            return;
        }
        pending = entry;
        if (!getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), 20);
            } catch (Exception unavailable) {
                error(new IllegalStateException("Use the CLI installer to allow Fire TV UI to install screensavers on this TV."));
            }
            return;
        }
        downloading = true;
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        status.setText("Downloading " + entry.name + "...");
        load();
        worker.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    final File apk = ScreensaverDownload.download(CatalogActivity.this, entry,
                            new ScreensaverDownload.Progress() {
                                @Override
                                public void update(final int percent) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            progress.setProgress(percent);
                                            status.setText("Downloading " + entry.name + ": " + percent + "%");
                                        }
                                    });
                                }
                            });
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) {
                                apk.delete();
                                return;
                            }
                            installing = apk;
                            status.setText("Checksum verified. Confirm the install on your TV.");
                            progress.setVisibility(View.GONE);
                            Uri uri = new Uri.Builder().scheme("content").authority(getPackageName() + ".install")
                                    .appendPath(apk.getName()).build();
                            Intent installer = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
                            try {
                                startActivityForResult(installer, 21);
                            } catch (RuntimeException unavailable) {
                                apk.delete();
                                installing = null;
                                downloading = false;
                                status.setText("Installer could not open. Select the screensaver to retry.");
                                load();
                                error(unavailable);
                            }
                        }
                    });
                } catch (final Exception failed) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            downloading = false;
                            if (!isFinishing() && !isDestroyed()) {
                                progress.setVisibility(View.GONE);
                                status.setText("Download did not complete. You can try again.");
                                load();
                                error(failed);
                            }
                        }
                    });
                }
            }
        });
    }

    private void uninstall(final ScreensaverCatalog.Entry entry) {
        new AlertDialog.Builder(this).setTitle("Uninstall " + entry.name + "?")
                .setMessage("The TV will remove this screensaver and its saved options. "
                        + "If it is selected, choose another screensaver after removal.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivityForResult(new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                                Uri.parse("package:" + entry.pkg)).putExtra(Intent.EXTRA_RETURN_RESULT, true), 22);
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 20 && pending != null && getPackageManager().canRequestPackageInstalls()) {
            install(pending);
        } else if (request == 20) {
            status.setText("Installation permission was not granted. Select the screensaver to try again.");
        } else if (request == 21 || request == 22) {
            downloading = false;
            if (installing != null) {
                installing.delete();
                installing = null;
            }
            status.setText(result == RESULT_OK ? "The TV completed the change." : "No change was completed.");
            load();
        }
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
