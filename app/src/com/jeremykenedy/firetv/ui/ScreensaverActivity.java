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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Installed screensavers, previews, options, timers, and storage in one card. */
public final class ScreensaverActivity extends CardActivity {
    private TextView current;
    private TextView enabled;
    private TextView previewCurrent;
    private LinearLayout rows;
    private List<Screensavers.Choice> choices = new ArrayList<>();
    private final Map<String, ServiceInfo> services = new HashMap<>();
    private final Map<String, View> focusTargets = new HashMap<>();
    private String focusKey = "toggle";
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
        LinearLayout first = toolbar();
        previewCurrent = toolbarButton(first, "Preview screensaver", "preview-current", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Screensavers.Choice selected = selected();
                if (selected != null) {
                    preview(selected.component);
                }
            }
        });
        toolbarButton(first, "Screensaver settings", "options", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                options();
            }
        });
        toolbarButton(first, "System timers", "timers", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ScreensaverActivity.this, TimersActivity.class));
            }
        });
        LinearLayout second = toolbar();
        toolbarButton(second, "Install screensavers", "install", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ScreensaverActivity.this, CatalogActivity.class));
            }
        });
        toolbarButton(second, "Memory usage", "memory", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ScreensaverActivity.this, MemoryActivity.class));
            }
        });
        enabled = toolbarButton(second, "", "toggle", new View.OnClickListener() {
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
        TextView hint = text("Select to make active. Preview controls opens the app; choose Preview or Show aquarium there. Back returns here. Your screensaver selection stays unchanged.", 13, MUTED);
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
        focusTargets.put(key, button);
        return button;
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
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
        rows.removeAllViews();
        String active = DeviceSettings.prefs(this).getBoolean(ScreensaverPreview.PENDING, false)
                ? DeviceSettings.prefs(this).getString("fire_tv_ui_preview_previous", null)
                : DeviceSettings.active(getContentResolver());
        int selected = Screensavers.indexOfActive(choices, active);
        current.setText("Current screensaver: " + (selected < 0 ? "None selected" : choices.get(selected).label)
                + (DeviceSettings.prefs(this).getBoolean(DeviceSettings.DREAM_UNLOCKED, false)
                    ? " (selection unlocked)" : ""));
        previewCurrent.setText(selected < 0 ? "Preview screensaver"
                : ScreensaverPreview.actionLabel(this, choices.get(selected).component, true));
        enabled.setText("Screensaver: " + (DeviceSettings.isEnabled(getContentResolver()) ? "On" : "Off"));
        for (int i = 0; i < choices.size(); i++) {
            row(choices.get(i), i == selected);
        }
        if (choices.isEmpty()) {
            rows.addView(text("No screensavers are installed. Open Install screensavers to add one.", 18, MUTED));
        }
        View focus = focusTargets.get(focusKey);
        if (focus != null) {
            focus.requestFocus();
        }
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

    private void row(final Screensavers.Choice choice, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(background(Color.argb(24, 255, 255, 255), 8));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(6);
        rows.addView(row, rowParams);
        final LinearLayout select = new LinearLayout(this);
        select.setOrientation(LinearLayout.HORIZONTAL);
        select.setGravity(Gravity.CENTER_VERTICAL);
        select.setPadding(dp(12), dp(8), dp(12), dp(8));
        select.setFocusable(true);
        select.setFocusableInTouchMode(true);
        select.setClickable(true);
        select.setContentDescription("Select " + choice.label);
        row.addView(select, new LinearLayout.LayoutParams(0, -2, 1));
        ScreensaverPreviews preview = ScreensaverPreviews.load(this, services.get(Screensavers.fullComponent(choice.component)), choice.component);
        ImageView image = new ImageView(this);
        image.setImageDrawable(preview.image);
        image.setScaleType(preview.available ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
        image.setBackground(background(Color.rgb(33, 40, 51), 6));
        image.setClipToOutline(true);
        image.setContentDescription(preview.available ? "Screenshot of " + choice.label : "Screenshot unavailable");
        select.addView(image, new LinearLayout.LayoutParams(dp(96), dp(54)));
        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(0, -2, 1);
        captionParams.leftMargin = dp(14);
        select.addView(caption, captionParams);
        final TextView name = text(choice.label, 20, INK);
        name.setTypeface(null, checked ? Typeface.BOLD : Typeface.NORMAL);
        caption.addView(name);
        String pkg = choice.component.substring(0, choice.component.indexOf('/'));
        final TextView size = text(PackageStorage.summary(this, pkg), 12, MUTED);
        caption.addView(size);
        final FrameLayout indicator = new FrameLayout(this);
        GradientDrawable outline = new GradientDrawable();
        outline.setShape(GradientDrawable.OVAL);
        outline.setStroke(dp(2), Color.WHITE);
        outline.setColor(Color.TRANSPARENT);
        indicator.setBackground(outline);
        select.addView(indicator, new LinearLayout.LayoutParams(dp(24), dp(24)));
        if (checked) {
            View dot = new View(this);
            GradientDrawable fill = new GradientDrawable();
            fill.setShape(GradientDrawable.OVAL);
            fill.setColor(Color.WHITE);
            dot.setBackground(fill);
            indicator.addView(dot, new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.CENTER));
        }
        select.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean focused) {
                select.setBackground(background(focused ? Color.WHITE : Color.TRANSPARENT, 8));
                name.setTextColor(focused ? Color.rgb(35, 35, 35) : INK);
                size.setTextColor(focused ? Color.rgb(55, 55, 55) : MUTED);
                ((GradientDrawable) indicator.getBackground()).setStroke(dp(2), focused ? Color.rgb(35, 35, 35) : Color.WHITE);
                if (indicator.getChildCount() > 0) {
                    ((GradientDrawable) indicator.getChildAt(0).getBackground()).setColor(focused ? Color.rgb(35, 35, 35) : Color.WHITE);
                }
            }
        });
        select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                focusKey = "select:" + choice.component;
                try {
                    ScreensaverPreview.restore(ScreensaverActivity.this);
                    DeviceSettings.choose(ScreensaverActivity.this, choice.component);
                    load();
                } catch (Exception failed) {
                    error(failed);
                }
            }
        });
        focusTargets.put("select:" + choice.component, select);
        TextView tryIt = button(ScreensaverPreview.actionLabel(this, choice.component, false), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                focusKey = "preview:" + choice.component;
                preview(choice.component);
            }
        });
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(142), dp(42));
        previewParams.setMargins(dp(6), dp(6), dp(8), dp(6));
        row.addView(tryIt, previewParams);
        focusTargets.put("preview:" + choice.component, tryIt);
    }

    private Screensavers.Choice selected() {
        int index = Screensavers.indexOfActive(choices, DeviceSettings.active(getContentResolver()));
        if (index < 0) {
            error(new IllegalStateException("Select a screensaver first."));
            return null;
        }
        return choices.get(index);
    }

    private void preview(String component) {
        try {
            ScreensaverPreview.start(this, component);
        } catch (Exception failed) {
            error(failed);
        }
    }

    private void options() {
        Screensavers.Choice choice = selected();
        if (choice == null) {
            return;
        }
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
