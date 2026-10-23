package com.jeremykenedy.firetv.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public final class PlexItemActivity extends CardActivity {
    private TextView details;
    private long request;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        title("Your Plex library item");
        LinearLayout body = scrollBody();
        details = text("Reading this item from your Plex server...", 18, INK);
        body.addView(details);
        addButton(
                body,
                button(
                        "Open Plex",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent plex =
                                        getPackageManager()
                                                .getLaunchIntentForPackage("com.plexapp.android");
                                if (plex == null) {
                                    details.setText(
                                            "Install the Plex app on this TV to open Plex.");
                                } else {
                                    startActivity(plex);
                                }
                            }
                        }));
        body.addView(text("Open Plex to browse your library in the installed app.", 17, MUTED));
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
        String source = uri.getPathSegments().get(1);
        String id = uri.getPathSegments().get(2);
        request = PlexSession.begin();
        new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    PlexConnection connection =
                                            PlexConnection.load(PlexItemActivity.this);
                                    if (connection == null
                                            || !source.equals(connection.sourceKey())) {
                                        show(
                                                "This card belongs to a previous server connection."
                                                        + " Refresh your Plex library cards.");
                                        return;
                                    }
                                    PlexApi api = new PlexApi(PlexItemActivity.this, request);
                                    List<PlexLibrary.Item> items =
                                            PlexLibrary.items(
                                                    api.server(
                                                            connection, "/library/metadata/" + id));
                                    if (items.isEmpty() || !id.equals(items.get(0).id)) {
                                        show(
                                                "This item is no longer available on your server."
                                                        + " Refresh your Plex library cards.");
                                        return;
                                    }
                                    PlexLibrary.Item item = items.get(0);
                                    show(item.title + "\n\n" + item.summary);
                                } catch (Exception error) {
                                    show(
                                            "This Plex item could not be read. Check your server or"
                                                    + " refresh your Plex connection.");
                                }
                            }
                        },
                        "read-plex-card-item")
                .start();
    }

    private void show(String value) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDestroyed() && PlexSession.current(request))
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
