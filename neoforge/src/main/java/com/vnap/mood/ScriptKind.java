package com.vnap.mood;

public enum ScriptKind {
    NONE,
    BUILD_FESTOON,
    BUILD_VIGIL,
    BUILD_FESTIVAL,
    BUILD_MOURNING,
    BUILD_MONUMENT,
    BUILD_ALTAR,
    BUILD_PAVILION,
    BUILD_PYRE,
    SUMMON,
    BOSS_ACTIVE;

    public static ScriptKind fromId(String id) {
        try {
            return ScriptKind.valueOf(id);
        } catch (RuntimeException ignored) {
            return NONE;
        }
    }
}
