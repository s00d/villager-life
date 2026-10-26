package com.vnap.neo.neoforge.event.entity.player;

public class ItemFishedEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(ItemFishedEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.ItemFishedEvent.class, priority, ItemFishedEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.ItemFishedEvent f;

	ItemFishedEvent(net.minecraftforge.event.entity.player.ItemFishedEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getDrops() {
		return f.getDrops();
	}

	public net.minecraft.world.entity.projectile.FishingHook getHookEntity() {
		return f.getHookEntity();
	}

	public int getRodDamage() {
		return f.getRodDamage();
	}
}
