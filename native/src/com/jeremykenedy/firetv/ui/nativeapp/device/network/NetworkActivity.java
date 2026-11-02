package com.jeremykenedy.firetv.ui.nativeapp.device.network;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceRoutes;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NetworkActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView details;
    private TextView status;
    private boolean reading;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Network and IP", 28));
        Button settings = Pages.button(this, "Open TV network settings", new View.OnClickListener() {
            @Override public void onClick(View view) {
                DeviceRoutes.Result result = DeviceRoutes.open(NetworkActivity.this, DeviceRoutes.Destination.NETWORK_SETTINGS);
                status.setText(result == DeviceRoutes.Result.STARTED ? "TV settings opened. Back returns here."
                        : "Network settings unavailable to apps. Open Network from the TV's factory settings.");
            }
        });
        settings.setTag("network:settings");
        page.addView(settings);
        Button refresh = Pages.button(this, "Refresh network details", new View.OnClickListener() {
            @Override public void onClick(View view) { refresh(); }
        });
        refresh.setTag("network:refresh");
        page.addView(refresh);
        status = Pages.text(this, "", 16);
        status.setTag("network:status");
        page.addView(status);
        scroll = new ScrollView(this);
        scroll.setTag("network:scroll");
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        details = Pages.text(this, "Reading network details...", 18);
        details.setTag("network:details");
        content.addView(details);
        content.addView(Pages.text(this, "Read-only snapshot. Refresh after changing connections. For a fixed address, reserve the TV's IP in your router or use Static in TV network settings if supported. Automatic/DHCP restores automatic addressing there. Router reservations cannot be detected here.", 16));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        View focused = saved == null ? null : page.findViewWithTag(saved.getString("focus"));
        if (focused == null) focused = settings;
        focused.requestFocus();
        if (saved != null) {
            final int position = saved.getInt("scroll");
            scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, position); } });
        }
    }

    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); refresh(); }

    private void refresh() {
        if (reading) return;
        reading = true;
        status.setText("Reading current network details...");
        worker.execute(new Runnable() {
            @Override public void run() {
                final String snapshot = NetworkSnapshot.read(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        reading = false;
                        if (isDestroyed()) return;
                        details.setText(snapshot);
                        status.setText("Snapshot refreshed. No network settings changed.");
                    }
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        super.onSaveInstanceState(state);
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) state.putString("focus", (String) focused.getTag());
        state.putInt("scroll", scroll.getScrollY());
    }

    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } worker.shutdownNow(); super.onDestroy(); }
}
