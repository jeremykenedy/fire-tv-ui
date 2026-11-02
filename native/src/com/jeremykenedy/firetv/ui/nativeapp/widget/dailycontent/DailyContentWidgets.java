package com.jeremykenedy.firetv.ui.nativeapp.widget.dailycontent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutItem;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;

public final class DailyContentWidgets {
    private DailyContentWidgets() {}
    public static List<WidgetProvider> providers() {
        List<WidgetProvider> providers = new ArrayList<>();
        for (DailyContent.Kind kind : DailyContent.Kind.values()) providers.add(new Provider(kind));
        return Collections.unmodifiableList(providers);
    }
    static int sign(Context context) {
        int value = context.getSharedPreferences("daily_content_v1", Context.MODE_PRIVATE).getInt("sign", 0);
        if (value < 0 || value >= DailyContent.SIGNS.length) throw new IllegalArgumentException("Choose a zodiac sign again");
        return value;
    }
    private static final class Provider implements WidgetProvider {
        private final DailyContent.Kind kind;
        Provider(DailyContent.Kind kind) { this.kind = kind; }
        @Override public LayoutItem item() { return new LayoutItem(kind.id, LayoutItem.Kind.WIDGET); }
        @Override public String title() { return kind.title; }
        @Override public State state(Context context) {
            try { if (kind == DailyContent.Kind.HOROSCOPE) sign(context); return State.READY; }
            catch (RuntimeException error) { return State.UNAVAILABLE; }
        }
        @Override public String message(Context context) {
            if (kind == DailyContent.Kind.HOROSCOPE) return "Original entertainment only. Select to read or choose a zodiac sign. No predictions.";
            return "Offline rotating collection using the TV's local date. Select for full content and attribution.";
        }
        @Override public int widthDp() { return 440; }
        @Override public int heightDp() { return kind == DailyContent.Kind.COMIC ? 440 : kind == DailyContent.Kind.ART ? 380 : 320; }
        @Override public View createView(Context context) { return new DailyContentView(context, kind, false); }
        @Override public void configure(Activity activity) { show(activity, kind); }
        @Override public void onStart(View view) { ((DailyContentView) view).start(); }
        @Override public void onStop(View view) { ((DailyContentView) view).stop(); }
    }
    private static void show(final Activity activity, final DailyContent.Kind kind) {
        final DailyContentView content = new DailyContentView(activity, kind, true);
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity); scroll.addView(content);
        AlertDialog.Builder builder = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle(kind.title).setView(scroll).setNegativeButton("Done", null);
        if (kind == DailyContent.Kind.TRIVIA) builder.setPositiveButton("Reveal answer", null);
        if (kind == DailyContent.Kind.HOROSCOPE) builder.setPositiveButton("Choose sign", null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface unused) {
                content.start();
                if (kind == DailyContent.Kind.TRIVIA) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { content.reveal(); }
                });
                if (kind == DailyContent.Kind.HOROSCOPE) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) { chooseSign(activity, content); }
                });
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface unused) { content.stop(); }
        }); dialog.show();
    }
    private static void chooseSign(final Activity activity, final DailyContentView content) {
        int current;
        try { current = sign(activity); } catch (RuntimeException error) { current = -1; }
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("Choose zodiac sign").setSingleChoiceItems(DailyContent.SIGNS, current, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (activity.getSharedPreferences("daily_content_v1", Context.MODE_PRIVATE).edit().putInt("sign", which).commit()) {
                    content.refresh(); dialog.dismiss();
                } else Toast.makeText(activity, "Could not save zodiac sign", Toast.LENGTH_LONG).show();
            }
        }).setNegativeButton("Back", null).show();
    }
}
