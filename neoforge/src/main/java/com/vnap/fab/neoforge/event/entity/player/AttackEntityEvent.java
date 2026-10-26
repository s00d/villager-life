package com.vnap.fab.neoforge.event.entity.player;

/** A player swinging at an entity (Fabric API AttackEntityCallback); a cancel stops the attack. */
public class AttackEntityEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.entity.Entity target;

	public AttackEntityEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target) {
		this.player = player;
		this.target = target;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.world.entity.Entity getTarget() {
		return target;
	}
}
