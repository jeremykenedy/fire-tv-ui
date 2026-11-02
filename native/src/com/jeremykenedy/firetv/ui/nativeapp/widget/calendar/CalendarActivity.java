package com.jeremykenedy.firetv.ui.nativeapp.widget.calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;

public final class CalendarActivity extends com.jeremykenedy.firetv.ui.nativeapp.MenuActivity {
    private boolean startupBlocked;
    private static final int READ_REQUEST = 71;
    private LinearLayout body;
    private CalendarLoader loader;
    private CalendarData.Result snapshot;
    private String focusKey = "refresh";
    private View desiredFocus;
    private String renderFocus;
    private String permissionNotice;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; } if (state != null) focusKey = state.getString("focus", "refresh");
        loader = new CalendarLoader(this, new CalendarLoader.Listener() {
            @Override public void onCalendar(CalendarOptions options, CalendarData.Result result) { snapshot = result; render(); }
        }); render();
    }
    @Override protected void onResume() {
        if (startupBlocked) { super.onResume(); return; } super.onResume(); snapshot = null; render(); loader.start(); }
    @Override protected void onPause() {
        if (startupBlocked) { super.onPause(); return; } loader.stop(); snapshot = null; super.onPause(); }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; } loader.close(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (startupBlocked) { super.onSaveInstanceState(state); return; } super.onSaveInstanceState(state); state.putString("focus", focusKey); }
    private void render() {
        desiredFocus = null; renderFocus = focusKey;
        LinearLayout page = Pages.page(this); page.addView(Pages.text(this, "Calendar", 30));
        page.addView(Pages.text(this, "Read only from calendars already available on this TV. No account sign-in, sync service or calendar writes. Only calendars you select are used. Event titles and times are never saved, logged or included in portable backups.", 16));
        ScrollView scroll = new ScrollView(this); body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        final CalendarOptions options;
        try { options = CalendarPreferences.load(this); }
        catch (IllegalStateException error) { body.addView(Pages.text(this, error.getMessage(), 20)); return; }
        boolean available;
        try { available = CalendarSource.available(this); } catch (RuntimeException error) { available = false; }
        if (!available) body.addView(Pages.text(this, "No supported Calendar provider is available on this TV. Installing this launcher does not supply calendars or accounts.", 20));
        if (!CalendarSource.permitted(this)) {
            Button allow = add("permission", "Allow calendar access", new Runnable() {
                @Override public void run() {
                    new AlertDialog.Builder(CalendarActivity.this).setTitle("Allow read-only calendar access?")
                            .setMessage("Android permission allows this app to read calendars on this TV. The launcher will display only calendars you select. Home starts with counts only.")
                            .setNegativeButton("Cancel", null).setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface dialog, int which) {
                                    try { requestPermissions(new String[] {CalendarSource.PERMISSION}, READ_REQUEST); }
                                    catch (RuntimeException unavailable) { permissionNotice = "Calendar permission cannot be requested on this TV."; render(); }
                                }
                            }).show();
                }
            }); allow.setEnabled(available);
        }
        add("app-settings", "Review Android app permissions", new Runnable() {
            @Override public void run() {
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:" + getPackageName()))); }
                catch (ActivityNotFoundException | SecurityException unavailable) { toast("App permission settings are unavailable on this TV."); }
            }
        });
        if (permissionNotice != null) body.addView(Pages.text(this, permissionNotice, 18));
        add("refresh", "Refresh calendars", new Runnable() { @Override public void run() { snapshot = null; render(); loader.load(); } });
        add("zone", "Display time zone: " + options.zone, new Runnable() { @Override public void run() { zone(options); } });
        add("privacy", "Home details: " + (options.summaryOnly ? "Hidden (counts only)" : "Visible"), new Runnable() {
            @Override public void run() {
                if (options.summaryOnly) new AlertDialog.Builder(CalendarActivity.this).setTitle("Show private event details on Home?")
                        .setMessage("An event title and schedule may be visible to anyone looking at this TV. Counts-only mode does not request event titles.")
                        .setNegativeButton("Cancel", null).setPositiveButton("Show details", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) { save(options.privacy(false)); }
                        }).show();
                else save(options.privacy(true));
            }
        });
        body.addView(Pages.text(this, "Calendar selection stays local to this TV and is excluded from portable backups. Timed events use the display time zone; all-day events keep their calendar dates. Anyone using this launcher can open these settings.", 16));
        if (snapshot == null) body.addView(Pages.text(this, "Checking calendar access…", 18));
        else {
            body.addView(Pages.text(this, snapshot.message(), 18));
            body.addView(Pages.text(this, "Checked " + DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm z").withZone(ZoneId.of(options.zone)).format(Instant.ofEpochMilli(snapshot.checkedAt)), 15));
            HashSet<Long> listed = new HashSet<>();
            for (final CalendarData.Calendar calendar : snapshot.calendars) {
                listed.add(calendar.id);
                add("calendar:" + calendar.id, (options.selected.contains(calendar.id) ? "[x] " : "[ ] ") + calendar.label,
                        new Runnable() { @Override public void run() { toggle(calendar.id); } });
            }
            for (final Long id : options.selected) if (!listed.contains(id)) add("calendar:" + id, "[x] Unavailable selected calendar #" + id + " · Remove selection",
                    new Runnable() { @Override public void run() { toggle(id); } });
            if (snapshot.calendars.isEmpty() && CalendarSource.permitted(this)) body.addView(Pages.text(this, "No readable calendars are available. Calendar apps and providers manage their own local or synced calendars.", 18));
            if (!options.selected.isEmpty()) {
                body.addView(Pages.text(this, "Selected calendar preview", 22));
                body.addView(Pages.text(this, snapshot.homeText(options, System.currentTimeMillis()), 18));
            }
        }
        if (desiredFocus != null) desiredFocus.requestFocus(); else {
            for (int i = 0; i < body.getChildCount(); i++) if (body.getChildAt(i).isFocusable() && body.getChildAt(i).isEnabled()) { body.getChildAt(i).requestFocus(); break; }
        }
    }
    private Button add(final String key, String label, final Runnable action) {
        Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { focusKey = key; action.run(); }
        }); button.setTag("calendar:" + key);
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) { if (focused) focusKey = key; }
        }); body.addView(button); if (key.equals(renderFocus)) desiredFocus = button; return button;
    }
    private void toggle(long id) {
        try { save(CalendarPreferences.load(this).toggle(id)); } catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); }
    }
    private void save(CalendarOptions options) {
        try { CalendarPreferences.save(this, options); snapshot = null; render(); loader.load(); }
        catch (IllegalArgumentException | IllegalStateException error) { toast(error.getMessage()); }
    }
    private void zone(final CalendarOptions options) {
        final EditText input = new EditText(this); input.setSingleLine(true); input.setText(options.zone); input.selectAll();
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)});
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Display time zone")
                .setMessage("Enter an IANA zone such as America/Los_Angeles. Calendar event times are converted to this zone.")
                .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                input.requestFocus(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try { CalendarPreferences.save(CalendarActivity.this, options.zone(input.getText().toString().trim())); dialog.dismiss(); snapshot = null; render(); loader.load(); }
                        catch (IllegalArgumentException | IllegalStateException error) { input.setError(error.getMessage()); }
                    }
                });
            }
        }); dialog.show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        if (startupBlocked) { super.onRequestPermissionsResult(request, permissions, results); return; }
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != READ_REQUEST) return;
        permissionNotice = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED
                ? "Calendar access allowed. Choose calendars below; none are selected automatically."
                : "Calendar access was not allowed. No events can be read. You can review Android app permissions.";
        snapshot = null; render(); loader.load();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
