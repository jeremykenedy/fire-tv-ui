package com.jeremykenedy.firetv.ui.nativeapp.widget.timers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.ZoneId;

final class TimerDialogs {
    private TimerDialogs() {}

    static void countdown(final Activity activity) {
        CountdownEntry previous = TimerPreferences.countdown(activity);
        LinearLayout fields = fields(activity);
        EditText label = input(activity, fields, "Label", previous == null ? "" : previous.label, 80, false);
        String[] dateTime = previous == null ? new String[]{"", ""} : previous.localDateTime().split(" ");
        EditText date = input(activity, fields, "Date: YYYY-MM-DD", dateTime[0], 10, false);
        EditText time = input(activity, fields, "24-hour time: HH:MM", dateTime[1], 5, false);
        EditText zone = input(activity, fields, "Time zone: America/Los_Angeles or UTC",
                previous == null ? ZoneId.systemDefault().getId() : previous.zone, 80, false);
        note(activity, fields, "Daylight saving is handled in the chosen zone. Repeated times use the first occurrence. Skipped times are rejected.");
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Countdown")
                .setView(scroll(activity, fields)).setPositiveButton("Save", null)
                .setNegativeButton("Back", null).setNeutralButton("Remove countdown", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (!TimerPreferences.saveCountdown(activity, null)) failed(activity);
                    }
                }).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            CountdownEntry entry = CountdownEntry.parse(label.getText().toString(), date.getText().toString(),
                                    time.getText().toString(), zone.getText().toString());
                            if (TimerPreferences.saveCountdown(activity, entry)) dialog.dismiss(); else failed(activity);
                        } catch (IllegalArgumentException error) { label.setError(error.getMessage()); }
                    }
                });
                label.requestFocus();
            }
        });
        dialog.show();
    }

    static void timerActions(final Activity activity) {
        TimerState state = TimerPreferences.timer(activity);
        if (state == null) { editTimer(activity); return; }
        String action = state.running ? "Pause" : state.pausedRemaining == 0 ? "Start again" : "Start / Resume";
        new AlertDialog.Builder(activity).setTitle(state.label)
                .setItems(new String[]{action, "Reset", "Edit timer", "Cancel timer", "Sound and standby behavior"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                    TimerState latest = TimerPreferences.timer(activity);
                    if (latest == null) { editTimer(activity); return; }
                    switch (which) {
                        case 0:
                            if (latest.running) latest = latest.pause(SystemClock.elapsedRealtime(), System.currentTimeMillis(), TimerPreferences.boot(activity));
                            else latest = (latest.pausedRemaining == 0 ? latest.reset() : latest)
                                    .resume(SystemClock.elapsedRealtime(), System.currentTimeMillis(), TimerPreferences.boot(activity));
                            if (!TimerPreferences.saveTimer(activity, latest)) failed(activity);
                            break;
                        case 1: if (!TimerPreferences.saveTimer(activity, latest.reset())) failed(activity); break;
                        case 2: editTimer(activity); break;
                        case 3:
                            new AlertDialog.Builder(activity).setTitle("Cancel timer?").setMessage("Remove this timer and its saved duration?")
                                    .setPositiveButton("Cancel timer", new DialogInterface.OnClickListener() {
                                        @Override public void onClick(DialogInterface d, int w) {
                                            if (!TimerPreferences.saveTimer(activity, null)) failed(activity);
                                        }
                                    })
                                    .setNegativeButton("Keep timer", null).show();
                            break;
                        case 4:
                            new AlertDialog.Builder(activity).setTitle("Sound and standby behavior")
                                    .setMessage(new TimerWidgetProvider().message(activity)
                                            + " The saved deadline continues while Home is closed. On return, elapsed timers show Finished."
                                            + " After a TV restart, recovery uses the saved wall-clock deadline. If the TV blocks boot identity access, recovery uses wall time.")
                                    .setPositiveButton("Done", null).show();
                            break;
                        default: break;
                    }
                    }
                }).setNegativeButton("Back", null).show();
    }

    private static void editTimer(final Activity activity) {
        TimerState previous = TimerPreferences.timer(activity);
        LinearLayout fields = fields(activity);
        EditText label = input(activity, fields, "Label", previous == null ? "" : previous.label, 80, false);
        EditText seconds = input(activity, fields, "Duration in seconds: 1 through 604800",
                previous == null ? "300" : Long.toString(previous.duration / 1000), 6, true);
        CheckBox sound = new CheckBox(activity);
        sound.setText("Completion beep while Home is visible");
        sound.setChecked(previous != null && previous.sound);
        sound.setTag("timer:sound");
        fields.addView(sound);
        note(activity, fields, "Save prepares the timer. Select Start / Resume to run it. Editing resets the current timer. Sound defaults to off. No standby alarm or wake is scheduled.");
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Set timer").setView(scroll(activity, fields))
                .setPositiveButton("Save timer", null).setNegativeButton("Back", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            TimerState state = TimerState.create(label.getText().toString(), Long.parseLong(seconds.getText().toString()), sound.isChecked());
                            if (TimerPreferences.saveTimer(activity, state)) dialog.dismiss(); else failed(activity);
                        } catch (IllegalArgumentException error) {
                            label.setError(error instanceof NumberFormatException ? "Enter a duration in whole seconds." : error.getMessage());
                        }
                    }
                });
                label.requestFocus();
            }
        });
        dialog.show();
    }

    private static LinearLayout fields(Activity activity) {
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        return fields;
    }

    private static EditText input(Activity activity, LinearLayout fields, String hint, String value, int max, boolean numeric) {
        TextView title = new TextView(activity);
        title.setText(hint);
        fields.addView(title);
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        input.setText(value);
        input.setContentDescription(hint);
        fields.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private static ScrollView scroll(Activity activity, LinearLayout fields) {
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);
        return scroll;
    }

    private static void note(Activity activity, LinearLayout fields, String message) {
        TextView text = new TextView(activity);
        text.setText(message);
        fields.addView(text);
    }

    private static void failed(Activity activity) { Toast.makeText(activity, "Could not save. Try again.", Toast.LENGTH_LONG).show(); }
}
