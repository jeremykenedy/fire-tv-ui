package com.jeremykenedy.firetv.ui.nativeapp.widget.breathing;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

final class BreathingDialogs {
    private BreathingDialogs() {}

    static void show(final Activity activity) {
        final BreathingSession session;
        try { session = BreathingPreferences.read(activity); }
        catch (IllegalArgumentException error) {
            new AlertDialog.Builder(activity).setTitle("Breathing break").setMessage(error.getMessage())
                    .setPositiveButton("Reset exercise", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) { save(activity, BreathingSession.defaults()); }
                    }).setNegativeButton("Back", null).show();
            return;
        }
        String action = session.running ? "Pause exercise" : session.elapsedAt(SystemClock.elapsedRealtime()) == session.duration * 1000L
                ? "Start again" : session.elapsed == 0 ? "Start exercise" : "Resume exercise";
        new AlertDialog.Builder(activity).setTitle("Breathing break")
                .setItems(new String[]{action, "Reset exercise", "Cadence and motion", "About this exercise"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        BreathingSession current;
                        try { current = BreathingPreferences.read(activity); }
                        catch (IllegalArgumentException error) { show(activity); return; }
                        switch (which) {
                            case 0: save(activity, current.running ? current.pause(SystemClock.elapsedRealtime()) : current.start(SystemClock.elapsedRealtime())); break;
                            case 1: save(activity, current.reset()); break;
                            case 2: configure(activity, current); break;
                            case 3:
                                new AlertDialog.Builder(activity).setTitle("Breathing break")
                                        .setMessage("An optional silent visual pace. Follow it only while comfortable. Choose your own cadence and session length."
                                                + " Reduced motion keeps the circle still and shows phase text. The exercise pauses when Home closes or the widget leaves view."
                                                + " It stays paused when you return. No sound, standby activity or wake is scheduled.")
                                        .setPositiveButton("Done", null).show();
                                break;
                            default: break;
                        }
                    }
                }).setNegativeButton("Back", null).show();
    }

    private static void configure(final Activity activity, BreathingSession session) {
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, padding, padding, padding);
        final EditText inhale = number(activity, fields, "Inhale seconds: 2 through 12", session.inhale);
        final EditText hold = number(activity, fields, "Hold after inhale: 0 through 8", session.hold);
        final EditText exhale = number(activity, fields, "Exhale seconds: 2 through 12", session.exhale);
        final EditText rest = number(activity, fields, "Rest after exhale: 0 through 8", session.rest);
        final EditText duration = number(activity, fields, "Session seconds: 30 through 1800", session.duration);
        final CheckBox reduced = new CheckBox(activity);
        reduced.setText("Reduced motion: still circle and phase text");
        reduced.setChecked(session.reducedMotion);
        reduced.setTag("breathing:reduced-motion");
        fields.addView(reduced);
        TextView note = new TextView(activity);
        note.setText("Save resets the exercise and leaves it paused. Holds of zero are skipped. This exercise is silent.");
        fields.addView(note);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);
        final AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Cadence and motion").setView(scroll)
                .setPositiveButton("Save", null).setNegativeButton("Back", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface unused) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            BreathingSession session = new BreathingSession(Integer.parseInt(inhale.getText().toString()),
                                    Integer.parseInt(hold.getText().toString()), Integer.parseInt(exhale.getText().toString()),
                                    Integer.parseInt(rest.getText().toString()), Integer.parseInt(duration.getText().toString()),
                                    reduced.isChecked(), 0, 0, false);
                            if (save(activity, session)) dialog.dismiss();
                        } catch (IllegalArgumentException error) {
                            inhale.setError(error instanceof NumberFormatException ? "Enter whole seconds for each phase and the session." : error.getMessage());
                        }
                    }
                });
                inhale.requestFocus();
            }
        });
        dialog.show();
    }

    private static EditText number(Activity activity, LinearLayout fields, String label, int value) {
        TextView title = new TextView(activity);
        title.setText(label);
        fields.addView(title);
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setContentDescription(label);
        input.setText(Integer.toString(value));
        fields.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }

    private static boolean save(Activity activity, BreathingSession session) {
        if (BreathingPreferences.save(activity, session)) return true;
        Toast.makeText(activity, "Could not save. Try again.", Toast.LENGTH_LONG).show();
        return false;
    }
}
