package com.vnap.neo.neoforge.event.brewing;

public class PlayerBrewedPotionEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(PlayerBrewedPotionEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.brewing.PlayerBrewedPotionEvent.class, priority, PlayerBrewedPotionEvent::new, sink));
	}

	private final net.minecraftforge.event.brewing.PlayerBrewedPotionEvent f;

	PlayerBrewedPotionEvent(net.minecraftforge.event.brewing.PlayerBrewedPotionEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.item.ItemStack getStack() {
		return f.getStack();
	}
}
