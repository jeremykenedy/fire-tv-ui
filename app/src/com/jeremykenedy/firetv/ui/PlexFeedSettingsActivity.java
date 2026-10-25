package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlexFeedSettingsActivity extends CardActivity {
    private LinearLayout body;
    private TextView status;
    private PlexConnection connection;
    private PlexFeedSettings settings;
    private boolean reading;
    private long activeRequest;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Plex library feeds");
        body = scrollBody();
        try {
            connection = PlexConnection.load(this);
            if (connection == null) {
                body.addView(text("Connect your own Plex server first.", 18, MUTED));
                return;
            }
            settings = connection.feeds;
            render();
        } catch (Exception unavailable) {
            body.addView(
                    text(
                            "This TV's private Plex settings could not be read. Recheck the"
                                    + " approved link without clearing it.",
                            18,
                            MUTED));
        }
    }

    private void render() {
        body.removeAllViews();
        body.addView(
                text(
                        "Choose the library feeds shown on Home. These settings stay private to"
                                + " this TV and server. Refresh library cards after changes.",
                        18,
                        MUTED));
        for (int i = 0; i < PlexFeedSettings.KEYS.length; i++) {
            final String key = PlexFeedSettings.KEYS[i];
            final String label = PlexFeedSettings.LABELS[i];
            addButton(
                    body,
                    button(
                            label + ": " + (settings.enabled.contains(key) ? "On" : "Off"),
                            new android.view.View.OnClickListener() {
                                @Override
                                public void onClick(android.view.View view) {
                                    Set<String> enabled = new LinkedHashSet<>(settings.enabled);
                                    if (!enabled.remove(key)) enabled.add(key);
                                    if (save(
                                            new PlexFeedSettings(
                                                    enabled, settings.users, settings.cards)))
                                        ((TextView) view)
                                                .setText(
                                                        label
                                                                + ": "
                                                                + (enabled.contains(key)
                                                                        ? "On"
                                                                        : "Off"));
                                }
                            }));
        }
        body.addView(
                text(
                        "Continue Watching uses the authorized Plex account. Activity user filters"
                            + " apply only to Currently playing and Recently watched. They do not"
                            + " switch a Plex profile or unlock another user's Continue Watching.",
                        17,
                        MUTED));
        addButton(
                body,
                button(
                        "Other users' Continue Watching",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                startActivity(
                                        new android.content.Intent(
                                                PlexFeedSettingsActivity.this,
                                                PlexHomeActivity.class));
                            }
                        }));
        addButton(
                body,
                button(
                        "Activity users: "
                                + (settings.users.isEmpty()
                                        ? "All available"
                                        : settings.users.size() + " selected"),
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                findUsers((TextView) view);
                            }
                        }));
        addButton(
                body,
                button(
                        "Per-feed card limit: " + settings.cards,
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                cardLimit((TextView) view);
                            }
                        }));
        body.addView(
                text(
                        "Up to 24 cards are shared across enabled feeds. Activity is limited to"
                            + " what your server permits; administrators may see all users, while"
                            + " other accounts see their own activity.",
                        17,
                        MUTED));
        addButton(
                body,
                button(
                        "Back to connection and refresh",
                        new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View view) {
                                finish();
                            }
                        }));
        status = text("Changes are saved privately on this TV.", 17, MUTED);
        body.addView(status);
    }

    private boolean save(PlexFeedSettings next) {
        long request = PlexSession.begin();
        try {
            PlexConnection current = PlexConnection.load(this);
            if (current == null || !current.sourceKey().equals(connection.sourceKey()))
                throw new IllegalStateException(
                        "The Plex connection changed. Reopen feed settings.");
            boolean saved =
                    PlexSession.commit(
                            request,
                            new PlexSession.Change() {
                                @Override
                                public void apply() throws Exception {
                                    current.withFeeds(next).save(PlexFeedSettingsActivity.this);
                                }
                            });
            if (!saved) return false;
            settings = next;
            status.setText(
                    "Saved. Up to "
                            + next.limit()
                            + " cards per enabled feed. Refresh library cards to apply your"
                            + " choices.");
            return true;
        } catch (Exception unavailable) {
            status.setText("These changes could not be saved. The previous settings were kept.");
            return false;
        }
    }

    private void cardLimit(TextView action) {
        int[] choices = {4, 6, 8, 12};
        String[] labels = {"4 cards", "6 cards", "8 cards", "12 cards"};
        int selected = 0;
        for (int i = 0; i < choices.length; i++) if (settings.cards == choices[i]) selected = i;
        new AlertDialog.Builder(this)
                .setTitle("Cards per feed")
                .setSingleChoiceItems(
                        labels,
                        selected,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (save(
                                        new PlexFeedSettings(
                                                settings.enabled,
                                                settings.users,
                                                choices[which]))) {
                                    action.setText("Per-feed card limit: " + settings.cards);
                                    dialog.dismiss();
                                }
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void findUsers(TextView action) {
        if (reading) return;
        reading = true;
        final long request = activeRequest = PlexSession.begin();
        status.setText("Reading users exposed by your server and its recent activity...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PlexConnection current =
                                            PlexConnection.load(PlexFeedSettingsActivity.this);
                                    if (current == null
                                            || !current.sourceKey().equals(connection.sourceKey()))
                                        throw new IllegalStateException();
                                    PlexApi api =
                                            new PlexApi(PlexFeedSettingsActivity.this, request);
                                    Map<String, String> users = new LinkedHashMap<>();
                                    try {
                                        users.putAll(
                                                PlexFeedData.accounts(
                                                        api.server(current, "/accounts", 100)));
                                    } catch (PlexApi.RequestFailure unavailable) {
                                        /* Recent activity remains available independently. */
                                    }
                                    int failed = 0;
                                    for (String path :
                                            new String[] {
                                                "/status/sessions/history/all?sort=viewedAt:desc",
                                                "/status/sessions"
                                            }) {
                                        try {
                                            users.putAll(
                                                    PlexFeedData.users(
                                                            PlexFeedData.namedActivity(
                                                                    api.server(current, path, 100),
                                                                    users)));
                                        } catch (PlexApi.RequestFailure unavailable) {
                                            failed++;
                                        }
                                    }
                                    if (failed == 2 && users.isEmpty())
                                        throw new java.io.IOException();
                                    final boolean partial = failed != 0;
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    reading = false;
                                                    if (isFinishing()
                                                            || isDestroyed()
                                                            || !PlexSession.current(request))
                                                        return;
                                                    chooseUsers(users, action, partial);
                                                }
                                            });
                                } catch (Exception unavailable) {
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    reading = false;
                                                    if (!isFinishing()
                                                            && !isDestroyed()
                                                            && PlexSession.current(request))
                                                        status.setText(
                                                                "Activity users could not be read"
                                                                    + " with this connection."
                                                                    + " Existing choices were kept."
                                                                    + " Try refreshing library"
                                                                    + " cards first.");
                                                }
                                            });
                                }
                            }
                        },
                        "read-authorized-plex-users")
                .start();
    }

    @Override
    protected void onDestroy() {
        PlexSession.cancel(activeRequest);
        super.onDestroy();
    }

    private void chooseUsers(Map<String, String> available, TextView action, boolean partial) {
        for (String selected : settings.users)
            if (!available.containsKey(selected))
                available.put(selected, "Previously selected user " + selected);
        if (available.isEmpty()) {
            status.setText(
                    partial
                            ? "Some activity could not be read, and no available users were found."
                                    + " Existing choices were kept."
                            : "The server has not exposed any recent activity users. Existing"
                                    + " choices were kept.");
            return;
        }
        List<String> ids = new ArrayList<>(available.keySet());
        String[] labels = new String[ids.size()];
        boolean[] checked = new boolean[ids.size()];
        Set<String> selected = new LinkedHashSet<>(settings.users);
        for (int i = 0; i < ids.size(); i++) {
            labels[i] = available.get(ids.get(i));
            checked[i] = selected.contains(ids.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle("Activity users")
                .setMultiChoiceItems(
                        labels,
                        checked,
                        new android.content.DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(
                                    android.content.DialogInterface dialog,
                                    int which,
                                    boolean checked) {
                                if (checked) selected.add(ids.get(which));
                                else selected.remove(ids.get(which));
                            }
                        })
                .setPositiveButton(
                        "Save selection",
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (selected.size() > 32) {
                                    status.setText(
                                            "Select up to 32 users, or choose All available.");
                                    return;
                                }
                                if (selected.isEmpty()) {
                                    status.setText(
                                            "Select at least one user, or choose All available.");
                                    return;
                                }
                                if (save(
                                        new PlexFeedSettings(
                                                settings.enabled, selected, settings.cards)))
                                    action.setText(
                                            "Activity users: "
                                                    + (settings.users.isEmpty()
                                                            ? "All available"
                                                            : settings.users.size() + " selected"));
                            }
                        })
                .setNeutralButton(
                        "All available",
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (save(
                                        new PlexFeedSettings(
                                                settings.enabled,
                                                java.util.Collections.<String>emptySet(),
                                                settings.cards)))
                                    action.setText("Activity users: All available");
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
        status.setText(
                (partial ? "Some activity could not be read. " : "")
                        + "Names appear when supplied by active sessions. Choices filter activity"
                        + " only; no profile is switched.");
    }
}
