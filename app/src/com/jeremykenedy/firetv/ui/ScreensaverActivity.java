package com.jeremykenedy.firetv.ui;

import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Installed screensavers, previews, options, timers, and storage in one card. */
public final class ScreensaverActivity extends CardActivity {
    private TextView current;
    private TextView enabled;
    private LinearLayout rows;
    private List<Screensavers.Choice> choices = new ArrayList<>();
    private final Map<String, ServiceInfo> services = new HashMap<>();
    private final Map<String, View> focusTargets = new HashMap<>();
    private String focusKey = "toggle";
    private int scrollPosition;
    private final BroadcastReceiver dreamStoppedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_DREAMING_STOPPED.equals(intent.getAction())) {
                return;
            }
            if (!DeviceSettings.prefs(ScreensaverActivity.this)
                    .getBoolean(ScreensaverPreview.PENDING, false)) {
                return;
            }
            try {
                ScreensaverPreview.restore(ScreensaverActivity.this);
                load();
            } catch (Exception failed) {
                error(failed);
            }
        }
    };
    private boolean dreamStoppedReceiverRegistered;

    public static void open(Context context) {
        context.startActivity(new Intent(context, ScreensaverActivity.class));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Screensavers");
        current = text("", 18, MUTED);
        current.setPadding(0, 0, 0, dp(10));
        card.addView(current);
        if (state != null) {
            focusKey = state.getString("focus", "toggle");
            scrollPosition = state.getInt("scroll", 0);
        }
        LinearLayout first = toolbar();
        toolbarButton(first, "System timers", "timers", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ScreensaverActivity.this, TimersActivity.class));
            }
        });

        toolbarButton(first, "Install screensavers", "install", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ScreensaverActivity.this, CatalogActivity.class));
            }
        });
        toolbarButton(first, "Memory usage", "memory", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ScreensaverActivity.this, MemoryActivity.class));
            }
        });
        enabled = toolbarButton(first, "", "toggle", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    DeviceSettings.enableDream(ScreensaverActivity.this,
                            !DeviceSettings.isEnabled(getContentResolver()));
                    load();
                } catch (Exception failed) {
                    error(failed);
                }
            }
        });
        TextView heading = text("Installed screensavers", 16, MUTED);
        heading.setPadding(0, dp(8), 0, dp(8));
        card.addView(heading);
        rows = scrollBody();
        TextView hint = text("Configure or preview any saver without changing the active selection. Only Set active changes it. Back returns to this list.", 14, MUTED);
        hint.setPadding(0, dp(8), 0, 0);
        card.addView(hint);
        IntentFilter filter = new IntentFilter(Intent.ACTION_DREAMING_STOPPED);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(dreamStoppedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dreamStoppedReceiver, filter);
        }
        dreamStoppedReceiverRegistered = true;
    }

    private LinearLayout toolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(6);
        card.addView(toolbar, params);
        return toolbar;
    }

    private TextView toolbarButton(LinearLayout row, String name, final String key, final View.OnClickListener action) {
        final TextView button = button(name, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                focusKey = key;
                action.onClick(view);
            }
        });
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(6);
        }
        row.addView(button, params);
        button.setTag(key);
        focusTargets.put(key, button);
        return button;
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onPause() {
        rememberPosition();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        rememberPosition();
        state.putString("focus", focusKey);
        state.putInt("scroll", scrollPosition);
        super.onSaveInstanceState(state);
    }

    private void rememberPosition() {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) {
            focusKey = (String) focused.getTag();
        }
        if (rows != null) scrollPosition = ((ScrollView) rows.getParent()).getScrollY();
    }

    @Override
    public void onBackPressed() {
        ScreensaverPreview.restore(this);
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (dreamStoppedReceiverRegistered) {
            unregisterReceiver(dreamStoppedReceiver);
            dreamStoppedReceiverRegistered = false;
        }
        if (isFinishing()) {
            ScreensaverPreview.restore(this);
        }
        super.onDestroy();
    }

    private void load() {
        if (rows == null) {
            return;
        }
        PackageManager pm = getPackageManager();
        List<Screensavers.Choice> found = new ArrayList<>();
        services.clear();
        int flags = PackageManager.GET_SERVICES | PackageManager.GET_META_DATA;
        for (PackageInfo pkg : pm.getInstalledPackages(flags)) {
            if (!Screensavers.isHidden(pkg.packageName) && pkg.services != null) {
                for (ServiceInfo service : pkg.services) {
                    addService(service, pm, found);
                }
            }
        }
        for (ResolveInfo match : pm.queryIntentServices(new Intent("android.service.dreams.DreamService"), PackageManager.GET_META_DATA)) {
            addService(match.serviceInfo, pm, found);
        }
        choices = Screensavers.sorted(found);
        if (rows.getChildCount() > 0) rememberPosition();
        rows.removeAllViews();
        java.util.Iterator<String> keys = focusTargets.keySet().iterator();
        while (keys.hasNext()) if (keys.next().contains(":")) keys.remove();
        String active = DeviceSettings.prefs(this).getBoolean(ScreensaverPreview.PENDING, false)
                ? DeviceSettings.prefs(this).getString("fire_tv_ui_preview_previous", null)
                : DeviceSettings.active(getContentResolver());
        int selected = Screensavers.indexOfActive(choices, active);
        current.setText("Active screensaver: " + (selected < 0 ? "None selected" : choices.get(selected).label)
                + (DeviceSettings.prefs(this).getBoolean(DeviceSettings.DREAM_UNLOCKED, false)
                    ? " (selection unlocked)" : ""));
        enabled.setText("Screensaver: " + (DeviceSettings.isEnabled(getContentResolver()) ? "On" : "Off"));
        for (int i = 0; i < choices.size(); i++) {
            row(choices.get(i), i == selected);
        }
        if (choices.isEmpty()) {
            rows.addView(text("No screensavers are installed. Open Install screensavers to add one.", 18, MUTED));
        }
        final View focus = focusTargets.get(focusKey);
        final int savedScroll = scrollPosition;
        rows.post(new Runnable() {
            @Override public void run() {
                if (focus != null) focus.requestFocus();
                else enabled.requestFocus();
                ((ScrollView) rows.getParent()).scrollTo(0, savedScroll);
            }
        });
    }

    private void addService(ServiceInfo service, PackageManager pm, List<Screensavers.Choice> found) {
        if (service == null || !service.enabled || !service.applicationInfo.enabled
                || Screensavers.isHidden(service.packageName) || !Screensavers.isScreensaver(service.permission)) {
            return;
        }
        String component = new ComponentName(service.packageName, service.name).flattenToShortString();
        services.put(Screensavers.fullComponent(component), service);
        found.add(new Screensavers.Choice(component, Screensavers.displayName(service.packageName, service.loadLabel(pm).toString())));
    }

    private void row(final Screensavers.Choice choice, final boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(background(Color.argb(24, 255, 255, 255), 8));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(10);
        rows.addView(row, rowParams);
        LinearLayout details = new LinearLayout(this);
        details.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(details);
        ScreensaverPreviews preview = ScreensaverPreviews.load(this, services.get(Screensavers.fullComponent(choice.component)), choice.component);
        ImageView image = new ImageView(this);
        image.setImageDrawable(preview.image);
        image.setScaleType(preview.available ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
        image.setBackground(background(Color.rgb(33, 40, 51), 6));
        image.setClipToOutline(true);
        image.setContentDescription(preview.available ? "Screenshot of " + choice.label : "Screenshot unavailable");
        details.addView(image, new LinearLayout.LayoutParams(dp(96), dp(54)));
        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(0, -2, 1);
        captionParams.leftMargin = dp(14);
        details.addView(caption, captionParams);
        TextView name = text(choice.label + (checked ? " (Active)" : ""), 20, INK);
        name.setTypeface(null, checked ? Typeface.BOLD : Typeface.NORMAL);
        caption.addView(name);
        String pkg = choice.component.substring(0, choice.component.indexOf('/'));
        caption.addView(text(PackageStorage.summary(this, pkg), 13, MUTED));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(8), 0, 0);
        row.addView(actions);
        TextView configure = toolbarButton(actions, "Configure", "configure:" + choice.component,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        options(choice);
                    }
                });
        configure.setContentDescription("Configure " + choice.label);
        String previewLabel = ScreensaverPreview.actionLabel(this, choice.component, false);
        TextView tryIt = toolbarButton(actions, "Preview it".equals(previewLabel) ? "Preview" : previewLabel,
                "preview:" + choice.component, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        preview(choice.component);
                    }
                });
        tryIt.setContentDescription("Preview " + choice.label);
        TextView activate = toolbarButton(actions, checked ? "Active" : "Set active", "select:" + choice.component,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        if (checked) return;
                        try {
                            ScreensaverPreview.restore(ScreensaverActivity.this);
                            DeviceSettings.choose(ScreensaverActivity.this, choice.component);
                            load();
                        } catch (Exception failed) {
                            error(failed);
                        }
                    }
                });
        activate.setContentDescription((checked ? "Active screensaver: " : "Set active: ") + choice.label);
    }

    private void preview(String component) {
        try {
            ScreensaverPreview.start(this, component);
        } catch (Exception failed) {
            error(failed);
        }
    }

    private void options(Screensavers.Choice choice) {
        try {
            Intent options = ScreensaverControls.resolve(this, choice.component);
            if (options == null) {
                throw new IllegalStateException(choice.label + " does not provide an accessible settings screen.");
            }
            startActivity(options);
        } catch (Exception failed) {
            error(failed);
        }
    }
}
