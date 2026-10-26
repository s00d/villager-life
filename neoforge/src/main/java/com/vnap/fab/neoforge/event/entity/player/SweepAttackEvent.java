package com.vnap.fab.neoforge.event.entity.player;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** A player's melee attack, just before the blow lands (com.vnap.fab.mixin.PlayerAttackMixin). */
public final class SweepAttackEvent extends Event {

	private final Player player;
	private final Entity target;

	public SweepAttackEvent(Player player, Entity target) {
		this.player = player;
		this.target = target;
	}

	public Player getEntity() {
		return player;
	}

	public Entity getTarget() {
		return target;
	}
}
