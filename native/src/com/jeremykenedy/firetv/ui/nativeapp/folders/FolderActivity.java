package com.jeremykenedy.firetv.ui.nativeapp.folders;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppInventory;
import com.jeremykenedy.firetv.ui.nativeapp.locks.LauncherLocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FolderActivity extends Activity {
    public static final String EXTRA_FOLDER_ID = "folder_id";
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private List<AppInventory.App> apps = new ArrayList<>();
    private boolean loaded;
    private String folderId;
    private String focusKey = "create";
    private boolean direct;
    private LinearLayout body;
    private View desiredFocus;
    private String renderFocusKey;

    public static Intent intent(Context context, String folderId) {
        return new Intent(context, FolderActivity.class).putExtra(EXTRA_FOLDER_ID, folderId);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        direct = getIntent().getStringExtra(EXTRA_FOLDER_ID) != null;
        final String requested = state == null ? getIntent().getStringExtra(EXTRA_FOLDER_ID) : state.getString("folder");
        final String restoredFocus = state == null ? null : state.getString("focus", "create");
        if (restoredFocus != null) focusKey = restoredFocus;
        render();
        if (requested != null) open(requested, restoredFocus);
        loader.execute(new Runnable() {
            @Override public void run() {
                final List<AppInventory.App> result = AppInventory.load(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isDestroyed()) { apps = result; loaded = true; render(); }
                    }
                });
            }
        });
    }
    @Override protected void onResume() { super.onResume(); if (body != null) render(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("folder", folderId); state.putString("focus", focusKey);
    }
    @Override protected void onDestroy() { loader.shutdownNow(); super.onDestroy(); }
    @Override public void onBackPressed() {
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
        LinearLayout page = Pages.page(this);
        final FolderOptions options;
        try { options = FolderPreferences.load(this); }
        catch (IllegalStateException unreadable) {
            page.addView(Pages.text(this, "Folders unavailable", 30));
            page.addView(Pages.text(this, unreadable.getMessage(), 18)); return;
        }
        final FolderOptions.Folder folder = options.find(folderId);
        if (folderId != null && folder == null) {
            folderId = null; focusKey = "create";
            toast("This folder was removed.");
        }
        page.addView(Pages.text(this, folder == null ? "App folders" : folder.name, 30));
        page.addView(Pages.text(this, folder == null
                ? "Organize apps without changing favorites or the ordinary app grid. Hold a folder for its controls."
                : "Select an app to open it. Hold an app to move it or remove it from this folder.", 16));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (folder == null) {
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
        } else {
            add("add", "Add apps", new Runnable() { @Override public void run() { chooseApp(folder); } });
            add("controls", "Folder controls", new Runnable() { @Override public void run() { folderActions(folder); } });
            for (final String key : folder.components) {
                final AppInventory.App app = findApp(key);
                Button button = add("app:" + key, app == null ? key + (loaded ? " · Unavailable" : " · Loading") : app.label,
                        new Runnable() { @Override public void run() { launch(key, app); } });
                button.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View view) { appActions(folder.id, key, app); return true; }
                });
            }
            if (folder.components.isEmpty()) body.addView(Pages.text(this, "This folder is empty. Add an app to get started.", 18));
        }
        if (desiredFocus != null) desiredFocus.requestFocus();
        else if (body.getChildCount() > 0) body.getChildAt(0).requestFocus();
    }
    private Button add(final String key, String label, final Runnable action) {
        Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; action.run(); }
        });
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { if (focused) focusKey = key; }
        });
        body.addView(button);
        if (key.equals(renderFocusKey)) desiredFocus = button;
        return button;
    }
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
        menu(folder.name, new String[] {"Rename", "Move earlier", "Move later", "Lock controls", "Remove folder"},
                new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) name(folder);
                        else if (which == 3) LauncherLocks.showControls(FolderActivity.this, "folder:" + folder.id,
                                folder.name, new Runnable() { @Override public void run() { render(); } });
                        else if (which == 4) confirmRemove(folder);
                        else {
                            try { save(FolderPreferences.load(FolderActivity.this).move(folder.id, which == 1 ? -1 : 1)); }
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
