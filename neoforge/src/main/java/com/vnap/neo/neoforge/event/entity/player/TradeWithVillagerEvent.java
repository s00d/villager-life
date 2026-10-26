package com.vnap.neo.neoforge.event.entity.player;

public class TradeWithVillagerEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(TradeWithVillagerEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.TradeWithVillagerEvent.class, priority, TradeWithVillagerEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.TradeWithVillagerEvent f;

	TradeWithVillagerEvent(net.minecraftforge.event.entity.player.TradeWithVillagerEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.item.trading.MerchantOffer getMerchantOffer() {
		return f.getMerchantOffer();
	}

	public net.minecraft.world.entity.npc.AbstractVillager getAbstractVillager() {
		return f.getAbstractVillager();
	}
}
