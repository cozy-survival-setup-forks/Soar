package dev.soar;

import java.util.function.IntPredicate;

/** Flight speed levels. Level 1 is the normal speed and every level above it adds one step. */
final class Speeds {

    /** The speed Minecraft gives a flying player. */
    static final float NORMAL = 0.1f;
    /** The most levels there can be, and the number of tempfly.speed.N permissions in plugin.yml. */
    static final int LIMIT = 20;

    private Speeds() {
    }

    /** The highest level up to {@code max} that {@code has} accepts. Level 1 is always allowed. */
    static int allowed(int max, IntPredicate has) {
        for (int level = Math.min(max, LIMIT); level > 1; level--) {
            if (has.test(level)) return level;
        }
        return 1;
    }

    /** The fly speed of a level. Minecraft only takes speeds from 0 to 1. */
    static float speed(int level, double step) {
        return (float) Math.max(0.01, Math.min(1.0, level * step));
    }
}
