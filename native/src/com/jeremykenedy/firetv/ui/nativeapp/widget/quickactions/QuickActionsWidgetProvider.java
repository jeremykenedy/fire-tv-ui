package com.jeremykenedy.firetv.ui.nativeapp.widget.quickactions;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.io.IOException;

public final class QuickActionsWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("local:quick-actions", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Quick actions"; }
    @Override public State state(Context context) {
        try {
            QuickActions actions = new QuickActionStore(context).read();
            if (actions.entries.isEmpty()) return State.SETUP_REQUIRED;
            for (QuickActions.Entry entry : actions.entries) if (QuickActionLaunch.available(context, entry)) return State.READY;
            return State.UNAVAILABLE;
        } catch (IOException failed) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        State state = state(context);
        if (state == State.SETUP_REQUIRED) return "Select to add an installed TV app or supported HTTPS content link.";
        if (state == State.UNAVAILABLE) return "Saved actions are retained. Select to review missing apps or unsupported content links.";
        return "Select an action, then Open now to launch. Editing never launches an app.";
    }
    @Override public int widthDp() { return 360; }
    @Override public int heightDp() { return 360; }
    @Override public View createView(Context context) {
        TextView view = new TextView(context); view.setTextColor(Color.WHITE); view.setTextSize(20);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding, padding, padding); render(view); return view;
    }
    @Override public void configure(Activity activity) { QuickActionSettings.show(activity); }
    @Override public void onStart(View view) { render((TextView) view); }
    @Override public void onStop(View view) { }

    private void render(TextView view) {
        StringBuilder text = new StringBuilder("Quick actions\n\n");
        try {
            QuickActions saved = new QuickActionStore(view.getContext()).read();
            for (int i = 0; i < Math.min(6, saved.entries.size()); i++) {
                QuickActions.Entry entry = saved.entries.get(i);
                text.append(entry.label).append(QuickActionLaunch.available(view.getContext(), entry) ? "" : " (unavailable)").append('\n');
            }
            if (saved.entries.size() > 6) text.append("And ").append(saved.entries.size() - 6).append(" more\n");
            text.append("\nSelect for actions and settings");
        } catch (IOException failed) { text.append("Saved actions unavailable. Select to review."); }
        view.setText(text);
    }
}
