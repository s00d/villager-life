package com.vnap.neo.neoforge.event.entity.player;

public class PlayerDestroyItemEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(PlayerDestroyItemEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerDestroyItemEvent.class, priority, PlayerDestroyItemEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.PlayerDestroyItemEvent f;

	PlayerDestroyItemEvent(net.minecraftforge.event.entity.player.PlayerDestroyItemEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.item.ItemStack getOriginal() {
		return f.getOriginal();
	}

}
