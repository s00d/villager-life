package com.vnap.neo.neoforge.event.village;

public class VillageSiegeEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(VillageSiegeEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.village.VillageSiegeEvent.class, priority, VillageSiegeEvent::new, sink));
	}

	private final net.minecraftforge.event.village.VillageSiegeEvent f;

	VillageSiegeEvent(net.minecraftforge.event.village.VillageSiegeEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.level.Level getLevel() {
		return f.getLevel();
	}

	public net.minecraft.world.phys.Vec3 getAttemptedSpawnPos() {
		return f.getAttemptedSpawnPos();
	}
}
