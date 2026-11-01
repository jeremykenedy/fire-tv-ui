package com.jeremykenedy.firetv.ui.nativeapp.layout;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LayoutSettingsActivity extends Activity {
    private LayoutOptions options;
    private List<LayoutItem> available;
    private LinearLayout rows;
    private TextView size;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
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
        content.addView(Pages.text(this,
                "No channel cards are available yet. This size is saved for future channel cards. Widget sizes stay independent.", 16));
        content.addView(Pages.text(this, "Order and visibility", 22));
        rows = column();
        content.addView(rows);
        render(null);
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
            item.addView(Pages.text(this, (index + 1) + ". " + HomeSlots.title(key) + "\n" + status, 19));
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
            else {
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
