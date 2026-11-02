package com.jeremykenedy.firetv.ui.nativeapp.holidays;

import java.util.Random;

/** Fixed-size scene particles. Positions advance only with the owning view's active timeline. */
public final class SceneSnowfall {
    public static final int COUNT = 64;
    private final float[] x = new float[COUNT], y = new float[COUNT], radius = new float[COUNT];
    private final float[] drift = new float[COUNT], velocity = new float[COUNT], angle = new float[COUNT];
    private final int[] depth = new int[COUNT];
    private final long seed;

    public SceneSnowfall() { this(0x4348524953544d41L); }

    public SceneSnowfall(long seed) { this.seed = seed; reset(); }

    public void reset() {
        Random random = new Random(seed);
        for (int index = 0; index < COUNT; index++) {
            depth[index] = index < 28 ? 0 : index < 52 ? 1 : 2;
            x[index] = random.nextFloat(); y[index] = random.nextFloat();
            radius[index] = depth[index] == 0 ? .6f + random.nextFloat() * .6f
                    : depth[index] == 1 ? 1.6f + random.nextFloat() * 1.2f : 3.8f + random.nextFloat() * 2.4f;
            velocity[index] = (.012f + depth[index] * .014f) * (.8f + random.nextFloat() * .4f);
            drift[index] = .002f + random.nextFloat() * .004f;
            angle[index] = random.nextFloat() * 360;
        }
    }

    public void advance(long deltaMillis, int speed) {
        float seconds = Math.max(0, Math.min(100, deltaMillis)) / 1000f;
        float rate = .45f + Math.max(0, Math.min(100, speed)) * .018f;
        for (int index = 0; index < COUNT; index++) {
            float sway = (float) Math.sin(angle[index] * Math.PI / 180) * drift[index];
            x[index] = wrap(x[index] + (drift[index] + sway) * seconds * rate);
            y[index] = wrap(y[index] + velocity[index] * seconds * rate);
            angle[index] = (angle[index] + (3 + depth[index] * 4) * seconds * rate) % 360;
        }
    }

    public float x(int index) { return x[index]; }
    public float y(int index) { return y[index]; }
    public float radius(int index) { return radius[index]; }
    public float angle(int index) { return angle[index]; }
    public int depth(int index) { return depth[index]; }

    private static float wrap(float value) { return value - (float) Math.floor(value); }
}
