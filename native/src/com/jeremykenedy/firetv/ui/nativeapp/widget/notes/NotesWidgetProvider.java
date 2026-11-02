package com.jeremykenedy.firetv.ui.nativeapp.widget.notes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class NotesWidgetProvider implements WidgetProvider {
    public static final LayoutItem ITEM = new LayoutItem("household-notes", LayoutItem.Kind.WIDGET);
    @Override public LayoutItem item() { return ITEM; }
    @Override public String title() { return "Household notes and lists"; }
    @Override public State state(Context context) {
        try {
            NotesOptions options = NotesPreferences.load(context);
            return !options.configured ? State.SETUP_REQUIRED : options.items.isEmpty() ? State.EMPTY : State.READY;
        } catch (IllegalStateException error) { return State.UNAVAILABLE; }
    }
    @Override public String message(Context context) {
        try {
            NotesOptions options = NotesPreferences.load(context);
            return !options.configured ? "Add local notes or checklist items. Home details start hidden."
                    : options.items.isEmpty() ? "No notes or list items yet. Open to add one."
                    : options.summaryOnly ? "Home shows counts only. Open to read or edit." : "Home shows up to three item previews.";
        } catch (IllegalStateException error) { return "Saved notes could not be read. Stored data has been kept."; }
    }
    @Override public int widthDp() { return 380; }
    @Override public int heightDp() { return 210; }
    @Override public View createView(Context context) {
        TextView text = new TextView(context); text.setTextColor(Color.WHITE); text.setTextSize(20);
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad); text.setMaxLines(7); refresh(text); return text;
    }
    @Override public void configure(Activity activity) { activity.startActivity(new Intent(activity, NotesActivity.class)); }
    @Override public void onStart(View view) { if (view instanceof TextView) refresh((TextView) view); }
    @Override public void onStop(View view) {}
    private void refresh(TextView view) {
        try { view.setText(title() + "\n" + NotesPreferences.load(view.getContext()).homeText()); }
        catch (IllegalStateException error) { view.setText(title() + "\n" + message(view.getContext())); }
    }
}
