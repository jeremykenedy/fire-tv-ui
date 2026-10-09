package com.jeremykenedy.firetv.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

public final class BackupActivity extends CardActivity {
    private static final int ACCENT = Color.WHITE;
    private TextView status;

    public static void open(Context context) {
        context.startActivity(new Intent(context, BackupActivity.class));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout root = card;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(20));
        TextView title = text("Backup and restore", 28, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(6), 0, dp(8));
        root.addView(title);
        TextView description = text("Your backup stays in TV Downloads after uninstalling or reinstalling Fire TV UI.",
                17, Color.rgb(193, 201, 214));
        root.addView(description);
        status = text("", 16, Color.rgb(151, 164, 183));
        status.setPadding(0, dp(12), 0, dp(18));
        root.addView(status);
        root.addView(button("Save backup on TV", 0));
        root.addView(button("Restore saved TV backup", 1));
        root.addView(button("Restore backup sent from computer", 2));
        root.addView(button("Allow TV backup access", 3));
        TextView hint = text("The Fire TV CLI can retrieve this backup or send one to the TV. Press Back to return.",
                14, Color.rgb(151, 164, 183));
        hint.setPadding(0, dp(20), 0, 0);
        root.addView(hint);
        root.getChildAt(3).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private TextView button(String label, final int action) {
        TextView button = text(label, 20, Color.WHITE);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setClickable(true);
        style(button, false);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                style((TextView) view, focused);
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (action == 0) {
                    save();
                } else if (action == 3) {
                    allowAccess();
                } else {
                    confirmRestore(action == 2);
                }
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.bottomMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private void save() {
        try {
            BackupStore.save(this);
            refresh();
            message("Backup saved", "Your settings are saved on the TV and will remain after uninstalling the app.");
        } catch (Exception error) {
            failure(error);
        }
    }

    private void confirmRestore(final boolean incoming) {
        new AlertDialog.Builder(this).setTitle("Restore settings?")
                .setMessage("This replaces the current Fire TV UI layout and settings with this backup.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        restore(incoming);
                    }
                }).show();
    }

    private void restore(boolean incoming) {
        try {
            BackupStore.restore(this, incoming);
            Intent main = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (main == null) {
                throw new IllegalStateException("The launcher could not be opened.");
            }
            main.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(main);
            finish();
        } catch (Exception error) {
            failure(error);
        }
    }

    private void allowAccess() {
        if (Build.VERSION.SDK_INT < 30) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            } else {
                message("Backup access ready", "You can save and restore your TV backup.");
            }
            return;
        }
        if (BackupStore.hasAccess()) {
            message("Backup access ready", "You can save and restore your TV backup.");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        } catch (Exception unavailable) {
            message("Allow TV backup access", "Run the Fire TV CLI installer on your computer. It grants backup access automatically.");
        }
    }

    private void refresh() {
        if (status == null) {
            return;
        }
        File backup = BackupStore.file(false);
        String saved = backup.isFile() ? "Saved " + DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(backup.lastModified())) : "No saved TV backup yet";
        status.setText(saved + "\nTV Downloads / " + BackupStore.BACKUP_NAME);
    }

    private void failure(Exception error) {
        message("Backup could not be completed", error.getMessage() == null ? "Please try again." : error.getMessage());
    }

    private void message(String title, String body) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("OK", null).show();
    }

    private void style(TextView view, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(focused ? Color.WHITE : Color.argb(28, 255, 255, 255));
        view.setTextColor(focused ? Color.rgb(35, 35, 35) : Color.WHITE);
        if (focused) {
            background.setStroke(dp(2), ACCENT);
        }
        view.setBackground(background);
    }

}
