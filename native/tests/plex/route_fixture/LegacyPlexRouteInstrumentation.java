package com.jeremykenedy.firetv.ui.nativeapp.providers.plex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.providers.RecommendationActions;
import com.jeremykenedy.firetv.ui.nativeapp.providers.TvRecommendations;

/** Own fixture package only. Invalid card must stop before any account or network access. */
public final class LegacyPlexRouteInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity activity = null, cover = null; java.io.File artwork = null; RoutePlexFixture fixture = null; android.content.BroadcastReceiver receiver = null;
        try {
            Context context = getTargetContext();
            if (!"com.jeremykenedy.firetv.plexrouteproof".equals(context.getPackageName())) throw new AssertionError("Unexpected target.");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("firetvui://plex/item/invalid/12"))
                    .setComponent(new ComponentName(context.getPackageName(), context.getPackageName() + ".PlexItemActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved == null || resolved.activityInfo.exported) throw new AssertionError("Legacy alias must resolve and stay private.");
            TvRecommendations.Program program = new TvRecommendations.Program("legacy", context.getPackageName(), "Fixture", null,
                    intent.toUri(Intent.URI_INTENT_SCHEME));
            if (!RecommendationActions.inspect(context, program).title)
                throw new AssertionError("Own private activity must remain eligible for the scoped title action.");
            ActivityMonitor monitor = addMonitor(LegacyPlexCardActivity.class.getName(), null, false);
            context.startActivity(intent);
            activity = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
            if (activity == null) throw new AssertionError("Private legacy alias did not open the compatibility activity.");
            waitForIdleSync();
            final Activity selected = activity; final boolean[] found = {false};
            runOnMainSync(new Runnable() { @Override public void run() { found[0] = contains(selected.getWindow().getDecorView(), "This Plex card is invalid."); } });
            if (!found[0]) throw new AssertionError("Invalid card did not produce an explicit error.");
            final Activity invalidActivity = activity;
            runOnMainSync(new Runnable() { @Override public void run() { invalidActivity.finish(); } });
            waitForIdleSync();
            fixture = RoutePlexFixture.start(context);
            String canonical = fixture.firstKey().substring("plex:".length());
            String[] identity = canonical.split(":");
            Intent valid = new Intent(Intent.ACTION_VIEW, Uri.parse("firetvui://plex/item/" + identity[0] + "/" + identity[1]))
                    .setComponent(new ComponentName(context.getPackageName(), context.getPackageName() + ".PlexItemActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            monitor = addMonitor(LegacyPlexCardActivity.class.getName(), null, false);
            context.startActivity(valid);
            activity = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor);
            if (activity == null || !awaitText(activity, "Fixture Show: Pilot", 5000)) throw new AssertionError("Verified item did not appear.");
            final java.util.concurrent.atomic.AtomicInteger launches = new java.util.concurrent.atomic.AtomicInteger();
            receiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent received) { launches.incrementAndGet(); }
            };
            String permission = "com.jeremykenedy.firetv.plexrouteproof.FIXTURE";
            if (context.getPackageManager().checkPermission(permission, context.getPackageName()) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    || context.getPackageManager().checkPermission(permission, "com.plexapp.android") != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw new AssertionError("Synthetic callback permission not granted to both packages.");
            IntentFilter callback = new IntentFilter("com.jeremykenedy.firetv.plexrouteproof.FAKE_OPENED");
            if (android.os.Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(receiver, callback, "com.jeremykenedy.firetv.plexrouteproof.FIXTURE", null, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, callback, "com.jeremykenedy.firetv.plexrouteproof.FIXTURE", null);
            Intent fake = new Intent(Intent.ACTION_VIEW, Uri.parse("plex://libraries/fixture-machine/unmatched/tv/%2Flibrary%2Fmetadata%2F12"))
                    .setPackage("com.plexapp.android");
            ResolveInfo fakeResolved = context.getPackageManager().resolveActivity(fake, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (fakeResolved == null || !"com.plexapp.android.FakePlexActivity".equals(fakeResolved.activityInfo.name))
                throw new AssertionError("Synthetic Plex handler not resolved.");
            final Activity positiveActivity = activity;
            runOnMainSync(new Runnable() { @Override public void run() {
                if (!click(positiveActivity.getWindow().getDecorView(), "Open item in Plex")) throw new AssertionError("Open action unavailable.");
            } });
            long positiveDeadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (launches.get() == 0 && android.os.SystemClock.uptimeMillis() < positiveDeadline) android.os.SystemClock.sleep(50);
            if (launches.get() != 1) throw new AssertionError("Explicit Open did not launch the synthetic Plex handler.");
            if (!awaitOpen(activity, 5000)) throw new AssertionError("Card did not resume after synthetic open.");
            result.putBoolean("explicit_open_reaches_synthetic_handler", true);
            fixture.holdNextIdentity();
            final Activity validActivity = activity;
            runOnMainSync(new Runnable() { @Override public void run() {
                if (!click(validActivity.getWindow().getDecorView(), "Open item in Plex")) throw new AssertionError("Open action unavailable.");
            } });
            if (!fixture.awaitIdentity()) throw new AssertionError("Delayed identity request did not start.");
            ActivityMonitor covering = addMonitor(CoverActivity.class.getName(), null, false);
            context.startActivity(new Intent(context, CoverActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            cover = waitForMonitorWithTimeout(covering, 5000); removeMonitor(covering);
            if (cover == null) throw new AssertionError("Fixture cover did not open.");
            waitForIdleSync(); fixture.releaseIdentity();
            if (!fixture.awaitIdentityFinished()) throw new AssertionError("Delayed identity request did not finish.");
            waitForIdleSync();
            if (launches.get() != 1) throw new AssertionError("Plex dispatch occurred after the card lost foreground.");
            final Activity closeCover = cover;
            runOnMainSync(new Runnable() { @Override public void run() { closeCover.finish(); } }); cover = null;
            if (!awaitOpen(activity, 5000)) throw new AssertionError("Card did not recover after returning.");
            fixture.holdNextIdentity();
            runOnMainSync(new Runnable() { @Override public void run() {
                if (!click(validActivity.getWindow().getDecorView(), "Open item in Plex")) throw new AssertionError("Open action unavailable after resume.");
            } });
            if (!fixture.awaitIdentity()) throw new AssertionError("Second identity request did not start.");
            PlexSession.begin(); fixture.releaseIdentity();
            if (!awaitText(activity, "The Plex connection changed", 5000)) throw new AssertionError("Connection change did not produce a clear message.");
            if (launches.get() != 1) throw new AssertionError("Changed connection dispatched to Plex.");
            result.putBoolean("connection_change_prevents_dispatch", true);
            fixture.allowFutureFailures(); fixture.assertHealthy();
            result.putBoolean("verified_item_visible", true); result.putBoolean("pause_prevents_dispatch_and_resume_recovers", true);
            java.io.File folder = new java.io.File(context.getNoBackupFilesDir(), "plex-card-art");
            if (!folder.isDirectory() && !folder.mkdirs()) throw new AssertionError("Fixture art directory unavailable.");
            String name = new String(new char[64]).replace('\0', 'a') + ".jpg";
            artwork = new java.io.File(folder, name);
            if (artwork.exists()) throw new AssertionError("Fixture art already exists.");
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(artwork)) { out.write(new byte[]{1, 2, 3}); }
            Uri legacyArt = Uri.parse("content://" + context.getPackageName() + ".plex.art/" + name);
            try (android.os.ParcelFileDescriptor opened = context.getContentResolver().openFileDescriptor(legacyArt, "r")) {
                if (opened == null || opened.getStatSize() != 3) throw new AssertionError("Retained artwork was not read.");
            }
            try { context.getContentResolver().openFileDescriptor(legacyArt.buildUpon().appendQueryParameter("bad", "1").build(), "r");
                throw new AssertionError("Query was accepted."); } catch (java.io.FileNotFoundException expected) { }
            try { context.getContentResolver().openFileDescriptor(legacyArt, "w");
                throw new AssertionError("Write was accepted."); } catch (java.io.FileNotFoundException expected) { }
            result.putBoolean("retained_art_read_only", true);
            result.putString("status", "PASS"); result.putBoolean("private_alias_resolves", true); result.putBoolean("invalid_card_rejected", true);
        } catch (Throwable failure) { result.putString("status", "FAIL"); result.putString("reason", failure.toString()); }
        finally { if (receiver != null) getTargetContext().unregisterReceiver(receiver); if (fixture != null) { try { fixture.close(); result.putBoolean("fixture_connection_removed", true); }
                catch (Exception failure) { result.putBoolean("fixture_connection_removed", false); result.putString("fixture_cleanup_error", failure.toString()); } }
            if (cover != null) { final Activity close = cover; runOnMainSync(new Runnable() { @Override public void run() { close.finish(); } }); }
            if (artwork != null && artwork.exists() && !artwork.delete()) result.putString("artwork_cleanup_error", "Fixture file could not be removed.");
            if (activity != null) { final Activity close = activity; runOnMainSync(new Runnable() { @Override public void run() { close.finish(); } }); } }
        finish(0, result);
    }
    private boolean awaitText(final Activity activity, final String text, long timeout) {
        long end = android.os.SystemClock.uptimeMillis() + timeout;
        while (android.os.SystemClock.uptimeMillis() < end) {
            final boolean[] found = {false};
            runOnMainSync(new Runnable() { @Override public void run() { found[0] = contains(activity.getWindow().getDecorView(), text); } });
            if (found[0]) return true;
            android.os.SystemClock.sleep(50);
        }
        return false;
    }
    private boolean awaitOpen(final Activity activity, long timeout) {
        long end = android.os.SystemClock.uptimeMillis() + timeout;
        while (android.os.SystemClock.uptimeMillis() < end) {
            final boolean[] found = {false};
            runOnMainSync(new Runnable() { @Override public void run() { found[0] = visibleButton(activity.getWindow().getDecorView(), "Open item in Plex"); } });
            if (found[0]) return true;
            android.os.SystemClock.sleep(50);
        }
        return false;
    }
    private static boolean visibleButton(View view, String text) {
        if (view instanceof android.widget.Button && ((android.widget.Button) view).getText().toString().equals(text))
            return view.getVisibility() == View.VISIBLE;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (visibleButton(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private static boolean click(View view, String text) {
        if (view instanceof android.widget.Button && ((android.widget.Button) view).getText().toString().equals(text)
                && view.getVisibility() == View.VISIBLE) return view.performClick();
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (click(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private static boolean contains(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (contains(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
}
