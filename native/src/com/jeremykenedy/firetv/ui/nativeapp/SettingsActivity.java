package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.appgrid.AppGridSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardsSettingsActivity;
import com.jeremykenedy.firetv.ui.nativeapp.device.DeviceSettingsActivity;

/** One settings entry with only working destinations in this review build. */
public final class SettingsActivity extends Activity {
    private LinearLayout detail;
    private Button appearance;
    private Button home;
    private Button cards;
    private Button device;
    private String selected = "appearance";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selected = state.getString("category", "appearance");
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Settings", 30));
        LinearLayout columns = new LinearLayout(this);
        page.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout categories = new LinearLayout(this); categories.setOrientation(LinearLayout.VERTICAL);
        columns.addView(categories, new LinearLayout.LayoutParams(Pages.dp(this, 245), -1));
        detail = new LinearLayout(this); detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(Pages.dp(this, 30), 0, 0, 0);
        columns.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));
        appearance = Pages.button(this, "Appearance", new View.OnClickListener() {
            @Override public void onClick(View view) { show("appearance", true); }
        });
        appearance.setId(0x2001); categories.addView(appearance);
        cards = Pages.button(this, "Cards", new View.OnClickListener() {
            @Override public void onClick(View view) { show("cards", true); }
        });
        cards.setId(0x2020); categories.addView(cards);
        home = Pages.button(this, "Home layout", new View.OnClickListener() {
            @Override public void onClick(View view) { show("home", true); }
        });
        home.setId(0x2003); categories.addView(home);
        device = Pages.button(this, "Device", new View.OnClickListener() {
            @Override public void onClick(View view) { show("device", true); }
        });
        device.setId(0x2030); categories.addView(device);
        show(selected, false);
        if (state == null) appearance.requestFocus();
    }

    private void show(String category, boolean focus) {
        selected = category; detail.removeAllViews();
        if ("device".equals(category)) {
            detail.addView(Pages.text(this, "Device", 24));
            Button screens = route("Device screens", DeviceSettingsActivity.class, 0x2031, device);
            device.setNextFocusRightId(screens.getId());
            if (focus) screens.requestFocus();
        } else if ("cards".equals(category)) {
            detail.addView(Pages.text(this, "Cards", 24));
            detail.addView(Pages.text(this, "Manage channels shared by your apps, rotation timers and hidden titles.", 18));
            Button manage = route("Channel cards and timers", CardsSettingsActivity.class, 0x2021, cards);
            cards.setNextFocusRightId(manage.getId());
            if (focus) manage.requestFocus();
        } else if ("home".equals(category)) {
            detail.addView(Pages.text(this, "Home layout", 24));
            Button apps = route("Apps grid", AppGridSettingsActivity.class, 0x2010, home);
            route("Your cards and widgets", LayoutSettingsActivity.class, 0x2011, home);
            home.setNextFocusRightId(apps.getId());
            if (focus) apps.requestFocus();
        } else {
            detail.addView(Pages.text(this, "Appearance", 24));
            detail.addView(Pages.text(this, "Adjust how app tiles respond to your remote. Hold an app on Home to change its icon.", 18));
            Button icons = route("Icons and focus", FocusSettingsActivity.class, 0x2002, appearance);
            appearance.setNextFocusRightId(icons.getId());
            if (focus) icons.requestFocus();
        }
    }

    private Button route(String title, final Class<?> target, int id, Button category) {
        Button button = Pages.button(this, title, new View.OnClickListener() {
            @Override public void onClick(View view) { startActivity(new Intent(SettingsActivity.this, target)); }
        });
        button.setId(id); button.setNextFocusLeftId(category.getId()); detail.addView(button);
        return button;
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("category", selected); super.onSaveInstanceState(state); }
}
