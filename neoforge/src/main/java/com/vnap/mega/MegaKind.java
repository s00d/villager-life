package com.vnap.mega;

/** The megastructures (docs/tower/PORTALS.md): each one controller, one plan (Shape), its own work. */
public enum MegaKind {
	/** The Builders' Quarry: digs a 32×32 square under it layer by layer down to the bedrock (Quarry). */
	QUARRY,
	/** A Waygate: its keystone in the controller; two of a colour are one way (Waygates). */
	GATE,
	/** The Builders' Smelter: a 3×3×3 furnace that smelts from the chest on its left into the one on its right, ores twice (MegaWork). */
	SMELTER,
	/** The Beacon of Calm: no monster is born within 64 blocks of it (MegaWork.calm). */
	CALM,
	/** The Dome of Growth: the plants within 16 grow eight times as fast (MegaWork). */
	DOME,
	/** The Well of Lore: the experience of the deaths within 16 gathers in it; a click gives it out (MegaWork). */
	WELL,
	/** The Drain: the water and lava within 24 drawn away, a source a tick (MegaWork). */
	DRAIN,
	/** The Sawmill: the trees within 12 felled whole, leaves and all, into the chest beside it, a sapling set back (MegaWork). */
	SAWMILL;

	public Shape shape() {
		return switch (this) {
			case QUARRY -> Shape.QUARRY;
			case GATE -> Shape.GATE;
			case SMELTER -> Shape.SMELTER;
			case CALM -> Shape.CALM;
			case DOME -> Shape.DOME;
			case WELL -> Shape.WELL;
			case DRAIN -> Shape.DRAIN;
			case SAWMILL -> Shape.SAWMILL;
		};
	}

	public String id() {
		return shape().id;
	}
}
