package com.jeremykenedy.firetv.ui.nativeapp.providers;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardOptions;
import com.jeremykenedy.firetv.ui.nativeapp.cards.CardPreferences;

/** Synthetic owned-provider cursor only; no accounts, network or persistent state. */
public final class PlexIdentityInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            String source = context.getPackageName();
            if (!"com.jeremykenedy.firetv.migrationproof".equals(source)) throw new AssertionError("Unexpected target.");
            String machine = repeat('a');
            String canonical = machine + ":12";
            String target = new Intent(Intent.ACTION_VIEW, Uri.parse("firetvui://plex/item/" + machine + "/12"))
                    .setClassName(source, source + ".PlexItemActivity").toUri(Intent.URI_INTENT_SCHEME);
            String first = identity(context, source, 11, 21, "12", target);
            String returned = identity(context, source, 99, 77, canonical, target);
            check(first.equals("plex:" + canonical) && first.equals(returned), "Recreated channel and row must keep Plex identity.");
            check(identity(context, source, 1, 2, "13", target) == null, "Mismatched publisher ID must be rejected.");
            check(identity(context, source, 1, 2, "12", "") == null, "Missing intent must be rejected.");
            String notificationTarget = new Intent(Intent.ACTION_VIEW, Uri.parse("firetvui://recommendation/item/" + machine))
                    .setClassName(source, source + ".RecommendationItemActivity").toUri(Intent.URI_INTENT_SCHEME);
            try (MatrixCursor notification = new MatrixCursor(new String[]{"internal_provider_id", "intent_uri"})) {
                notification.addRow(new Object[]{"fire-tv-ui-recommendations:example.player:" + machine, notificationTarget});
                notification.moveToFirst();
                check(OwnedPlexIdentity.notification(context, notification, source), "Verified notification cards remain available.");
                check(OwnedPlexIdentity.canonical(context, notification, source) == null, "Notification is never treated as Plex.");
            }
            String old = RecommendationKeys.program(RecommendationKeys.feed(source, false, 11), 21, "12");
            TvRecommendations.Program program = new TvRecommendations.Program(first, source, "Episode", null, target,
                    null, null, null, null, null, old);
            CardOptions previous = CardOptions.defaults().hide(source, old, "Episode");
            check(CardPreferences.itemHidden(previous, program), "Prior preview identity must remain hidden.");
            check(!CardPreferences.itemHidden(CardPreferences.restoreItem(previous, program), program), "Restore clears both identities.");
            CardOptions migrated = CardOptions.defaults().hide(source, first, "Episode")
                    .hide("com.plexapp.android", first, "Episode");
            check(migrated.isHidden(source, returned) && migrated.isHidden("com.plexapp.android", first), "Both representations remain hidden.");
            check(!migrated.isHidden(source, "plex:" + machine + ":13"), "Unrelated episode stays visible.");
            String legacy = LegacyRecommendationIdentities.item(source, false, canonical, "");
            check(legacy.equals("0bf006035061e47755305c6d5a29cef44d18a806a28a0eafffdb9f5ab272688c"), "Legacy evidence remains exact.");
            result.putString("status", "PASS");
            result.putString("scenario", "owned Plex identity, recreated rows, compatibility and dual hide");
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finish(0, result);
    }
    private static String identity(Context context, String source, long channel, long rowId, String providerId, String target) {
        try (MatrixCursor row = new MatrixCursor(new String[]{"_id", "channel_id", "internal_provider_id", "intent_uri"})) {
            row.addRow(new Object[]{rowId, channel, providerId, target});
            row.moveToFirst();
            String canonical = OwnedPlexIdentity.canonical(context, row, source);
            return canonical == null ? null : "plex:" + canonical;
        }
    }
    private static String repeat(char c) { return new String(new char[64]).replace('\0', c); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
