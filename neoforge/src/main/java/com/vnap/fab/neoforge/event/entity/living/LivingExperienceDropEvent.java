package com.vnap.fab.neoforge.event.entity.living;

/** The experience a dying entity drops (com.vnap.fab.mixin.ExperienceDropMixin). */
public class LivingExperienceDropEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.LivingEntity entity;
	private final net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player attackingPlayer;
	private int droppedExperience;

	public LivingExperienceDropEvent(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player attackingPlayer, int droppedExperience) {
		this.entity = entity;
		this.attackingPlayer = attackingPlayer;
		this.droppedExperience = droppedExperience;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return entity;
	}

	public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player getAttackingPlayer() {
		return attackingPlayer;
	}

	public int getDroppedExperience() {
		return droppedExperience;
	}

	public void setDroppedExperience(int droppedExperience) {
		this.droppedExperience = droppedExperience;
	}
}
