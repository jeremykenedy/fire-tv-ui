package com.jeremykenedy.firetv.ui;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RecommendationService extends NotificationListenerService {
    private static final String PREFIX = "fire-tv-ui-recommendations:";
    private static volatile RecommendationService connected;
    private static volatile String status = "Recommendation access is not connected.";
    private static final Object PUBLISH_LOCK = new Object();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, PendingIntent> actions = new HashMap<>();
    private volatile boolean active;
    private volatile boolean collecting;
    private boolean cleanupQueued;

    static boolean enabled(Context context) {
        return context.getSharedPreferences("recommendation-cards", MODE_PRIVATE)
                .getBoolean("enabled", false);
    }

    static void setEnabled(Context context, boolean enabled) {
        if (!context.getSharedPreferences("recommendation-cards", MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).commit()) {
            throw new IllegalStateException("The recommendation setting could not be saved.");
        }
        refresh(context);
    }

    static String status() { return status; }

    static void refresh(Context context) {
        RecommendationService service = connected;
        if (service != null) service.refresh();
        else requestRebind(new ComponentName(context, RecommendationService.class));
    }

    static boolean open(Context context, String id) {
        RecommendationService service = connected;
        if (service == null || !service.active || !enabled(context)
                || !RecommendationRules.validIdentity(id)) return false;
        synchronized (service.actions) { if (!service.actions.containsKey(id)) return false; }
        PendingIntent action = null;
        try {
            StatusBarNotification[] current = service.getActiveNotifications();
            if (current == null) return false;
            for (StatusBarNotification posted : current) {
                Notification notification = posted.getNotification();
                if (notification == null || notification.contentIntent == null) continue;
                CharSequence value = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
                String title = value == null ? "" : value.toString().trim();
                if (RecommendationRules.eligible(posted.getPackageName(), notification.category,
                        notification.contentIntent.getCreatorPackage(), title)
                        && id.equals(RecommendationRules.identity(posted.getPackageName(), posted.getKey(), title))) {
                    action = notification.contentIntent;
                    break;
                }
            }
        } catch (Exception unavailable) { return false; }
        if (action == null) { service.refresh(); return false; }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                action.send(context, 0, null, null, null, null, options.toBundle());
            } else {
                action.send();
            }
            return true;
        } catch (PendingIntent.CanceledException unavailable) {
            service.refresh();
            return false;
        }
    }

    @Override
    public void onListenerConnected() {
        cleanupQueued = false;
        active = true;
        connected = this;
        refresh();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        if (notification != null && RecommendationRules.label(notification.getPackageName()) != null)
            refresh();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) {
        if (notification != null && RecommendationRules.label(notification.getPackageName()) != null)
            refresh();
    }

    @Override
    public void onListenerDisconnected() {
        active = false;
        if (connected == this) connected = null;
        synchronized (actions) { actions.clear(); }
        status = "Recommendation access is disconnected. Reconnect it in card setup.";
        if (!cleanupQueued) {
            cleanupQueued = true;
            worker.execute(new Runnable() {
                @Override public void run() { publish(new LinkedHashMap<>()); }
            });
        }
    }

    @Override
    public void onDestroy() {
        onListenerDisconnected();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
        worker.shutdown();
    }

    private final Runnable reload = new Runnable() {
        @Override public void run() {
        if (!active) return;
        if (collecting) {
            main.postDelayed(this, 150);
            return;
        }
        final StatusBarNotification[] notifications;
        try {
            notifications = enabled(RecommendationService.this) ? getActiveNotifications() : new StatusBarNotification[0];
        } catch (Exception unavailable) {
            status = "The TV could not read recommendation notifications.";
            return;
        }
        collecting = true;
        worker.execute(new Runnable() {
            @Override public void run() {
                try { collect(notifications); }
                finally { collecting = false; }
            }
        });
        }
    };

    private void refresh() {
        main.removeCallbacks(reload);
        main.postDelayed(reload, 150);
    }

    private void collect(StatusBarNotification[] notifications) {
        LinkedHashMap<String, ArrayList<Card>> sources = new LinkedHashMap<>();
        if (notifications == null) {
            status = "The TV returned no recommendation notification snapshot.";
            return;
        }
        Arrays.sort(notifications, new java.util.Comparator<StatusBarNotification>() {
            @Override public int compare(StatusBarNotification a, StatusBarNotification b) {
                return Long.compare(b.getPostTime(), a.getPostTime());
            }
        });
        int count = 0;
        for (StatusBarNotification posted : notifications) {
            if (!active || !enabled(this)) break;
            if (count >= 96) break;
            try {
                String source = posted.getPackageName();
                Notification notification = posted.getNotification();
                if (RecommendationRules.label(source) == null || notification == null
                        || notification.contentIntent == null) continue;
                CharSequence value = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
                String title = value == null ? "" : value.toString().trim();
                if (!RecommendationRules.eligible(source, notification.category,
                        notification.contentIntent.getCreatorPackage(), title)) continue;
                ArrayList<Card> cards = sources.get(source);
                if (cards == null) { cards = new ArrayList<>(); sources.put(source, cards); }
                if (cards.size() >= 24) continue;
                String id = RecommendationRules.identity(source, posted.getKey(), title);
                CharSequence summary = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
                String description = summary == null ? "" : summary.toString();
                if (description.length() > 2048) description = description.substring(0, 2048);
                Uri art = cache(id, source, notification);
                cards.add(new Card(id, source, title, description, art, notification.contentIntent));
                count++;
            } catch (Exception malformed) {
                // A malformed recommendation must not stop other apps' cards.
            }
        }
        if (!active || !enabled(this)) sources.clear();
        publish(sources);
    }

    private void publish(LinkedHashMap<String, ArrayList<Card>> sources) {
        synchronized (PUBLISH_LOCK) {
            if (connected != null && connected != this) return;
            if (!active || !enabled(this)) sources.clear();
            publishCurrent(sources);
        }
    }

    private void publishCurrent(LinkedHashMap<String, ArrayList<Card>> sources) {
        Map<String, Long> channels = new LinkedHashMap<>();
        Set<String> referenced = new HashSet<>();
        Map<String, PendingIntent> updated = new HashMap<>();
        try {
            try (Cursor cursor = getContentResolver().query(TvContract.Channels.CONTENT_URI,
                    new String[]{"_id", "package_name", "internal_provider_id"}, null, null, null)) {
                if (cursor == null) throw new java.io.IOException();
                while (cursor.moveToNext()) {
                    String provider = cursor.getString(2);
                    if (getPackageName().equals(cursor.getString(1))
                            && provider != null && provider.startsWith(PREFIX))
                        channels.put(provider.substring(PREFIX.length()), cursor.getLong(0));
                }
            }
            for (String source : sources.keySet()) {
                if (channels.containsKey(source)) continue;
                ContentValues values = new ContentValues();
                values.put("type", TvContract.Channels.TYPE_PREVIEW);
                values.put("display_name", RecommendationRules.label(source) + " recommendations");
                values.put("internal_provider_id", PREFIX + source);
                Intent setup = new Intent(this, RecommendationCardsActivity.class);
                values.put("app_link_intent_uri", setup.toUri(Intent.URI_INTENT_SCHEME));
                Uri uri = getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
                if (uri == null) throw new java.io.IOException();
                channels.put(source, ContentUris.parseId(uri));
            }
            ArrayList<ContentProviderOperation> changes = new ArrayList<>();
            for (Map.Entry<String, Long> channel : channels.entrySet()) {
                try (Cursor cursor = getContentResolver().query(
                        TvContract.buildPreviewProgramsUriForChannel(channel.getValue()),
                        new String[]{"_id", "package_name"}, null, null, null)) {
                    if (cursor == null) throw new java.io.IOException();
                    while (cursor.moveToNext()) {
                        if (getPackageName().equals(cursor.getString(1)))
                            changes.add(ContentProviderOperation.newDelete(ContentUris.withAppendedId(
                                    TvContract.PreviewPrograms.CONTENT_URI, cursor.getLong(0))).build());
                    }
                }
                ArrayList<Card> cards = sources.get(channel.getKey());
                if (cards == null) continue;
                int weight = cards.size();
                for (Card card : cards) {
                    ContentValues values = new ContentValues();
                    values.put("channel_id", channel.getValue());
                    values.put("internal_provider_id", PREFIX + card.source + ":" + card.id);
                    values.put("title", card.title);
                    values.put("short_description", card.description);
                    values.put("type", TvContract.PreviewPrograms.TYPE_CLIP);
                    values.put("poster_art_aspect_ratio", TvContract.PreviewPrograms.ASPECT_RATIO_16_9);
                    if (card.art != null) { values.put("poster_art_uri", card.art.toString()); referenced.add(card.id + ".png"); }
                    Intent intent = new Intent(this, RecommendationItemActivity.class).setAction(Intent.ACTION_VIEW)
                            .setData(new Uri.Builder().scheme("firetvui").authority("recommendation")
                                    .appendPath("item").appendPath(card.id).build());
                    values.put("intent_uri", intent.toUri(Intent.URI_INTENT_SCHEME));
                    values.put("weight", weight--);
                    changes.add(ContentProviderOperation.newInsert(TvContract.PreviewPrograms.CONTENT_URI)
                            .withValues(values).build());
                    updated.put(card.id, card.action);
                }
            }
            if (!changes.isEmpty()) getContentResolver().applyBatch(TvContract.AUTHORITY, changes);
            synchronized (actions) { actions.clear(); actions.putAll(updated); }
            File[] files = directory(this).listFiles();
            if (files != null) for (File file : files)
                if (file.getName().matches("[a-f0-9]{64}\\.png") && !referenced.contains(file.getName())) file.delete();
            status = !enabled(this) ? "App recommendation cards are off."
                    : !active ? "Recommendation access is disconnected. Reconnect it in card setup."
                    : updated.size() + " app recommendation cards available.";
        } catch (Exception unavailable) {
            synchronized (actions) { actions.clear(); }
            status = "Recommendation cards could not be updated. Check access and refresh.";
        }
    }

    static File directory(Context context) {
        return new File(context.getNoBackupFilesDir(), "recommendation-card-art");
    }

    private Uri cache(String id, String source, Notification notification) throws Exception {
        File folder = directory(this);
        File imageFile = new File(folder, id + ".png");
        {
            File[] existing = folder.listFiles();
            long bytes = 0;
            int count = 0;
            if (existing != null) for (File file : existing) {
                if (file.getName().matches("[a-f0-9]{64}\\.png(?:\\.bak)?")) {
                    count++;
                    bytes += file.length();
                }
            }
            // Leave room for one replacement snapshot before old art is pruned.
            if ((!imageFile.isFile() && count >= 192)
                    || bytes - imageFile.length() + 2L * 1024 * 1024 > 48L * 1024 * 1024)
                return null;
        }
        Bitmap image = null;
        Object picture = notification.extras.get(Notification.EXTRA_PICTURE);
        if (picture instanceof Bitmap) image = (Bitmap) picture;
        if (image == null && android.os.Build.VERSION.SDK_INT < 28)
            image = notification.largeIcon;
        if (image == null && android.os.Build.VERSION.SDK_INT >= 28
                && notification.getLargeIcon() != null) {
            Icon icon = notification.getLargeIcon();
            // URI icons can address private files using the listener's identity.
            if (icon.getType() == Icon.TYPE_BITMAP || icon.getType() == Icon.TYPE_ADAPTIVE_BITMAP
                    || (icon.getType() == Icon.TYPE_RESOURCE && source.equals(icon.getResPackage()))) {
                Drawable drawable = icon.loadDrawable(this);
                if (drawable != null) {
                    int originalWidth = Math.max(1, drawable.getIntrinsicWidth());
                    int originalHeight = Math.max(1, drawable.getIntrinsicHeight());
                    float ratio = Math.min(1f, Math.min(960f / originalWidth, 540f / originalHeight));
                    int width = Math.max(1, Math.round(originalWidth * ratio));
                    int height = Math.max(1, Math.round(originalHeight * ratio));
                    image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    drawable.setBounds(0, 0, width, height);
                    drawable.draw(new Canvas(image));
                }
            }
        }
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() > 8192 || image.getHeight() > 8192) return null;
        float scale = Math.min(1f, Math.min(960f / image.getWidth(), 540f / image.getHeight()));
        Bitmap resized = Bitmap.createScaledBitmap(image, Math.max(1, Math.round(image.getWidth() * scale)),
                Math.max(1, Math.round(image.getHeight() * scale)), true);
        if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException();
        android.util.AtomicFile target = new android.util.AtomicFile(imageFile);
        FileOutputStream stream = null;
        try {
            stream = target.startWrite();
            if (!resized.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new java.io.IOException();
            target.finishWrite(stream);
        } catch (Exception failure) {
            if (stream != null) target.failWrite(stream);
            throw failure;
        } finally {
            if (resized != image) resized.recycle();
        }
        return new Uri.Builder().scheme("content").authority(getPackageName() + ".recommendation.art")
                .appendPath(id + ".png").build();
    }

    private static final class Card {
        final String id, source, title, description;
        final Uri art;
        final PendingIntent action;
        Card(String id, String source, String title, String description, Uri art, PendingIntent action) {
            this.id = id; this.source = source; this.title = title; this.description = description;
            this.art = art; this.action = action;
        }
    }
}
