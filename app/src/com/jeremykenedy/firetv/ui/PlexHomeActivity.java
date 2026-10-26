package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class PlexHomeActivity extends CardActivity {
    private LinearLayout body;
    private TextView status;
    private long request;
    private boolean busy;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Plex Home Continue Watching");
        body = scrollBody();
        render();
    }

    private void render() {
        body.removeAllViews();
        body.addView(
                text(
                        "Add one or more authorized Plex Home profiles. Each profile gets its own"
                            + " real Continue Watching feed. This does not change the profile"
                            + " selected in the Plex app or another user's settings. A protected"
                            + " profile needs its own PIN.",
                        18,
                        MUTED));
        try {
            PlexConnection connection = PlexConnection.load(PlexHomeActivity.this);
            if (connection == null) {
                body.addView(text("Connect your Plex server first.", 18, MUTED));
                return;
            }
            for (final PlexConnection.Profile profile : connection.profiles) {
                addButton(
                        body,
                        button(
                                "Remove " + profile.name + " Continue Watching",
                                new android.view.View.OnClickListener() {
                                    @Override
                                    public void onClick(android.view.View view) {
                                        if (busy) return;
                                        long change = request = PlexSession.begin();
                                        try {
                                            PlexConnection current =
                                                    PlexConnection.load(PlexHomeActivity.this);
                                            if (current == null
                                                    || !current.sourceKey()
                                                            .equals(connection.sourceKey()))
                                                throw new IllegalStateException();
                                            List<PlexConnection.Profile> selected =
                                                    new ArrayList<>(current.profiles);
                                            for (int i = selected.size() - 1; i >= 0; i--)
                                                if (selected.get(i).id.equals(profile.id))
                                                    selected.remove(i);
                                            if (PlexSession.commit(
                                                    change,
                                                    new PlexSession.Change() {
                                                        @Override
                                                        public void apply() throws Exception {
                                                            current.withProfiles(selected)
                                                                    .save(PlexHomeActivity.this);
                                                        }
                                                    })) {
                                                render();
                                                status.setText(
                                                        "Removed this profile from launcher feeds."
                                                            + " Refresh library cards to apply.");
                                            }
                                        } catch (Exception unavailable) {
                                            status.setText(
                                                    "This change could not be saved. Existing"
                                                        + " profiles were kept.");
                                        }
                                    }
                                }));
            }
            addButton(
                    body,
                    button(
                            "Add a Plex Home profile",
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(android.view.View view) {
                                    readUsers();
                                }
                            }));
            addButton(
                    body,
                    button(
                            "Back to feed settings",
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(android.view.View view) {
                                    finish();
                                }
                            }));
            status =
                    text(
                            "After changing profiles, refresh library cards. Account credentials"
                                + " stay encrypted on this TV and are excluded from backups.",
                            17,
                            MUTED);
            body.addView(status);
        } catch (Exception unavailable) {
            body.addView(
                    text(
                            "This TV's Plex connection could not be read. Recheck its approved"
                                + " link.",
                            18,
                            MUTED));
        }
    }

    private void readUsers() {
        if (busy) return;
        busy = true;
        final long reading = request = PlexSession.begin();
        status.setText("Reading the profiles authorized by your Plex Home...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    final List<PlexHomeUsers.User> users =
                                            new PlexApi(PlexHomeActivity.this, reading).homeUsers();
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!active(reading)) return;
                                                    busy = false;
                                                    if (users.isEmpty()) {
                                                        status.setText(
                                                                "This account has no available Plex"
                                                                    + " Home profiles.");
                                                        return;
                                                    }
                                                    String[] labels = new String[users.size()];
                                                    for (int i = 0; i < labels.length; i++)
                                                        labels[i] =
                                                                users.get(i).name
                                                                        + (users.get(i)
                                                                                        .protectedByPin
                                                                                ? " (PIN required)"
                                                                                : "");
                                                    new AlertDialog.Builder(PlexHomeActivity.this)
                                                            .setTitle("Choose a Plex Home profile")
                                                            .setItems(
                                                                    labels,
                                                                    new android.content
                                                                            .DialogInterface
                                                                            .OnClickListener() {
                                                                        @Override
                                                                        public void onClick(
                                                                                android.content
                                                                                                .DialogInterface
                                                                                        dialog,
                                                                                int index) {
                                                                            confirm(
                                                                                    users.get(
                                                                                            index));
                                                                        }
                                                                    })
                                                            .setNegativeButton("Cancel", null)
                                                            .show();
                                                }
                                            });
                                } catch (Exception unavailable) {
                                    failed(
                                            reading,
                                            "Plex Home profiles could not be read. Recheck the"
                                                + " approved account link.");
                                }
                            }
                        },
                        "read-plex-home")
                .start();
    }

    private void confirm(final PlexHomeUsers.User user) {
        AlertDialog.Builder dialog =
                new AlertDialog.Builder(this)
                        .setTitle("Add " + user.name + " Continue Watching?")
                        .setNegativeButton("Cancel", null);
        if (user.protectedByPin) {
            final EditText pin = new EditText(this);
            pin.setTextColor(INK);
            pin.setBackgroundColor(android.graphics.Color.rgb(45, 45, 45));
            pin.setHintTextColor(MUTED);
            pin.setHint("This profile's own PIN");
            pin.setInputType(
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            pin.setFilters(
                    new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(4)});
            pin.setSaveEnabled(false);
            dialog.setView(pin)
                    .setPositiveButton(
                            "Authorize profile",
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        android.content.DialogInterface dialog, int which) {
                                    String entered = pin.getText().toString();
                                    pin.setText("");
                                    authorize(user, entered);
                                }
                            });
        } else {
            dialog.setMessage(
                            "Plex will authorize this profile using this TV's approved Home"
                                + " account.")
                    .setPositiveButton(
                            "Add profile",
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(
                                        android.content.DialogInterface dialog, int which) {
                                    authorize(user, null);
                                }
                            });
        }
        dialog.show();
    }

    private void authorize(final PlexHomeUsers.User user, final String pin) {
        if (busy) return;
        busy = true;
        final long adding = request = PlexSession.begin();
        status.setText("Checking this profile's access to your exact server...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PlexConnection current =
                                            PlexConnection.load(PlexHomeActivity.this);
                                    if (current == null) throw new IllegalStateException();
                                    PlexConnection approved =
                                            new PlexApi(PlexHomeActivity.this, adding)
                                                    .authorizeHome(
                                                            current.server,
                                                            current.machine,
                                                            user.id,
                                                            pin);
                                    if (approved.token.equals(current.token))
                                        throw new java.io.IOException(
                                                "Plex did not return separate profile access.");
                                    List<PlexConnection.Profile> selected =
                                            new ArrayList<>(current.profiles);
                                    for (int i = selected.size() - 1; i >= 0; i--)
                                        if (selected.get(i).id.equals(user.id)) selected.remove(i);
                                    selected.add(
                                            new PlexConnection.Profile(
                                                    user.id, user.name, approved.token));
                                    final PlexConnection changed = current.withProfiles(selected);
                                    boolean saved =
                                            PlexSession.commit(
                                                    adding,
                                                    new PlexSession.Change() {
                                                        @Override
                                                        public void apply() throws Exception {
                                                            changed.save(PlexHomeActivity.this);
                                                        }
                                                    });
                                    if (!saved) return;
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!active(adding)) return;
                                                    busy = false;
                                                    render();
                                                    status.setText(
                                                            "Profile authorized for this server."
                                                                + " Refresh library cards to show"
                                                                + " its Continue Watching.");
                                                }
                                            });
                                } catch (Exception unavailable) {
                                    failed(
                                            adding,
                                            "This profile was not added. Check its own PIN and"
                                                + " server access. Existing profiles were kept.");
                                }
                            }
                        },
                        "authorize-plex-home")
                .start();
    }

    private boolean active(long generation) {
        return !isFinishing() && !isDestroyed() && PlexSession.current(generation);
    }

    private void failed(final long generation, final String message) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!active(generation)) return;
                        busy = false;
                        status.setText(message);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        PlexSession.cancel(request);
        super.onDestroy();
    }
}
