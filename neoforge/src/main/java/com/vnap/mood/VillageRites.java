package com.vnap.mood;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Picks the one missing rite. The strongest rite the mood allows goes first: a jump to −100 raises the forge at once,
 * and the vigil, mourning and pyre follow after it. Starting a script stays in the loader module.
 */
public final class VillageRites {
    /** Strongest first. */
    public static final List<ScriptKind> NEGATIVE = List.of(
            ScriptKind.BUILD_ALTAR, ScriptKind.SUMMON, ScriptKind.BUILD_PYRE,
            ScriptKind.BUILD_MOURNING, ScriptKind.BUILD_VIGIL);
    /** Strongest first. */
    public static final List<ScriptKind> POSITIVE = List.of(
            ScriptKind.BUILD_MONUMENT, ScriptKind.BUILD_PAVILION,
            ScriptKind.BUILD_FESTIVAL, ScriptKind.BUILD_FESTOON);
    /** Every rite that raises a structure, in threshold order for books and recipe viewers. */
    public static final List<ScriptKind> BUILDS = List.of(
            ScriptKind.BUILD_FESTOON, ScriptKind.BUILD_FESTIVAL, ScriptKind.BUILD_MONUMENT, ScriptKind.BUILD_PAVILION,
            ScriptKind.BUILD_VIGIL, ScriptKind.BUILD_MOURNING, ScriptKind.BUILD_ALTAR, ScriptKind.BUILD_PYRE);

    private VillageRites() {
    }

    public static boolean clearEphemeral(MoodState state) {
        if (Math.abs(state.mood) <= 10.0F && MoodFlags.has(state.flags, MoodFlags.EPHEMERAL)) {
            state.flags = MoodFlags.clear(state.flags, MoodFlags.EPHEMERAL);
            return true;
        }
        return false;
    }

    public static @Nullable ScriptKind nextMissing(MoodState state) {
        List<ScriptKind> order = state.mood < 0.0F ? NEGATIVE : POSITIVE;
        for (ScriptKind kind : order) {
            if (missing(state, kind)) {
                return kind;
            }
        }
        return null;
    }

    public static boolean positive(ScriptKind kind) {
        return POSITIVE.contains(kind);
    }

    /** True when mood has reached the rite's threshold and the village does not have it yet. */
    public static boolean missing(MoodState state, ScriptKind kind) {
        float at = MoodThresholds.of(kind);
        boolean reached = positive(kind) ? state.mood >= at : state.mood <= at;
        if (!reached) {
            return false;
        }
        return switch (kind) {
            case BUILD_MONUMENT -> !state.hasMonument();
            case BUILD_ALTAR -> !state.hasAltar();
            case SUMMON -> state.hasAltar() && !state.altarTainted();
            case NONE, BOSS_ACTIVE -> false;
            default -> !MoodFlags.has(state.flags, flagFor(kind));
        };
    }

    public static int flagFor(ScriptKind kind) {
        return switch (kind) {
            case BUILD_FESTOON -> MoodFlags.FESTOON;
            case BUILD_VIGIL -> MoodFlags.VIGIL;
            case BUILD_FESTIVAL -> MoodFlags.FESTIVAL;
            case BUILD_MOURNING -> MoodFlags.MOURNING;
            case BUILD_PAVILION -> MoodFlags.PAVILION;
            case BUILD_PYRE -> MoodFlags.PYRE;
            case BUILD_MONUMENT -> MoodFlags.HAS_MONUMENT;
            case BUILD_ALTAR -> MoodFlags.HAS_ALTAR;
            default -> 0;
        };
    }

    public static void markDone(MoodState state, ScriptKind kind) {
        int bit = flagFor(kind);
        if (bit != 0) {
            state.flags = MoodFlags.set(state.flags, bit);
        }
    }

    public static String schematicName(ScriptKind kind) {
        return switch (kind) {
            case BUILD_ALTAR, SUMMON, BOSS_ACTIVE -> "tocsin_forge";
            case BUILD_FESTOON -> "festoon";
            case BUILD_VIGIL -> "vigil";
            case BUILD_FESTIVAL -> "festival";
            case BUILD_MOURNING -> "mourning";
            case BUILD_PAVILION -> "pavilion";
            case BUILD_PYRE -> "pyre";
            case BUILD_MONUMENT, NONE -> "monument";
        };
    }
}
