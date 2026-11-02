package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PhotoSettingsActivity extends Activity {
    private static final int PICK_PHOTOS = 41;
    private LinearLayout body;
    private TextView status;
    private PhotoFrameView preview;
    private ExecutorService worker;
    private CancellationSignal cancellation;
    private boolean importing, foreground, canceledImport;
    private int generation;
    private String replacement, focusKey = "add";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { replacement = state.getString("replacement"); focusKey = state.getString("focus", "add"); }
        render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("replacement", replacement); state.putString("focus", focusKey);
    }
    @Override protected void onStart() {
        super.onStart(); foreground = true;
        if (canceledImport && status != null) { status.setText("Photo selection cancelled. Your previous selections have been kept."); canceledImport = false; }
        if (preview != null) preview.start();
    }
    @Override protected void onStop() {
        foreground = false; generation++; canceledImport = importing; importing = false;
        if (cancellation != null) cancellation.cancel();
        if (worker != null) worker.shutdownNow(); worker = null;
        if (preview != null) preview.stop();
        super.onStop();
    }
    private void render() {
        if (preview != null) preview.stop();
        preview = null;
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Photo frame", 30));
        page.addView(Pages.text(this, "Choose up to 32 document images. Photo access stays on this TV and is excluded from portable setups. The widget starts hidden in Home layout.", 16));
        status = Pages.text(this, "", 16); status.setTag("photos:status"); page.addView(status);
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        final PhotoOptions options;
        try { options = PhotoPreferences.load(this); }
        catch (IllegalStateException error) { status.setText(error.getMessage()); return; }
        add("add", "Add photos", new Runnable() { @Override public void run() { browse(null); } });
        add("interval", "Slideshow: " + options.intervalSeconds + " seconds per photo", new Runnable() {
            @Override public void run() { interval(); }
        });
        add("fit", "Photo fit: " + (options.fit == PhotoOptions.Fit.CONTAIN ? "Fit complete image" : "Fill frame (crop edges)"), new Runnable() {
            @Override public void run() {
                try { PhotoOptions old = PhotoPreferences.load(PhotoSettingsActivity.this);
                    save(old.presentation(old.intervalSeconds, old.fit == PhotoOptions.Fit.CONTAIN ? PhotoOptions.Fit.CROP : PhotoOptions.Fit.CONTAIN));
                } catch (RuntimeException error) { status.setText(error.getMessage()); }
            }
        });
        preview = new PhotoFrameView(this); body.addView(preview, new LinearLayout.LayoutParams(-1, Pages.dp(this, 210)));
        if (foreground) preview.start();
        for (int i = 0; i < options.photos.size(); i++) {
            final PhotoOptions.Photo photo = options.photos.get(i);
            add(photo.id, (i + 1) + ". " + photo.label, new Runnable() { @Override public void run() { actions(photo); } });
        }
        View focus = body.findViewWithTag("photos:" + focusKey);
        if (focus == null) focus = body.findViewWithTag("photos:add");
        if (focus != null) focus.requestFocus();
    }
    private Button add(final String key, String label, final Runnable action) {
        Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { if (!importing) { focusKey = key; action.run(); } }
        });
        button.setTag("photos:" + key);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { if (focused) focusKey = key; }
        }); body.addView(button); return button;
    }
    private void interval() {
        String[] labels = {"10 seconds", "30 seconds", "1 minute", "5 minutes", "15 minutes"};
        new AlertDialog.Builder(this).setTitle("Slideshow interval").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                try { PhotoOptions old = PhotoPreferences.load(PhotoSettingsActivity.this); save(old.presentation(PhotoOptions.INTERVALS[which], old.fit)); }
                catch (RuntimeException error) { status.setText(error.getMessage()); }
            }
        }).setNegativeButton("Cancel", null).show();
    }
    private void actions(final PhotoOptions.Photo photo) {
        new AlertDialog.Builder(this).setTitle("Photo controls").setItems(new String[] {"Choose replacement photo", "Move earlier", "Move later", "Remove photo"},
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) { browse(photo.id); return; }
                        try {
                            PhotoOptions old = PhotoPreferences.load(PhotoSettingsActivity.this);
                            if (which == 3) { focusKey = "add"; save(old.remove(photo.id)); }
                            else save(old.move(photo.id, which == 1 ? -1 : 1));
                        } catch (RuntimeException error) { status.setText(error.getMessage()); }
                    }
                }).setNegativeButton("Cancel", null).show();
    }
    private void save(PhotoOptions options) { PhotoPreferences.save(this, options); render(); }
    private void browse(String replaceId) {
        replacement = replaceId;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, replaceId == null)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, PICK_PHOTOS); }
        catch (RuntimeException unavailable) { replacement = null; status.setText("No document image picker is available. Install a compatible document picker to select photos."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PHOTOS) return;
        final String replaceId = replacement; replacement = null;
        if (result != RESULT_OK || data == null) return;
        final List<Uri> sources = new ArrayList<>();
        if (data.getClipData() != null) {
            if (data.getClipData().getItemCount() > PhotoOptions.MAX_PHOTOS) { status.setText("Choose up to 32 photos."); return; }
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri(); if (uri != null && !sources.contains(uri)) sources.add(uri);
            }
        } else if (data.getData() != null) sources.add(data.getData());
        if (sources.isEmpty() || replaceId != null && sources.size() != 1) return;
        final int flags = data.getFlags(); final int requestGeneration = generation;
        importing = true; status.setText("Checking selected photos...");
        cancellation = new CancellationSignal(); final CancellationSignal signal = cancellation;
        if (worker != null) worker.shutdownNow();
        worker = Executors.newSingleThreadExecutor();
        final ExecutorService requestWorker = worker;
        worker.execute(new Runnable() {
            @Override public void run() {
                final List<Uri> acquired = new ArrayList<>(); final List<String> labels = new ArrayList<>();
                String failure = null;
                try {
                    PhotoOptions saved = PhotoPreferences.load(getApplicationContext());
                    if (replaceId == null && saved.photos.size() + sources.size() > PhotoOptions.MAX_PHOTOS) throw new IOException("Keep up to 32 selected photos. Remove a photo before adding more.");
                    for (Uri uri : sources) {
                        if (signal.isCanceled() || Thread.currentThread().isInterrupted()) throw new IOException("Photo selection cancelled.");
                        if (!"content".equals(uri.getScheme()) || uri.toString().length() > 2048) throw new IOException("Choose photos from a document provider.");
                        if (!PhotoWidgetProvider.hasGrant(getApplicationContext(), uri)) {
                            if ((flags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0 || (flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
                                throw new IOException("This provider cannot retain photo access. Choose a document provider with persistent access.");
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); acquired.add(uri);
                        }
                        Bitmap image = PhotoDecoder.read(getApplicationContext(), uri, signal); image.recycle();
                        labels.add(label(uri, signal));
                    }
                } catch (Exception error) { failure = error instanceof IOException ? error.getMessage() : "Selected photos could not be opened. Your previous selections have been kept."; }
                final String problem = failure;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        requestWorker.shutdown();
                        if (requestGeneration != generation || isFinishing() || isDestroyed()) { releaseUnused(acquired); return; }
                        importing = false;
                        if (problem != null) { releaseUnused(acquired); status.setText(problem); return; }
                        try {
                            PhotoOptions next = PhotoPreferences.load(PhotoSettingsActivity.this);
                            for (int i = 0; i < sources.size(); i++) next = replaceId == null ? next.add(sources.get(i).toString(), labels.get(i)) : next.replace(replaceId, sources.get(i).toString(), labels.get(i));
                            save(next);
                        } catch (RuntimeException error) { releaseUnused(acquired); status.setText(error.getMessage()); }
                    }
                });
            }
        });
    }
    private String label(Uri uri, CancellationSignal signal) {
        String label = "Photo";
        try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null, signal)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) label = cursor.getString(0);
        } catch (RuntimeException unavailable) { /* Provider names are optional. */ }
        label = label.replaceAll("\\p{Cntrl}", " ").trim();
        if (label.isEmpty()) label = "Photo";
        return label.substring(0, Math.min(160, label.length()));
    }
    private void releaseUnused(List<Uri> acquired) {
        try {
            PhotoOptions saved = PhotoPreferences.load(getApplicationContext());
            for (Uri uri : acquired) {
                boolean used = false; for (PhotoOptions.Photo photo : saved.photos) if (photo.uri.equals(uri.toString())) used = true;
                if (!used) try { getContentResolver().releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (RuntimeException ignored) {}
            }
        } catch (RuntimeException unreadableSettings) { /* Keep grants when their saved references cannot be established. */ }
    }
}
