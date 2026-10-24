package com.vnap.village.script;

import com.vnap.mood.ScriptKind;
import org.jspecify.annotations.Nullable;

/** Dialogue titles the director plays at each step of a rite; null means that rite stays quiet at that step. */
final class RiteLines {
    static final String RING = "Ring Around the Forge";
    static final String CHANT = "Tocsin Chant";
    static final String ABORTED = "Script Aborted";

    private RiteLines() {
    }

    static String gather(ScriptKind kind) {
        return switch (kind) {
            case BUILD_ALTAR -> "Gather for a Dark Altar";
            case SUMMON, BOSS_ACTIVE -> RING;
            case BUILD_FESTOON -> "Gather for a Festoon";
            case BUILD_FESTIVAL -> "Gather for the Festival";
            case BUILD_PAVILION -> "Gather for a Pavilion";
            case BUILD_VIGIL -> "Gather for a Vigil";
            case BUILD_MOURNING -> "Gather to Mourn";
            case BUILD_PYRE -> "Gather for a Pyre";
            case BUILD_MONUMENT, NONE -> "Gather for a Monument";
        };
    }

    static @Nullable String begin(ScriptKind kind) {
        return switch (kind) {
            case BUILD_ALTAR -> "Begin Raising an Altar";
            case BUILD_MONUMENT -> "Begin Building a Monument";
            default -> null;
        };
    }

    static @Nullable String place(ScriptKind kind) {
        return switch (kind) {
            case BUILD_ALTAR -> "Place a Dark Stone";
            case BUILD_MONUMENT -> "Place a Monument Brick";
            default -> null;
        };
    }

    static @Nullable String done(ScriptKind kind) {
        return switch (kind) {
            case BUILD_MONUMENT -> "Consecrate the Monument";
            case BUILD_ALTAR -> "The Altar Awakens";
            case BUILD_FESTOON -> "Festoon Hung";
            case BUILD_FESTIVAL -> "Festival Begins";
            case BUILD_PAVILION -> "Pavilion Raised";
            case BUILD_VIGIL -> "Vigil Lit";
            case BUILD_MOURNING -> "Mourning Laid";
            case BUILD_PYRE -> "Pyre Lit";
            default -> null;
        };
    }
}
