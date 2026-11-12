package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.MenuActivity;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconPackStore;
import com.jeremykenedy.firetv.ui.nativeapp.setups.SavedSetupsActivity;

public final class WallpaperThemeActivity extends MenuActivity {
    private static final int IMAGE = 7101, VIDEO = 7102;
    private boolean startupBlocked;
    private Button light, dark;
    private String focusTag;
    private WallpaperRemoteImages.Request download;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.sidePanel(this);
        TextView heading = Pages.text(this, "Wallpaper theme", 22);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, Pages.dp(this, 4), 0, Pages.dp(this, 20));
        page.addView(heading);
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                view.setClipBounds(new android.graphics.Rect(-page.getPaddingLeft(), 0,
                        view.getWidth() + page.getPaddingRight(), view.getHeight() + page.getPaddingBottom()));
            }
        });
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false); rows.setClipToPadding(false);
        rows.setPadding(0, Pages.dp(this, 4), 0, Pages.dp(this, 8));
        scroll.addView(rows); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        light = row(rows, "Light", "light", new Runnable() { @Override public void run() { theme("still_light"); } });
        dark = row(rows, "Dark", "dark", new Runnable() { @Override public void run() { theme("still_dark"); } });
        row(rows, "Custom image", "image", new Runnable() { @Override public void run() { pick(IMAGE); } });
        row(rows, "Custom image from URL", "url", new Runnable() { @Override public void run() { imageUrl(); } });
        row(rows, "Custom Video", "video", new Runnable() { @Override public void run() { pick(VIDEO); } });
        row(rows, "Subreddit Wallpaper", "subreddit", new Runnable() { @Override public void run() {
            String name = getSharedPreferences("launcher_prefs", MODE_PRIVATE).getString("subreddit_wallpaper_name", "");
            new AlertDialog.Builder(WallpaperThemeActivity.this).setTitle("Subreddit Wallpaper")
                    .setMessage((name.isEmpty() ? "" : "Saved subreddit: r/" + name + "\n\n")
                            + "Your saved choice is kept. Online wallpaper integration is paused. You can choose a personal image or an image URL.")
                    .setPositiveButton("Choose image", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) { pick(IMAGE); }
                    }).setNegativeButton("Back", null).show();
        } });
        row(rows, "Icon Pack", "icons", new Runnable() { @Override public void run() { IconPackStore.showPicker(WallpaperThemeActivity.this, null); } });
        row(rows, "Backgrounds and overlays", "backgrounds", new Runnable() { @Override public void run() {
            startActivity(new Intent(WallpaperThemeActivity.this, BackgroundSettingsActivity.class));
        } });
        row(rows, "Saved setups", "setups", new Runnable() { @Override public void run() {
            startActivity(new Intent(WallpaperThemeActivity.this, SavedSetupsActivity.class));
        } });
        refresh();
        String focus = state == null ? "light" : state.getString("focus", "light");
        focusTag = "wallpaper:" + focus;
        View selected = rows.findViewWithTag(focusTag);
        if (selected != null) selected.requestFocus();
    }

    private Button row(LinearLayout rows, String label, String key, final Runnable action) {
        Button button = Pages.menuButton(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        button.setTag("wallpaper:" + key);
        LinearLayout.LayoutParams bounds = (LinearLayout.LayoutParams) button.getLayoutParams();
        bounds.bottomMargin = Pages.dp(this, 16);
        button.setPadding(Pages.dp(this, 18), 0, Pages.dp(this, 18), 0);
        rows.addView(button);
        return button;
    }

    private void theme(String selected) {
        try {
            AppearancePreferences.update(this, new String[]{"base", "scene", "wallpaper_mode"}, new Object[]{selected, "", "system"});
            refresh();
        } catch (IllegalStateException failure) { error(failure.getMessage()); }
    }

    private void refresh() {
        AppearanceOptions options = AppearancePreferences.load(this);
        mark(light, options.base.equals("still_light") && options.scene.isEmpty());
        mark(dark, options.base.equals("still_dark") && options.scene.isEmpty());
    }

    private void mark(Button button, boolean selected) {
        android.graphics.drawable.Drawable check = selected ? new CheckMark() : null;
        if (check != null) check.setBounds(0, 0, Pages.dp(this, 24), Pages.dp(this, 24));
        button.setCompoundDrawables(null, null, check, null);
    }

    private static final class CheckMark extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(android.graphics.Canvas canvas) {
            boolean focused = false;
            for (int state : getState()) if (state == android.R.attr.state_focused) focused = true;
            paint.setColor(focused ? 0xff000000 : 0xffd3d3d3);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            paint.setStrokeWidth(getBounds().width() / 10f);
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(getBounds().width() * .2f, getBounds().height() * .55f);
            path.lineTo(getBounds().width() * .4f, getBounds().height() * .8f);
            path.lineTo(getBounds().width() * .85f, getBounds().height() * .2f);
            canvas.drawPath(path, paint);
        }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] states) { invalidateSelf(); return true; }
        @Override public void setAlpha(int value) { paint.setAlpha(value); }
        @Override public void setColorFilter(android.graphics.ColorFilter value) { paint.setColorFilter(value); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void pick(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(request == IMAGE ? "image/*" : "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(intent, request); }
        catch (ActivityNotFoundException | SecurityException unavailable) { error("This device has no available document picker. Your current wallpaper is kept."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (startupBlocked || (request != IMAGE && request != VIDEO) || result != RESULT_OK || data == null || data.getData() == null) return;
        if (!SourceStartup.ready(this)) { startupBlocked = SourceStartup.redirectIfBlocked(this); return; }
        Uri uri = data.getData();
        if (!"content".equals(uri.getScheme())) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            applyMedia(uri, request == VIDEO);
        } catch (SecurityException | IllegalStateException failure) { error("The selected wallpaper could not be saved. Your current wallpaper is kept."); }
    }

    private void applyMedia(Uri uri, boolean video) {
        AppearancePreferences.update(this,
                new String[]{video ? "wallpaper_video_uri" : "wallpaper_image_uri", "wallpaper_mode", "base", "scene"},
                new Object[]{uri.toString(), video ? "video" : "image", "off", ""});
        refresh();
    }

    private void imageUrl() {
        final EditText input = new EditText(this);
        input.setHint("Image URL"); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Enter Image URL").setView(input)
                .setNeutralButton("Pick from device instead", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface owner, int which) { pick(IMAGE); }
                }).setNegativeButton("Cancel", null).setPositiveButton("Apply", null).create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface owner) { if (download != null) { download.cancel(); download = null; } }
        });
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface owner) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        input.setEnabled(false);
                        download = WallpaperRemoteImages.download(WallpaperThemeActivity.this, input.getText().toString().trim(), new WallpaperRemoteImages.Callback() {
                            @Override public void success(Uri uri) {
                                download = null;
                                if (isDestroyed() || !SourceStartup.ready(WallpaperThemeActivity.this)) return;
                                try { applyMedia(uri, false); dialog.dismiss(); }
                                catch (IllegalStateException failure) { failure("The image could not be saved. Your current wallpaper is kept."); }
                            }
                            @Override public void failure(String message) {
                                download = null;
                                if (isDestroyed()) return;
                                input.setEnabled(true); input.setError(message);
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); input.requestFocus();
                            }
                        });
                    }
                });
            }
        });
        dialog.show();
    }

    private void error(String message) { new AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show(); }
    @Override protected void onResume() {
        super.onResume();
        if (startupBlocked) return;
        if (!SourceStartup.ready(this)) { startupBlocked = SourceStartup.redirectIfBlocked(this); return; }
        if (light != null) {
            refresh();
            View selected = getWindow().getDecorView().findViewWithTag(focusTag);
            if (selected != null) selected.requestFocus();
        }
    }
    private void rememberFocus() {
        View focused = getCurrentFocus();
        if (focused != null && focused.isShown() && focused.getTag() instanceof String)
            focusTag = focused.getTag().toString();
    }
    @Override protected void onPause() {
        rememberFocus();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        rememberFocus();
        if (focusTag != null) state.putString("focus", focusTag.replace("wallpaper:", ""));
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (download != null) download.cancel();
        super.onDestroy();
    }
}
