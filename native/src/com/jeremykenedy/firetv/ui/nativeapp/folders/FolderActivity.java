package com.jeremykenedy.firetv.ui.nativeapp.folders;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.accessibility.AccessibilityStyle;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusOptions;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusTileView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.AppFocusViewport;
import com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FolderActivity extends Activity {
    private boolean startupBlocked;
    public static final String EXTRA_FOLDER_ID = "folder_id";
    private static final String EXTRA_BACKDROP = "folder_backdrop";
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private List<AppInventory.App> apps = new ArrayList<>();
    private boolean loaded;
    private final Map<String, Drawable> artwork = new LinkedHashMap<>();
    private String folderId;
    private String focusKey = "create";
    private boolean direct;
    private LinearLayout body;
    private View desiredFocus;
    private String renderFocusKey;
    private ScrollView memberScroll;
    private String memberFolderId;
    private int restoredScroll;
    private Bitmap backdrop;
    private ImageView backdropView;

    public static Intent intent(Context context, String folderId) {
        Intent intent = new Intent(context, FolderActivity.class).putExtra(EXTRA_FOLDER_ID, folderId);
        if (context instanceof Activity) {
            View decor = ((Activity) context).getWindow().getDecorView();
            if (decor.getWidth() > 0 && decor.getHeight() > 0) {
                Bitmap captured = null;
                try {
                    float scale = Math.min(0.05f, Math.min(192f / decor.getWidth(), 108f / decor.getHeight()));
                    captured = Bitmap.createBitmap(Math.max(1, Math.round(decor.getWidth() * scale)),
                            Math.max(1, Math.round(decor.getHeight() * scale)), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(captured);
                    canvas.scale(captured.getWidth() / (float) decor.getWidth(), captured.getHeight() / (float) decor.getHeight());
                    decor.draw(canvas); intent.putExtra(EXTRA_BACKDROP, captured);
                } catch (RuntimeException unavailable) {
                    if (captured != null) captured.recycle();
                }
            }
        }
        return intent;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        Bitmap captured = getIntent().getParcelableExtra(EXTRA_BACKDROP);
        if (captured != null && !captured.isRecycled() && captured.getWidth() <= 192 && captured.getHeight() <= 108)
            backdrop = captured.copy(Bitmap.Config.ARGB_8888, false);
        direct = getIntent().getStringExtra(EXTRA_FOLDER_ID) != null;
        final String requested = state == null ? getIntent().getStringExtra(EXTRA_FOLDER_ID) : state.getString("folder");
        final String restoredFocus = state == null ? null : state.getString("focus", "create");
        if (restoredFocus != null) focusKey = restoredFocus;
        render();
        if (requested != null) {
            restoredScroll = state == null ? 0 : state.getInt("scroll", 0);
            open(requested, restoredFocus);
        }
        loader.execute(new Runnable() {
            @Override public void run() {
                final List<AppInventory.App> result = AppInventory.load(getApplicationContext());
                final Map<String, Drawable> resolved = new LinkedHashMap<>();
                for (AppInventory.App app : result) {
                    try { resolved.put(app.key(), IconSelectionStore.resolve(getApplicationContext(), app.key(), app.artwork, null)); }
                    catch (RuntimeException unavailable) { resolved.put(app.key(), app.artwork); }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isDestroyed()) { apps = result; artwork.clear(); artwork.putAll(resolved); loaded = true; render(); }
                    }
                });
            }
        });
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); if (body != null) render(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        super.onSaveInstanceState(state); state.putString("folder", folderId); state.putString("focus", focusKey);
        state.putInt("scroll", memberScroll == null ? 0 : memberScroll.getScrollY());
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; }
        loader.shutdownNow();
        if (backdropView != null) backdropView.setImageDrawable(null);
        if (backdrop != null) { backdrop.recycle(); backdrop = null; }
        if (isFinishing()) getIntent().removeExtra(EXTRA_BACKDROP);
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (startupBlocked) { super.onBackPressed(); return; }
        if (folderId != null && !direct) {
            focusKey = "folder:" + folderId; folderId = null; render();
        } else super.onBackPressed();
    }
    private void open(final String id) { open(id, null); }
    private void open(final String id, final String restoredFocus) {
        LauncherLocks.guard(this, "folder:" + id, new Runnable() {
            @Override public void run() {
                try {
                    FolderOptions.Folder folder = FolderPreferences.load(FolderActivity.this).find(id);
                    folderId = id;
                    if (restoredFocus != null) focusKey = restoredFocus;
                    else if (folder != null)
                        focusKey = folder.components.isEmpty() ? "add" : "app:" + folder.components.get(0);
                    render();
                } catch (IllegalStateException unreadable) { toast(unreadable.getMessage()); }
            }
        });
    }
    private void render() {
        desiredFocus = null;
        renderFocusKey = focusKey;
        final FolderOptions options;
        try { options = FolderPreferences.load(this); }
        catch (IllegalStateException unreadable) {
            LinearLayout page = Pages.page(this);
            page.addView(Pages.text(this, "Folders unavailable", 30));
            page.addView(Pages.text(this, unreadable.getMessage(), 18)); return;
        }
        final FolderOptions.Folder folder = options.find(folderId);
        if (folderId != null && folder == null) {
            folderId = null; focusKey = "create";
            toast("This folder was removed.");
        }
        if (folder != null) { renderFolder(folder); return; }
        memberScroll = null; memberFolderId = null; restoredScroll = 0;
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "App folders", 30));
        page.addView(Pages.text(this, "Organize apps without changing favorites or the ordinary app grid. Hold a folder for its controls.", 16));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add("create", "Create folder", new Runnable() { @Override public void run() { name(null); } });
        for (final FolderOptions.Folder item : options.folders) {
            Button button = add("folder:" + item.id, item.name + " · " + item.components.size() + " apps",
                    new Runnable() { @Override public void run() { open(item.id); } });
            button.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) {
                    LauncherLocks.guard(FolderActivity.this, "folder:" + item.id,
                            new Runnable() { @Override public void run() { folderActions(item); } });
                    return true;
                }
            });
        }
        if (options.folders.isEmpty()) body.addView(Pages.text(this, "No folders yet.", 18));
        if (desiredFocus != null) desiredFocus.requestFocus();
        else if (body.getChildCount() > 0) body.getChildAt(0).requestFocus();
    }
    private Button add(final String key, String label, final Runnable action) {
        Button button = control(key, label, action);
        AccessibilityStyle.apply(this, button); body.addView(button);
        return button;
    }
    private Button control(final String key, String label, final Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; action.run(); }
        });
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { if (focused) focusKey = key; }
        });
        button.setTag(key);
        if (key.equals(renderFocusKey)) desiredFocus = button;
        return button;
    }
    private void renderFolder(final FolderOptions.Folder folder) {
        final int scrollY = restoredScroll != 0 ? restoredScroll : memberScroll == null ? 0
                : folder.id.equals(memberFolderId) ? memberScroll.getScrollY() : 0;
        memberFolderId = folder.id;
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false); root.setClipToPadding(false);
        if (backdrop != null) {
            backdropView = new ImageView(this);
            backdropView.setScaleType(ImageView.ScaleType.FIT_XY);
            android.graphics.drawable.BitmapDrawable blurred = new android.graphics.drawable.BitmapDrawable(getResources(), backdrop);
            blurred.setFilterBitmap(true); backdropView.setImageDrawable(blurred);
            root.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));
        } else root.setBackgroundColor(0xff101523);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false); body.setClipToPadding(false);
        body.setPadding(dp(36), dp(16), dp(36), dp(20));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xe6080618); panel.setCornerRadius(dp(24)); body.setBackground(panel);
        body.setTag("folder:panel");
        int width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.60f);
        int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.82f);
        root.addView(body, new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
        TextView heading = new TextView(this);
        heading.setText(folder.name); heading.setTextSize(20); heading.setTextColor(0xffb5b2bd);
        heading.setSingleLine(true); heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setTypeface(null, Typeface.BOLD); heading.setGravity(Gravity.CENTER);
        AccessibilityStyle.apply(this, heading);
        body.addView(heading, new LinearLayout.LayoutParams(-1, Math.max(dp(40), textHeight(heading))));
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false); scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(16), 0, dp(16));
        scroll.setTag("folder:scroll"); memberScroll = scroll;
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        GridLayout grid = new GridLayout(this);
        grid.setClipChildren(false); grid.setClipToPadding(false);
        AppFocusViewport.attach(grid, scroll);
        int columns = Math.max(1, Math.min(3, (width - dp(72)) / dp(160)));
        grid.setColumnCount(columns); grid.setTag("folder:apps"); scroll.addView(grid);
        int cellWidth = (width - dp(72)) / columns;
        int artworkWidth = Math.min(dp(140), FocusOptions.legacyAppArtworkWidth(cellWidth));
        int artworkHeight = FocusOptions.legacyAppArtworkHeight(artworkWidth);
        int tileHeight = artworkHeight + 2 * FocusOptions.legacyAppTopInset(artworkHeight);
        for (final String key : folder.components) {
            final AppInventory.App app = findApp(key);
            final String label = app == null ? key + (loaded ? " · Unavailable" : " · Loading") : app.label;
            FocusTileView tile = new FocusTileView(this);
            tile.useAppArtworkPresentation(artworkWidth); tile.setTitleVisible(false);
            tile.setTitle(label); tile.setArtworkFit(true); tile.setTag("app:" + key);
            tile.setIcon(app == null ? getDrawable(android.R.drawable.sym_def_app_icon)
                    : artwork.containsKey(key) ? artwork.get(key) : app.artwork);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { focusKey = "app:" + key; launch(key, app); }
            });
            tile.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) { focusKey = "app:" + key; appActions(folder.id, key, app); return true; }
            });
            tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) { if (focused) focusKey = "app:" + key; }
            });
            TextView title = new TextView(this); title.setText(app == null ? loaded ? "Unavailable" : "Loading..." : app.label);
            title.setTextSize(14); title.setTextColor(Color.WHITE); title.setTypeface(null, Typeface.BOLD);
            title.setTranslationZ(dp(40));
            title.setGravity(Gravity.CENTER); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setPadding(dp(4), dp(2), dp(4), 0);
            title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            AccessibilityStyle.apply(this, title);
            int labelHeight = Math.max(dp(28), textHeight(title));
            FrameLayout.LayoutParams titleBounds = new FrameLayout.LayoutParams(-1, labelHeight, Gravity.TOP);
            titleBounds.topMargin = tileHeight - dp(3); tile.addView(title, titleBounds);
            GridLayout.LayoutParams bounds = new GridLayout.LayoutParams();
            bounds.width = cellWidth; bounds.height = tileHeight + Math.max(dp(44), labelHeight + dp(16));
            grid.addView(tile, bounds);
            if (("app:" + key).equals(renderFocusKey)) desiredFocus = tile;
        }
        if (folder.components.isEmpty()) {
            TextView empty = Pages.text(this, "This folder is empty. Add an app to get started.", 18);
            empty.setGravity(Gravity.CENTER); grid.addView(empty);
        }
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsBounds = new LinearLayout.LayoutParams(-1, dp(44));
        body.addView(controls, controlsBounds);
        Button addApps = control("add", "Add apps", new Runnable() { @Override public void run() { chooseApp(folder); } });
        Button folderControls = control("controls", "Folder controls", new Runnable() { @Override public void run() { folderActions(folder); } });
        for (Button button : new Button[]{addApps, folderControls}) {
            button.setTextSize(16); button.setTextColor(Color.WHITE);
            button.setSingleLine(true); button.setEllipsize(android.text.TextUtils.TruncateAt.END);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
            button.setPadding(dp(8), 0, dp(8), 0); button.setStateListAnimator(null);
            button.setBackground(controlBackground());
            AccessibilityStyle.apply(this, button);
            int buttonWidth = Math.min((width - dp(96)) / 2, Math.max(dp(140),
                    (int) Math.ceil(button.getPaint().measureText(button.getText().toString())) + dp(16)));
            int buttonHeight = Math.max(dp(40), textHeight(button) + dp(8));
            controlsBounds.height = Math.max(controlsBounds.height, buttonHeight + dp(4));
            LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(buttonWidth, buttonHeight);
            bounds.setMargins(dp(6), 0, dp(6), 0); controls.addView(button, bounds);
        }
        AccessibilityStyle.apply(this, root); setContentView(root);
        if (desiredFocus != null) desiredFocus.requestFocus(); else addApps.requestFocus();
        final View restoredFocus = desiredFocus;
        if (restoredFocus != null) scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                if (memberScroll != scroll || !scroll.isAttachedToWindow()) return;
                scroll.scrollTo(0, scrollY);
                restoredScroll = 0;
                if (restoredFocus instanceof FocusTileView) {
                    int[] point = new int[2]; scroll.getLocationOnScreen(point);
                    android.graphics.Rect viewport = new android.graphics.Rect(point[0], point[1],
                            point[0] + scroll.getWidth(), point[1] + scroll.getHeight());
                    restoredFocus.getLocationOnScreen(point);
                    if (!viewport.contains(point[0], point[1], point[0] + restoredFocus.getWidth(),
                            point[1] + restoredFocus.getHeight())) {
                        restoredFocus.requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                                restoredFocus.getWidth(), restoredFocus.getHeight()), true);
                    }
                }
            }
        });
    }
    private Drawable controlBackground() {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        for (boolean focused : new boolean[]{true, false}) {
            GradientDrawable background = new GradientDrawable();
            background.setColor(focused ? 0xff555563 : 0x18ffffff); background.setCornerRadius(dp(8));
            background.setStroke(dp(focused ? 2 : 1), focused ? Color.WHITE : 0x44ffffff);
            states.addState(focused ? new int[]{android.R.attr.state_focused} : new int[]{}, background);
        }
        return states;
    }
    private int textHeight(TextView text) {
        android.graphics.Paint.FontMetricsInt metrics = text.getPaint().getFontMetricsInt();
        return metrics.bottom - metrics.top + text.getCompoundPaddingTop() + text.getCompoundPaddingBottom();
    }
    private int dp(int value) { return Pages.dp(this, value); }
    private AppInventory.App findApp(String key) {
        for (AppInventory.App app : apps) if (app.key().equals(key)) return app;
        return null;
    }
    private void launch(final String key, AppInventory.App app) {
        if (app == null) { toast(loaded ? "App unavailable. Its place in this folder is kept." : "Apps are loading."); return; }
        LauncherLocks.guard(this, key, new Runnable() {
            @Override public void run() {
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setComponent(ComponentName.unflattenFromString(key)));
                } catch (ActivityNotFoundException | SecurityException unavailable) { toast("This app could not be opened."); }
            }
        });
    }
    private void folderActions(final FolderOptions.Folder folder) {
        menu(folder.name, new String[] {"Rename", "Move earlier on Home", "Move later on Home", "Lock controls", "Remove folder"},
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) name(folder);
                        else if (which == 3) LauncherLocks.showControls(FolderActivity.this, "folder:" + folder.id,
                                folder.name, new Runnable() { @Override public void run() { render(); } });
                        else if (which == 4) confirmRemove(folder);
                        else {
                            try {
                                com.jeremykenedy.firetv.ui.nativeapp.appgrid.HomeTileActions.move(
                                        FolderActivity.this, "folder:" + folder.id, which == 1 ? -1 : 1);
                                render();
                            }
                            catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); }
                        }
                    }
                });
    }
    private void confirmRemove(final FolderOptions.Folder folder) {
        new AlertDialog.Builder(this).setTitle("Remove " + folder.name + "?")
                .setMessage("Only this folder is removed. Its apps and favorites are kept.")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove folder", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try { save(FolderPreferences.load(FolderActivity.this).remove(folder.id)); }
                        catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); }
                    }
                }).show();
    }
    private void appActions(final String id, final String key, AppInventory.App app) {
        menu(app == null ? key : app.label, new String[] {"Move earlier", "Move later", "Remove from folder"},
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            FolderOptions old = FolderPreferences.load(FolderActivity.this);
                            save(which == 2 ? old.removeApp(id, key) : old.moveApp(id, key, which == 0 ? -1 : 1));
                        } catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); }
                    }
                });
    }
    private void chooseApp(final FolderOptions.Folder folder) {
        if (!loaded) { toast("Apps are loading."); return; }
        final List<AppInventory.App> available = new ArrayList<>();
        for (AppInventory.App app : apps) if (!folder.components.contains(app.key())) available.add(app);
        if (available.isEmpty()) { toast("No more TV apps to add."); return; }
        String[] labels = new String[available.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = available.get(i).label;
        menu("Add app to " + folder.name, labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                try { save(FolderPreferences.load(FolderActivity.this).addApp(folder.id, available.get(which).key())); }
                catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); }
            }
        });
    }
    private void name(final FolderOptions.Folder folder) {
        final EditText input = new EditText(this); input.setSingleLine(true);
        if (folder != null) { input.setText(folder.name); input.selectAll(); }
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(folder == null ? "Create folder" : "Rename folder")
                .setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            FolderOptions old = FolderPreferences.load(FolderActivity.this);
                            FolderOptions next = folder == null ? old.create(input.getText().toString())
                                    : old.rename(folder.id, input.getText().toString());
                            if (folder == null) focusKey = "folder:" + next.folders.get(next.folders.size() - 1).id;
                            FolderPreferences.save(FolderActivity.this, next); dialog.dismiss(); render();
                        } catch (IllegalArgumentException | IllegalStateException error) { input.setError(error.getMessage()); }
                    }
                });
            }
        });
        dialog.show();
    }
    private void save(FolderOptions value) { FolderPreferences.save(this, value); render(); }
    private void menu(String title, String[] labels, DialogInterface.OnClickListener action) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setItems(labels, action)
                .setNegativeButton("Cancel", null).show();
        dialog.getListView().requestFocus(); dialog.getListView().setSelection(0);
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
}
