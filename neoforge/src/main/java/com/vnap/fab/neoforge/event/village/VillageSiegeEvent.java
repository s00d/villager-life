package com.vnap.fab.neoforge.event.village;

/** A zombie siege about to begin (com.vnap.fab.mixin.SiegeMixin); a cancel calls it off. */
public class VillageSiegeEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.level.Level level;
	private final net.minecraft.world.phys.Vec3 attemptedSpawnPos;

	public VillageSiegeEvent(net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 attemptedSpawnPos) {
		this.level = level;
		this.attemptedSpawnPos = attemptedSpawnPos;
	}

	public net.minecraft.world.level.Level getLevel() {
		return level;
	}

	public net.minecraft.world.phys.Vec3 getAttemptedSpawnPos() {
		return attemptedSpawnPos;
	}
}
