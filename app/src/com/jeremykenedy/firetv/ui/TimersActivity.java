package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class TimersActivity extends CardActivity {
    private TextView tvSleep;
    private TextView deepTimer;
    private TextView idle;
    private TextView deep;
    private TextView protection;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("System timers");
        LinearLayout body = scrollBody();
        tvSleep = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SystemSettings.openSleepTimer(TimersActivity.this);
            }
        });
        idle = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                minutes(false);
            }
        });
        deepTimer = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                minutes(true);
            }
        });
        deep = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    DeviceSettings.deepSleep(TimersActivity.this,
                            !DeviceSettings.deepSleepEnabled(TimersActivity.this));
                    refresh();
                } catch (Exception failed) {
                    error(failed);
                }
            }
        });
        protection = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean enabled = DeviceSettings.prefs(TimersActivity.this).getBoolean(DeviceSettings.PROTECT, false);
                if (!enabled) {
                    DeviceSettings.capture(TimersActivity.this);
                }
                DeviceSettings.prefs(TimersActivity.this).edit().putBoolean(DeviceSettings.PROTECT, !enabled).commit();
                ProtectionService.start(TimersActivity.this);
                refresh();
            }
        });
        addButton(body, tvSleep);
        addButton(body, idle);
        addButton(body, deepTimer);
        if (DeviceSettings.hasAutoWake(this)) {
            addButton(body, deep);
        }
        addButton(body, protection);
        TextView hint = text("TV Sleep Timer is a one-time countdown provided by the TV. It is separate "
                + "from the screensaver delay and Deep Sleep. The Deep Sleep toggle controls whether Alexa "
                + "can wake the TV after it enters Deep Sleep. Its timer independently controls how long "
                + "the idle TV waits before entering Deep Sleep. "
                + "Fire TV UI saves the deep sleep choice and timer separately and restores them after restart. "
                + "Protect settings restores your chosen screensaver and its delay if the system changes them. "
                + "Fire TV Toolkit timer and screensaver commands also update your protected settings.", 16, MUTED);
        hint.setPadding(0, dp(12), 0, dp(12));
        body.addView(hint);
        idle.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        tvSleep.setText("TV Sleep Timer: Open TV timer");
        idle.setText("Screensaver starts after: " + TimeoutValues.label(DeviceSettings.idle(this)));
        deepTimer.setText("Deep sleep timer: " + TimeoutValues.label(DeviceSettings.deepSleepTimeout(this)));
        if (DeviceSettings.hasAutoWake(this)) {
            deep.setText("Deep sleep: " + (DeviceSettings.deepSleepEnabled(this) ? "On" : "Off"));
        }
        protection.setText("Protect screensaver and other settings: "
                + (DeviceSettings.prefs(this).getBoolean(DeviceSettings.PROTECT, false) ? "On" : "Off"));
    }

    private void minutes(final boolean sleeping) {
        final String[] options = sleeping
                ? new String[] {"Never", "1 minute", "5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes", "60 minutes", "Custom minutes"}
                : new String[] {"1 minute", "5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes", "60 minutes", "Custom minutes"};
        final int[] values = sleeping ? new int[] {0, 1, 5, 10, 15, 20, 30, 60} : new int[] {1, 5, 10, 15, 20, 30, 60};
        new AlertDialog.Builder(this).setTitle(sleeping ? "Deep sleep timer" : "Screensaver delay")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int index) {
                        if (index == values.length) {
                            custom(sleeping);
                        } else {
                            save(sleeping, String.valueOf(values[index]));
                        }
                    }
                }).show();
    }

    private void custom(final boolean sleeping) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Minutes");
        new AlertDialog.Builder(this).setTitle("Minutes (1 to " + TimeoutValues.MAX_MINUTES + ")")
                .setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        save(sleeping, input.getText().toString());
                    }
                }).show();
    }

    private void save(boolean sleeping, String minutes) {
        try {
            int value = TimeoutValues.milliseconds(minutes);
            if (sleeping) {
                DeviceSettings.deepSleepTimeout(this, value);
            } else {
                DeviceSettings.idle(this, value);
            }
            refresh();
        } catch (Exception failed) {
            error(failed);
        }
    }
}
