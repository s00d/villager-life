package com.vnap.fab.neoforge.event.entity.player;

/** Whether a player's hit is critical (com.vnap.fab.mixin.PlayerAttackMixin). */
public class CriticalHitEvent extends com.vnap.fab.bus.api.Event {
	private final net.minecraft.world.entity.player.Player player;
	private final net.minecraft.world.entity.Entity target;
	private final boolean vanillaCritical;
	private boolean criticalHit;

	public CriticalHitEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.Entity target, boolean vanillaCritical, boolean criticalHit) {
		this.player = player;
		this.target = target;
		this.vanillaCritical = vanillaCritical;
		this.criticalHit = criticalHit;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public net.minecraft.world.entity.Entity getTarget() {
		return target;
	}

	public boolean isVanillaCritical() {
		return vanillaCritical;
	}

	public boolean isCriticalHit() {
		return criticalHit;
	}

	public void setCriticalHit(boolean criticalHit) {
		this.criticalHit = criticalHit;
	}
}
