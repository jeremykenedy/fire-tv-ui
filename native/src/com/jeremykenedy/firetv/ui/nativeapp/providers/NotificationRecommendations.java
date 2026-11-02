package com.jeremykenedy.firetv.ui.nativeapp.providers;

import com.jeremykenedy.firetv.ui.nativeapp.SourceStartup;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Optional, user-authorized recommendation notifications. No notifications are stored or republished. */
public final class NotificationRecommendations extends NotificationListenerService {
    private static final String FILE = "native_notification_recommendations";
    private static volatile NotificationRecommendations connected;
    private boolean active;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    public static final class Result {
        public final TvRecommendations.SourceStatus status;
        public final List<TvRecommendations.Feed> feeds;
        Result(TvRecommendations.SourceStatus.State state, String message, List<TvRecommendations.Feed> feeds) {
            status = new TvRecommendations.SourceStatus("notifications", "Recommendation notifications", state, message);
            this.feeds = Collections.unmodifiableList(new ArrayList<>(feeds));
        }
    }

    public static boolean enabled(Context context) {
        return context.getSharedPreferences(FILE, MODE_PRIVATE).getBoolean("enabled", false);
    }
    public static void setEnabled(Context context, boolean enabled) {
        if (!context.getSharedPreferences(FILE, MODE_PRIVATE).edit().putBoolean("enabled", enabled).commit())
            throw new IllegalStateException("Notification card setting could not be saved.");
        if (enabled) {
            try { requestRebind(new ComponentName(context, NotificationRecommendations.class)); }
            catch (RuntimeException unavailable) { /* The source status reports a disconnected listener. */ }
        }
        changed();
    }
    public static boolean accessGranted(Context context) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(context, NotificationRecommendations.class));
        } catch (RuntimeException unavailable) { return false; }
    }
    public static Intent accessSettings() { return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS); }
    static void addListener(Runnable listener) { listeners.add(listener); }
    static void removeListener(Runnable listener) { listeners.remove(listener); }
    private static void changed() { for (Runnable listener : listeners) listener.run(); }

    @Override public void onListenerConnected() {
        active = true;
        SourceStartup.start(this, new Runnable() {
            @Override public void run() { if (active && SourceStartup.ready(NotificationRecommendations.this)) {
                connected = NotificationRecommendations.this; changed();
            } }
        });
    }
    @Override public void onListenerDisconnected() { active = false; if (connected == this) connected = null; changed(); }
    @Override public void onDestroy() { onListenerDisconnected(); super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification notification) { if (SourceStartup.ready(this) && enabled(this)) changed(); }
    @Override public void onNotificationRemoved(StatusBarNotification notification) { if (SourceStartup.ready(this) && enabled(this)) changed(); }

    public static Result read(Context context, CancellationSignal cancellation) {
        List<TvRecommendations.Feed> feeds = new ArrayList<>();
        if (!enabled(context)) return new Result(TvRecommendations.SourceStatus.State.DISABLED,
                "Notification recommendations are off.", feeds);
        if (cancellation != null) cancellation.throwIfCanceled();
        if (!accessGranted(context)) return new Result(TvRecommendations.SourceStatus.State.SETUP_REQUIRED,
                "Allow notification access in Android settings to read recommendation notifications.", feeds);
        NotificationRecommendations service = connected;
        if (service == null) return new Result(TvRecommendations.SourceStatus.State.UNAVAILABLE,
                "Notification access is disconnected. Open notification access settings to reconnect.", feeds);
        try {
            StatusBarNotification[] notifications = service.getActiveNotifications();
            if (notifications == null) return new Result(TvRecommendations.SourceStatus.State.UNAVAILABLE,
                    "Android did not return a notification snapshot.", feeds);
            Arrays.sort(notifications, new java.util.Comparator<StatusBarNotification>() {
                @Override public int compare(StatusBarNotification a, StatusBarNotification b) {
                    return Long.compare(b.getPostTime(), a.getPostTime());
                }
            });
            Map<String, List<TvRecommendations.Program>> groups = new LinkedHashMap<>();
            int scanned = 0;
            for (StatusBarNotification posted : notifications) {
                if (cancellation != null) cancellation.throwIfCanceled();
                if (scanned++ >= 512) break;
                if (!eligible(context, posted)) continue;
                String source = posted.getPackageName();
                if (!groups.containsKey(source)) {
                    if (groups.size() >= 32) continue;
                    groups.put(source, new ArrayList<TvRecommendations.Program>());
                }
                List<TvRecommendations.Program> programs = groups.get(source);
                if (programs.size() >= 24) continue;
                String title = title(posted.getNotification());
                String key = RecommendationKeys.notification(source, posted.getKey(), title);
                programs.add(new TvRecommendations.Program(key, source, title, null, null,
                        null, null, null, null, posted.getKey()));
            }
            for (Map.Entry<String, List<TvRecommendations.Program>> group : groups.entrySet()) {
                String label = group.getKey();
                try { label = context.getPackageManager().getApplicationLabel(
                        context.getPackageManager().getApplicationInfo(group.getKey(), 0)).toString(); }
                catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { }
                feeds.add(new TvRecommendations.Feed(RecommendationKeys.notificationFeed(group.getKey()),
                        label + " recommendations", group.getKey(), group.getValue()));
            }
            return new Result(feeds.isEmpty() ? TvRecommendations.SourceStatus.State.EMPTY : TvRecommendations.SourceStatus.State.READY,
                    feeds.isEmpty() ? "No supported recommendation notifications are active."
                    : feeds.size() + " apps shared recommendation notifications. These do not establish personal watch history or playback support.", feeds);
        } catch (android.os.OperationCanceledException canceled) { throw canceled; }
        catch (RuntimeException unavailable) { return new Result(TvRecommendations.SourceStatus.State.UNAVAILABLE,
                "Recommendation notifications could not be read.", Collections.<TvRecommendations.Feed>emptyList()); }
    }

    static boolean canOpen(Context context, TvRecommendations.Program program) { return currentAction(context, program) != null; }
    static RecommendationActions.Outcome open(final Context context, final TvRecommendations.Program program) {
        if (currentAction(context, program) == null) return RecommendationActions.Outcome.UNAVAILABLE;
        return RecommendationActions.guarded(context, program.source, null, new RecommendationActions.Dispatch() {
            @Override public RecommendationActions.Outcome run() { return sendCurrent(context, program); }
        });
    }
    private static RecommendationActions.Outcome sendCurrent(Context context, TvRecommendations.Program program) {
        PendingIntent action = currentAction(context, program);
        if (action == null) return RecommendationActions.Outcome.UNAVAILABLE;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                action.send(context, 0, null, null, null, null, options.toBundle());
            } else action.send();
            return RecommendationActions.Outcome.OPENED;
        } catch (PendingIntent.CanceledException | RuntimeException unavailable) {
            changed();
            return RecommendationActions.Outcome.FAILED;
        }
    }
    private static PendingIntent currentAction(Context context, TvRecommendations.Program program) {
        NotificationRecommendations service = connected;
        if (program.notificationKey == null || service == null || !enabled(context) || !accessGranted(context)) return null;
        try {
            StatusBarNotification[] notifications = service.getActiveNotifications();
            if (notifications == null) return null;
            for (StatusBarNotification posted : notifications) {
                if (program.source.equals(posted.getPackageName()) && program.notificationKey.equals(posted.getKey())
                        && eligible(context, posted) && program.key.equals(RecommendationKeys.notification(program.source,
                        posted.getKey(), title(posted.getNotification())))) return posted.getNotification().contentIntent;
            }
        } catch (RuntimeException unavailable) { }
        return null;
    }
    private static boolean eligible(Context context, StatusBarNotification posted) {
        if (posted == null || !RecommendationKeys.validPackage(posted.getPackageName())) return false;
        Notification notification = posted.getNotification();
        if (notification == null || !Notification.CATEGORY_RECOMMENDATION.equals(notification.category)
                || title(notification).isEmpty() || notification.contentIntent == null) return false;
        PendingIntent action = notification.contentIntent;
        String source = posted.getPackageName();
        if (!source.equals(action.getCreatorPackage()) || (Build.VERSION.SDK_INT >= 31 && !action.isActivity())) return false;
        try {
            PackageManager packages = context.getPackageManager();
            int sourceUid = packages.getApplicationInfo(source, 0).uid;
            return NotificationRules.eligible(source, notification.category, action.getCreatorPackage(),
                    sourceUid, action.getCreatorUid(), title(notification))
                    && (Build.VERSION.SDK_INT < 29 || posted.getUid() == sourceUid)
                    && packages.getLeanbackLaunchIntentForPackage(source) != null;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return false; }
    }
    private static String title(Notification notification) {
        if (notification.extras == null) return "";
        CharSequence value = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        if (value == null || value.length() > 512) return "";
        return value.toString().trim();
    }
}
