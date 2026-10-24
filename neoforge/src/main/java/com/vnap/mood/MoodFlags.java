package com.vnap.mood;

public final class MoodFlags {
    public static final int HAS_MONUMENT = 1;
    public static final int HAS_ALTAR = 1 << 1;
    public static final int ALTAR_TAINTED = 1 << 2;
    public static final int BOSS_SLAIN_ONCE = 1 << 3;
    public static final int SCAR = 1 << 4;
    public static final int FESTOON = 1 << 5;
    public static final int VIGIL = 1 << 6;
    public static final int FESTIVAL = 1 << 7;
    public static final int MOURNING = 1 << 8;
    public static final int PAVILION = 1 << 9;
    public static final int PYRE = 1 << 10;
    /** Garlands and candles may return once mood is calm again. */
    public static final int EPHEMERAL = FESTOON | VIGIL;

    private MoodFlags() {
    }

    public static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }

    public static int set(int flags, int bit) {
        return flags | bit;
    }

    public static int clear(int flags, int bit) {
        return flags & ~bit;
    }
}
