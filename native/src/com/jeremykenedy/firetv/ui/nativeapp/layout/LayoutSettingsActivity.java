package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LayoutSettingsActivity extends Activity {
    private LayoutOptions options;
    private List<LayoutItem> available;
    private LinearLayout rows;
    private TextView size;
    private TextView providerStatus;
    private Button providerAction;
    private List<TvRecommendations.Feed> feeds = Collections.emptyList();
    private TvRecommendations.Status status;
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private int readGeneration;
    private boolean permissionDenied;
    private static final int LISTINGS_REQUEST = 41;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        permissionDenied = state != null && state.getBoolean("listings_denied", false);
        available = HomeSlots.available();
        options = LayoutPreferences.load(this, HomeSlots.defaults()).discover(available);
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Your cards and widgets", 30));
        page.addView(Pages.text(this, "Keep cards and widgets together in the order you choose.", 17));
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        scroll.addView(content);
        content.addView(Pages.text(this, "Channel card size", 22));
        size = Pages.text(this, "", 18);
        content.addView(size);
        LinearLayout presets = new LinearLayout(this);
        preset(presets, "Compact", 240, 135);
        preset(presets, "Standard", 320, 180);
        preset(presets, "Large", 400, 225);
        presets.addView(Pages.button(this, "Custom size", new View.OnClickListener() {
            @Override public void onClick(View view) { customSize(); }
        }));
        content.addView(presets);
        content.addView(Pages.text(this, "Every channel card shares this size. Widget sizes stay independent.", 16));
        providerStatus = Pages.text(this, "Checking TV recommendations…", 17);
        providerStatus.setTag("layout:provider-status");
        content.addView(providerStatus);
        providerAction = Pages.button(this, "Refresh channel cards", new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (status == TvRecommendations.Status.PERMISSION_REQUIRED) requestListings();
                else refreshFeeds();
            }
        });
        providerAction.setTag("layout:provider-action");
        content.addView(providerAction);
        content.addView(Pages.text(this, "Order and visibility", 22));
        rows = column();
        content.addView(rows);
        render(null);
    }

    @Override protected void onStart() {
        super.onStart();
        refreshFeeds();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("listings_denied", permissionDenied);
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        readGeneration++;
        reader.shutdownNow();
        super.onDestroy();
    }

    private void refreshFeeds() {
        final int generation = ++readGeneration;
        providerStatus.setText("Checking TV recommendations…");
        providerAction.setEnabled(false);
        reader.execute(new Runnable() {
            @Override public void run() {
                final TvRecommendations.Result result = TvRecommendations.read(getApplicationContext());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || isDestroyed() || generation != readGeneration) return;
                        View focused = getCurrentFocus();
                        String tag = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
                        status = result.status;
                        feeds = result.feeds;
                        available = HomeSlots.available(feeds);
                        try {
                            options = options.discover(available);
                        } catch (IllegalArgumentException full) {
                            providerStatus.setText("Your saved layout is full. Existing slots and their order are preserved.");
                            providerAction.setEnabled(true);
                            return;
                        }
                        providerStatus.setText(permissionDenied && status == TvRecommendations.Status.PERMISSION_REQUIRED
                                ? "TV listings access was denied. Your saved cards and order are preserved. Allow access to read shared recommendations."
                                : result.message());
                        providerAction.setText(status == TvRecommendations.Status.PERMISSION_REQUIRED
                                ? "Allow TV listings access" : "Refresh channel cards");
                        providerAction.setEnabled(true);
                        render(tag);
                    }
                });
            }
        });
    }

    private void requestListings() {
        if (Build.VERSION.SDK_INT < 23) {
            providerStatus.setText("TV listings permission cannot be requested on this Android version.");
            return;
        }
        try {
            requestPermissions(new String[]{TvRecommendations.PERMISSION}, LISTINGS_REQUEST);
        } catch (RuntimeException unsupported) {
            providerStatus.setText("TV listings access cannot be requested on this device.");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != LISTINGS_REQUEST) return;
        permissionDenied = results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED;
        refreshFeeds();
    }

    private void preset(LinearLayout row, String label, final int width, final int height) {
        row.addView(Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { save(options.resizeCards(width, height), null); }
        }));
    }

    private void render(String focusTag) {
        size.setText(options.cardWidthDp + " × " + options.cardHeightDp + " for every channel card");
        rows.removeAllViews();
        Set<String> present = new HashSet<>();
        for (LayoutItem item : available) present.add(item.key());
        for (int index = 0; index < options.order.size(); index++) {
            final String key = options.order.get(index);
            final int position = index;
            LinearLayout item = column();
            String status = present.contains(key) ? (options.hidden.contains(key) ? "Hidden" : "Shown") : "Source unavailable";
            item.addView(Pages.text(this, (index + 1) + ". " + HomeSlots.title(key, feeds) + "\n" + status, 19));
            LinearLayout actions = new LinearLayout(this);
            Button visibility = Pages.button(this, options.hidden.contains(key) ? "Show" : "Hide", new View.OnClickListener() {
                @Override public void onClick(View view) { save(options.setVisible(key, options.hidden.contains(key)), key + ":visibility"); }
            });
            visibility.setTag(key + ":visibility");
            actions.addView(visibility);
            Button up = Pages.button(this, "Move up", new View.OnClickListener() {
                @Override public void onClick(View view) { save(options.moveBefore(key, options.order.get(position - 1)), key + ":up"); }
            });
            up.setEnabled(index > 0);
            up.setTag(key + ":up");
            actions.addView(up);
            Button down = Pages.button(this, "Move down", new View.OnClickListener() {
                @Override public void onClick(View view) {
                    String before = position + 2 < options.order.size() ? options.order.get(position + 2) : null;
                    save(options.moveBefore(key, before), key + ":down");
                }
            });
            down.setEnabled(index + 1 < options.order.size());
            down.setTag(key + ":down");
            actions.addView(down);
            item.addView(actions);
            rows.addView(item);
        }
        if (focusTag != null) {
            View target = rows.findViewWithTag(focusTag);
            if (target != null && target.isEnabled()) target.requestFocus();
            else if (focusTag.startsWith("channel:") || focusTag.startsWith("widget:")) {
                int separator = focusTag.lastIndexOf(':');
                View fallback = rows.findViewWithTag(focusTag.substring(0, separator) + ":visibility");
                if (fallback != null) fallback.requestFocus();
            }
        }
    }

    private boolean save(LayoutOptions value, String focusTag) {
        try {
            LayoutPreferences.save(this, value);
            options = value;
            render(focusTag);
            return true;
        } catch (IllegalStateException failed) {
            Toast.makeText(this, failed.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void customSize() {
        LinearLayout fields = column();
        int padding = Pages.dp(this, 24);
        fields.setPadding(padding, 0, padding, 0);
        fields.addView(Pages.text(this, "Sizes scale to your TV's display. Width: 64–1024. Height: 48–1024.", 16));
        fields.addView(Pages.text(this, "Width", 17));
        final EditText width = number(options.cardWidthDp, "Width");
        fields.addView(width);
        fields.addView(Pages.text(this, "Height", 17));
        final EditText height = number(options.cardHeightDp, "Height");
        fields.addView(height);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Custom card size").setView(fields)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            LayoutOptions value = options.resizeCards(Integer.parseInt(width.getText().toString()),
                                    Integer.parseInt(height.getText().toString()));
                            if (save(value, null)) dialog.dismiss();
                        } catch (IllegalArgumentException invalid) {
                            width.setError("Use width 64–1024 and height 48–1024.");
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    private EditText number(int value, String label) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(Integer.toString(value));
        field.setContentDescription(label);
        return field;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
}
