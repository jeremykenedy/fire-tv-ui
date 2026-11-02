package com.jeremykenedy.firetv.ui.nativeapp.widget.photos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class PhotoOptions {
    public static final int MAX_PHOTOS = 32;
    public static final int[] INTERVALS = {10, 30, 60, 300, 900};
    public enum Fit { CONTAIN, CROP }
    public static final class Photo {
        public final String id, uri, label;
        public Photo(String id, String uri, String label) {
            if (id == null || !id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")) throw invalid();
            if (uri == null || uri.length() > 2048 || !uri.startsWith("content://") || uri.length() < 11 || controls(uri)) throw invalid();
            if (label == null || label.isEmpty() || label.length() > 160 || controls(label)) throw invalid();
            this.id = id; this.uri = uri; this.label = label;
        }
    }
    public final boolean configured;
    public final int intervalSeconds;
    public final Fit fit;
    public final List<Photo> photos;
    public PhotoOptions(boolean configured, int seconds, Fit fit, List<Photo> photos) {
        boolean interval = false;
        for (int candidate : INTERVALS) if (candidate == seconds) interval = true;
        if (!interval || fit == null || photos == null || photos.size() > MAX_PHOTOS) throw invalid();
        HashSet<String> ids = new HashSet<>(), uris = new HashSet<>();
        for (Photo photo : photos) if (photo == null || !ids.add(photo.id) || !uris.add(photo.uri)) throw invalid();
        if (!configured && !photos.isEmpty()) throw invalid();
        this.configured = configured; intervalSeconds = seconds; this.fit = fit;
        this.photos = Collections.unmodifiableList(new ArrayList<>(photos));
    }
    public static PhotoOptions defaults() { return new PhotoOptions(false, 30, Fit.CONTAIN, Collections.<Photo>emptyList()); }
    public Photo find(String id) { for (Photo photo : photos) if (photo.id.equals(id)) return photo; return null; }
    public PhotoOptions add(String uri, String label) {
        for (Photo photo : photos) if (photo.uri.equals(uri)) return this;
        List<Photo> next = new ArrayList<>(photos); next.add(new Photo(UUID.randomUUID().toString(), uri, label));
        return new PhotoOptions(true, intervalSeconds, fit, next);
    }
    public PhotoOptions replace(String id, String uri, String label) {
        List<Photo> next = new ArrayList<>(photos);
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(id)) {
            next.set(i, new Photo(id, uri, label)); return new PhotoOptions(true, intervalSeconds, fit, next);
        }
        throw invalid();
    }
    public PhotoOptions remove(String id) {
        List<Photo> next = new ArrayList<>(photos);
        for (Photo photo : photos) if (photo.id.equals(id)) next.remove(photo);
        return new PhotoOptions(true, intervalSeconds, fit, next);
    }
    public PhotoOptions move(String id, int delta) {
        if (delta != -1 && delta != 1) throw invalid();
        List<Photo> next = new ArrayList<>(photos);
        for (int i = 0; i < next.size(); i++) if (next.get(i).id.equals(id)) {
            int target = Math.max(0, Math.min(next.size() - 1, i + delta));
            Photo photo = next.remove(i); next.add(target, photo);
            return new PhotoOptions(configured, intervalSeconds, fit, next);
        }
        throw invalid();
    }
    public PhotoOptions presentation(int seconds, Fit fit) { return new PhotoOptions(configured, seconds, fit, photos); }
    private static boolean controls(String text) { for (int i = 0; i < text.length(); i++) if (Character.isISOControl(text.charAt(i))) return true; return false; }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid photo frame settings."); }
}
