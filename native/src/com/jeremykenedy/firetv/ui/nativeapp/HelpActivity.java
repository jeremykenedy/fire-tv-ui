package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.focus.FocusSettingsActivity;

/** Original remote-navigable help for the implemented review surface. */
public final class HelpActivity extends Activity {
    private static final String[][] TOPICS = {
        {"Getting started", "This isolated source preview opens installed TV apps and demonstrates native focus controls. It does not replace your normal Home screen or copy your production settings."},
        {"Remote navigation", "Use the direction buttons to move between apps and actions. Select opens the focused item. Back returns from Help or focus settings. App movement and visual animation are independent."},
        {"Focus and movement", "Choose movement speed, focus growth, and an optional border with color and opacity. None removes growth; Instant removes the visual transition. Existing preferences belong only to this review app."},
        {"Migration status", "This preview includes app organization, icon choices and a local clock. Full channel cards, the broader widget catalog, screensaver administration, themes, device controls, backups and production Home handling are not integrated. The released Fire TV UI continues to provide those functions. This preview is not ready to replace it."},
        {"Licenses and credits", "This native review shell is maintained in the Fire TV UI repository under its MIT license. It builds from Java and Android resources without a supplied launcher APK. Android platform components retain their respective notices. Installed application names and artwork identify their respective owners."}
    };
    private int topic;
    private TextView content;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        topic = saved == null ? 0 : saved.getInt("topic", 0);
        if (topic < 0 || topic >= TOPICS.length) topic = 0;
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Help", 30));
        LinearLayout columns = new LinearLayout(this);
        page.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout topics = new LinearLayout(this);
        topics.setOrientation(LinearLayout.VERTICAL);
        columns.addView(topics, new LinearLayout.LayoutParams(Pages.dp(this, 245), -1));
        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(Pages.dp(this, 30), 0, 0, 0);
        content = Pages.text(this, "", 20);
        detail.addView(content);
        Button settings = Pages.button(this, "Open focus settings", new View.OnClickListener() {
            @Override public void onClick(View view) {
                startActivity(new Intent(HelpActivity.this, FocusSettingsActivity.class));
            }
        });
        detail.addView(settings);
        scroll.addView(detail);
        columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        for (int i = 0; i < TOPICS.length; i++) {
            final int index = i;
            Button button = Pages.button(this, TOPICS[i][0], new View.OnClickListener() {
                @Override public void onClick(View view) {
                    topic = index;
                    updateContent();
                    scroll.scrollTo(0, 0);
                }
            });
            topics.addView(button);
            if (i == topic) button.requestFocus();
        }
        updateContent();
    }

    private void updateContent() {
        content.setText(TOPICS[topic][0] + "\n\n" + TOPICS[topic][1]);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("topic", topic);
    }
}
