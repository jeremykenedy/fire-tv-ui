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
    private TextView sleep;
    private TextView idle;
    private TextView deep;
    private TextView protection;
    private TextView awake;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("System timers");
        LinearLayout body = scrollBody();
        idle = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                minutes(false);
            }
        });
        sleep = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                minutes(true);
            }
        });
        deep = button("", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    int current = DeviceSettings.sleep(TimersActivity.this);
                    if (current > 0) {
                        DeviceSettings.sleep(TimersActivity.this, 0);
                    } else {
                        DeviceSettings.sleep(TimersActivity.this, DeviceSettings.prefs(TimersActivity.this)
                                .getInt("fire_tv_ui_sleep_saved", 1200000));
                    }
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
        addButton(body, idle);
        addButton(body, sleep);
        addButton(body, deep);
        if (DeviceSettings.hasAutoWake(this)) {
            awake = button("", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        DeviceSettings.autoWake(TimersActivity.this, !DeviceSettings.autoWake(TimersActivity.this));
                        refresh();
                    } catch (Exception failed) {
                        error(failed);
                    }
                }
            });
            addButton(body, awake);
        }
        addButton(body, protection);
        TextView hint = text("Deep sleep puts the TV into standby. Turn it off to keep the TV awake. "
                + "Protect settings restores your chosen screensaver and timers if the system changes them. "
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
        idle.setText("Screensaver starts after: " + TimeoutValues.label(DeviceSettings.idle(this)));
        sleep.setText("TV sleeps after: " + TimeoutValues.label(DeviceSettings.sleep(this)));
        deep.setText("Deep sleep: " + (DeviceSettings.sleep(this) == 0 ? "Off" : "On"));
        if (awake != null) {
            awake.setText("Keep Alexa available during sleep: " + (DeviceSettings.autoWake(this) ? "On" : "Off"));
        }
        protection.setText("Protect screensaver and timers: "
                + (DeviceSettings.prefs(this).getBoolean(DeviceSettings.PROTECT, false) ? "On" : "Off"));
    }

    private void minutes(final boolean sleeping) {
        final String[] options = {"1 minute", "5 minutes", "10 minutes", "15 minutes", "20 minutes", "30 minutes", "60 minutes", "Custom minutes"};
        final int[] values = {1, 5, 10, 15, 20, 30, 60};
        new AlertDialog.Builder(this).setTitle(sleeping ? "TV sleep timer" : "Screensaver delay")
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
                DeviceSettings.sleep(this, value);
            } else {
                DeviceSettings.idle(this, value);
            }
            refresh();
        } catch (Exception failed) {
            error(failed);
        }
    }
}
