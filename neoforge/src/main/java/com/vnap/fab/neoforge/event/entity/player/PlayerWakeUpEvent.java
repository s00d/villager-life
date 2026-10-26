package com.vnap.fab.neoforge.event.entity.player;

/** A player out of bed (com.vnap.fab.mixin.WakeUpMixin). */
public class PlayerWakeUpEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.player.Player player;
	private final boolean wakeImmediately;
	private final boolean updateLevel;

	public PlayerWakeUpEvent(net.minecraft.world.entity.player.Player player, boolean wakeImmediately, boolean updateLevel) {
		this.player = player;
		this.wakeImmediately = wakeImmediately;
		this.updateLevel = updateLevel;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public boolean wakeImmediately() {
		return wakeImmediately;
	}

	public boolean updateLevel() {
		return updateLevel;
	}
}
