package com.jeremykenedy.firetv.ui.nativeapp.widget.uv;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UvSettings {
    private UvSettings() {}
    public static void show(final Activity activity) {
        String saved = null;
        try { saved = UvPreferences.read(activity); } catch (IllegalArgumentException invalid) { }
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        TextView explanation = new TextView(activity);
        explanation.setText("Enter a U.S. ZIP code for EPA's hourly UV Index forecast. Your chosen ZIP is sent to EPA when the widget refreshes. Forecast hours use that ZIP's local time.");
        fields.addView(explanation);
        final EditText input = new EditText(activity);
        input.setHint("Five-digit ZIP code"); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5)});
        if (saved != null) input.setText(saved);
        fields.addView(input);
        if (saved != null) {
            Button forecast = new Button(activity);
            forecast.setText("View hourly forecast");
            forecast.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View clicked) { details(activity); }
            });
            fields.addView(forecast);
        }
        final AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("UV forecast location").setView(fields)
                .setNegativeButton("Cancel", null).setNeutralButton("Clear ZIP", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface closed, int which) {
                        try { UvPreferences.save(activity, null); }
                        catch (IllegalStateException error) { failure(activity, error.getMessage()); }
                    }
                }).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface shown) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try { UvPreferences.save(activity, UvForecast.zip(input.getText().toString().trim())); dialog.dismiss(); }
                        catch (IllegalArgumentException error) { input.setError(error.getMessage()); }
                        catch (IllegalStateException error) { failure(activity, error.getMessage()); }
                    }
                });
            }
        });
        dialog.show();
    }
    private static void details(final Activity activity) {
        final UvView forecast = new UvView(activity, true);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(forecast);
        final AlertDialog dialog = new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("EPA hourly UV forecast").setView(scroll)
                .setPositiveButton("Done", null).create();
        final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity current) { if (current == activity && dialog.isShowing()) forecast.start(); }
            @Override public void onActivityStopped(Activity current) { if (current == activity) forecast.stop(); }
            @Override public void onActivityDestroyed(Activity current) { if (current == activity) dialog.dismiss(); }
            @Override public void onActivityCreated(Activity current, Bundle state) {}
            @Override public void onActivityResumed(Activity current) {}
            @Override public void onActivityPaused(Activity current) {}
            @Override public void onActivitySaveInstanceState(Activity current, Bundle state) {}
        };
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface closed) {
                forecast.stop(); activity.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);
            }
        });
        activity.getApplication().registerActivityLifecycleCallbacks(lifecycle);
        dialog.show(); forecast.start();
    }
    private static void failure(Activity activity, String message) {
        new com.jeremykenedy.firetv.ui.nativeapp.HomeDialogBuilder(activity).setTitle("ZIP was not saved").setMessage(message).setPositiveButton("OK", null).show();
    }
}
