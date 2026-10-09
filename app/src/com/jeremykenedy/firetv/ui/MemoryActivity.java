package com.jeremykenedy.firetv.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class MemoryActivity extends CardActivity {
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Memory usage");
        body = scrollBody();
    }

    @Override
    protected void onResume() {
        super.onResume();
        body.removeAllViews();
        ActivityManager.MemoryInfo ram = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(ram);
        usage("TV memory (RAM)", ram.totalMem, ram.availMem);
        StatFs disk = new StatFs(Environment.getDataDirectory().getPath());
        usage("TV internal storage", disk.getTotalBytes(), disk.getAvailableBytes());
        TextView hint = text("Screensaver rows show app files and data, including cache. "
                + "RAM changes as apps run.", 16, MUTED);
        hint.setPadding(0, dp(16), 0, dp(16));
        body.addView(hint);
        addButton(body, button("Allow storage statistics", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception missing) {
                    error(new IllegalStateException("Use the CLI installer to allow storage statistics on this TV."));
                }
            }
        }));
    }

    private void usage(String name, long total, long free) {
        int percent = total == 0 ? 0 : (int) Math.round(100.0 * (total - free) / total);
        TextView label = text(name + ": " + percent + "% used", 22, INK);
        label.setPadding(0, dp(12), 0, dp(10));
        body.addView(label);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(percent);
        body.addView(bar, new LinearLayout.LayoutParams(-1, dp(12)));
        TextView detail = text(PackageStorage.size(total - free) + " used  |  "
                + PackageStorage.size(free) + " free  |  " + PackageStorage.size(total) + " total", 17, MUTED);
        detail.setPadding(0, dp(10), 0, dp(16));
        body.addView(detail);
    }
}
