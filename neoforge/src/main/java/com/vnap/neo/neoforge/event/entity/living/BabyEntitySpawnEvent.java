package com.vnap.neo.neoforge.event.entity.living;

public class BabyEntitySpawnEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(BabyEntitySpawnEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.BabyEntitySpawnEvent.class, priority, BabyEntitySpawnEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.BabyEntitySpawnEvent f;

	BabyEntitySpawnEvent(net.minecraftforge.event.entity.living.BabyEntitySpawnEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.Mob getParentA() {
		return f.getParentA();
	}

	public net.minecraft.world.entity.Mob getParentB() {
		return f.getParentB();
	}

	public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player getCausedByPlayer() {
		return f.getCausedByPlayer();
	}

	public net.minecraft.world.entity.@org.jspecify.annotations.Nullable AgeableMob getChild() {
		return f.getChild();
	}

	public void setChild(net.minecraft.world.entity.@org.jspecify.annotations.Nullable AgeableMob child) {
		f.setChild(child);
	}
}
