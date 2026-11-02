package com.jeremykenedy.firetv.ui.nativeapp.widget.livetv;

import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LiveTvSettingsActivity extends Activity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<String, CheckBox> choices = new LinkedHashMap<>();
    private CancellationSignal cancellation;
    private LinearLayout rows;
    private TextView status;
    private EditText zone;
    private boolean destroyed, active, loaded;
    private List<String> draft;
    private LiveTvPreferences saved;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Live TV favorites now/next", 28));
        status = Pages.text(this, "Choose up to six channels from your configured TV sources.", 16);
        status.setTag("livetv:status"); page.addView(status);
        page.addView(Pages.button(this, "Allow TV listings access", new View.OnClickListener() {
            @Override public void onClick(View view) { requestPermissions(new String[]{LiveTvRepository.PERMISSION}, 720); }
        }));
        zone = new EditText(this);
        zone.setHint("Time zone: blank uses TV setting, or America/Los_Angeles");
        zone.setSingleLine(); zone.setTag("livetv:zone");
        page.addView(zone);
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(Pages.button(this, "Save favorites", new View.OnClickListener() {
            @Override public void onClick(View view) { save(); }
        }));
        try {
            saved = LiveTvPreferences.read(this);
            draft = state != null && state.containsKey("draft") ? state.getStringArrayList("draft") : new ArrayList<>(saved.favorites);
            zone.setText(state == null ? saved.zone : state.getString("zone", saved.zone));
        }
        catch (IllegalStateException unreadable) { status.setText(unreadable.getMessage()); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        if (startupBlocked) { super.onRequestPermissionsResult(request, permissions, results); return; }
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 720 && saved != null && active) load();
    }
    @Override protected void onStart() {
        if (startupBlocked) { super.onStart(); return; }
        super.onStart(); active = true;
        if (saved != null) load();
    }
    @Override protected void onStop() {
        if (startupBlocked) { super.onStop(); return; }
        active = false; retainDraft(); loaded = false;
        if (cancellation != null) cancellation.cancel();
        super.onStop();
    }
    private void retainDraft() {
        if (!loaded) return;
        draft = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : choices.entrySet()) if (entry.getValue().isChecked()) draft.add(entry.getKey());
    }
    private void load() {
        retainDraft();
        loaded = false;
        if (cancellation != null) cancellation.cancel();
        final CancellationSignal signal = new CancellationSignal(); cancellation = signal;
        worker.execute(new Runnable() { @Override public void run() {
            final LiveTvRepository.Channels channels;
            try { channels = LiveTvRepository.channels(LiveTvSettingsActivity.this, signal); }
            catch (RuntimeException unavailable) {
                if (!signal.isCanceled()) runOnUiThread(new Runnable() { @Override public void run() {
                    if (!destroyed && active && !signal.isCanceled()) status.setText("Channel provider unavailable. Saved favorites were kept.");
                }});
                return;
            }
            runOnUiThread(new Runnable() { @Override public void run() {
                if (destroyed || !active || signal.isCanceled()) return;
                rows.removeAllViews(); choices.clear();
                status.setText(channels.message());
                for (LiveGuide.Channel channel : channels.values) add(channel.key(), channel.name + " | " + channel.publisher);
                for (String key : draft) if (!choices.containsKey(key)) add(key, "Unavailable saved channel: " + key);
                loaded = true;
            }});
        }});
    }
    private void add(String key, String label) {
        CheckBox checkbox = new CheckBox(this);
        checkbox.setText(label); checkbox.setTextColor(0xffffffff);
        checkbox.setChecked(draft.contains(key)); checkbox.setTag("livetv:favorite:" + key);
        choices.put(key, checkbox); rows.addView(checkbox);
    }
    private void save() {
        if (saved == null) { status.setText("Saved favorites are unreadable and were kept. No changes saved."); return; }
        if (!loaded) { status.setText("Wait for channel availability before saving favorites."); return; }
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : choices.entrySet()) if (entry.getValue().isChecked()) selected.add(entry.getKey());
        try {
            LiveTvPreferences.save(this, selected, zone.getText().toString().trim());
            status.setText("Favorites saved. Guide times use " + (zone.getText().toString().trim().isEmpty() ? "the TV time zone" : zone.getText().toString().trim()) + ".");
            saved = LiveTvPreferences.read(this);
            draft = new ArrayList<>(saved.favorites);
        } catch (RuntimeException invalid) { status.setText(invalid.getMessage()); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        ArrayList<String> selected = new ArrayList<>();
        if (loaded) {
            for (Map.Entry<String, CheckBox> entry : choices.entrySet()) if (entry.getValue().isChecked()) selected.add(entry.getKey());
        } else if (draft != null) selected.addAll(draft);
        state.putStringArrayList("draft", selected);
        state.putString("zone", zone.getText().toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } destroyed = true; if (cancellation != null) cancellation.cancel(); worker.shutdownNow(); super.onDestroy(); }
}
