package com.jeremykenedy.firetv.ui.nativeapp.widget.watchlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class WatchlistWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("tonight-watchlist", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Tonight watchlist"; }
    @Override public State state(Context context) {
        try { return new WatchlistStore(context).load().items.isEmpty() ? State.EMPTY : State.READY; }
        catch (IllegalStateException unavailable) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) { return "Saved titles stay on this TV. Availability and subscription access are unknown."; }
    @Override public int widthDp() { return 380; }
    @Override public int heightDp() { return 180; }
    @Override public View createView(Context context) {
        LinearLayout panel = new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(12 * context.getResources().getDisplayMetrics().density); panel.setPadding(pad, pad, pad, pad);
        TextView heading = text(context, 19); heading.setText(title()); panel.addView(heading);
        for (int i = 0; i < 3; i++) {
            TextView row = text(context, 17); row.setTag("watchlist:title:" + i);
            panel.addView(row, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        TextView status = text(context, 15); status.setTag("watchlist:availability");
        status.setText("Availability unknown"); panel.addView(status);
        refresh(panel); return panel;
    }
    private static TextView text(Context context, int size) {
        TextView view = new TextView(context); view.setTextColor(Color.WHITE); view.setTextSize(size);
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END); return view;
    }
    @Override public void configure(Activity activity) { activity.startActivity(new Intent(activity, WatchlistActivity.class)); }
    @Override public void onStart(View view) { if (view instanceof LinearLayout) refresh((LinearLayout) view); }
    @Override public void onStop(View view) {}
    private void refresh(LinearLayout panel) {
        String[] lines = {"", "", ""};
        try {
            Watchlist list = new WatchlistStore(panel.getContext()).load(); int shown = 0;
            for (Watchlist.Item item : list.items) if (!item.watched && shown < lines.length) lines[shown++] = item.title;
            if (shown == 0) lines[0] = "No unwatched titles.";
        } catch (IllegalStateException invalid) { lines[0] = "Saved data unavailable; kept on this TV."; }
        for (int i = 0; i < lines.length; i++) ((TextView) panel.findViewWithTag("watchlist:title:" + i)).setText(lines[i]);
    }
}
