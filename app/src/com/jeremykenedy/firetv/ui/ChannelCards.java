package com.jeremykenedy.firetv.ui;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChannelCards {
    private ChannelCards() {}

    static List<HiddenCards.Card> read(Context context, String source, long[] channels) {
        Map<String, HiddenCards.Card> cards = new LinkedHashMap<>();
        if (source == null || source.isEmpty() || channels == null || channels.length > 256)
            throw new IllegalArgumentException("This channel could not be identified.");
        for (long channel : channels) {
            if (channel < 0) throw new IllegalArgumentException("Invalid channel.");
            try (Cursor cursor =
                    context.getContentResolver()
                            .query(
                                    TvContract.buildPreviewProgramsUriForChannel(channel),
                                    new String[] {
                                        "package_name",
                                        "channel_id",
                                        "title",
                                        "internal_provider_id",
                                        "content_id",
                                        "intent_uri"
                                    },
                                    null,
                                    null,
                                    null)) {
                if (cursor == null)
                    throw new IllegalStateException(
                            "This source could not be read. Try Refresh sources.");
                int scanned = 0;
                while (cursor.moveToNext()) {
                    if (++scanned > 4096)
                        throw new IllegalStateException(
                                "This source has too many cards to list at once.");
                    if (!source.equals(
                                    cursor.getString(cursor.getColumnIndexOrThrow("package_name")))
                            || cursor.getLong(cursor.getColumnIndexOrThrow("channel_id"))
                                    != channel) continue;
                    HiddenCards.Card card = HiddenCards.identify(context, cursor, false);
                    if (card != null) cards.put(card.key, card);
                    else {
                        String title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                        cards.put(
                                "unavailable:" + channel + ":" + scanned,
                                new HiddenCards.Card(
                                        null, source, title == null ? "Untitled card" : title));
                    }
                }
            }
        }
        return new ArrayList<>(cards.values());
    }
}
