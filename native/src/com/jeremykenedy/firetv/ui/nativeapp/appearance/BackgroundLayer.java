package com.jeremykenedy.firetv.ui.nativeapp.appearance;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;
import com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidayScenes;
import com.jeremykenedy.firetv.ui.nativeapp.holidays.HolidaySceneView;

/** Shared composition for Home, settings and previews, ordered below interactive content. */
public final class BackgroundLayer extends FrameLayout {
    public enum MediaState { SYSTEM, LOADING, READY, UNAVAILABLE }
    public interface MediaListener { void changed(MediaState state, String message); }

    private final ImageView wallpaper;
    private final ImageView featured;
    private final ImageView blurred;
    private boolean scrollBlur;
    private Bitmap blurFrame;
    private final HolidaySceneView scene;
    private final VideoView video;
    private final AppearanceView base, overlay;
    private WallpaperImages.Request imageRequest;
    private int generation;
    private boolean resumed, videoFrozen;
    private String mediaKey = "", mediaMode = "system";
    private MediaState mediaState = MediaState.SYSTEM;
    private String mediaMessage = "Using system wallpaper";
    private MediaListener mediaListener;
    private Runnable contentChanged;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener appearanceChanged =
            new android.content.SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(android.content.SharedPreferences preferences, String key) {
                    post(new Runnable() { @Override public void run() { if (isAttachedToWindow()) refresh(); } });
                }
            };

    public BackgroundLayer(Context context) {
        super(context);
        setTag("appearance:layers");
        setBackgroundColor(0x00101523);
        video = new VideoView(context);
        video.setTag("appearance:video");
        addView(video, new LayoutParams(-1, -1, android.view.Gravity.CENTER));
        video.setVisibility(GONE);
        video.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(android.media.MediaPlayer player) {
                if (!mediaMode.equals("video") || video.getVisibility() != VISIBLE) return;
                player.setLooping(true);
                player.setVolume(0, 0);
                mediaStatus(MediaState.READY, "Saved video ready. Sound is off.");
                if(videoFrozen) video.seekTo(1);
                updateVideo();
            }
        });
        video.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
            @Override public boolean onError(android.media.MediaPlayer player, int what, int extra) {
                if (mediaMode.equals("video")) {
                    video.setVisibility(GONE);
                    mediaStatus(MediaState.UNAVAILABLE, "Saved video is unavailable. Retry or choose a video again. The saved reference is kept.");
                }
                return true;
            }
        });
        wallpaper = new ImageView(context);
        wallpaper.setTag("appearance:wallpaper");
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(wallpaper, new LayoutParams(-1, -1));
        base = new AppearanceView(context, false);
        base.setTag("appearance:base");
        addView(base, new LayoutParams(-1, -1));
        scene = new HolidaySceneView(context);
        addView(scene, new LayoutParams(-1, -1));
        featured = new ImageView(context);
        featured.setTag("appearance:featured");
        featured.setScaleType(ImageView.ScaleType.CENTER_CROP);
        featured.setVisibility(GONE);
        addView(featured, new LayoutParams(-1, -1));
        overlay = new AppearanceView(context, true);
        overlay.setTag("appearance:overlay");
        addView(overlay, new LayoutParams(-1, -1));
        blurred = new ImageView(context);
        blurred.setTag("appearance:scroll-blur");
        blurred.setScaleType(ImageView.ScaleType.FIT_XY);
        blurred.setVisibility(GONE);
        addView(blurred, new LayoutParams(-1, -1));
    }

    public static BackgroundLayer attach(Activity activity, View content) {
        ViewGroup parent = (ViewGroup) content.getParent();
        if (parent != null) parent.removeView(content);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        BackgroundLayer layer = new BackgroundLayer(activity);
        content.setBackgroundColor(0x66101523);
        layer.addView(content, new LayoutParams(-1, -1));
        activity.setContentView(layer);
        return layer;
    }

    public void setMediaListener(MediaListener listener) {
        mediaListener = listener;
        if (listener != null) listener.changed(mediaState, mediaMessage);
    }

    public MediaState mediaState() { return mediaState; }

    public void setContentChangedListener(Runnable listener) { contentChanged = listener; }

    public void setFeaturedArtwork(Bitmap artwork) {
        featured.setImageBitmap(artwork);
        featured.setVisibility(artwork == null ? GONE : VISIBLE);
        refreshBlur();
        if (contentChanged != null) contentChanged.run();
    }

    public void setScrollBlur(boolean enabled) {
        if (scrollBlur == enabled) return;
        scrollBlur = enabled;
        refreshBlur();
    }

    public Bitmap blurredRegion(android.graphics.Rect region) {
        if (region.width() <= 0 || region.height() <= 0) return null;
        Bitmap result = Bitmap.createBitmap(Math.max(1, region.width() / 20), Math.max(1, region.height() / 20), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(result);
        canvas.scale(result.getWidth() / (float) region.width(), result.getHeight() / (float) region.height());
        canvas.translate(-region.left, -region.top);
        for (View layer : new View[]{wallpaper, base, scene, featured, overlay})
            if (layer.getVisibility() == VISIBLE) layer.draw(canvas);
        return result;
    }

    private void refreshBlur() {
        if (!scrollBlur || getWidth() == 0 || getHeight() == 0) {
            blurred.setVisibility(GONE);
            return;
        }
        blurFrame = blurredRegion(new android.graphics.Rect(0, 0, getWidth(), getHeight()));
        blurred.setImageBitmap(blurFrame);
        if (blurred.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)
            ((android.graphics.drawable.BitmapDrawable) blurred.getDrawable()).setFilterBitmap(true);
        blurred.setVisibility(VISIBLE);
    }

    public void retryMedia() {
        mediaKey = "";
        refresh();
    }

    public void refresh() {
        if (!com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(getContext())) return;
        AppearanceOptions options = AppearancePreferences.load(getContext());
        boolean reducedMotion = AppearancePreferences.reducedMotion(getContext());
        videoFrozen = reducedMotion || AppearancePreferences.videoFrozen(getContext());
        base.setReducedMotion(reducedMotion);
        overlay.setReducedMotion(reducedMotion);
        base.setOptions(options);
        overlay.setOptions(options);
        String mode = AppearancePreferences.wallpaperMode(getContext());
        String nextMedia = mode + ":" + options.imageUri.length() + ":" + options.imageUri
                + ":" + options.videoUri.length() + ":" + options.videoUri;
        if (!mediaKey.equals(nextMedia)) loadMedia(mode, options, nextMedia);
        scene.setScene(HolidayScenes.find(options.scene), !options.baseFrozen && !reducedMotion, options.baseSpeed);
        updateVideo();
        refreshBlur();
    }

    private void loadMedia(String mode, AppearanceOptions options, String key) {
        mediaKey = key;
        mediaMode = mode;
        final int request = ++generation;
        if (imageRequest != null) imageRequest.cancel();
        imageRequest = null;
        video.stopPlayback();
        video.setVisibility(GONE);
        wallpaper.setImageDrawable(null);
        if (mode.equals("image")) {
            mediaStatus(MediaState.LOADING, "Loading saved image");
            imageRequest = WallpaperImages.load(getContext(), options.imageUri, new WallpaperImages.Callback() {
                @Override public void loaded(Bitmap bitmap) {
                    if (request != generation) { if (bitmap != null) bitmap.recycle(); return; }
                    imageRequest = null;
                    wallpaper.setImageBitmap(bitmap);
                    refreshBlur();
                    if (contentChanged != null) contentChanged.run();
                    mediaStatus(bitmap == null ? MediaState.UNAVAILABLE : MediaState.READY,
                            bitmap == null ? "Saved image is unavailable. Retry or choose an image again. The saved reference is kept." : "Saved image ready");
                }
            });
        } else if (mode.equals("video")) {
            Uri uri = Uri.parse(options.videoUri);
            if (options.videoUri.isEmpty() || !WallpaperAccess.supported(getContext(), uri)) {
                mediaStatus(MediaState.UNAVAILABLE, "Saved video is unavailable. Choose a video again. The saved reference is kept.");
            } else {
                mediaStatus(MediaState.LOADING, "Loading saved video");
                video.setVisibility(VISIBLE);
                video.setVideoURI(uri);
            }
        } else {
            mediaStatus(MediaState.SYSTEM, "Using system wallpaper");
        }
    }

    private void mediaStatus(MediaState state, String message) {
        mediaState = state;
        mediaMessage = message;
        if (mediaListener != null) mediaListener.changed(state, message);
    }

    private void updateVideo() {
        if (video == null) return;
        if (resumed && !videoFrozen && hasWindowFocus() && isShown() && getWindowVisibility() == VISIBLE
                && video.getVisibility() == VISIBLE) video.start();
        else if (video.isPlaying()) video.pause();
    }

    public void setResumed(boolean value) {
        if (value && !com.jeremykenedy.firetv.ui.nativeapp.SourceStartup.ready(getContext())) value = false;
        resumed = value;
        if (value) refresh();
        else if (imageRequest != null) {
            imageRequest.cancel();
            imageRequest = null;
            generation++;
            mediaKey = "";
        }
        updateVideo();
        base.setResumed(value);
        scene.setResumed(value);
        overlay.setResumed(value);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        updateVideo();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateVideo();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (base != null && resumed) refresh();
        else if (blurred != null) refreshBlur();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AppearancePreferences.file(getContext()).registerOnSharedPreferenceChangeListener(appearanceChanged);
    }

    @Override protected void onDetachedFromWindow() {
        AppearancePreferences.file(getContext()).unregisterOnSharedPreferenceChangeListener(appearanceChanged);
        setResumed(false);
        generation++;
        mediaKey = "";
        video.stopPlayback();
        super.onDetachedFromWindow();
    }
}
