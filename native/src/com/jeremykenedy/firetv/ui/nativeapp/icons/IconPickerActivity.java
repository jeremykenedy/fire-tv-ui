package com.jeremykenedy.firetv.ui.nativeapp.icons;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Curated artwork is the default; personal media is an explicit separate action. */
public final class IconPickerActivity extends Activity {
    public static final String EXTRA_TILE_KEY = "tile_key";
    public static final String EXTRA_TILE_LABEL = "tile_label";
    private static final int IMAGE_REQUEST = 21;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String tileKey;
    private TextView status;
    private boolean importing;

    public static void open(Activity activity, String key, String label) {
        activity.startActivity(new Intent(activity, IconPickerActivity.class)
                .putExtra(EXTRA_TILE_KEY, key).putExtra(EXTRA_TILE_LABEL, label));
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        tileKey = getIntent().getStringExtra(EXTRA_TILE_KEY);
        if (ComponentName.unflattenFromString(tileKey == null ? "" : tileKey) == null) {
            finish();
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(0xff101523);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(32), dp(20), dp(32), dp(24));
        body.setClipChildren(false);
        scroll.addView(body);
        setContentView(scroll);
        String label = getIntent().getStringExtra(EXTRA_TILE_LABEL);
        body.addView(text("Icon for " + (label == null ? "this app" : label), 28));
        status = text("Current: " + IconSelectionStore.label(this, tileKey), 16);
        status.setTextColor(0xffcec7df);
        body.addView(status);
        LinearLayout actions = new LinearLayout(this);
        actions.addView(button("Use factory icon", new View.OnClickListener() {
            @Override public void onClick(View view) { apply(IconSelectionStore.FACTORY); }
        }));
        actions.addView(button("Use theme icon", new View.OnClickListener() {
            @Override public void onClick(View view) { apply(IconSelectionStore.THEME); }
        }));
        actions.addView(button("Browse my images", new View.OnClickListener() {
            @Override public void onClick(View view) { browse(); }
        }));
        body.addView(actions);
        body.addView(text("Curated icons", 20));
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        body.addView(grid);
        int width = (getResources().getDisplayMetrics().widthPixels - dp(64)) / 4;
        View first = null;
        for (final IconCatalog.Entry entry : IconCatalog.ALL) {
            final FocusTileView tile = new FocusTileView(this);
            tile.setTitle(entry.label);
            tile.setTag(entry.id);
            tile.setContentDescription(entry.category + ": " + entry.label);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { apply("catalog:" + entry.id); }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width;
            params.height = dp(125);
            grid.addView(tile, params);
            worker.execute(new Runnable() {
                @Override public void run() {
                    android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
                    options.inSampleSize = 4;
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(getResources(), entry.drawable, options);
                    final Drawable image = bitmap == null ? null : new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
                    runOnUiThread(new Runnable() {
                        @Override public void run() { if (!isDestroyed()) tile.setIcon(image); }
                    });
                }
            });
            if (first == null) first = tile;
        }
        if (first != null) first.requestFocus();
    }

    private void apply(String choice) {
        if (importing) return;
        try {
            IconSelectionStore.save(this, tileKey, choice);
            setResult(RESULT_OK);
            finish();
        } catch (IOException error) {
            status.setText(error.getMessage());
        }
    }

    private void browse() {
        if (importing) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, IMAGE_REQUEST);
        } catch (RuntimeException unavailable) {
            status.setText("No image browser is available. Curated icons still work; install a compatible document picker to browse your images.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMAGE_REQUEST || result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) {
            status.setText("Choose an image from a document provider.");
            return;
        }
        importing = true;
        status.setText("Importing image…");
        worker.execute(new Runnable() {
            @Override public void run() {
                String prepared = null;
                String failure = null;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("This image is unavailable.");
                    prepared = IconSelectionStore.prepareImage(getApplicationContext(), input);
                } catch (Exception error) {
                    failure = error instanceof IOException ? error.getMessage() : "This image could not be opened.";
                }
                final String selected = prepared;
                final String problem = failure;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        importing = false;
                        if (isFinishing() || isDestroyed()) {
                            IconSelectionStore.discardPreparedImage(getApplicationContext(), selected);
                            return;
                        }
                        if (selected == null) status.setText(problem);
                        else {
                            apply(selected);
                            if (!isFinishing()) IconSelectionStore.discardPreparedImage(getApplicationContext(), selected);
                        }
                    }
                });
            }
        });
    }

    private Button button(String label, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(action);
        return button;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(0xfff2f1f9);
        view.setTextSize(size);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
