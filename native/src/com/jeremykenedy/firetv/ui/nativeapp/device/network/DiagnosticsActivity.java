package com.jeremykenedy.firetv.ui.nativeapp.device.network;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiagnosticsActivity extends Activity {
    private static final int EXPORT = 731;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView report;
    private TextView status;
    private Button save;
    private String snapshot;
    private String exportSnapshot;
    private boolean reading;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Diagnostics", 28));
        page.addView(Pages.text(this, "Read-only build, storage and memory snapshot. Export contains only the report below. Choose a destination to save; no automatic upload.", 16));
        Button refresh = Pages.button(this, "Refresh report", new View.OnClickListener() {
            @Override public void onClick(View view) { refresh(); }
        });
        refresh.setTag("diagnostics:refresh");
        page.addView(refresh);
        save = Pages.button(this, "Export sanitized report", new View.OnClickListener() {
            @Override public void onClick(View view) { export(); }
        });
        save.setTag("diagnostics:export");
        save.setEnabled(false);
        page.addView(save);
        status = Pages.text(this, "", 16);
        status.setTag("diagnostics:status");
        page.addView(status);
        scroll = new ScrollView(this);
        scroll.setTag("diagnostics:scroll");
        report = Pages.text(this, "Reading report...", 18);
        report.setTag("diagnostics:report");
        scroll.addView(report);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (saved != null) exportSnapshot = saved.getString("pendingExport");
        View focused = saved == null ? null : page.findViewWithTag(saved.getString("focus"));
        if (focused == null || !focused.isEnabled()) focused = refresh;
        focused.requestFocus();
        refresh();
    }

    private void refresh() {
        if (reading) return;
        reading = true;
        status.setText("Reading device status...");
        worker.execute(new Runnable() {
            @Override public void run() {
                final String value = DiagnosticReport.read(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        reading = false;
                        if (isDestroyed()) return;
                        snapshot = value;
                        report.setText(value);
                        save.setEnabled(true);
                        status.setText("Report ready.");
                    }
                });
            }
        });
    }

    private void export() {
        if (snapshot == null || exportSnapshot != null) return;
        exportSnapshot = snapshot;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain").putExtra(Intent.EXTRA_TITLE, "fire-tv-ui-diagnostics.txt");
        try { startActivityForResult(intent, EXPORT); }
        catch (RuntimeException unavailable) {
            exportSnapshot = null;
            status.setText("Document export unavailable on this TV. The report remains visible here.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT) return;
        final String value = exportSnapshot;
        exportSnapshot = null;
        final Uri destination = data == null ? null : data.getData();
        if (result != RESULT_OK || destination == null || value == null) {
            status.setText("Export canceled. No report saved.");
            return;
        }
        if (!"content".equals(destination.getScheme())) {
            status.setText("Export failed: document provider did not return a supported destination.");
            return;
        }
        status.setText("Saving report...");
        worker.execute(new Runnable() {
            @Override public void run() {
                String message;
                try (OutputStream stream = getContentResolver().openOutputStream(destination, "wt")) {
                    if (stream == null) throw new IllegalStateException();
                    stream.write(value.getBytes(StandardCharsets.UTF_8));
                    message = "Report saved to the chosen document.";
                } catch (Exception failed) { message = "Report could not be saved. Choose Export to retry."; }
                final String completed = message;
                runOnUiThread(new Runnable() {
                    @Override public void run() { if (!isDestroyed()) status.setText(completed); }
                });
            }
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("pendingExport", exportSnapshot);
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) state.putString("focus", (String) focused.getTag());
    }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
