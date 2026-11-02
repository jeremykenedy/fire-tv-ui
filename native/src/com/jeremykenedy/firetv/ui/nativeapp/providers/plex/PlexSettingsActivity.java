package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.Pages;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pairing and profile changes require explicit TV actions. Merely opening setup is local. */
public final class PlexSettingsActivity extends Activity {
    private boolean startupBlocked;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private TextView status;
    private EditText server;
    private PlexConnection connection;
    private PlexApi pairing;
    private long pin;
    private long request;
    private String pairingServer;
    private boolean busy;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.redirectIfBlocked(this)) { startupBlocked = true; return; }
        LinearLayout page = Pages.page(this);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        page.addView(scroll);
        render();
        task("Checking this device's saved connection...", new Job() {
            @Override public String run() throws Exception {
                connection = PlexConnection.load(PlexSettingsActivity.this);
                return connection == null ? "No Plex account connected on this device."
                        : "Saved encrypted connection available. Refresh to check server access.";
            }
        }, true);
    }
    private void render() {
        body.removeAllViews();
        body.addView(Pages.text(this, "Plex library cards", 28));
        body.addView(Pages.text(this, "Use your authorized Plex server for Continue Watching, new movies and episodes. Title actions open Plex details. Choose playback and the active playback profile in Plex.", 17));
        status = Pages.text(this, "Plex setup", 18);
        status.setTag("plex:status");
        body.addView(status);
        button(PlexRepository.enabled(this) ? "Turn Plex cards off" : "Enable Plex cards", "plex:enabled", new Runnable() {
            @Override public void run() {
                PlexRepository.setEnabled(PlexSettingsActivity.this, !PlexRepository.enabled(PlexSettingsActivity.this));
                render();
            }
        });
        if (connection != null) {
            body.addView(Pages.text(this, "Connected server: " + connection.server + "\nAuthorized extra profiles: " + connection.profiles.size(), 17));
            button("Refresh authorized library", "plex:refresh", new Runnable() {
                @Override public void run() {
                    task("Reading selected Plex feeds...", new Job() {
                        @Override public String run() { return PlexRepository.read(PlexSettingsActivity.this, null).message(); }
                    }, false);
                }
            });
            button("Choose feeds", "plex:feeds", new Runnable() {
                @Override public void run() { chooseFeeds(); }
            });
            button("Cards per feed: " + connection.feeds.cards, "plex:count", new Runnable() {
                @Override public void run() {
                    new AlertDialog.Builder(PlexSettingsActivity.this).setTitle("Cards per feed")
                            .setItems(new String[]{"4", "6", "8", "12"}, new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface dialog, int index) {
                                PlexFeedSettings feeds = connection.feeds;
                                save(connection.withFeeds(new PlexFeedSettings(feeds.enabled, feeds.users,
                                        new int[]{4, 6, 8, 12}[index])));
                            } }).setNegativeButton("Cancel", null).show();
                }
            });
            button("Add authorized Plex Home profile", "plex:profiles", new Runnable() {
                @Override public void run() { readProfiles(); }
            });
            for (final PlexConnection.Profile profile : connection.profiles) {
                button("Remove " + profile.name + " launcher feed", "plex:profile:" + profile.id, new Runnable() {
                    @Override public void run() {
                        List<PlexConnection.Profile> selected = new ArrayList<>(connection.profiles);
                        selected.remove(profile);
                        save(connection.withProfiles(selected));
                    }
                });
            }
            button("Disconnect launcher Plex connection", "plex:disconnect", new Runnable() {
                @Override public void run() {
                    new AlertDialog.Builder(PlexSettingsActivity.this).setTitle("Disconnect Plex?")
                            .setMessage("Remove this launcher's encrypted server connection and selected profiles. The Plex app account stays separate.")
                            .setPositiveButton("Disconnect", new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface dialog, int which) { task("Disconnecting...", new Job() {
                                @Override public String run() {
                                    try {
                                        if (!PlexSession.commit(request, new PlexSession.Change() {
                                            @Override public void apply() {
                                                PlexConnection.remove(PlexSettingsActivity.this);
                                                PlexRepository.setEnabled(PlexSettingsActivity.this, false);
                                            }
                                        })) throw new java.util.concurrent.CancellationException();
                                    } catch (Exception failed) { throw new IllegalStateException("Plex disconnect could not finish."); }
                                    connection = null;
                                    return "Launcher Plex connection removed.";
                                }
                            }, true); } }).setNegativeButton("Cancel", null).show();
                }
            });
        }
        body.addView(Pages.text(this, "Connect or replace server", 22));
        server = new EditText(this);
        server.setSingleLine(true);
        server.setHint("https://your-server:32400 or trusted LAN IP");
        server.setTextColor(android.graphics.Color.WHITE);
        server.setHintTextColor(0xffb8c0d0);
        server.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        server.setSaveEnabled(false);
        server.setTag("plex:server");
        body.addView(server);
        button("Request a new Plex approval code", "plex:pair", new Runnable() {
            @Override public void run() {
                final String entered;
                try { entered = PlexValues.serverBase(server.getText().toString()); }
                catch (IllegalArgumentException invalid) { status.setText(invalid.getMessage()); return; }
                task("Requesting Plex account approval...", new Job() {
                    @Override public String run() throws Exception {
                        pairing = new PlexApi(PlexSettingsActivity.this, request);
                        org.json.JSONObject result = pairing.createPin();
                        pin = result.getLong("id");
                        pairingServer = entered;
                        return "Visit https://plex.tv/link and approve code " + result.getString("code")
                                + ". Then choose Check approval. Existing connection stays until verified replacement.";
                    }
                }, false);
            }
        });
        button("Check approval and connect selected server", "plex:approval", new Runnable() {
            @Override public void run() {
                if (pairing == null || pin <= 0) { status.setText("Request and approve a new code first."); return; }
                task("Checking your approved Plex link...", new Job() {
                    @Override public String run() throws Exception {
                        // Use the current generation after the explicit approval action.
                        pairing = new PlexApi(PlexSettingsActivity.this, request);
                        String token = pairing.pollPin(pin);
                        if (token == null) return "Plex approval is pending. Approve the code before checking again.";
                        PlexConnection candidate = new PlexConnection(pairingServer, "");
                        String machine = pairing.server(candidate, "/identity")
                                .getJSONObject("MediaContainer").getString("machineIdentifier");
                        if (!machine.matches("[A-Za-z0-9_-]{1,128}"))
                            throw new java.io.IOException("Plex server identity is invalid.");
                        PlexConnection authorized = pairing.authorize(pairingServer, machine, token);
                        if (!PlexSession.commit(request, new PlexSession.Change() { @Override public void apply() throws Exception { authorized.save(PlexSettingsActivity.this); } }))
                            throw new java.util.concurrent.CancellationException();
                        connection = authorized;
                        pin = 0;
                        pairing = null;
                        PlexRepository.setEnabled(PlexSettingsActivity.this, true);
                        return "Plex connected. Refresh authorized library to check selected feeds.";
                    }
                }, true);
            }
        });
        body.addView(Pages.text(this, "Remote servers require HTTPS. HTTP is limited to numeric private LAN addresses allowed by this device's network policy. Credentials stay encrypted in this app's private storage and are excluded from portable backups. A preview package cannot read another package's account. Profile reads do not switch the Plex app playback profile.", 16));
    }
    private void button(String label, String tag, final Runnable action) {
        android.widget.Button button = Pages.button(this, label, new View.OnClickListener() {
            @Override public void onClick(View view) { if (!busy) action.run(); }
        });
        button.setTag(tag);
        body.addView(button);
    }
    private void chooseFeeds() {
        final boolean[] selected = new boolean[PlexFeedSettings.KEYS.length];
        for (int i = 0; i < selected.length; i++) selected[i] = connection.feeds.enabled.contains(PlexFeedSettings.KEYS[i]);
        new AlertDialog.Builder(this).setTitle("Authorized library feeds")
                .setMultiChoiceItems(PlexFeedSettings.LABELS, selected, new android.content.DialogInterface.OnMultiChoiceClickListener() { @Override public void onClick(android.content.DialogInterface dialog, int which, boolean checked) { selected[which] = checked; } })
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface dialog, int which) {
                    LinkedHashSet<String> enabled = new LinkedHashSet<>();
                    for (int i = 0; i < selected.length; i++) if (selected[i]) enabled.add(PlexFeedSettings.KEYS[i]);
                    save(connection.withFeeds(new PlexFeedSettings(enabled, connection.feeds.users, connection.feeds.cards)));
                } }).setNegativeButton("Cancel", null).show();
    }
    private void save(final PlexConnection selected) {
        task("Saving Plex choices...", new Job() {
            @Override public String run() throws Exception {
                if (!PlexSession.commit(request, new PlexSession.Change() { @Override public void apply() throws Exception { selected.save(PlexSettingsActivity.this); } }))
                    throw new java.util.concurrent.CancellationException();
                connection = selected;
                return "Plex feed choices saved.";
            }
        }, true);
    }
    private void readProfiles() {
        task("Reading authorized Home profiles...", new Job() {
            @Override public String run() throws Exception {
                final List<PlexHomeUsers.User> profiles = new PlexApi(PlexSettingsActivity.this, request).homeUsers();
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (isDestroyed()) return;
                    String[] names = new String[profiles.size()];
                    for (int i = 0; i < names.length; i++) names[i] = profiles.get(i).name;
                    new AlertDialog.Builder(PlexSettingsActivity.this).setTitle("Choose authorized Home profile")
                            .setItems(names, new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface dialog, int index) { authorizeProfile(profiles.get(index)); } })
                            .setNegativeButton("Cancel", null).show();
                } });
                return "Choose a profile. Protected profiles require their own PIN.";
            }
        }, false);
    }
    private void authorizeProfile(final PlexHomeUsers.User user) {
        final EditText password = new EditText(this);
        password.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        password.setSaveEnabled(false);
        password.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Authorize " + user.name + " launcher feed?");
        if (user.protectedByPin) dialog.setView(password);
        dialog.setPositiveButton("Authorize", new android.content.DialogInterface.OnClickListener() { @Override public void onClick(android.content.DialogInterface d, int which) {
            final String entered = password.getText().toString();
            password.setText("");
            task("Authorizing selected Plex Home profile...", new Job() {
                @Override public String run() throws Exception {
                    PlexConnection authorized = new PlexApi(PlexSettingsActivity.this, request)
                            .authorizeHome(connection.server, connection.machine, user.id, entered);
                    List<PlexConnection.Profile> selected = new ArrayList<>(connection.profiles);
                    for (int i = selected.size() - 1; i >= 0; i--) if (selected.get(i).id.equals(user.id)) selected.remove(i);
                    selected.add(new PlexConnection.Profile(user.id, user.name, authorized.token));
                    PlexConnection updated = connection.withProfiles(selected);
                    if (!PlexSession.commit(request, new PlexSession.Change() { @Override public void apply() throws Exception { updated.save(PlexSettingsActivity.this); } }))
                        throw new java.util.concurrent.CancellationException();
                    connection = updated;
                    return "Authorized profile feed saved. Playback profile selection remains in Plex.";
                }
            }, true);
        } }).setNegativeButton("Cancel", null).show();
    }
    private interface Job { String run() throws Exception; }
    private void task(String message, final Job job, final boolean redraw) {
        if (busy) return;
        busy = true;
        request = PlexSession.begin();
        final long generation = request;
        status.setText(message);
        worker.execute(new Runnable() { @Override public void run() {
            String outcome;
            try { outcome = job.run(); }
            catch (PlexApi.TransportUnavailable policy) { outcome = policy.getMessage(); }
            catch (Exception failed) { outcome = "Plex could not finish this action. Check server access and account approval. Retry without clearing your saved connection."; }
            final String result = outcome;
            runOnUiThread(new Runnable() { @Override public void run() {
                if (isDestroyed() || !PlexSession.current(generation)) return;
                busy = false;
                if (redraw) render();
                status.setText(result);
            } });
        } });
    }
    @Override protected void onDestroy() {
        if (startupBlocked) { super.onDestroy(); return; }
        PlexSession.cancel(request);
        worker.shutdownNow();
        super.onDestroy();
    }
}
