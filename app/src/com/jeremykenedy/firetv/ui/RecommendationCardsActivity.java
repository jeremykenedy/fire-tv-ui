package com.jeremykenedy.firetv.ui;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class RecommendationCardsActivity extends CardActivity {
    private TextView report;
    private TextView toggle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("App recommendation cards");
        LinearLayout body = scrollBody();
        body.addView(text("Show movie, show, video and music recommendations shared by your installed"
                + " apps. Only recommendation notifications from Netflix, Prime Video, Live TV, News,"
                + " YouTube, Spotify and Tubi are used. Availability depends on each app.", 18, INK));
        body.addView(text("Android notification access is required. The listener ignores other apps"
                + " and other notification categories. Recommendations stay on this TV; card actions"
                + " use the original app's action. Hiding a card does not dismiss its notification.", 17, MUTED));
        report = text("", 18, INK);
        body.addView(report);
        toggle = button("", new View.OnClickListener() {
            @Override public void onClick(View view) {
            boolean next = !RecommendationService.enabled(RecommendationCardsActivity.this);
            getSharedPreferences("recommendation-cards", MODE_PRIVATE).edit().putBoolean("enabled", next).apply();
            RecommendationService.refresh(RecommendationCardsActivity.this);
            refresh();
            }
        });
        addButton(body, toggle);
        addButton(body, button("Manage notification access", new View.OnClickListener() {
            @Override public void onClick(View view) {
            try {
                Intent settings = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(settings);
            } catch (android.content.ActivityNotFoundException unavailable) {
                new android.app.AlertDialog.Builder(RecommendationCardsActivity.this).setTitle("Notification access")
                        .setMessage("This Fire TV does not expose the Android notification-access"
                                + " screen. Notification access must be configured through this TV's"
                                + " authorized ADB connection. No access has been changed.")
                        .setPositiveButton("OK", null).show();
            }
            }
        }));
        addButton(body, button("Refresh app recommendations", new View.OnClickListener() {
            @Override public void onClick(View view) {
                RecommendationService.refresh(RecommendationCardsActivity.this);
                report.postDelayed(new Runnable() {
                    @Override public void run() { refresh(); }
                }, 500);
            }
        }));
        addButton(body, button("Reload home cards", new View.OnClickListener() {
            @Override public void onClick(View view) {
            Intent home = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (home != null) {
                home.addCategory(Intent.CATEGORY_HOME);
                home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(home); finish();
            }
            }
        }));
        toggle.requestFocus();
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (report == null) return;
        boolean access;
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            access = manager != null && manager.isNotificationListenerAccessGranted(
                    new ComponentName(this, RecommendationService.class));
        } else access = false;
        toggle.setText(RecommendationService.enabled(this) ? "App recommendation cards: ON" : "App recommendation cards: OFF");
        report.setText("\nNotification access: " + (access ? "allowed" : "not allowed")
                + "\n" + RecommendationService.status() + "\n");
    }
}
