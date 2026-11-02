package com.jeremykenedy.firetv.ui.nativeapp.widget.reminders;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class RemindersActivity extends Activity {
    private boolean startupBlocked;
    private LinearLayout body;
    private String focusKey = "add";
    private View desiredFocus;
    private String renderFocus;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { refreshStatuses(); handler.postDelayed(this, 30000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; } if (state != null) focusKey = state.getString("focus", "add"); render();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; } super.onSaveInstanceState(state); state.putString("focus", focusKey); }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); render(); handler.removeCallbacks(tick); handler.post(tick); }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } handler.removeCallbacks(tick); super.onPause(); }
    private void render() {
        desiredFocus = null; renderFocus = focusKey;
        LinearLayout page = Pages.page(this);
        page.addView(Pages.text(this, "Household reminders", 30));
        page.addView(Pages.text(this, "Local schedules on this TV. Updates while this launcher is open; no background alarms, sound, notifications or TV wakeups. Anyone using the launcher can open this screen.", 16));
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        final RemindersOptions options;
        try { options = RemindersPreferences.load(this); }
        catch (IllegalStateException error) { body.addView(Pages.text(this, error.getMessage(), 20)); return; }
        add("enabled", "Reminders: " + (options.enabled ? "Enabled" : "Disabled"), new Runnable() {
            @Override public void run() { settings(!options.enabled, options.summaryOnly, options.allowExport); }
        });
        add("add", "Add reminder", new Runnable() { @Override public void run() { edit(null); } });
        add("privacy", "Home details: " + (options.summaryOnly ? "Hidden (summary only)" : "Visible"), new Runnable() {
            @Override public void run() {
                if (options.summaryOnly) new AlertDialog.Builder(RemindersActivity.this).setTitle("Show reminder details on Home?")
                        .setMessage("The next reminder label and schedule will be visible to anyone looking at this TV.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Show details", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { settings(options.enabled, false, options.allowExport); }
                        }).show();
                else settings(options.enabled, true, options.allowExport);
            }
        });
        add("export", "Portable backups: " + (options.allowExport ? "Private reminders allowed" : "Private reminders excluded"), new Runnable() {
            @Override public void run() {
                if (options.allowExport) settings(options.enabled, options.summaryOnly, false);
                else new AlertDialog.Builder(RemindersActivity.this).setTitle("Allow private reminders in backups?")
                        .setMessage("A portable file may contain readable labels, dates, time zones and completion state. Anyone with that file can read them. This does not send a backup or enable sync.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Allow in backups", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { settings(options.enabled, options.summaryOnly, true); }
                        }).show();
            }
        });
        TextView summary = Pages.text(this, "", 18); summary.setTag("reminders:summary"); body.addView(summary);
        if (options.items.isEmpty()) body.addView(Pages.text(this, "No reminders yet. Add a chore, trash day, or pet or plant reminder.", 18));
        for (final RemindersOptions.Item item : options.items) {
            add(item.id, label(item), new Runnable() { @Override public void run() { actions(item); } });
        }
        body.addView(Pages.text(this, "Each schedule keeps its selected time zone when the TV zone changes. Daily and weekly repeats keep the local clock time. A missing daylight-saving time shifts forward by the gap; a repeated time uses its earlier offset. Completing a repeat skips missed dates and schedules the first future occurrence.", 16));
        refreshStatuses();
        if (desiredFocus != null) desiredFocus.requestFocus(); else body.getChildAt(0).requestFocus();
    }
    private String label(RemindersOptions.Item item) { return item.label + "\n" + item.status(System.currentTimeMillis()) + " · " + repeatLabel(item.repeat); }
    private String repeatLabel(RemindersOptions.Repeat repeat) { return repeat == RemindersOptions.Repeat.ONCE ? "Once" : repeat == RemindersOptions.Repeat.DAILY ? "Daily" : "Weekly"; }
    private void refreshStatuses() {
        if (body == null) return;
        try {
            RemindersOptions options = RemindersPreferences.load(this);
            TextView summary = body.findViewWithTag("reminders:summary");
            if (summary != null) summary.setText(options.enabled ? options.dueCount(System.currentTimeMillis()) + " reminders due" : "Disabled. Schedules are kept but Home reminders are inactive.");
            for (RemindersOptions.Item item : options.items) {
                Button button = body.findViewWithTag("reminders:" + item.id); if (button != null) button.setText(label(item));
            }
        } catch (IllegalStateException error) {
            TextView summary = body.findViewWithTag("reminders:summary");
            if (summary != null) summary.setText(error.getMessage());
        }
    }
    private Button add(final String key, String label, final Runnable action) {
        Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; action.run(); }
        });
        button.setTag("reminders:" + key);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { if (focused) focusKey = key; }
        });
        body.addView(button); if (key.equals(renderFocus)) desiredFocus = button; return button;
    }
    private void settings(boolean enabled, boolean summary, boolean export) {
        try { save(RemindersPreferences.load(this).settings(enabled, summary, export)); }
        catch (IllegalArgumentException | IllegalStateException error) { toast(error); }
    }
    private void actions(final RemindersOptions.Item item) {
        String[] labels = item.completed ? new String[] {"Edit and schedule again", "Delete"}
                : new String[] {"Complete occurrence", "Snooze 1 hour", "Edit schedule", "Delete"};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Reminder controls").setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if ((item.completed && which == 0) || (!item.completed && which == 2)) edit(item);
                else if (item.completed || which == 3) confirmDelete(item.id);
                else {
                    try {
                        RemindersOptions old = RemindersPreferences.load(RemindersActivity.this);
                        save(which == 0 ? old.complete(item.id, System.currentTimeMillis()) : old.snooze(item.id, System.currentTimeMillis()));
                    } catch (IllegalArgumentException | IllegalStateException error) { toast(error); }
                }
            }
        }).setNegativeButton("Cancel", null).show();
        dialog.getListView().requestFocus(); dialog.getListView().setSelection(0);
    }
    private void confirmDelete(final String id) {
        new AlertDialog.Builder(this).setTitle("Delete this reminder?").setMessage("This removes its local schedule and completion state.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try { RemindersOptions next = RemindersPreferences.load(RemindersActivity.this).remove(id); focusKey = "add"; save(next); }
                        catch (IllegalArgumentException | IllegalStateException error) { toast(error); }
                    }
                }).show();
    }
    private void edit(final RemindersOptions.Item item) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        final EditText title = new EditText(this); title.setSingleLine(true); title.setHint("Reminder label (1 to 128 characters)");
        title.setFilters(new InputFilter[] {new InputFilter.LengthFilter(RemindersOptions.MAX_LABEL)}); title.setTag("reminders:edit-label");
        if (item != null) title.setText(item.label); form.addView(title);
        final LocalDate[] date = {item == null ? LocalDate.now() : item.date};
        final LocalTime[] time = {item == null ? LocalTime.of(9, 0) : item.time};
        final Button dateButton = Pages.button(this, "Date: " + date[0], null); dateButton.setTag("reminders:edit-date");
        dateButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                DatePickerDialog picker = new DatePickerDialog(RemindersActivity.this, new DatePickerDialog.OnDateSetListener() {
                    @Override public void onDateSet(DatePicker picker, int year, int month, int day) { date[0] = LocalDate.of(year, month + 1, day); dateButton.setText("Date: " + date[0]); }
                }, date[0].getYear(), date[0].getMonthValue() - 1, date[0].getDayOfMonth());
                picker.show();
            }
        }); form.addView(dateButton);
        final Button timeButton = Pages.button(this, "Time: " + time[0], null); timeButton.setTag("reminders:edit-time");
        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                new TimePickerDialog(RemindersActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override public void onTimeSet(TimePicker picker, int hour, int minute) { time[0] = LocalTime.of(hour, minute); timeButton.setText("Time: " + time[0]); }
                }, time[0].getHour(), time[0].getMinute(), android.text.format.DateFormat.is24HourFormat(RemindersActivity.this)).show();
            }
        }); form.addView(timeButton);
        final EditText zone = new EditText(this); zone.setSingleLine(true); zone.setHint("IANA time zone, e.g. America/Los_Angeles");
        zone.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)}); zone.setTag("reminders:edit-zone");
        String defaultZone = ZoneId.systemDefault().getId();
        zone.setText(item == null ? ZoneId.getAvailableZoneIds().contains(defaultZone) ? defaultZone : "UTC" : item.zone.getId()); form.addView(zone);
        final Spinner repeat = new Spinner(this); repeat.setTag("reminders:edit-repeat");
        repeat.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[] {"Once", "Daily", "Weekly"}));
        repeat.setSelection(item == null ? 0 : item.repeat.ordinal()); form.addView(repeat);
        form.addView(Pages.text(this, "Editing schedules a new occurrence and clears any completion or snooze. Enabling reminders is a separate choice.", 16));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(item == null ? "Add reminder" : "Edit schedule")
                .setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                title.requestFocus();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            RemindersOptions old = RemindersPreferences.load(RemindersActivity.this);
                            RemindersOptions.Repeat recurrence = RemindersOptions.Repeat.values()[repeat.getSelectedItemPosition()];
                            RemindersOptions next = item == null ? old.add(title.getText().toString(), date[0], time[0], zone.getText().toString().trim(), recurrence)
                                    : old.edit(item.id, title.getText().toString(), date[0], time[0], zone.getText().toString().trim(), recurrence);
                            RemindersPreferences.save(RemindersActivity.this, next);
                            focusKey = item == null ? next.items.get(next.items.size() - 1).id : item.id;
                            dialog.dismiss(); render();
                        } catch (IllegalArgumentException | IllegalStateException error) { title.setError(error.getMessage()); }
                    }
                });
            }
        }); dialog.show();
    }
    private void save(RemindersOptions options) { RemindersPreferences.save(this, options); render(); }
    private void toast(Exception error) { Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show(); }
}
