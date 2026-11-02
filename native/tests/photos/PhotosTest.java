package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public final class PhotosTest {
    public static void main(String[] args) {
        Context context = new Context();
        PhotoOptions fresh = PhotoPreferences.load(context);
        check(!fresh.configured && fresh.photos.isEmpty() && fresh.fit == PhotoOptions.Fit.CONTAIN, "Fresh widget needs setup and fits full images.");
        PhotoOptions options = fresh.add("content://photos/a", "One \"photo\"").add("content://photos/b", "Two");
        String first = options.photos.get(0).id, second = options.photos.get(1).id;
        PhotoPreferences.save(context, options);
        check(PhotoPreferences.encode(options).equals(PhotoPreferences.encode(PhotoPreferences.load(context))), "Exact source/order survives local round trip.");
        options = options.move(second, -1).presentation(300, PhotoOptions.Fit.CROP);
        check(options.photos.get(0).id.equals(second) && options.intervalSeconds == 300, "Photo order and interval are independent.");
        options = options.replace(first, "content://photos/reconnected", "Reconnected");
        check(options.find(first).uri.endsWith("reconnected") && options.photos.get(1).id.equals(first), "Reconnect retains stable identity/order.");
        check(options.add("content://photos/reconnected", "Duplicate").photos.size() == 2, "Repeated selection does not duplicate sources.");
        PhotoPreferences.save(context, options);
        context.prefs.failNext = true;
        try { PhotoPreferences.save(context, options.remove(first)); throw new AssertionError("Failed save must report error."); }
        catch (IllegalStateException expected) {}
        check(PhotoPreferences.load(context).find(first) != null, "Failed save keeps prior working settings.");
        PhotoOptions empty = options.remove(first).remove(second);
        check(empty.configured && empty.photos.isEmpty(), "Explicitly emptied widget is distinct from fresh setup.");
        String encoded = PhotoPreferences.encode(options);
        reject(encoded.replace("\"seconds\":300", "\"seconds\":0"));
        reject(encoded.replace("\"version\":1", "\"version\":1,\"version\":1"));
        reject(encoded.replace("content://photos/reconnected", "file:///private/image"));
        List<PhotoOptions.Photo> many = new ArrayList<>();
        for (int i = 0; i < 33; i++) many.add(new PhotoOptions.Photo(java.util.UUID.randomUUID().toString(), "content://photos/" + i, "Photo"));
        try { new PhotoOptions(true, 30, PhotoOptions.Fit.CONTAIN, many); throw new AssertionError("Photo count must be bounded."); }
        catch (IllegalArgumentException expected) {}
        context.prefs.values.put("document", "broken");
        try { PhotoPreferences.load(context); throw new AssertionError("Corrupt settings must report unavailable."); }
        catch (IllegalStateException expected) {}
        check("broken".equals(context.prefs.values.get("document")), "Unavailable settings must be retained for recovery.");
        System.out.println("PASS: photo order, replace, remove, interval, fit, bounded sources, persistence and recovery");
    }
    private static void reject(String document) {
        try { PhotoPreferences.decode(document); throw new AssertionError("Invalid document must be rejected."); }
        catch (IllegalArgumentException expected) {}
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
