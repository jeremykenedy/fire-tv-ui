package com.jeremykenedy.firetv.ui.nativeapp.appgrid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppGridSettingsActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private List<AppInventory.App> apps = new ArrayList<>();
    private LinearLayout body;
    private String focusKey = "";
    private boolean gridOptions;
    private boolean resumedOnce;
    private final Map<String, Drawable> artwork = new LinkedHashMap<>();
    private final Map<String, Boolean> pendingApps = new LinkedHashMap<>();
    private final Map<String, Boolean> pendingCards = new LinkedHashMap<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        render();
        reloadApps();
    }

    private void reloadApps() {
        loader.execute(new Runnable() {
            @Override public void run() {
                final List<AppInventory.App> loaded = AppInventory.load(getApplicationContext());
                final Map<String, Drawable> resolved = new LinkedHashMap<>();
                for (AppInventory.App app : loaded) {
                    try { resolved.put(app.key(), com.jeremykenedy.firetv.ui.nativeapp.icons.IconSelectionStore.resolve(
                            getApplicationContext(), app.key(), app.artwork, null)); }
                    catch (RuntimeException unavailable) { resolved.put(app.key(), app.artwork); }
                }
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isDestroyed()) { apps = loaded; artwork.clear(); artwork.putAll(resolved); render(); }
                    }
                });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (startupBlocked) return;
        if (resumedOnce) reloadApps(); else resumedOnce = true;
    }

    private void render() {
        if (gridOptions) { renderGridOptions(); return; }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xaa000000);
        LinearLayout dialog = new LinearLayout(this);
        dialog.setOrientation(LinearLayout.VERTICAL);
        dialog.setPadding(dp(32), dp(24), dp(32), dp(32));
        dialog.setBackground(round(0xff252525, 16));
        int width = Math.min(dp(816), getResources().getDisplayMetrics().widthPixels - dp(48));
        int height = Math.min(dp(460), getResources().getDisplayMetrics().heightPixels - dp(40));
        root.addView(dialog, new FrameLayout.LayoutParams(width, height, Gravity.CENTER));
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("App Manager", 28, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(64), 1));
        Button grid = smallButton("Grid options", new View.OnClickListener() {
            @Override public void onClick(View view) { gridOptions = true; focusKey = "columns"; render(); }
        });
        titleRow.addView(grid, new LinearLayout.LayoutParams(dp(125), dp(36)));
        dialog.addView(titleRow);
        LinearLayout headers = new LinearLayout(this);
        headers.setGravity(Gravity.CENTER_VERTICAL);
        cell(headers, "App", 4.4f, Gravity.START);
        cell(headers, "Hide", 1.3f, Gravity.CENTER);
        cell(headers, "Hide Cards", 1.5f, Gravity.CENTER);
        cell(headers, "Icon", 1.3f, Gravity.CENTER);
        cell(headers, "Settings", 1.5f, Gravity.CENTER);
        dialog.addView(headers, new LinearLayout.LayoutParams(-1, dp(48)));
        View divider = new View(this); divider.setBackgroundColor(0xff555555);
        dialog.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false); scroll.setClipToPadding(false);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); dialog.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView section = label("Apps", 14, 0xffaaaaaa); section.setPadding(dp(8), dp(14), 0, dp(8)); body.addView(section);
        AppGridOptions options = AppGridPreferences.load(this);
        View first = null;
        for (final AppInventory.App app : apps) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(12), 0); row.setBackground(round(0xff333333, 8));
            LinearLayout.LayoutParams rowBounds = new LinearLayout.LayoutParams(-1, dp(56)); rowBounds.bottomMargin = dp(8);
            body.addView(row, rowBounds);
            LinearLayout description = new LinearLayout(this); description.setGravity(Gravity.CENTER_VERTICAL);
            ImageView image = new ImageView(this); image.setImageDrawable(artwork.containsKey(app.key()) ? artwork.get(app.key()) : app.artwork); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            description.addView(image, new LinearLayout.LayoutParams(dp(56), dp(32)));
            TextView name = label(app.label, 16, Color.WHITE); name.setPadding(dp(12), 0, 0, 0);
            name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            description.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(description, weighted(4.4f));
            final boolean originalHidden = options.hidden.contains(app.key());
            boolean hidden = pendingApps.containsKey(app.key()) ? pendingApps.get(app.key()) : originalHidden;
            Button hide = toggle(hidden, "Hide " + app.label, new Toggle() {
                @Override public void changed(boolean value) { if (value == originalHidden) pendingApps.remove(app.key()); else pendingApps.put(app.key(), value); }
            });
            hide.setTag("app-manager:hide:" + app.key()); wrap(row, hide, 1.3f);
            String packageName = app.component.getPackageName();
            boolean cardsAvailable = true;
            boolean storedCardsHidden = false;
            try { storedCardsHidden = com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences.isPackageHidden(this, packageName); }
            catch (IllegalStateException unavailable) { cardsAvailable = false; }
            final String sourcePackage = packageName;
            final boolean originalCardsHidden = storedCardsHidden;
            boolean cardsHidden = pendingCards.containsKey(packageName) ? pendingCards.get(packageName) : originalCardsHidden;
            Button cards = toggle(cardsHidden, "Hide cards from " + app.label, new Toggle() {
                @Override public void changed(boolean value) { if (value == originalCardsHidden) pendingCards.remove(sourcePackage); else pendingCards.put(sourcePackage, value); }
            });
            cards.setTag("app-manager:cards:" + packageName);
            if (!cardsAvailable) { cards.setEnabled(false); cards.setContentDescription("Card visibility unavailable for " + app.label); }
            wrap(row, cards, 1.5f);
            Button pick = smallButton("Pick", new View.OnClickListener() {
                @Override public void onClick(View view) { focusKey = "app-manager:icon:" + app.key(); com.jeremykenedy.firetv.ui.nativeapp.icons.IconPickerActivity.open(AppGridSettingsActivity.this, app.key(), app.label); }
            });
            pick.setTag("app-manager:icon:" + app.key()); wrap(row, pick, 1.3f);
            Button details = smallButton("", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    focusKey = "app-manager:settings:" + app.key();
                    try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.component.getPackageName()))); }
                    catch (RuntimeException unavailable) { Toast.makeText(AppGridSettingsActivity.this, "App settings are unavailable.", Toast.LENGTH_LONG).show(); }
                }
            });
            details.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_manage, 0, 0, 0);
            details.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View view) { appActions(app); return true; }
            });
            details.setContentDescription("Settings for " + app.label + ". Hold for app options."); details.setTag("app-manager:settings:" + app.key()); wrap(row, details, 1.5f);
            if (first == null) first = hide;
            for (View child : new View[]{hide, cards, pick, details}) if (focusKey.equals(child.getTag())) first = child;
        }
        if (apps.isEmpty()) body.addView(label("No TV apps available.", 16, 0xffcccccc));
        Button save = smallButton("Save", new View.OnClickListener() {
            @Override public void onClick(View view) { saveManager(); }
        });
        save.setTag("app-manager:save"); save.setBackground(round(0xff4caf50, 10));
        save.setGravity(Gravity.CENTER); save.setTypeface(null, Typeface.BOLD); save.setTextSize(20);
        LinearLayout.LayoutParams saveBounds = new LinearLayout.LayoutParams(-1, dp(56)); saveBounds.topMargin = dp(16);
        dialog.addView(save, saveBounds);
        setContentView(root);
        if (first != null) first.requestFocus(); else save.requestFocus();
    }

    private interface Toggle { void changed(boolean value); }
    private Button toggle(boolean selected, final String description, final Toggle action) {
        final CheckBox button = new CheckBox(this);
        button.setButtonDrawable(null); button.setGravity(Gravity.CENTER);
        button.setTextSize(16); button.setTextColor(Color.WHITE);
        button.setPadding(0, 0, 0, 0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setBackground(buttonBackground()); button.setChecked(selected);
        button.setText(selected ? "✓" : ""); button.setContentDescription(description);
        button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton view, boolean checked) {
                button.setText(checked ? "✓" : "");
                action.changed(checked);
            }
        });
        return button;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    private Button smallButton(String text, View.OnClickListener click) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); button.setTextSize(16);
        button.setTextColor(Color.WHITE); button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(0, 0, 0, 0);
        button.setBackground(buttonBackground()); button.setOnClickListener(click); return button;
    }
    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = round(0xff555555, 6); focused.setStroke(dp(2), Color.WHITE);
        GradientDrawable normal = round(0xff494949, 6); normal.setStroke(dp(1), 0xff888888);
        states.addState(new int[]{android.R.attr.state_focused}, focused); states.addState(new int[]{}, normal);
        return states;
    }
    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private LinearLayout.LayoutParams weighted(float weight) { return new LinearLayout.LayoutParams(0, -1, weight); }
    private void wrap(LinearLayout row, View child, float weight) {
        FrameLayout box = new FrameLayout(this); box.addView(child, new FrameLayout.LayoutParams(dp(56), dp(32), Gravity.CENTER)); row.addView(box, weighted(weight));
    }
    private void cell(LinearLayout row, String text, float weight, int gravity) {
        TextView view = label(text, 16, 0xffbbbbbb); view.setTypeface(null, Typeface.BOLD); view.setGravity(gravity | Gravity.CENTER_VERTICAL); row.addView(view, weighted(weight));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void saveManager() { synchronized(com.jeremykenedy.firetv.ui.nativeapp.setups.PresentationState.class) {
        try { com.jeremykenedy.firetv.ui.nativeapp.setups.HolidayPresetTransaction.assertWritable(this); }
        catch (IllegalStateException unavailable) { Toast.makeText(this, unavailable.getMessage(), Toast.LENGTH_LONG).show(); return; }
        SharedPreferences prefs = getSharedPreferences("native_app_grid", Context.MODE_PRIVATE);
        Object previous = prefs.getAll().get("hidden");
        if (previous != null && !(previous instanceof String)) { Toast.makeText(this, "Saved app choices are unavailable.", Toast.LENGTH_LONG).show(); return; }
        try {
            String hidden = AppManagerChanges.hidden((String) previous, pendingApps);
            boolean changed = !pendingApps.isEmpty();
            if (changed && !prefs.edit().putString("hidden", hidden).commit()) {
                if (!restoreHidden(prefs, previous)) throw new IllegalStateException("App choices could not be restored. Review app visibility.");
                throw new IllegalStateException("App choices could not be saved. Try again.");
            }
            try { if (!pendingCards.isEmpty()) com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences.setPackagesHidden(this, pendingCards); }
            catch (RuntimeException failed) {
                if (changed && !restoreHidden(prefs, previous))
                    throw new IllegalStateException("App choices could not be restored. Review app visibility.", failed);
                throw failed;
            }
            pendingApps.clear(); pendingCards.clear(); finish();
        } catch (RuntimeException failure) {
            Toast.makeText(this, failure.getMessage() == null ? "App choices could not be saved." : failure.getMessage(), Toast.LENGTH_LONG).show();
        }
    } }

    private boolean restoreHidden(SharedPreferences prefs, Object previous) {
        SharedPreferences.Editor rollback = prefs.edit();
        if (previous == null) rollback.remove("hidden"); else rollback.putString("hidden", (String) previous);
        return rollback.commit() && java.util.Objects.equals(previous, prefs.getAll().get("hidden"))
                && (previous != null || !prefs.contains("hidden"));
    }

    private void renderGridOptions() {
        gridOptions = true;
        AppGridOptions options = AppGridPreferences.load(this);
        LinearLayout page = Pages.sidePanel(this);
        page.addView(Pages.text(this, "Apps grid", 26));
        page.addView(Pages.text(this, "Favorites appear first. Hold an app on Home to move, hide, or customize it. Hidden apps stay available here.", 16));
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        add("columns", "Apps per row: " + options.columns, new Runnable() {
            @Override public void run() { chooseColumns(); }
        });
        add("direction", "Scroll: " + (options.horizontal ? "Horizontal" : "Vertical (default)"), new Runnable() {
            @Override public void run() {
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this);
                save(old.grid(old.columns, old.rows, !old.horizontal));
            }
        });
        add("rows", "Horizontal rows: " + (options.rows == 0 ? "Auto" : options.rows), new Runnable() {
            @Override public void run() { number(false); }
        });
        add("reset", "Restore default grid dimensions", new Runnable() {
            @Override public void run() {
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this);
                AppGridOptions defaults = AppGridOptions.defaults();
                save(old.grid(defaults.columns, defaults.rows, defaults.horizontal));
            }
        });

    }

    private void add(final String key, String label, final Runnable action) {
        Button button = Pages.menuButton(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; action.run(); }
        });
        body.addView(button);
        if (key.equals(focusKey)) button.requestFocus();
    }

    private void save(AppGridOptions options) {
        try { AppGridPreferences.save(this, options); render(); }
        catch (IllegalStateException failure) { Toast.makeText(this, failure.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void appActions(final AppInventory.App app) {
        AppGridOptions options = AppGridPreferences.load(this);
        AlertDialog actions = new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[] {
            options.hidden.contains(app.key()) ? "Show on Home" : "Hide from Home",
            options.favorites.contains(app.key()) ? "Remove favorite" : "Add favorite",
            "Move earlier", "Move later"
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this).discover(AppInventory.keys(apps));
                if (which == 0) save(old.toggleHidden(app.key()));
                else if (which == 1) save(old.toggleFavorite(app.key()));
                else {
                    try { HomeTileActions.move(AppGridSettingsActivity.this, app.key(), which == 2 ? -1 : 1); render(); }
                    catch (RuntimeException failed) { Toast.makeText(AppGridSettingsActivity.this, failed.getMessage(), Toast.LENGTH_LONG).show(); }
                }
            }
        }).setNegativeButton("Cancel", null).show();
        actions.getListView().requestFocus();
        actions.getListView().setSelection(0);
    }

    private void chooseColumns() {
        new AlertDialog.Builder(this).setTitle("Apps per row").setItems(new String[] {
            "3", "4", "5", "6 (default)", "7", "8", "Custom (2–12)"
        }, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 6) { number(true); return; }
                AppGridOptions old = AppGridPreferences.load(AppGridSettingsActivity.this);
                save(old.grid(which + 3, old.rows, old.horizontal));
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void number(final boolean columns) {
        final EditText value = new EditText(this); value.setInputType(InputType.TYPE_CLASS_NUMBER);
        AppGridOptions old = AppGridPreferences.load(this);
        value.setText(String.valueOf(columns ? old.columns : old.rows)); value.selectAll();
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(columns ? "Apps per row (2–12)" : "Horizontal rows (0 = Auto, 1–8)")
                .setView(value).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            int number = Integer.parseInt(value.getText().toString());
                            if (columns && (number < 2 || number > 12)) throw new IllegalArgumentException();
                            AppGridOptions current = AppGridPreferences.load(AppGridSettingsActivity.this);
                            save(current.grid(columns ? number : current.columns, columns ? current.rows : number, current.horizontal));
                            dialog.dismiss();
                        } catch (IllegalArgumentException bad) { value.setError(columns ? "Choose 2 through 12." : "Choose 0 through 8."); }
                    }
                });
            }
        });
        dialog.show();
    }
    @Override public void onBackPressed() {
        if (gridOptions) { gridOptions = false; focusKey = ""; render(); }
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } loader.shutdownNow(); super.onDestroy(); }
}
