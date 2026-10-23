package com.jeremykenedy.firetv.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public final class PlexItemActivity extends CardActivity {
    private TextView details;
    private TextView open;
    private TextView resume;
    private String source;
    private String id;
    private long request;
    private boolean reading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Your Plex library item");
        LinearLayout body = scrollBody();
        details = text("Reading this item from your Plex server...", 18, INK);
        body.addView(details);
        open =
                button(
                        "Open item in Plex",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                readItem(true, false);
                            }
                        });
        resume =
                button(
                        "Resume in Plex",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                readItem(true, true);
                            }
                        });
        addButton(body, open);
        addButton(body, resume);
        open.setVisibility(View.GONE);
        resume.setVisibility(View.GONE);
        Uri uri = getIntent().getData();
        if (uri == null
                || !"firetvui".equals(uri.getScheme())
                || !"plex".equals(uri.getAuthority())
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !PlexValues.itemPath(uri.getEncodedPath())) {
            details.setText("This Plex card is invalid. Refresh your library cards.");
            return;
        }
        source = uri.getPathSegments().get(1);
        id = uri.getPathSegments().get(2);
        request = PlexSession.begin();
        readItem(false, false);
    }

    private void readItem(boolean launch, boolean resumePlayback) {
        if (reading || !PlexSession.current(request)) return;
        reading = true;
        open.setVisibility(View.GONE);
        resume.setVisibility(View.GONE);
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PlexConnection connection =
                                            PlexConnection.load(PlexItemActivity.this);
                                    connection =
                                            connection == null
                                                    ? null
                                                    : connection.forSource(source);
                                    if (connection == null) {
                                        showError(
                                                "This card belongs to a previous server connection."
                                                        + " Refresh your Plex library cards.");
                                        return;
                                    }
                                    PlexApi api = new PlexApi(PlexItemActivity.this, request);
                                    String machine =
                                            api.server(connection, "/identity")
                                                    .getJSONObject("MediaContainer")
                                                    .getString("machineIdentifier");
                                    if (connection.machine.isEmpty()
                                            || !connection.machine.equals(machine)) {
                                        showError(
                                                "The Plex server identity changed. Reconnect your"
                                                        + " server before opening this item.");
                                        return;
                                    }
                                    List<PlexLibrary.Item> items =
                                            PlexLibrary.items(
                                                    api.server(
                                                            connection, "/library/metadata/" + id));
                                    if (items.isEmpty() || !id.equals(items.get(0).id)) {
                                        showError(
                                                "This item is no longer available on your server."
                                                        + " Refresh your Plex library cards.");
                                        return;
                                    }
                                    PlexLibrary.Item item = items.get(0);
                                    String detailsLink = PlexItemLinks.details(machine, item);
                                    String resumeLink = PlexItemLinks.resume(machine, item);
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!active()) return;
                                                    reading = false;
                                                    details.setText(
                                                            item.title + "\n\n" + item.summary);
                                                    open.setVisibility(
                                                            detailsLink == null
                                                                    ? View.GONE
                                                                    : View.VISIBLE);
                                                    resume.setVisibility(
                                                            resumeLink == null
                                                                    ? View.GONE
                                                                    : View.VISIBLE);
                                                    if (detailsLink == null) {
                                                        details.append(
                                                                "\n\n"
                                                                    + "This item cannot be opened"
                                                                    + " in Plex. Refresh your"
                                                                    + " library cards.");
                                                    } else if (launch) {
                                                        String link =
                                                                resumePlayback
                                                                        ? resumeLink
                                                                        : detailsLink;
                                                        if (link == null) {
                                                            details.append(
                                                                    "\n\n"
                                                                        + "This item no longer has"
                                                                        + " a saved playback"
                                                                        + " position to resume.");
                                                        } else {
                                                            openItem(link);
                                                        }
                                                    }
                                                }
                                            });
                                } catch (Exception error) {
                                    showError(
                                            "This Plex item could not be read. Check your server or"
                                                    + " refresh your Plex connection.");
                                }
                            }
                        },
                        "read-plex-card-item")
                .start();
    }

    private void openItem(String link) {
        try {
            PlexSession.commit(
                    request,
                    new PlexSession.Change() {
                        @Override
                        public void apply() {
                            startActivity(
                                    new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                            .setPackage("com.plexapp.android"));
                        }
                    });
        } catch (ActivityNotFoundException unavailable) {
            details.append("\n\nInstall or update Plex on this TV to open this item.");
        } catch (Exception unavailable) {
            details.append("\n\nPlex could not open this item. Your library connection is saved.");
        }
    }

    private boolean active() {
        return !isFinishing() && !isDestroyed() && PlexSession.current(request);
    }

    private void showError(String value) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!active()) return;
                        reading = false;
                        details.setText(value);
                    }
                });
    }

    @Override
    protected void onDestroy() {
        PlexSession.cancel(request);
        super.onDestroy();
    }
}
