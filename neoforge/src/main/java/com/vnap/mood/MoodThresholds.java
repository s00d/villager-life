package com.vnap.mood;

/**
 * Thresholds the rite queue reads. {@code MoodConfig} is the file on disk and copies values here after load.
 */
public final class MoodThresholds {
    private static float happy = 60.0F;
    private static float angry = -60.0F;
    private static float cultReady = -90.0F;
    private static float festoon = 20.0F;
    private static float festival = 50.0F;
    private static float pavilion = 75.0F;
    private static float vigil = -20.0F;
    private static float mourning = -50.0F;
    private static float pyre = -75.0F;

    private MoodThresholds() {
    }

    public static float happy() {
        return happy;
    }

    public static float angry() {
        return angry;
    }

    public static float cultReady() {
        return cultReady;
    }

    public static float festoon() {
        return festoon;
    }

    public static float festival() {
        return festival;
    }

    public static float pavilion() {
        return pavilion;
    }

    public static float vigil() {
        return vigil;
    }

    public static float mourning() {
        return mourning;
    }

    public static float pyre() {
        return pyre;
    }

    public static void set(float happyThreshold, float angryThreshold, float cultReadyThreshold) {
        happy = happyThreshold;
        angry = angryThreshold;
        cultReady = cultReadyThreshold;
    }

    public static void setLesser(float festoonAt, float festivalAt, float pavilionAt,
                                 float vigilAt, float mourningAt, float pyreAt) {
        festoon = festoonAt;
        festival = festivalAt;
        pavilion = pavilionAt;
        vigil = vigilAt;
        mourning = mourningAt;
        pyre = pyreAt;
    }

    /** The mood a rite needs; positive rites need at least this, negative ones at most. */
    public static float of(ScriptKind kind) {
        return switch (kind) {
            case BUILD_FESTOON -> festoon;
            case BUILD_FESTIVAL -> festival;
            case BUILD_MONUMENT -> happy;
            case BUILD_PAVILION -> pavilion;
            case BUILD_VIGIL -> vigil;
            case BUILD_MOURNING -> mourning;
            case BUILD_ALTAR -> angry;
            case BUILD_PYRE -> pyre;
            case SUMMON, BOSS_ACTIVE -> cultReady;
            case NONE -> 0.0F;
        };
    }
}
