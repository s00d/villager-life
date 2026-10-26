package com.vnap.neo.neoforge.event.village;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;

public final class VillagerTradesEvent extends Event {
	static {
		Bridge.bind(VillagerTradesEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.village.VillagerTradesEvent.class, priority, VillagerTradesEvent::new, sink));
	}

	private final net.minecraftforge.event.village.VillagerTradesEvent f;

	VillagerTradesEvent(net.minecraftforge.event.village.VillagerTradesEvent f) {
		super(f);
		this.f = f;
	}

	public Int2ObjectMap<List<VillagerTrades.ItemListing>> getTrades() {
		return f.getTrades();
	}

	//? if >=1.21.5 {
	/*public net.minecraft.resources.ResourceKey<VillagerProfession> getType() {
		return f.getType();
	}
	*///?} else {
	public VillagerProfession getType() {
		return f.getType();
	}
	//?}
}
