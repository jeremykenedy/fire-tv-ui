package com.jeremykenedy.firetv.ui.nativeapp.inputs;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InputsSettingsActivity extends Activity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int generation;
    private String focusedId;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (state != null) focusedId = state.getString("focused_input");
    }
    @Override public void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); refresh(); }
    @Override public void onSaveInstanceState(Bundle out) {
        if (startupBlocked) { super.onSaveInstanceState(out); return; }
        out.putString("focused_input", focusedId);
        super.onSaveInstanceState(out);
    }
    @Override public void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } generation++; worker.shutdownNow(); super.onDestroy(); }
    public void refresh() {
        final int request = ++generation;
        worker.execute(new Runnable() {
            @Override public void run() {
                final InputRepository.Snapshot snapshot = InputRepository.load(InputsSettingsActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() { if (!isDestroyed() && request == generation) render(snapshot); }
                });
            }
        });
    }
    private void render(InputRepository.Snapshot snapshot) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff101523);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(28 * getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding, padding, padding);
        scroll.addView(body);
        setContentView(scroll);
        body.addView(text("TV inputs", 28));
        body.addView(text(snapshot.message, 17));
        View first = null;
        View restore = null;
        for (final InputEntry input : snapshot.inputs) {
            Button button = new Button(this);
            button.setText(input.displayName + (input.hidden ? " (hidden from Home)" : "") + "  |  " + input.message());
            button.setTag(input.artworkKey());
            button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) { if (focused) focusedId = input.id; }
            });
            button.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { InputViews.actions(InputsSettingsActivity.this, input, new Runnable() {
                    @Override public void run() { refresh(); }
                }); }
            });
            body.addView(button, new LinearLayout.LayoutParams(-1, -2));
            if (first == null) first = button;
            if (input.id.equals(focusedId)) restore = button;
        }
        Button showAll = new Button(this);
        showAll.setText("Show all inputs on Home");
        showAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    InputPreferences saved = new InputPreferences(InputsSettingsActivity.this);
                    saved.saveOptions(saved.options().showAll());
                    refresh();
                } catch (Exception failure) {
                    android.widget.Toast.makeText(InputsSettingsActivity.this, failure.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
        body.addView(showAll);
        Button refresh = new Button(this);
        refresh.setText("Refresh inputs");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { refresh(); }
        });
        body.addView(refresh);
        if (restore != null) restore.requestFocus(); else if (first != null) first.requestFocus(); else refresh.requestFocus();
    }
    private TextView text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(0xfff4f1fa);
        text.setPadding(0, 0, 0, 12);
        return text;
    }
}
