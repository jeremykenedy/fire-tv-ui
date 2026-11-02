package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

/** Identifies only an owned historical Plex item handoff with matching publisher metadata. */
final class OwnedPlexIdentity {
    private OwnedPlexIdentity() {}

    static String canonical(Context context, Cursor row, String source) {
        return canonical(context, source, value(row, "internal_provider_id"), value(row, "intent_uri"));
    }

    private static String value(Cursor row, String column) {
        int index = row.getColumnIndex(column);
        return index < 0 || row.isNull(index) ? "" : row.getString(index);
    }

    static boolean notification(Context context, Cursor row, String source) {
        if (!context.getPackageName().equals(source)) return false;
        String providerId = value(row, "internal_provider_id");
        try {
            Intent intent = Intent.parseUri(value(row, "intent_uri"), Intent.URI_INTENT_SCHEME);
            Uri data = intent.getData();
            if (intent.getComponent() == null || !source.equals(intent.getComponent().getPackageName())
                    || !(source + ".RecommendationItemActivity").equals(intent.getComponent().getClassName())
                    || data == null || !"firetvui".equals(data.getScheme()) || !"recommendation".equals(data.getAuthority())
                    || data.getQuery() != null || data.getFragment() != null || data.getEncodedPath() == null
                    || !data.getEncodedPath().matches("/item/[a-f0-9]{64}")) return false;
            String id = data.getPathSegments().get(1);
            return providerId.matches("fire-tv-ui-recommendations:[A-Za-z0-9_.]+:" + id);
        } catch (Exception invalid) { return false; }
    }

    static String canonical(Context context, String source, String providerId, String target) {
        if (!context.getPackageName().equals(source)) return null;
        try {
            Intent intent = Intent.parseUri(target, Intent.URI_INTENT_SCHEME);
            Uri data = intent.getData();
            if (intent.getComponent() == null || !source.equals(intent.getComponent().getPackageName())
                    || !(source + ".PlexItemActivity").equals(intent.getComponent().getClassName())
                    || data == null || !"firetvui".equals(data.getScheme()) || !"plex".equals(data.getAuthority())
                    || data.getQuery() != null || data.getFragment() != null || data.getEncodedPath() == null
                    || !data.getEncodedPath().matches("/item/[a-f0-9]{64}/[0-9]{1,18}")) return null;
            String item = data.getPathSegments().get(2);
            String canonical = data.getPathSegments().get(1) + ":" + item;
            return providerId.equals(item) || providerId.equals(canonical) ? canonical : null;
        } catch (Exception invalid) { return null; }
    }
}
