package com.vnap.block;

public enum AltarState {
    SMOULDERING,
    READY,
    CHARGING,
    TAINTED,
    RUINED;

    public static AltarState fromId(String id) {
        try {
            return AltarState.valueOf(id);
        } catch (RuntimeException ignored) {
            return SMOULDERING;
        }
    }
}
