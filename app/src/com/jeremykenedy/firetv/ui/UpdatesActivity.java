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

        Row(String name, String pkg, ScreensaverCatalog.Entry bundled) {
            this.name = name;
            this.pkg = pkg;
            this.bundled = bundled;
            sourceRepo = bundled == null ? null : bundled.repo;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Updates");
        status = text("Check published stable releases for Fire TV UI and installed catalog apps. "
                + "Your selections, timers and app settings are preserved during updates.", 16, MUTED);
        card.addView(status);
        check = button("Check for updates", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkUpdates();
            }
        });
        addButton(card, check);
        rows = scrollBody();
        Row self = new Row("Fire TV UI", getPackageName(), null);
        self.sourceRepo = "jeremykenedy/fire-tv-ui";
        self.sourceAsset = "fire-tv-ui.apk";
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
        rows.removeAllViews();
        check.setEnabled(!busy);
        for (final Row app : apps) {
            try {
                PackageInfo installed = getPackageManager().getPackageInfo(app.pkg, 0);
                TextView heading = text(app.name + "  |  Installed " + installed.versionName, 21, INK);
                heading.setPadding(0, dp(12), 0, dp(4));
                rows.addView(heading);
                if (app.publishedCode != null) {
                    rows.addView(text("Verified Android app " + app.publishedAppVersion
                            + " (code " + app.publishedCode + ")", 15, MUTED));
                }
                rows.addView(text(app.message, 15, MUTED));
                if (app.published != null) {
                    rows.addView(text("SHA-256: " + app.published.sha256, 12, MUTED));
                    Integer order = UpdateVersions.compare(app.published.tag, installed.versionName,
                            app.publishedCode, versionCode(installed));
                    String publishedName = app.publishedAppVersion == null
                            ? app.published.tag : app.publishedAppVersion;
                    String label = order == null ? "Verify and install published release"
                            : order > 0 ? "Install update " + publishedName
                            : order == 0 ? "Matches checked release" : "Installed version is newer";
                    TextView install = button(label, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            install(app);
                        }
                    });
                    install.setEnabled(!busy && (order == null || order > 0));
                    addButton(rows, install);
                }
            } catch (PackageManager.NameNotFoundException removed) {
                rows.addView(text(app.name + " is no longer installed.", 16, MUTED));
            }
        }
    }

    private void checkUpdates() {
        if (busy) {
            return;
        }
        busy = true;
        status.setText("Checking published releases on GitHub...");
        render();
        worker.submit(new Runnable() {
            @Override
            public void run() {
                boolean rateLimited = false;
                for (final Row app : apps) {
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
                        StagedUiUpdate.Release staged = getPackageName().equals(app.pkg) ? StagedUiUpdate.load(UpdatesActivity.this) : null;
                        if (staged != null) {
                            found = staged.entry;
                            local = true;
                            message = checkedMessage(found, staged.checkedAt, app.apkVersioned) + " (sent by computer)";
                        } else {
                            if (getPackageName().equals(app.pkg)) {
                                throw new IllegalStateException("Private repositories require an authenticated computer check.");
                            }
                            JSONObject cached = cached(app);
                            if (cached != null) {
                                found = new ScreensaverCatalog.Entry(cached.getJSONObject("release"));
                                message = checkedMessage(found, cached.getLong("checked_at"), app.apkVersioned) + " (cached)";
                            } else {
                                if (rateLimited) {
                                    throw new IllegalStateException("Not checked: GitHub rate limit paused remaining requests.");
                                }
                                found = app.apkVersioned
                                    ? ReleaseUpdates.latestContainingAsset(app.name, app.pkg,
                                            app.sourceRepo, app.sourceAsset, "", "")
                                    : app.bundled == null
                                            ? ReleaseUpdates.latest(app.name, app.pkg, app.sourceRepo, app.sourceAsset, "", "")
                                            : ReleaseUpdates.latest(app.bundled);
                                long checkedAt = System.currentTimeMillis();
                                saveCached(found, checkedAt);
                                message = checkedMessage(found, checkedAt, app.apkVersioned);
                            }
                        }
                        if (app.apkVersioned) {
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
                                && String.valueOf(failed.getMessage()).contains("Private repositories")) {
                            message = "Computer sign-in required. Use Toolkit on an authenticated computer to check and send a private UI update, then check here again.";
                        }
                    }
                    final Long checkedCode = apkCode;
                    final String checkedName = apkName;
                    final boolean staged = local;
                    final ScreensaverCatalog.Entry release = found;
                    final String result = message;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing() && !isDestroyed()) {
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
                            status.setText("Check finished: " + DateFormat.getDateTimeInstance().format(new Date())
                                    + ". Verified checks are reused for six hours. Each row shows its check time; unavailable rows are not current-version claims.");
                            render();
                            check.requestFocus();
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
                                status.setText("Package, checksum and signing certificate verified. Confirm the update on your TV.");
                                startActivityForResult(new Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        .putExtra(Intent.EXTRA_RETURN_RESULT, true), 31);
                            } catch (Exception failed) {
                                apk.delete();
                                installing = null;
                                busy = false;
                                render();
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
                                status.setText("The update was not installed. Your existing app was preserved.");
                                render();
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
        } else if (request == 31) {
            busy = false;
            boolean updated = false;
            try {
                updated = pending != null && versionCode(getPackageManager().getPackageInfo(pending.pkg, 0)) > previousCode;
            } catch (PackageManager.NameNotFoundException absent) {
                // Keep the result truthful if another process removed the app.
            }
            status.setText(updated ? "Update installed and version verified." : "No update was completed.");
            if (installing != null) {
                installing.delete();
                installing = null;
            }
            render();
            check.requestFocus();
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
