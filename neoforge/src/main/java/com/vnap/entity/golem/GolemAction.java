package com.vnap.entity.golem;

/** Every animated state of the Tocsin Golem. {@code length} is the full clip in ticks. */
public enum GolemAction {
    NONE(0, 0, 0, false, 0),
    AWAKEN(1, 80, 0, true, 0),
    SWING_COMBO(2, 40, 1, false, 4),
    FIST_SLAM(3, 80, 1, false, 3),
    GRAB_THROW(4, 36, 1, false, 2),
    BELL_TOLL(5, 50, 1, false, 2),
    KNEEL(6, 40, 2, true, 0),
    BELL_WAVE(7, 56, 2, false, 3),
    ANVIL_RAIN(8, 40, 2, false, 3),
    CHAIN_PULL(9, 30, 2, false, 2),
    CRACK(10, 60, 3, true, 0),
    CHARGE(11, 66, 3, false, 3),
    MOLTEN_SLAM(12, 50, 3, false, 3),
    VENT(13, 60, 3, false, 0),
    STUN(14, 40, 0, false, 0),
    DEATH(15, 60, 0, true, 0);

    /** Network and animation index; stable, never reorder. */
    public final int id;
    public final int length;
    /** First phase where the golem may pick this attack on its own. */
    public final int phase;
    public final boolean invulnerable;
    public final int weight;

    GolemAction(int id, int length, int phase, boolean invulnerable, int weight) {
        this.id = id;
        this.length = length;
        this.phase = phase;
        this.invulnerable = invulnerable;
        this.weight = weight;
    }

    public boolean attack() {
        return weight > 0;
    }

    public static GolemAction byId(int id) {
        for (GolemAction action : values()) {
            if (action.id == id) {
                return action;
            }
        }
        return NONE;
    }
}
