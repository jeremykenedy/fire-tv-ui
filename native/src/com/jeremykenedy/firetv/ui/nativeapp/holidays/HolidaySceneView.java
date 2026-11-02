package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Choreographer;
import android.view.View;

/** Complete static composition with optional independently decoded scene animation layers. */
public final class HolidaySceneView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect frameSource = new Rect();
    private final RectF sceneBounds = new RectF(), characterBounds = new RectF();
    private final HolidayAnimation timeline = new HolidayAnimation();
    private HolidayScenes.Scene scene;
    private Bitmap still, clean, characters;
    private HolidaySceneLoader.Request stillRequest, cleanRequest, characterRequest;
    private boolean resumed, attached, animated, scheduled;
    private int generation, loadedWidth, loadedHeight;
    private long previousFrame;
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            scheduled = false;
            if (!canAnimate()) { previousFrame = 0; return; }
            if (previousFrame != 0) timeline.advance((frameTimeNanos - previousFrame) / 1000000);
            previousFrame = frameTimeNanos;
            invalidate();
            updateAnimation();
        }
    };

    public HolidaySceneView(Context context) {
        super(context);
        setTag("holiday:scene");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        glowPaint.setShader(new RadialGradient(0, 0, 1,
                new int[]{0x90ffcc55, 0x30ff982f, Color.TRANSPARENT},
                new float[]{0, .35f, 1}, Shader.TileMode.CLAMP));
    }

    public void setScene(HolidayScenes.Scene value, boolean animate, int speed) {
        if (value != null && HolidayScenes.find(value.id) != value)
            throw new IllegalArgumentException("Choose a known holiday scene.");
        animated = animate;
        timeline.setSpeed(speed);
        if (scene != value) {
            cancelLoads();
            scene = value;
            still = clean = characters = null;
            loadedWidth = loadedHeight = 0;
            timeline.reset();
        }
        loadImages();
        updateAnimation();
        invalidate();
    }

    public void setResumed(boolean value) {
        resumed = value;
        if (value) loadImages();
        updateAnimation();
    }

    private void loadImages() {
        if (!attached || scene == null || getWidth() <= 0 || getHeight() <= 0) return;
        if (loadedWidth != getWidth() || loadedHeight != getHeight()) {
            cancelLoads();
            loadedWidth = getWidth(); loadedHeight = getHeight();
            final int expected = generation;
            stillRequest = HolidaySceneLoader.load(getContext(), scene, loadedWidth, loadedHeight,
                    new HolidaySceneLoader.Callback() {
                        @Override public void onLoaded(Bitmap bitmap) {
                            if (expected != generation) return;
                            still = bitmap;
                            invalidate();
                        }
                    });
        }
        if (!animated || scene.characterResourceName.isEmpty() || cleanRequest != null) return;
        final int expected = generation;
        cleanRequest = HolidaySceneLoader.loadAnimationBackground(getContext(), scene, loadedWidth, loadedHeight,
                new HolidaySceneLoader.Callback() {
                    @Override public void onLoaded(Bitmap bitmap) {
                        if (expected != generation) return;
                        clean = bitmap;
                        updateAnimation(); invalidate();
                    }
                });
        characterRequest = HolidaySceneLoader.loadCharacters(getContext(), scene,
                new HolidaySceneLoader.Callback() {
                    @Override public void onLoaded(Bitmap bitmap) {
                        if (expected != generation) return;
                        characters = bitmap;
                        updateAnimation(); invalidate();
                    }
                });
    }

    private boolean layersReady() { return clean != null && characters != null; }

    private boolean canAnimate() {
        return animated && layersReady() && attached && resumed && isShown() && hasWindowFocus()
                && getWindowVisibility() == VISIBLE;
    }

    private void updateAnimation() {
        if (canAnimate()) {
            if (!scheduled) {
                scheduled = true;
                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, 32);
            }
        } else {
            if (scheduled) Choreographer.getInstance().removeFrameCallback(frameCallback);
            scheduled = false;
            previousFrame = 0;
        }
    }

    private void cancelLoads() {
        generation++;
        if (stillRequest != null) stillRequest.cancel();
        if (cleanRequest != null) cleanRequest.cancel();
        if (characterRequest != null) characterRequest.cancel();
        stillRequest = cleanRequest = characterRequest = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean layered = animated && layersReady();
        Bitmap background = layered ? clean : still;
        if (background == null) return;
        float scale = Math.max(getWidth() / (float) background.getWidth(), getHeight() / (float) background.getHeight());
        float width = background.getWidth() * scale, height = background.getHeight() * scale;
        sceneBounds.set((getWidth() - width) / 2, (getHeight() - height) / 2,
                (getWidth() + width) / 2, (getHeight() + height) / 2);
        imagePaint.setAlpha(255);
        canvas.drawBitmap(background, null, sceneBounds, imagePaint);
        if (!layered) return;
        drawGlow(canvas, .075f, .73f, .06f);
        drawGlow(canvas, .25f, .72f, .04f);
        drawGlow(canvas, .53f, .81f, .035f);
        float progress = timeline.progress();
        float characterScale = 1 - .5f * progress;
        float characterWidth = width * .215f * characterScale;
        float characterHeight = characterWidth * (characters.getHeight() / 2f) / (characters.getWidth() / 3f);
        float centerX = sceneBounds.left + width * (.82f - .25f * progress);
        float bottom = sceneBounds.top + height * (.87f - .14f * progress);
        characterBounds.set(centerX - characterWidth / 2, bottom - characterHeight,
                centerX + characterWidth / 2, bottom);
        int frame = timeline.frame(), column = frame % 3, row = frame / 3;
        frameSource.set(column * characters.getWidth() / 3, row * characters.getHeight() / 2,
                (column + 1) * characters.getWidth() / 3, (row + 1) * characters.getHeight() / 2);
        imagePaint.setAlpha(timeline.opacity());
        canvas.drawBitmap(characters, frameSource, characterBounds, imagePaint);
        imagePaint.setAlpha(255);
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius) {
        int checkpoint = canvas.save();
        canvas.translate(sceneBounds.left + sceneBounds.width() * x, sceneBounds.top + sceneBounds.height() * y);
        float size = sceneBounds.width() * radius;
        canvas.scale(size, size * .8f);
        glowPaint.setAlpha(Math.round(45 * timeline.flicker()));
        canvas.drawCircle(0, 0, 1, glowPaint);
        canvas.restoreToCount(checkpoint);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached = true; loadImages(); updateAnimation();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false; updateAnimation(); cancelLoads();
        still = clean = characters = null; loadedWidth = loadedHeight = 0;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight); loadImages();
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused); updateAnimation();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (timeline != null) updateAnimation();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (timeline != null) updateAnimation();
    }
}
