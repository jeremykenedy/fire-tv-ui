package com.jeremykenedy.firetv.ui.nativeapp.layoutschedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutOptions;

import java.util.*;

/**
 * Explicit opt-in controls. Selection never applies a full saved setup or starts background work.
 */
public final class LayoutScheduleActivity extends Activity {
    private boolean startupBlocked;
    private LayoutScheduleStore store;
    private LinearLayout rows;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        store = new LayoutScheduleStore(this);
        refresh();
    }

    private void refresh() {
        ScrollView scroll = new ScrollView(this);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(48, 32, 48, 32);
        scroll.addView(rows);
        setContentView(scroll);
        text("Dashboard layouts", 26);
        text(
                "Save snapshots of the Home layout editor. These change card/widget positions,"
                    + " visibility, mode and dimensions only. Provider accounts, household content"
                    + " and active timers stay local.",
                18);
        try {
            final LayoutSchedule.State state = store.read();
            LayoutSchedule.Decision decision =
                    LayoutSchedule.resolve(state, System.currentTimeMillis());
            String current =
                    decision.status == LayoutSchedule.Status.READY
                            ? decision.profile.name
                            : decision.status == LayoutSchedule.Status.BASE_LAYOUT
                                    ? "Default Home layout"
                                    : decision.status == LayoutSchedule.Status.MISSING_PROFILE
                                            ? "Selected dashboard is missing; choose another"
                                            : "Automatic selection needs configuration";
            text("Current selection: " + current, 18);
            if (!state.manual.isEmpty())
                text("Manual override stays selected until you choose Resume automatic.", 18);
            button(
                    "Automatic selection: " + state.mode,
                    new Runnable() {
                        @Override
                        public void run() {
                            chooseMode();
                        }
                    });
            button(
                    "Resume automatic",
                    new Runnable() {
                        @Override
                        public void run() {
                            change(
                                    new Work() {
                                        @Override
                                        public void run() throws Exception {
                                            store.resumeAutomatic();
                                        }
                                    });
                        }
                    });
            button(
                    "Open Home layout editor",
                    new Runnable() {
                        @Override
                        public void run() {
                            startActivity(
                                    new Intent()
                                            .setClassName(
                                                    LayoutScheduleActivity.this,
                                                    "com.jeremykenedy.firetv.ui.nativeapp.layout.LayoutSettingsActivity"));
                        }
                    });
            button(
                    "Save editor layout as dashboard",
                    new Runnable() {
                        @Override
                        public void run() {
                            saveName(null, "");
                        }
                    });
            button(
                    "Rotation dashboards (" + state.rotation.size() + ")",
                    new Runnable() {
                        @Override
                        public void run() {
                            rotation();
                        }
                    });
            button(
                    "Rotation interval: " + state.rotationSeconds + " seconds",
                    new Runnable() {
                        @Override
                        public void run() {
                            prompt(
                                    "Seconds from 1 through 86400",
                                    Integer.toString(state.rotationSeconds),
                                    new TextWork() {
                                        @Override
                                        public void run(String value) throws Exception {
                                            LayoutSchedule.State latest = store.read();
                                            store.rotation(
                                                    latest.rotation,
                                                    Integer.parseInt(value.trim()),
                                                    System.currentTimeMillis());
                                        }
                                    });
                        }
                    });
            button(
                    "Daily time zone: " + (state.zone.isEmpty() ? "Choose explicitly" : state.zone),
                    new Runnable() {
                        @Override
                        public void run() {
                            prompt(
                                    "IANA time zone",
                                    state.zone,
                                    new TextWork() {
                                        @Override
                                        public void run(String value) throws Exception {
                                            store.daily(value.trim(), store.read().slots);
                                        }
                                    });
                        }
                    });
            button(
                    "Add daily change",
                    new Runnable() {
                        @Override
                        public void run() {
                            addSlot();
                        }
                    });
            for (final LayoutSchedule.Slot slot : state.slots)
                button(
                        slot.time() + " - " + label(state, slot.profile),
                        new Runnable() {
                            @Override
                            public void run() {
                                slotMenu(slot);
                            }
                        });
            text("Saved dashboards", 22);
            for (final LayoutSchedule.Profile profile : state.profiles)
                button(
                        profile.name,
                        new Runnable() {
                            @Override
                            public void run() {
                                profileMenu(profile);
                            }
                        });
        } catch (Exception failure) {
            error(failure);
        }
    }

    private String label(LayoutSchedule.State state, String id) {
        LayoutSchedule.Profile profile = state.profile(id);
        return profile == null ? "Missing dashboard" : profile.name;
    }

    private void chooseMode() {
        final String[] labels = {"Off", "Rotate selected dashboards", "Daily schedule"};
        new AlertDialog.Builder(this)
                .setTitle("Automatic selection")
                .setItems(
                        labels,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, final int which) {
                                change(
                                        new Work() {
                                            @Override
                                            public void run() throws Exception {
                                                store.mode(LayoutSchedule.Mode.values()[which]);
                                            }
                                        });
                            }
                        })
                .show();
    }

    private void profileMenu(final LayoutSchedule.Profile profile) {
        new AlertDialog.Builder(this)
                .setTitle(profile.name)
                .setItems(
                        new String[] {
                            "Select manually", "Replace from Home layout editor", "Rename", "Delete"
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0)
                                    change(
                                            new Work() {
                                                @Override
                                                public void run() throws Exception {
                                                    store.selectManual(profile.id);
                                                }
                                            });
                                else if (which == 1)
                                    new AlertDialog.Builder(LayoutScheduleActivity.this)
                                            .setMessage(
                                                    "Replace this dashboard with the Home layout"
                                                            + " editor's current layout?")
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton(
                                                    "Replace",
                                                    new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(
                                                                DialogInterface dialog, int which) {
                                                            change(
                                                                    new Work() {
                                                                        @Override
                                                                        public void run()
                                                                                throws Exception {
                                                                            store.save(
                                                                                    profile.name,
                                                                                    profile.id,
                                                                                    editorLayout());
                                                                        }
                                                                    });
                                                        }
                                                    })
                                            .show();
                                else if (which == 2)
                                    prompt(
                                            "Rename dashboard",
                                            profile.name,
                                            new TextWork() {
                                                @Override
                                                public void run(String value) throws Exception {
                                                    store.rename(profile.id, value);
                                                }
                                            });
                                else
                                    new AlertDialog.Builder(LayoutScheduleActivity.this)
                                            .setMessage(
                                                    "Delete this dashboard? Selections or schedules"
                                                            + " using it will report a missing"
                                                            + " dashboard until you change them.")
                                            .setNegativeButton("Cancel", null)
                                            .setPositiveButton(
                                                    "Delete",
                                                    new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(
                                                                DialogInterface dialog, int which) {
                                                            change(
                                                                    new Work() {
                                                                        @Override
                                                                        public void run()
                                                                                throws Exception {
                                                                            store.delete(
                                                                                    profile.id);
                                                                        }
                                                                    });
                                                        }
                                                    })
                                            .show();
                            }
                        })
                .show();
    }

    private LayoutOptions editorLayout() {
        Map<String, Object> values =
                new LinkedHashMap<>(LayoutOptions.defaults(320, 180).backupValues());
        Map<String, ?> stored = getSharedPreferences("native_layout", MODE_PRIVATE).getAll();
        for (String key : new ArrayList<>(values.keySet()))
            if (stored.containsKey(key)) values.put(key, stored.get(key));
        return LayoutOptions.fromBackup(values);
    }

    private void saveName(final String id, String previous) {
        prompt(
                "Dashboard name",
                previous,
                new TextWork() {
                    @Override
                    public void run(String value) throws Exception {
                        store.save(value, id, editorLayout());
                    }
                });
    }

    private void rotation() {
        try {
            final LayoutSchedule.State state = store.read();
            List<String> labels = new ArrayList<>();
            for (String id : state.rotation) labels.add(label(state, id));
            labels.add("Add dashboard");
            new AlertDialog.Builder(this)
                    .setTitle("Rotation order")
                    .setItems(
                            labels.toArray(new String[0]),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, final int which) {
                                    if (which == state.rotation.size())
                                        chooseProfile(
                                                new ProfileWork() {
                                                    @Override
                                                    public void run(String id) throws Exception {
                                                        List<String> next =
                                                                new ArrayList<>(state.rotation);
                                                        next.add(id);
                                                        store.rotation(
                                                                next,
                                                                state.rotationSeconds,
                                                                System.currentTimeMillis());
                                                    }
                                                });
                                    else
                                        new AlertDialog.Builder(LayoutScheduleActivity.this)
                                                .setItems(
                                                        new String[] {
                                                            "Move earlier", "Move later", "Remove"
                                                        },
                                                        new DialogInterface.OnClickListener() {
                                                            @Override
                                                            public void onClick(
                                                                    DialogInterface dialog,
                                                                    final int action) {
                                                                change(
                                                                        new Work() {
                                                                            @Override
                                                                            public void run()
                                                                                    throws
                                                                                            Exception {
                                                                                List<String> next =
                                                                                        new ArrayList<>(
                                                                                                state.rotation);
                                                                                if (action == 2)
                                                                                    next.remove(
                                                                                            which);
                                                                                else {
                                                                                    int target =
                                                                                            which
                                                                                                    + (action
                                                                                                                    == 0
                                                                                                            ? -1
                                                                                                            : 1);
                                                                                    if (target >= 0
                                                                                            && target
                                                                                                    < next
                                                                                                            .size())
                                                                                        Collections
                                                                                                .swap(
                                                                                                        next,
                                                                                                        which,
                                                                                                        target);
                                                                                }
                                                                                store.rotation(
                                                                                        next,
                                                                                        state.rotationSeconds,
                                                                                        System
                                                                                                .currentTimeMillis());
                                                                            }
                                                                        });
                                                            }
                                                        })
                                                .show();
                                }
                            })
                    .show();
        } catch (Exception failure) {
            error(failure);
        }
    }

    private void addSlot() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("08:30");
        final AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Daily local time (HH:mm)")
                        .setView(input)
                        .setPositiveButton("Choose dashboard", null)
                        .setNegativeButton("Cancel", null)
                        .create();
        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface ignored) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                try {
                                                    final int minute =
                                                            LayoutSchedule.minute(
                                                                    input.getText()
                                                                            .toString()
                                                                            .trim());
                                                    dialog.dismiss();
                                                    chooseProfile(
                                                            new ProfileWork() {
                                                                @Override
                                                                public void run(String id)
                                                                        throws Exception {
                                                                    LayoutSchedule.State state =
                                                                            store.read();
                                                                    List<LayoutSchedule.Slot> next =
                                                                            new ArrayList<>(
                                                                                    state.slots);
                                                                    next.add(
                                                                            new LayoutSchedule.Slot(
                                                                                    minute, id));
                                                                    store.daily(state.zone, next);
                                                                }
                                                            });
                                                } catch (Exception failure) {
                                                    input.setError(failure.getMessage());
                                                }
                                            }
                                        });
                    }
                });
        dialog.show();
    }

    private void slotMenu(final LayoutSchedule.Slot slot) {
        new AlertDialog.Builder(this)
                .setTitle(slot.time())
                .setItems(
                        new String[] {"Change dashboard", "Remove"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0)
                                    chooseProfile(
                                            new ProfileWork() {
                                                @Override
                                                public void run(String id) throws Exception {
                                                    LayoutSchedule.State state = store.read();
                                                    List<LayoutSchedule.Slot> next =
                                                            new ArrayList<>();
                                                    for (LayoutSchedule.Slot existing : state.slots)
                                                        next.add(
                                                                existing.minute == slot.minute
                                                                        ? new LayoutSchedule.Slot(
                                                                                slot.minute, id)
                                                                        : existing);
                                                    store.daily(state.zone, next);
                                                }
                                            });
                                else
                                    change(
                                            new Work() {
                                                @Override
                                                public void run() throws Exception {
                                                    LayoutSchedule.State state = store.read();
                                                    List<LayoutSchedule.Slot> next =
                                                            new ArrayList<>();
                                                    for (LayoutSchedule.Slot existing : state.slots)
                                                        if (existing.minute != slot.minute)
                                                            next.add(existing);
                                                    store.daily(state.zone, next);
                                                }
                                            });
                            }
                        })
                .show();
    }

    private void chooseProfile(final ProfileWork work) {
        try {
            final LayoutSchedule.State state = store.read();
            if (state.profiles.isEmpty())
                throw new IllegalStateException("Save a dashboard first.");
            String[] names = new String[state.profiles.size()];
            for (int i = 0; i < names.length; i++) names[i] = state.profiles.get(i).name;
            new AlertDialog.Builder(this)
                    .setTitle("Choose dashboard")
                    .setItems(
                            names,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, final int which) {
                                    change(
                                            new Work() {
                                                @Override
                                                public void run() throws Exception {
                                                    work.run(state.profiles.get(which).id);
                                                }
                                            });
                                }
                            })
                    .show();
        } catch (Exception failure) {
            error(failure);
        }
    }

    private void prompt(String title, String previous, final TextWork action) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(previous);
        final AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setView(input)
                        .setPositiveButton("Save", null)
                        .setNegativeButton("Cancel", null)
                        .create();
        dialog.setOnShowListener(
                new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface ignored) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                try {
                                                    action.run(input.getText().toString());
                                                    dialog.dismiss();
                                                    refresh();
                                                } catch (Exception failure) {
                                                    input.setError(failure.getMessage());
                                                }
                                            }
                                        });
                    }
                });
        dialog.show();
    }

    private interface Work {
        void run() throws Exception;
    }

    private interface TextWork {
        void run(String value) throws Exception;
    }

    private interface ProfileWork {
        void run(String id) throws Exception;
    }

    private void change(Work work) {
        try {
            work.run();
            refresh();
        } catch (Exception failure) {
            error(failure);
        }
    }

    private void text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        rows.addView(text);
    }

    private void button(String value, final Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        action.run();
                    }
                });
        rows.addView(button);
    }

    private void error(Exception failure) {
        new AlertDialog.Builder(this)
                .setTitle("Dashboard unavailable")
                .setMessage(failure.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }
}
