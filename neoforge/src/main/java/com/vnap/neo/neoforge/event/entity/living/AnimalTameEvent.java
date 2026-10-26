package com.vnap.neo.neoforge.event.entity.living;

public class AnimalTameEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(AnimalTameEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.AnimalTameEvent.class, priority, AnimalTameEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.AnimalTameEvent f;

	AnimalTameEvent(net.minecraftforge.event.entity.living.AnimalTameEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.animal.Animal getAnimal() {
		return f.getAnimal();
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.entity.player.Player getTamer() {
		return f.getTamer();
	}
}
