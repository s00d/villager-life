package com.vnap.neo.neoforge.event.entity.player;

public class AnvilRepairEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(AnvilRepairEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.AnvilRepairEvent.class, priority, AnvilRepairEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.AnvilRepairEvent f;

	AnvilRepairEvent(net.minecraftforge.event.entity.player.AnvilRepairEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.item.ItemStack getLeft() {
		return f.getLeft();
	}

	public net.minecraft.world.item.ItemStack getRight() {
		return f.getRight();
	}

	public net.minecraft.world.item.ItemStack getOutput() {
		return f.getOutput();
	}
}
