package com.jeremykenedy.firetv.ui.nativeapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.help.HelpTopics;
import com.jeremykenedy.firetv.ui.nativeapp.help.RemoteDiagram;

/** Original, remote-navigable guidance for the launcher. */
public final class HelpActivity extends Activity {
    private boolean startupBlocked;
    private static final int TOPIC_ID = 0x5000;
    private static final int CONTENT_ID = 0x5100;
    private int topic;
    private int scrollY;
    private String focusTag;
    private ScrollView scroll;
    private ScrollView topicScroll;
    private LinearLayout detail;
    private Button[] topics;
    private boolean restoreOnResume;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        if (saved != null) {
            topic = HelpTopics.indexOf(saved.getString("topic"));
            scrollY = saved.getInt("scroll", 0);
            focusTag = saved.getString("focus");
        }
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Help", 30));
        LinearLayout columns = new LinearLayout(this);
        page.addView(columns, new LinearLayout.LayoutParams(-1, 0, 1));
        topicScroll = new ScrollView(this);
        columns.addView(topicScroll, new LinearLayout.LayoutParams(Pages.dp(this, 240), -1));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        topicScroll.addView(list);
        scroll = new ScrollView(this);
        scroll.setTag("help:scroll");
        scroll.setFillViewport(true);
        detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(Pages.dp(this, 24), 0, 0, Pages.dp(this, 24));
        scroll.addView(detail);
        columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        topics = new Button[HelpTopics.count()];
        for (int i = 0; i < topics.length; i++) {
            final int index = i;
            Button button = Pages.button(this, HelpTopics.at(i).title, new View.OnClickListener() {
                @Override public void onClick(View view) {
                    topic = index;
                    scrollY = 0;
                    focusTag = null;
                    showTopic();
                    scroll.scrollTo(0, 0);
                    detail.findViewById(CONTENT_ID).requestFocus();
                }
            });
            button.setTextSize(17);
            button.setId(TOPIC_ID + i);
            button.setTag("help:topic:" + HelpTopics.at(i).key);
            topics[i] = button;
            list.addView(button);
        }
        showTopic();
        restorePosition();
    }

    private void showTopic() {
        HelpTopics.Topic selected = HelpTopics.at(topic);
        detail.removeAllViews();
        for (int i = 0; i < topics.length; i++) {
            topics[i].setActivated(i == topic);
            topics[i].setNextFocusRightId(CONTENT_ID);
        }
        detail.addView(Pages.text(this, selected.title, 25));
        if ("remote".equals(selected.key)) {
            detail.addView(new RemoteDiagram(this), new LinearLayout.LayoutParams(-1, Pages.dp(this, 150)));
        }
        String[] paragraphs = HelpTopics.bodyForPackage(selected, getPackageName()).split("\n\n");
        for (int i = 0; i < paragraphs.length; i++) {
            TextView text = Pages.text(this, paragraphs[i], 20);
            text.setId(CONTENT_ID + i);
            text.setTag("help:paragraph:" + i);
            text.setFocusable(true);
            text.setNextFocusLeftId(TOPIC_ID + topic);
            text.setPadding(Pages.dp(this, 10), Pages.dp(this, 8), Pages.dp(this, 10), Pages.dp(this, 14));
            text.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override public void onFocusChange(View view, boolean focused) {
                    view.setBackgroundColor(focused ? 0xff263851 : 0x00000000);
                }
            });
            detail.addView(text);
        }
        if (selected.settingsClass != null) {
            final Intent route = new Intent().setClassName(this, selected.settingsClass);
            if (getPackageManager().resolveActivity(route, 0) != null) {
                Button link = Pages.button(this, selected.settingsLabel, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        rememberPosition();
                        try { startActivity(route); }
                        catch (ActivityNotFoundException missing) {
                            Toast.makeText(HelpActivity.this, "This settings page is unavailable in this build.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
                link.setId(CONTENT_ID + paragraphs.length);
                link.setTag("help:settings");
                link.setNextFocusLeftId(TOPIC_ID + topic);
                detail.addView(link);
            } else {
                detail.addView(Pages.text(this, "This settings page is not available in this build.", 18));
            }
        }
    }

    private void rememberPosition() {
        scrollY = scroll.getScrollY();
        View focused = getCurrentFocus();
        focusTag = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
    }

    private void restorePosition() {
        scroll.post(new Runnable() {
            @Override public void run() {
                View focused = focusTag == null ? null : getWindow().getDecorView().findViewWithTag(focusTag);
                if (focused == null) focused = topics[topic];
                focused.requestFocus();
                final int position = scrollY;
                scroll.post(new Runnable() {
                    @Override public void run() { scroll.scrollTo(0, position); }
                });
            }
        });
    }

    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; }
        rememberPosition();
        restoreOnResume = true;
        super.onPause();
    }

    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; }
        super.onResume();
        if (restoreOnResume) {
            restoreOnResume = false;
            restorePosition();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; }
        rememberPosition();
        state.putString("topic", HelpTopics.at(topic).key);
        state.putInt("scroll", scrollY);
        state.putString("focus", focusTag);
        super.onSaveInstanceState(state);
    }
}
