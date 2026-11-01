package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity;

/** One settings entry with only working destinations in this review build. */
public final class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Settings", 30));
        LinearLayout columns = new LinearLayout(this);
        page.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout categories = new LinearLayout(this);
        categories.setOrientation(LinearLayout.VERTICAL);
        columns.addView(categories, new LinearLayout.LayoutParams(Pages.dp(this, 245), -1));
        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(Pages.dp(this, 30), 0, 0, 0);
        detail.addView(Pages.text(this, "Appearance", 24));
        detail.addView(Pages.text(this, "Adjust how app tiles respond to your remote.", 18));
        final Button focus = Pages.button(this, "Icons and focus", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(SettingsActivity.this, FocusSettingsActivity.class));
            }
        });
        focus.setId(0x2002);
        detail.addView(focus);
        columns.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));
        Button appearance = Pages.button(this, "Appearance", new View.OnClickListener() {
            @Override public void onClick(View view) {
                focus.requestFocus();
            }
        });
        appearance.setId(0x2001);
        appearance.setNextFocusRightId(focus.getId());
        focus.setNextFocusLeftId(appearance.getId());
        categories.addView(appearance);
        if (state == null) appearance.requestFocus();
    }
}
