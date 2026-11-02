package com.jeremykenedy.firetv.ui.nativeapp.widget.breathing;

public final class BreathingSession {
    public final int inhale;
    public final int hold;
    public final int exhale;
    public final int rest;
    public final int duration;
    public final boolean reducedMotion;
    public final long elapsed;
    public final long anchor;
    public final boolean running;

    public BreathingSession(int inhale, int hold, int exhale, int rest, int duration,
                            boolean reducedMotion, long elapsed, long anchor, boolean running) {
        if (inhale < 2 || inhale > 12 || exhale < 2 || exhale > 12 || hold < 0 || hold > 8 || rest < 0 || rest > 8
                || duration < 30 || duration > 1800 || elapsed < 0 || elapsed > duration * 1000L || anchor < 0) {
            throw new IllegalArgumentException("Inhale/exhale: 2 through 12 seconds. Holds: 0 through 8. Session: 30 through 1800 seconds.");
        }
        this.inhale = inhale; this.hold = hold; this.exhale = exhale; this.rest = rest;
        this.duration = duration; this.reducedMotion = reducedMotion;
        this.elapsed = elapsed; this.anchor = anchor; this.running = running;
    }

    public static BreathingSession defaults() { return new BreathingSession(4, 0, 6, 0, 120, false, 0, 0, false); }

    public long elapsedAt(long now) {
        return Math.min(duration * 1000L, elapsed + (running ? Math.max(0, now - anchor) : 0));
    }

    public BreathingSession start(long now) {
        if (running) return this;
        return copy(elapsed == duration * 1000L ? 0 : elapsed, now, true);
    }

    public BreathingSession pause(long now) { return copy(elapsedAt(now), 0, false); }
    public BreathingSession reset() { return copy(0, 0, false); }
    private BreathingSession copy(long elapsed, long anchor, boolean running) {
        return new BreathingSession(inhale, hold, exhale, rest, duration, reducedMotion, elapsed, anchor, running);
    }

    public Phase phase(long now) {
        long elapsed = elapsedAt(now);
        if (elapsed >= duration * 1000L) return new Phase("Complete", 0, 0.0f);
        long position = elapsed % ((inhale + hold + exhale + rest) * 1000L);
        int[] seconds = {inhale, hold, exhale, rest};
        String[] names = {"Inhale", "Hold", "Exhale", "Rest"};
        for (int i = 0; i < seconds.length; i++) {
            long length = seconds[i] * 1000L;
            if (position < length) {
                float progress = (float) position / length;
                float ease = (float) (0.5 - 0.5 * Math.cos(Math.PI * progress));
                return new Phase(names[i], length - position, i == 0 ? ease : i == 1 ? 1.0f : i == 2 ? 1.0f - ease : 0.0f);
            }
            position -= length;
        }
        throw new IllegalStateException("Invalid breathing phase.");
    }

    public static final class Phase {
        public final String name;
        public final long remaining;
        public final float expansion;
        Phase(String name, long remaining, float expansion) { this.name = name; this.remaining = remaining; this.expansion = expansion; }
    }
}
