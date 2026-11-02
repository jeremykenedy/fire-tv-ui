package com.jeremykenedy.firetv.ui.nativeapp.device.screensavers;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScreensaverMemoryActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private boolean destroyed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Screensaver memory and storage", 28));
        page.addView(Pages.button(this, "Refresh measurements", new View.OnClickListener() {
            @Override public void onClick(View view) { load(); }
        }));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        load();
    }
    private void load() {
        worker.execute(new Runnable() {
            @Override public void run() {
                final StringBuilder text = new StringBuilder();
                try {
                    ActivityManager.MemoryInfo ram = new ActivityManager.MemoryInfo();
                    getSystemService(ActivityManager.class).getMemoryInfo(ram);
                    text.append("TV RAM: ").append(ScreensaverMemory.size(ram.availMem)).append(" available of ")
                            .append(ScreensaverMemory.size(ram.totalMem)).append('\n');
                    StatFs disk = new StatFs(Environment.getDataDirectory().getPath());
                    text.append("TV storage: ").append(ScreensaverMemory.size(disk.getAvailableBytes()))
                            .append(" available of ").append(ScreensaverMemory.size(disk.getTotalBytes())).append("\n\n");
                    List<ScreensaverEntry> entries = ScreensaverSystem.installed(ScreensaverMemoryActivity.this);
                    for (ScreensaverEntry entry : entries) {
                        String pkg = entry.component.substring(0, entry.component.indexOf('/'));
                        text.append(entry.label).append('\n').append(ScreensaverMemory.storage(ScreensaverMemoryActivity.this, pkg))
                                .append('\n').append(ScreensaverMemory.process(ScreensaverMemoryActivity.this, pkg)).append("\n\n");
                    }
                    if (entries.isEmpty()) text.append("No accessible installed screensavers found.");
                } catch (RuntimeException unavailable) { text.append("Measurements unavailable on this device."); }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (destroyed) return;
                        body.removeAllViews();
                        TextView measurements = Pages.text(ScreensaverMemoryActivity.this, text.toString(), 18);
                        measurements.setTag("screensaver:memory");
                        body.addView(measurements);
                    }
                });
            }
        });
    }
    @Override protected void onDestroy() { destroyed = true; worker.shutdownNow(); super.onDestroy(); }
}
