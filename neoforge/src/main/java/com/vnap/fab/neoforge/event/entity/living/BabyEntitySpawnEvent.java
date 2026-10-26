package com.vnap.fab.neoforge.event.entity.living;

/** Two parents about to make a child (com.vnap.fab.mixin.BreedMixin): the child can be changed or the birth cancelled. */
public class BabyEntitySpawnEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.Mob parentA;
	private final net.minecraft.world.entity.Mob parentB;
	private final net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player causedByPlayer;
	private net.minecraft.world.entity.@org.jspecify.annotations.Nullable AgeableMob child;

	public BabyEntitySpawnEvent(net.minecraft.world.entity.Mob parentA, net.minecraft.world.entity.Mob parentB, net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player causedByPlayer, net.minecraft.world.entity.@org.jspecify.annotations.Nullable AgeableMob child) {
		this.parentA = parentA;
		this.parentB = parentB;
		this.causedByPlayer = causedByPlayer;
		this.child = child;
	}

	public net.minecraft.world.entity.Mob getParentA() {
		return parentA;
	}

	public net.minecraft.world.entity.Mob getParentB() {
		return parentB;
	}

	public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player getCausedByPlayer() {
		return causedByPlayer;
	}

	public net.minecraft.world.entity.@org.jspecify.annotations.Nullable AgeableMob getChild() {
		return child;
	}

	public void setChild(net.minecraft.world.entity.@org.jspecify.annotations.Nullable AgeableMob child) {
		this.child = child;
	}
}
