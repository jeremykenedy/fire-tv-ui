package com.jeremykenedy.firetv.ui;

/** Stable independent motion without allocating objects on every frame. */
final class MenuBackgroundMotion {
    private MenuBackgroundMotion() {}

    static float seed(int index, int salt) {
        int value = index * 0x9e3779b9 + salt * 0x85ebca6b;
        value ^= value >>> 16;
        value *= 0x7feb352d;
        value ^= value >>> 15;
        value *= 0x846ca68b;
        value ^= value >>> 16;
        return (value & 0xffffff) / 16777216f;
    }

    static float wrap(double value) {
        return (float) (value - Math.floor(value));
    }

    static float envelope(float position) {
        float edge = Math.min(position, 1 - position) * 8;
        edge = Math.max(0, Math.min(1, edge));
        return edge * edge * (3 - 2 * edge);
    }

    static float wave(double position, double time, int band) {
        return (float) (Math.sin(position * Math.PI * 2 + time + band * .73) * .72
                + Math.sin(position * Math.PI * 4 - time * .61 + band * 1.17) * .28);
    }

    static float waveSlope(double position, double time, int band) {
        return (float) (Math.cos(position * Math.PI * 2 + time + band * .73) * Math.PI * 1.44
                + Math.cos(position * Math.PI * 4 - time * .61 + band * 1.17) * Math.PI * 1.12);
    }
}
