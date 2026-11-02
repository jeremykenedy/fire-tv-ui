package com.jeremykenedy.firetv.ui.nativeapp.setups;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import com.jeremykenedy.firetv.ui.nativeapp.migration.PreferenceSnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/** Isolated native persistence proof. No owner preference file or setup directory is opened. */
public final class SetupInstrumentation extends Instrumentation {
    private static final String PREFIX = "saved_setup_proof_";
    private int checks;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        final Context target = getTargetContext();
        final File root = new File(target.getCacheDir(), "saved-setup-proof");
        remove(root);
        final Context isolated =
                new ContextWrapper(target) {
                    @Override
                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return target.getSharedPreferences(PREFIX + name, mode);
                    }

                    @Override
                    public File getFilesDir() {
                        File file = new File(root, "files");
                        file.mkdirs();
                        return file;
                    }

                    @Override
                    public File getNoBackupFilesDir() {
                        File file = new File(root, "private");
                        file.mkdirs();
                        return file;
                    }
                };
        int resultCode = 0;
        try {
            for (String file : PresentationState.files())
                target.deleteSharedPreferences(PREFIX + file);
            SharedPreferences grid = isolated.getSharedPreferences("native_app_grid", 0);
            SharedPreferences account = isolated.getSharedPreferences("launcher_prefs", 0);
            check(grid.edit().putInt("columns", 7).commit(), "Fixture grid commit failed.");
            check(
                    account.edit()
                            .putString("account_token", "private fixture")
                            .putInt("fire_tv_ui_sleep", 100)
                            .commit(),
                    "Private fixture commit failed.");
            SavedSetupManager manager = new SavedSetupManager(isolated);
            String id = manager.save("Native evening", null);
            check(
                    new SavedSetupManager(isolated).list().size() == 1,
                    "Index must survive manager recreation.");
            check(
                    grid.edit().putInt("columns", 3).putBoolean("horizontal", true).commit(),
                    "Fixture change failed.");
            manager.apply(id, true);
            check(
                    grid.getInt("columns", 0) == 7 && !grid.contains("horizontal"),
                    "Apply must restore an integer and remove an absent default.");
            check(
                    "private fixture".equals(account.getString("account_token", ""))
                            && account.getInt("fire_tv_ui_sleep", 0) == 100,
                    "Accounts and hardware preferences must remain unchanged.");
            check(
                    !PresentationState.hasRecovery(isolated),
                    "Successful transaction must resolve its private journal.");
            manager.rename(id, "Native renamed");
            check(
                    "Native renamed"
                            .equals(
                                    new SavedSetupManager(isolated)
                                            .list()
                                            .get(0)
                                            .getString("name")),
                    "Rename must persist.");
            File source = new File(isolated.getFilesDir(), "source.png");
            try (FileOutputStream output = new FileOutputStream(source)) {
                output.write(new byte[] {1, 2, 3});
            }
            SharedPreferences appearance = isolated.getSharedPreferences("source_appearance", 0);
            check(
                    appearance
                            .edit()
                            .putString("wallpaper_image_uri", Uri.fromFile(source).toString())
                            .putString("wallpaper_mode", "image")
                            .commit(),
                    "Wallpaper fixture failed.");
            String picture = manager.save("Native picture", null);
            check(source.delete(), "Original image fixture must be removable.");
            manager.apply(picture, true);
            File retained =
                    new File(Uri.parse(appearance.getString("wallpaper_image_uri", "")).getPath());
            check(retained.isFile(), "Saved image must survive original deletion.");
            check(retained.delete(), "Retained source fixture removal failed.");
            Map<String, PreferenceSnapshot> before = PresentationState.capture(isolated);
            try {
                manager.apply(picture, true);
                throw new AssertionError("Missing media must explicitly fail.");
            } catch (IOException expected) {
                checks++;
            }
            Map<String, PreferenceSnapshot> after = PresentationState.capture(isolated);
            for (String file : PresentationState.files())
                check(
                        before.get(file).values().equals(after.get(file).values()),
                        "Missing media must leave presentation unchanged.");
            check(manager.list().size() == 2, "Missing media must never delete the setup.");
            manager.delete(id);
            check(new SavedSetupManager(isolated).list().size() == 1, "Delete must persist.");
            result.putString("result", "PASS: " + checks + " isolated native persistence checks");
            resultCode = -1;
        } catch (Throwable failure) {
            result.putString("result", "FAIL: " + failure.toString());
            resultCode = 0;
        } finally {
            for (String file : PresentationState.files())
                target.deleteSharedPreferences(PREFIX + file);
            remove(root);
        }
        finish(resultCode, result);
    }

    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }
}
