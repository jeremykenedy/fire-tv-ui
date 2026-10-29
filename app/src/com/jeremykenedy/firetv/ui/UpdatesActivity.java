package com.jeremykenedy.firetv.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.app.AlertDialog;
import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** User-initiated stable release checks and normal Android replacement installs. */
public final class UpdatesActivity extends CardActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Row> apps = new ArrayList<>();
    private LinearLayout rows;
    private TextView status;
    private TextView check;
    private ProgressBar progress;
    private String focusPackage;
    private boolean busy;
    private Row pending;
    private File installing;
    private long previousCode;

    private static final class Row {
        final String name;
        final String pkg;
        final ScreensaverCatalog.Entry bundled;
        ScreensaverCatalog.Entry published;
        String message = "Published release: not checked";
        boolean staged;
        String sourceRepo;
        String sourceAsset;
        boolean apkVersioned;
        Long publishedCode;
        String publishedAppVersion;
        boolean checked;
        TextView heading;
        TextView detail;
        TextView action;
        TextView verification;

        Row(String name, String pkg, ScreensaverCatalog.Entry bundled) {
            this.name = name;
            this.pkg = pkg;
            this.bundled = bundled;
            sourceRepo = bundled == null ? null : bundled.repo;
        }
    }

    public static void open(android.content.Context context) {
        context.startActivity(new Intent(context, UpdatesActivity.class));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Updates");
        status = text("Check for updates, then choose an app to install. Your TV asks you to confirm each update. Settings are preserved.", 16, MUTED);
        card.addView(status);
        check = button("Check for updates", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkUpdates();
            }
        });
        addButton(card, check);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.INVISIBLE);
        card.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
        rows = scrollBody();
        Row self = new Row("Fire TV UI", getPackageName(), null);
        self.sourceRepo = "jeremykenedy/fire-tv-ui";
        self.sourceAsset = "fire-tv-ui-{version}.apk";
        self.apkVersioned = true;
        apps.add(self);
        try {
            for (ScreensaverCatalog.Entry entry : ScreensaverCatalog.load(this)) {
                try {
                    getPackageManager().getPackageInfo(entry.pkg, 0);
                    apps.add(new Row(entry.name, entry.pkg, entry));
                } catch (PackageManager.NameNotFoundException absent) {
                    // This screen checks only apps already installed on the TV.
                }
            }
        } catch (Exception failed) {
            error(failed);
        }
        String[][] additional = {
            {"AT4K Launcher", "com.overdevs.at4k", "avadhesh18/at4k", "app-release.apk"},
            {"LTvLauncher", "com.leanbitlab.ltvL", "leanbitlab-org/LtvLauncher", "LTvLauncher-universal-release.apk"},
            {"Home Redirect", "com.jeremykenedy.firetv.homeredirect", "jeremykenedy/fire-tv-toolkit", "firetv-home-redirect.apk"},
            {"fire-iptv", "com.jeremykenedy.fireiptv", "jeremykenedy/fire-iptv", "fire-iptv.apk"},
            {"OwnTV", "tv.own.owntv", "ahXN00/OwnTV", "OwnTV-{tag}.apk"},
        };
        for (String[] source : additional) {
            try {
                getPackageManager().getPackageInfo(source[1], 0);
                Row app = new Row(source[0], source[1], null);
                app.sourceRepo = source[2];
                app.sourceAsset = source[3];
                app.apkVersioned = "com.jeremykenedy.firetv.homeredirect".equals(app.pkg);
                apps.add(app);
            } catch (PackageManager.NameNotFoundException absent) {
                // Only installed catalog apps appear in the update list.
            }
        }
        Set<String> known = new HashSet<>();
        for (Row app : apps) {
            known.add(app.pkg);
        }
        for (ResolveInfo dream : getPackageManager().queryIntentServices(
                new Intent("android.service.dreams.DreamService"), 0)) {
            if (dream.serviceInfo != null && known.add(dream.serviceInfo.packageName)) {
                Row app = new Row(String.valueOf(dream.loadLabel(getPackageManager())),
                        dream.serviceInfo.packageName, null);
                app.message = "No reviewed release source. Check this app's own publisher or TV system updates.";
                apps.add(app);
            }
        }
        render();
        check.requestFocus();
    }

    private void render() {
        check.setEnabled(!busy);
        for (final Row app : apps) {
            if (app.heading == null) {
                app.heading = text(app.name, 21, INK);
                app.heading.setPadding(0, dp(12), 0, dp(4));
                rows.addView(app.heading);
                app.detail = text("", 15, MUTED);
                app.detail.setMinLines(3);
                rows.addView(app.detail);
                app.action = button("", new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        focusPackage = app.pkg;
                        if (app.published == null) checkUpdates(app);
                        else install(app);
                    }
                });
                addButton(rows, app.action);
                app.verification = button("Release details", new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        new AlertDialog.Builder(UpdatesActivity.this)
                                .setTitle(app.name + " release details")
                                .setMessage(app.message + "\n\nSource: " + app.sourceRepo
                                        + "\n\nSHA-256: " + app.published.sha256
                                        + "\n\nPackage, checksum, signing certificate and version are verified before installation.")
                                .setPositiveButton("Close", null).show();
                    }
                });
                addButton(rows, app.verification);
            }
            try {
                PackageInfo installed = getPackageManager().getPackageInfo(app.pkg, 0);
                String available = app.published == null ? "Not checked"
                        : app.publishedAppVersion == null ? app.published.tag : app.publishedAppVersion;
                if (app.checked && app.published == null) available = "Check unavailable";
                app.detail.setText("Installed: " + installed.versionName + "   Available: " + available
                        + "\n" + app.message);
                Integer order = app.published == null ? null
                        : UpdateVersions.compare(app.published.tag, installed.versionName,
                                app.publishedCode, versionCode(installed));
                String label = app.published == null ? (app.checked ? "Retry check" : "Check this app")
                        : order == null ? "Verify and install release " + available
                        : order > 0 ? "Install update " + available
                        : order == 0 ? "Matches checked release" : "Installed version is newer";
                if (app.sourceRepo == null) label = "No update source available";
                app.action.setText(label);
                app.action.setEnabled(!busy && app.sourceRepo != null
                        && (app.published == null || order == null || order > 0));
                app.action.setContentDescription(label + ": " + app.name);
                app.verification.setVisibility(app.published == null ? View.INVISIBLE : View.VISIBLE);
            } catch (PackageManager.NameNotFoundException removed) {
                app.detail.setText("This app is no longer installed.");
                app.action.setEnabled(false);
                app.verification.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void restoreFocus() {
        for (Row app : apps) {
            if (app.pkg.equals(focusPackage)) {
                if (app.action.isEnabled()) app.action.requestFocus();
                else if (app.verification.getVisibility() == View.VISIBLE) app.verification.requestFocus();
                else check.requestFocus();
                return;
            }
        }
        check.requestFocus();
    }

    private String checkSummary() {
        int updates = 0, unavailable = 0;
        for (Row app : apps) {
            if (!app.checked) continue;
            if (app.published == null) {
                unavailable++;
                continue;
            }
            try {
                PackageInfo installed = getPackageManager().getPackageInfo(app.pkg, 0);
                Integer order = UpdateVersions.compare(app.published.tag, installed.versionName,
                        app.publishedCode, versionCode(installed));
                if (order == null || order > 0) updates++;
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return "Check finished. " + updates + " update(s) to review, " + unavailable
                + " unavailable check(s). Select an app below. Release checks are reused for six hours; Retry check checks that app again.";
    }

    private void checkUpdates() {
        focusPackage = null;
        checkUpdates(null);
    }

    private void checkUpdates(final Row retry) {
        if (busy) {
            return;
        }
        busy = true;
        status.setText(retry == null ? "Checking installed apps..." : "Checking " + retry.name + "...");
        progress.setIndeterminate(true);
        progress.setVisibility(View.VISIBLE);
        render();
        worker.submit(new Runnable() {
            @Override
            public void run() {
                boolean rateLimited = false;
                int checkedCount = 0;
                for (final Row app : apps) {
                    if (retry != null && retry != app) continue;
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    ScreensaverCatalog.Entry found = null;
                    boolean local = false;
                    Long apkCode = null;
                    String apkName = null;
                    String message;
                    try {
                        if (app.sourceRepo == null) {
                            throw new IllegalStateException("No reviewed release source. Use the app publisher or TV system updates.");
                        }
                        try {
                            JSONObject cached = retry == app ? null : cached(app);
                            if (cached != null) {
                                found = new ScreensaverCatalog.Entry(cached.getJSONObject("release"));
                                message = checkedMessage(found, cached.getLong("checked_at"), app.apkVersioned) + " (cached)";
                            } else {
                                if (rateLimited) {
                                    throw new IllegalStateException("Not checked: GitHub rate limit paused remaining requests.");
                                }
                                found = app.apkVersioned && !getPackageName().equals(app.pkg)
                                    ? ReleaseUpdates.latestContainingAsset(app.name, app.pkg,
                                            app.sourceRepo, app.sourceAsset, "", "")
                                    : app.bundled == null
                                            ? ReleaseUpdates.latest(app.name, app.pkg, app.sourceRepo, app.sourceAsset, "", "")
                                            : ReleaseUpdates.latest(app.bundled);
                                long checkedAt = System.currentTimeMillis();
                                saveCached(found, checkedAt);
                                message = checkedMessage(found, checkedAt, app.apkVersioned);
                            }
                        } catch (ReleaseUpdates.AccessUnavailableException inaccessible) {
                            StagedUiUpdate.Release staged = getPackageName().equals(app.pkg)
                                    ? StagedUiUpdate.load(UpdatesActivity.this) : null;
                            if (staged == null) throw inaccessible;
                            found = staged.entry;
                            local = true;
                            message = checkedMessage(found, staged.checkedAt, app.apkVersioned)
                                    + " (sent by computer)";
                        }
                        if (app.apkVersioned && !local) {
                            PackageInfo inspected = ScreensaverDownload.inspectRelease(UpdatesActivity.this, found);
                            apkCode = versionCode(inspected);
                            if (apkCode <= 0) throw new IllegalStateException("The Android APK has no usable version code.");
                            apkName = inspected.versionName == null || inspected.versionName.isEmpty()
                                    ? "Unknown" : inspected.versionName;
                        }
                    } catch (Exception failed) {
                        found = null;
                        local = false;
                        rateLimited |= failed instanceof ReleaseUpdates.RateLimitException;
                        message = "Check unavailable: " + failed.getMessage();
                        if (app.bundled == null && getPackageName().equals(app.pkg)
                                && failed instanceof ReleaseUpdates.AccessUnavailableException) {
                            message = "Published release inaccessible. Use Toolkit on an authenticated computer to check and send a private UI update, then check here again.";
                        }
                    }
                    final Long checkedCode = apkCode;
                    final String checkedName = apkName;
                    final boolean staged = local;
                    final ScreensaverCatalog.Entry release = found;
                    final String result = message;
                    final int completed = ++checkedCount;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing() && !isDestroyed()) {
                                app.checked = true;
                                status.setText("Checked " + completed + " of " + (retry == null ? apps.size() : 1) + " apps: " + app.name);
                                app.published = release;
                                app.publishedCode = release == null ? null : checkedCode;
                                app.publishedAppVersion = release == null ? null : checkedName;
                                app.staged = staged;
                                app.message = result;
                                render();
                            }
                        }
                    });
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDestroyed()) {
                            busy = false;
                            progress.setVisibility(View.INVISIBLE);
                            status.setText(checkSummary());
                            render();
                            restoreFocus();
                        }
                    }
                });
            }
        });
    }

    private String checkedMessage(ScreensaverCatalog.Entry release, long checkedAt, boolean repositoryTag) {
        return (repositoryTag ? "Repository release " : "Published ") + release.tag + "  |  Checked "
                + DateFormat.getDateTimeInstance().format(new Date(checkedAt))
                + "  |  Download " + PackageStorage.size(release.bytes);
    }

    private JSONObject cached(Row app) {
        try {
            String value = getSharedPreferences("release_update_cache", MODE_PRIVATE).getString(app.pkg, "");
            JSONObject record = new JSONObject(value);
            if (!UpdateVersions.cacheFresh(record.getLong("checked_at"), System.currentTimeMillis())) {
                return null;
            }
            ScreensaverCatalog.Entry entry = new ScreensaverCatalog.Entry(record.getJSONObject("release"));
            String repo = app.sourceRepo;
            if (app.sourceAsset != null && (app.apkVersioned || app.sourceAsset.contains("{tag}"))
                    && !ReleaseUpdates.matchesRequestedAsset(entry, app.sourceAsset)) return null;
            return app.pkg.equals(entry.pkg) && repo.equals(entry.repo) ? record : null;
        } catch (Exception unavailable) {
            return null;
        }
    }

    private void saveCached(ScreensaverCatalog.Entry release, long checkedAt) throws Exception {
        JSONObject value = new JSONObject().put("name", release.name).put("package", release.pkg)
                .put("repo", release.repo).put("dream", release.dream).put("preview", release.preview)
                .put("url", release.url).put("sha256", release.sha256).put("bytes", release.bytes).put("tag", release.tag);
        JSONObject record = new JSONObject().put("checked_at", checkedAt).put("release", value);
        getSharedPreferences("release_update_cache", MODE_PRIVATE).edit()
                .putString(release.pkg, record.toString()).apply();
    }

    private void install(final Row app) {
        if (busy || app.published == null) {
            return;
        }
        pending = app;
        if (!getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), 30);
            } catch (RuntimeException unsupported) {
                error(new IllegalStateException("Allow app installation for Fire TV UI in the TV's settings first."));
            }
            return;
        }
        busy = true;
        status.setText("Downloading and verifying " + app.name + "...");
        progress.setIndeterminate(false);
        progress.setProgress(0);
        progress.setVisibility(View.VISIBLE);
        render();
        worker.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    ScreensaverDownload.Progress progress = new ScreensaverDownload.Progress() {
                                @Override
                                public void update(final int percent) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (isFinishing() || isDestroyed()) return;
                                            UpdatesActivity.this.progress.setProgress(percent);
                                            status.setText("Downloading " + app.name + ": " + percent + "%");
                                        }
                                    });
                                }
                            };
                    final File apk = app.staged
                            ? ScreensaverDownload.localUpdate(UpdatesActivity.this, app.published, StagedUiUpdate.apk(), progress)
                            : ScreensaverDownload.download(UpdatesActivity.this, app.published, progress);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isDestroyed()) {
                                apk.delete();
                                return;
                            }
                            try {
                                previousCode = versionCode(getPackageManager().getPackageInfo(app.pkg, 0));
                                installing = apk;
                                Uri uri = new Uri.Builder().scheme("content").authority(getPackageName() + ".install")
                                        .appendPath(apk.getName()).build();
                                UpdatesActivity.this.progress.setVisibility(View.INVISIBLE);
                                status.setText("Ready to install " + app.name + ". Confirm the update on your TV.");
                                startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        .putExtra(Intent.EXTRA_RETURN_RESULT, true), 31);
                            } catch (Exception failed) {
                                apk.delete();
                                installing = null;
                                busy = false;
                                UpdatesActivity.this.progress.setVisibility(View.INVISIBLE);
                                status.setText("Installer could not open. Select the app to retry.");
                                render();
                                restoreFocus();
                                error(failed);
                            }
                        }
                    });
                } catch (final Exception failed) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing() && !isDestroyed()) {
                                busy = false;
                                UpdatesActivity.this.progress.setVisibility(View.INVISIBLE);
                                status.setText("Update not installed. Select " + app.name + " to retry. Your existing app was preserved.");
                                render();
                                restoreFocus();
                                error(failed);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 30 && pending != null && getPackageManager().canRequestPackageInstalls()) {
            install(pending);
        } else if (request == 30) {
            status.setText("Installation permission was not granted. Select the app to try again.");
            restoreFocus();
        } else if (request == 31) {
            busy = false;
            boolean updated = false;
            try {
                updated = pending != null && versionCode(getPackageManager().getPackageInfo(pending.pkg, 0)) > previousCode;
            } catch (PackageManager.NameNotFoundException absent) {
                // Keep the result truthful if another process removed the app.
            }
            status.setText(updated ? pending.name + " updated. Installed version verified."
                    : "No update was installed. Select the app to retry.");
            if (installing != null) {
                installing.delete();
                installing = null;
            }
            render();
            restoreFocus();
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
