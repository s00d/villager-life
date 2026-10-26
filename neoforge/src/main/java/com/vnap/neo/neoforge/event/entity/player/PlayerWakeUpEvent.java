package com.vnap.neo.neoforge.event.entity.player;

public class PlayerWakeUpEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(PlayerWakeUpEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerWakeUpEvent.class, priority, PlayerWakeUpEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.PlayerWakeUpEvent f;

	PlayerWakeUpEvent(net.minecraftforge.event.entity.player.PlayerWakeUpEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public boolean wakeImmediately() {
		return f.wakeImmediately();
	}

	public boolean updateLevel() {
		return f.updateLevel();
	}
}
