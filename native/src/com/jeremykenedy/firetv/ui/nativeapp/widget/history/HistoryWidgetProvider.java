package com.jeremykenedy.firetv.ui.nativeapp.widget.history;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ScrollView;

import com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import com.jeremykenedy.firetv.ui.nativeapp.widget.feeds.FeedLinks;

import java.time.LocalDate;
import java.time.ZoneId;

public final class HistoryWidgetProvider implements WidgetProvider {
    private static final LayoutItem ITEM = new LayoutItem("on-this-day", LayoutItem.Kind.WIDGET);

    @Override
    public LayoutItem item() {
        return ITEM;
    }

    @Override
    public String title() {
        return "On This Day";
    }

    @Override
    public State state(Context context) {
        return HistoryRepository.SHARED.hasFresh(LocalDate.now(ZoneId.systemDefault()))
                ? State.READY
                : State.UNAVAILABLE;
    }

    @Override
    public String message(Context context) {
        return "Historical events from Wikipedia for the TV's local date. Internet access is"
                   + " required.";
    }

    @Override
    public boolean canRender(State state) {
        return true;
    }

    @Override
    public int widthDp() {
        return 440;
    }

    @Override
    public int heightDp() {
        return 180;
    }

    @Override
    public View createView(Context context) {
        return new HistoryView(context, false);
    }

    @Override
    public void onStart(View view) {
        ((HistoryView) view).start();
    }

    @Override
    public void onStop(View view) {
        ((HistoryView) view).stop();
    }

    @Override
    public void configure(final Activity activity) {
        final HistoryView content = new HistoryView(activity, true);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        final AlertDialog dialog =
                new HomeDialogBuilder(activity)
                        .setTitle("On This Day")
                        .setView(scroll)
                        .setPositiveButton("Open source", null)
                        .setNeutralButton("Open license", null)
                        .setNegativeButton("Done", null)
                        .create();
        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface ignored) {
                        content.start();
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                FeedLinks.open(
                                                        activity,
                                                        HistoryRepository.sourceUrl(
                                                                LocalDate.now(
                                                                        ZoneId.systemDefault())));
                                            }
                                        });
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                                .setOnClickListener(
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                FeedLinks.open(activity, HistoryRepository.LICENSE);
                                            }
                                        });
                    }
                });
        dialog.setOnDismissListener(
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface ignored) {
                        content.stop();
                    }
                });
        dialog.show();
    }
}
