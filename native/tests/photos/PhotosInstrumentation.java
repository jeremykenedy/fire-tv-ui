package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ImageView;
import android.widget.TextView;
import com.jeremykenedy.firetv.ui.nativeapp.layout.WidgetProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PhotosInstrumentation extends Instrumentation {
    private Activity activity;
    private volatile int pickerMode;
    private volatile boolean documentIntent;
    private PhotoFrameView detached;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); final Context context = getTargetContext();
        android.app.UiAutomation automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        android.accessibilityservice.AccessibilityServiceInfo service = automation.getServiceInfo();
        service.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        automation.setServiceInfo(service);
        SharedPreferences prefs = context.getSharedPreferences(PhotoPreferences.STORE, 0); Map<String, ?> before = prefs.getAll();
        Set<Uri> grants = new HashSet<>();
        for (UriPermission grant : context.getContentResolver().getPersistedUriPermissions()) grants.add(grant.getUri());
        ActivityMonitor selector = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction())) {
                    documentIntent = "image/*".equals(intent.getType()) && intent.hasCategory(Intent.CATEGORY_OPENABLE)
                            && (intent.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0;
                    intent.setComponent(new ComponentName("proof.photos.fixture", "proof.photos.fixture.PickerActivity"));
                    intent.putExtra("fixture_mode", pickerMode);
                }
                return null;
            }
        };
        addMonitor(selector);
        try {
            for (Uri uri : grants) check(!"proof.photos.fixture".equals(uri.getAuthority()), "Use a fresh photo fixture so existing grants remain untouched");
            check(prefs.edit().clear().commit(), "Clear photo fixture settings");
            PhotoWidgetProvider provider = new PhotoWidgetProvider();
            check(!provider.defaultVisible() && provider.state(context) == WidgetProvider.State.SETUP_REQUIRED, "Fresh Photo frame is hidden and needs setup");
            Bitmap original = Bitmap.createBitmap(2560, 1280, Bitmap.Config.ARGB_8888); original.eraseColor(Color.RED);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); original.compress(Bitmap.CompressFormat.PNG, 100, bytes); original.recycle();
            Bitmap bounded = PhotoDecoder.decode(new ByteArrayInputStream(bytes.toByteArray()), new CancellationSignal());
            check(bounded.getWidth() <= 1024 && bounded.getWidth() == bounded.getHeight() * 2, "Decode is bounded and proportional"); bounded.recycle();
            CancellationSignal canceled = new CancellationSignal(); canceled.cancel();
            try { PhotoDecoder.decode(new ByteArrayInputStream(bytes.toByteArray()), canceled); throw new AssertionError("Canceled decode must fail"); } catch (IOException expected) {}
            activity = startActivitySync(new Intent(context, PhotoSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync();
            clickTag("add"); awaitCount(2);
            check(documentIntent, "Only the explicit native document image picker is used");
            PhotoOptions saved = PhotoPreferences.load(context); String first = saved.photos.get(0).id, second = saved.photos.get(1).id;
            check(PhotoWidgetProvider.hasGrant(context, Uri.parse(saved.photos.get(0).uri)), "Android retained read permission for selected source");
            check(provider.state(context) == WidgetProvider.State.READY, "Selected granted photos are configured");
            awaitImage();
            clickTag(first); clickText("Move later");
            check(PhotoPreferences.load(context).photos.get(0).id.equals(second), "Native reorder preserves stable photo identities");
            clickTag("interval"); clickText("5 minutes");
            check(PhotoPreferences.load(context).intervalSeconds == 300, "Native slideshow interval persists");
            clickTag("fit"); check(PhotoPreferences.load(context).fit == PhotoOptions.Fit.CROP, "Native fill selection persists");
            final String previous = PhotoPreferences.encode(PhotoPreferences.load(context));
            pickerMode = 1; clickTag("add"); SystemClock.sleep(250); waitForIdleSync();
            check(previous.equals(PhotoPreferences.encode(PhotoPreferences.load(context))), "Document picker cancellation preserves selected sources");
            pickerMode = 2; clickTag("add"); awaitText("Choose a readable photo");
            check(previous.equals(PhotoPreferences.encode(PhotoPreferences.load(context))), "Unreadable selection preserves previous photos");
            clickTag(first); clickText("Remove photo"); awaitCount(1);
            check(PhotoPreferences.load(context).find(second) != null, "Native removal preserves the other photo");
            PhotoOptions only = PhotoPreferences.load(context);
            context.getContentResolver().releasePersistableUriPermission(Uri.parse(only.photos.get(0).uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            check(provider.state(context) == WidgetProvider.State.UNAVAILABLE && PhotoPreferences.load(context).photos.size() == 1,
                    "Revoked access reports unavailable without deleting selection");
            final String retained = only.photos.get(0).id;
            pickerMode = 3; clickTag(retained); clickText("Choose replacement photo");
            for (int i = 0; i < 50 && !PhotoWidgetProvider.hasGrant(context, Uri.parse("content://proof.photos.fixture/two")); i++) SystemClock.sleep(100);
            awaitSettingsReady();
            check(PhotoPreferences.load(context).find(retained) != null, "Reconnection preserves stable identity");
            check(PhotoWidgetProvider.hasGrant(context, Uri.parse(PhotoPreferences.load(context).find(retained).uri)), "Reconnect obtains durable access again");
            PhotoOptions missing = PhotoPreferences.load(context).replace(retained, "content://proof.photos.fixture/missing", "Missing fixture");
            PhotoPreferences.save(context, missing);
            runOnMainSync(new Runnable() { @Override public void run() { detached = new PhotoFrameView(context); detached.start(); } });
            SystemClock.sleep(250); waitForIdleSync();
            final boolean[] unavailable = {false};
            runOnMainSync(new Runnable() { @Override public void run() { unavailable[0] = contains(detached, "Photo unavailable"); detached.stop(); } });
            check(unavailable[0] && PhotoPreferences.load(context).find(retained) != null, "Missing source is truthful and retained");
            runOnMainSync(new Runnable() { @Override public void run() { detached.start(); detached.stop(); } });
            SystemClock.sleep(200); waitForIdleSync();
            final boolean[] stale = {false};
            runOnMainSync(new Runnable() { @Override public void run() { ImageView image = findImage(detached); stale[0] = image != null && image.getDrawable() != null; } });
            check(!stale[0], "Stopped widget rejects stale decode callbacks");
            result.putString("result", "PASS: SAF selection, durable grants, cancel, invalid source preservation, order, interval, fit, missing-source recovery and lifecycle cancellation");
        } catch (Throwable error) { result.putString("error", android.util.Log.getStackTraceString(error)); }
        finally {
            removeMonitor(selector);
            runOnMainSync(new Runnable() { @Override public void run() { if (detached != null) detached.stop(); if (activity != null) activity.finish(); } });
            SharedPreferences.Editor restore = prefs.edit().clear();
            for (Map.Entry<String, ?> entry : before.entrySet()) {
                Object value = entry.getValue(); String key = entry.getKey();
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) restore.putInt(key, (Integer) value);
                else if (value instanceof Long) restore.putLong(key, (Long) value);
                else if (value instanceof Float) restore.putFloat(key, (Float) value);
                else if (value instanceof Set) { @SuppressWarnings("unchecked") Set<String> values = (Set<String>) value; restore.putStringSet(key, values); }
            }
            boolean restored = restore.commit() && before.equals(prefs.getAll());
            for (UriPermission grant : context.getContentResolver().getPersistedUriPermissions())
                if (!grants.contains(grant.getUri()) && "proof.photos.fixture".equals(grant.getUri().getAuthority()))
                    context.getContentResolver().releasePersistableUriPermission(grant.getUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            result.putBoolean("restored", restored); if (!restored) result.putString("error", "Photo settings restoration failed");
        }
        finish(result.containsKey("error") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
    }
    private void clickTag(final String key) {
        runOnMainSync(new Runnable() { @Override public void run() {
            View view = activity.getWindow().getDecorView().findViewWithTag("photos:" + key);
            check(view != null, "Missing photo control " + key); view.requestFocus(); view.performClick();
        } }); waitForIdleSync();
    }
    private void clickText(String text) {
        AccessibilityNodeInfo target = null;
        for (int attempt = 0; attempt < 80 && target == null; attempt++) {
            android.app.UiAutomation automation = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            for (AccessibilityWindowInfo window : automation.getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) {
                    target = matching(root, text);
                    if (target != null) break;
                }
            }
            if (target == null) {
                AccessibilityNodeInfo root = automation.getRootInActiveWindow();
                if (root != null && getTargetContext().getPackageName().equals(String.valueOf(root.getPackageName()))) target = matching(root, text);
            }
            if (target == null) SystemClock.sleep(100);
        }
        check(target != null, "Missing dialog control " + text);
        while (!target.isClickable() && target.getParent() != null) target = target.getParent();
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Activate dialog control"); waitForIdleSync();
    }
    private static AccessibilityNodeInfo matching(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (node.isVisibleToUser() && !node.isPassword() && text.equalsIgnoreCase(String.valueOf(node.getText()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = matching(node.getChild(i), text); if (found != null) return found;
        }
        return null;
    }
    private void awaitCount(int count) {
        for (int i = 0; i < 60; i++) { if (PhotoPreferences.load(getTargetContext()).photos.size() == count) { waitForIdleSync(); return; } SystemClock.sleep(100); }
        throw new AssertionError("Selected photo count did not update");
    }
    private void awaitText(final String text) {
        final boolean[] found = {false};
        for (int i = 0; i < 60; i++) {
            runOnMainSync(new Runnable() { @Override public void run() { found[0] = contains(activity.getWindow().getDecorView(), text); } });
            if (found[0]) return; SystemClock.sleep(100);
        } throw new AssertionError("Expected photo status missing");
    }
    private void awaitSettingsReady() {
        final boolean[] ready = {false};
        for (int i = 0; i < 60; i++) {
            runOnMainSync(new Runnable() { @Override public void run() {
                View view = activity.getWindow().getDecorView().findViewWithTag("photos:status");
                ready[0] = view instanceof TextView && ((TextView) view).getText().length() == 0;
            } });
            if (ready[0]) return; SystemClock.sleep(100);
        } throw new AssertionError("Photo import did not finish");
    }
    private void awaitImage() {
        final boolean[] found = {false};
        for (int i = 0; i < 60; i++) {
            runOnMainSync(new Runnable() { @Override public void run() { ImageView view = findImage(activity.getWindow().getDecorView()); found[0] = view != null && view.getDrawable() instanceof BitmapDrawable; } });
            if (found[0]) return; SystemClock.sleep(100);
        } throw new AssertionError("Selected photo did not render");
    }
    private static boolean contains(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) if (contains(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private static ImageView findImage(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) { ImageView image = findImage(((ViewGroup) view).getChildAt(i)); if (image != null) return image; }
        return null;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
