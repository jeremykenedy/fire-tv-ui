package com.jeremykenedy.firetv.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

public final class PlexCardsActivity extends CardActivity {
    private EditText address;
    private TextView status;
    private TextView connect;
    private TextView refresh;
    private TextView recheck;
    private TextView completionFocus;
    private boolean destroyed;
    private long activeRequest;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Your Plex library cards");
        LinearLayout body = scrollBody();
        body.addView(
                text(
                        "Connect your own Plex server for Continue Watching, new movies and new TV"
                            + " episodes. This is separate from cards shared by the installed Plex"
                            + " app. Credentials stay encrypted on this TV and are excluded from"
                            + " backups.",
                        18,
                        MUTED));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(INK);
        address.setHintTextColor(MUTED);
        address.setTextSize(18);
        android.graphics.drawable.StateListDrawable inputBackground =
                new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable focusedInput =
                background(android.graphics.Color.rgb(30, 39, 48), 8);
        focusedInput.setStroke(dp(2), INK);
        inputBackground.addState(new int[] {android.R.attr.state_focused}, focusedInput);
        inputBackground.addState(new int[0], background(android.graphics.Color.rgb(30, 39, 48), 8));
        address.setBackground(inputBackground);
        address.setPadding(dp(12), dp(8), dp(12), dp(8));
        address.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint("http://your-server:32400");
        body.addView(address);
        status =
                text(
                        "Enter your server address and connect. Plex will ask you to approve a code"
                                + " using your own account.",
                        18,
                        INK);
        body.addView(status);
        connect =
                button(
                        "Connect to Plex",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                pair();
                            }
                        });
        addButton(body, connect);
        recheck =
                button(
                        "Recheck approved link",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                recheck();
                            }
                        });
        addButton(body, recheck);
        refresh =
                button(
                        "Refresh my library cards",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                refresh();
                            }
                        });
        addButton(body, refresh);
        addButton(
                body,
                button(
                        "Library feeds and activity users",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                startActivity(
                                        new android.content.Intent(
                                                PlexCardsActivity.this,
                                                PlexFeedSettingsActivity.class));
                            }
                        }));
        addButton(
                body,
                button(
                        "Disconnect this TV's library cards",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                new AlertDialog.Builder(PlexCardsActivity.this)
                                        .setTitle("Disconnect Plex library cards?")
                                        .setMessage(
                                                "Remove this TV's saved connection and its personal"
                                                    + " Plex cards. The Plex app and your server"
                                                    + " library stay unchanged.")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton(
                                                "Disconnect",
                                                new android.content.DialogInterface
                                                        .OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            android.content.DialogInterface dialog,
                                                            int which) {
                                                        disconnect();
                                                    }
                                                })
                                        .show();
                            }
                        }));
        connect.requestFocus();
        try {
            PlexConnection saved = PlexConnection.load(PlexCardsActivity.this);
            if (saved != null) {
                address.setText(saved.server);
                status.setText(
                        "A private Plex connection is saved on this TV. Refresh to check the server"
                                + " and update your library cards.");
            }
        } catch (Exception error) {
            status.setText(
                    "This TV's encrypted Plex connection could not be read. Connect again to"
                            + " replace it.");
        }
    }

    private void pair() {
        completionFocus = connect;
        final String server;
        try {
            server = PlexValues.serverBase(address.getText().toString());
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            return;
        }
        final long request = activeRequest = PlexSession.begin();
        busy(true);
        show(request, "Checking your Plex server...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PlexApi api = new PlexApi(PlexCardsActivity.this, request);
                                    JSONObject identity =
                                            api.server(
                                                            new PlexConnection(server, null),
                                                            "/identity")
                                                    .getJSONObject("MediaContainer");
                                    String machine = identity.getString("machineIdentifier");
                                    if (!PlexSession.current(request)) return;
                                    JSONObject pin = api.createPin();
                                    long id = pin.getLong("id");
                                    String code = pin.getString("code");
                                    if (!code.matches("[A-Za-z0-9]{4,16}"))
                                        throw new java.io.IOException(
                                                "Plex returned an unsupported pairing code.");
                                    show(
                                            request,
                                            "On your phone or computer, open plex.tv/link and enter"
                                                    + " "
                                                    + code
                                                    + ". Approve Fire TV UI. Keep this screen open;"
                                                    + " the code expires in 15 minutes.");
                                    long deadline =
                                            System.currentTimeMillis()
                                                    + Math.min(
                                                                    900,
                                                                    Math.max(
                                                                            1,
                                                                            pin.optInt(
                                                                                    "expiresIn",
                                                                                    900)))
                                                            * 1000L;
                                    while (PlexSession.current(request)
                                            && System.currentTimeMillis() < deadline) {
                                        Thread.sleep(5000);
                                        if (!PlexSession.current(request)) return;
                                        String token = api.pollPin(id);
                                        if (token == null) continue;
                                        if (!PlexSession.current(request)) return;
                                        PlexConnection connection =
                                                api.authorize(server, machine, token);
                                        synchronize(api, connection, request);
                                        return;
                                    }
                                    show(
                                            request,
                                            "The pairing code expired. Connect again for a new"
                                                    + " code.");
                                } catch (PlexApi.RequestFailure error) {
                                    show(
                                            request,
                                            error.getMessage()
                                                    + " Use Recheck approved link if you already"
                                                    + " approved this TV.");
                                } catch (Exception error) {
                                    show(
                                            request,
                                            "Plex could not finish connecting. Check the server and"
                                                + " retry. Any previous saved connection has been"
                                                + " kept.");
                                } finally {
                                    finishRequest(request);
                                }
                            }
                        },
                        "pair-personal-plex")
                .start();
    }

    private void recheck() {
        completionFocus = recheck;
        final String server;
        try {
            server = PlexValues.serverBase(address.getText().toString());
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            return;
        }
        final long request = activeRequest = PlexSession.begin();
        busy(true);
        show(request, "Rechecking this TV's approved Plex link without requesting a new code...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PlexApi api = new PlexApi(PlexCardsActivity.this, request);
                                    String machine =
                                            api.server(
                                                            new PlexConnection(server, null),
                                                            "/identity")
                                                    .getJSONObject("MediaContainer")
                                                    .getString("machineIdentifier");
                                    String accountToken = api.refreshToken();
                                    if (!PlexSession.current(request)) return;
                                    synchronize(
                                            api,
                                            api.authorize(server, machine, accountToken),
                                            request);
                                } catch (PlexApi.RequestFailure error) {
                                    show(
                                            request,
                                            error.getMessage()
                                                    + " Your previous saved connection has been"
                                                    + " kept.");
                                } catch (Exception error) {
                                    show(
                                            request,
                                            "The approved link could not finish connecting to this"
                                                + " server. Check its address and account access,"
                                                + " then retry. Your previous saved connection has"
                                                + " been kept.");
                                } finally {
                                    finishRequest(request);
                                }
                            }
                        },
                        "recheck-approved-plex")
                .start();
    }

    private void refresh() {
        completionFocus = refresh;
        final long request = activeRequest = PlexSession.begin();
        busy(true);
        show(request, "Refreshing your Plex library cards...");
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PlexConnection connection =
                                            PlexConnection.load(PlexCardsActivity.this);
                                    if (connection == null) {
                                        show(request, "Connect your own Plex server first.");
                                        return;
                                    }
                                    PlexApi api = new PlexApi(PlexCardsActivity.this, request);
                                    try {
                                        synchronize(api, connection, request);
                                    } catch (PlexApi.AuthorizationRequired expired) {
                                        String token = api.refreshToken();
                                        synchronize(
                                                api,
                                                api.authorize(
                                                        connection.server,
                                                        connection.machine,
                                                        token),
                                                request);
                                    }
                                } catch (PlexApi.RequestFailure error) {
                                    show(
                                            request,
                                            error.getMessage()
                                                    + " Existing cards and the saved connection"
                                                    + " have been kept.");
                                } catch (Exception error) {
                                    show(
                                            request,
                                            "Plex cards could not finish updating. Your saved"
                                                    + " connection has not been cleared. Check the"
                                                    + " server and retry.");
                                } finally {
                                    finishRequest(request);
                                }
                            }
                        },
                        "refresh-personal-plex")
                .start();
    }

    private void synchronize(PlexApi api, PlexConnection connection, long request)
            throws Exception {
        if (!PlexSession.current(request)) return;
        PlexConnection saved = PlexConnection.load(this);
        if (saved != null
                && saved.machine.equals(connection.machine)
                && saved.server.equals(connection.server))
            connection = connection.withFeeds(saved.feeds);
        final PlexConnection selected = connection;
        List<PlexFeedData.Feed> feeds =
                PlexFeedData.read(
                        new PlexFeedData.Reader() {
                            @Override
                            public org.json.JSONObject read(String path, int count)
                                    throws Exception {
                                if (!PlexSession.current(request))
                                    throw new java.io.IOException("Refresh was cancelled.");
                                return api.server(selected, path, count);
                            }
                        },
                        connection.feeds);
        if (!PlexSession.current(request)) return;
        int count = PlexCards.publish(this, api, connection, feeds, request);
        if (count < 0) return;
        show(
                request,
                count == 0
                        ? "Connected. Your selected feeds currently have no matching cards."
                        : "Updated "
                                + count
                                + " real Plex library cards. Return to Card sources and reload home"
                                + " cards to show them.");
    }

    private void disconnect() {
        long request = activeRequest = PlexSession.begin();
        try {
            PlexSession.commit(
                    request,
                    new PlexSession.Change() {
                        @Override
                        public void apply() throws Exception {
                            PlexConnection.remove(PlexCardsActivity.this);
                            PlexCards.remove(PlexCardsActivity.this);
                            PlexKeys.reset(PlexCardsActivity.this);
                        }
                    });
            status.setText(
                    "Disconnected. This TV's private Plex connection and personal library cards"
                            + " have been removed.");
            busy(false);
        } catch (Exception error) {
            status.setText(
                    "The Plex connection was removed, but some card cleanup could not finish. Retry"
                            + " Disconnect.");
        }
    }

    private void show(long request, String value) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed && PlexSession.current(request)) status.setText(value);
                    }
                });
    }

    private void finishRequest(long request) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed && PlexSession.current(request)) {
                            busy(false);
                            if (completionFocus != null) completionFocus.requestFocus();
                        }
                    }
                });
    }

    private void busy(boolean value) {
        connect.setClickable(!value);
        refresh.setClickable(!value);
        recheck.setClickable(!value);
        address.setEnabled(!value);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        PlexSession.cancel(activeRequest);
        super.onDestroy();
    }
}
