package com.vnap.fab.neoforge.event.entity.living;

/** A player taming an animal (com.vnap.fab.mixin.TameMixin); a cancel keeps it wild. */
public class AnimalTameEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.animal.Animal animal;
	private final net.minecraft.world.entity.player.Player tamer;

	public AnimalTameEvent(net.minecraft.world.entity.animal.Animal animal, net.minecraft.world.entity.player.Player tamer) {
		this.animal = animal;
		this.tamer = tamer;
	}

	public net.minecraft.world.entity.animal.Animal getAnimal() {
		return animal;
	}

	public net.minecraft.world.entity.animal.Animal getEntity() {
		return animal;
	}

	public net.minecraft.world.entity.player.Player getTamer() {
		return tamer;
	}
}
