package com.vnap.neo.neoforge.event.entity.player;

public final class PlayerXpEvent {
	private PlayerXpEvent() {
	}

	public static class PickupXp extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
		static {
			com.vnap.neo.Bridge.bind(PlayerXpEvent.PickupXp.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerXpEvent.PickupXp.class, priority, PickupXp::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerXpEvent.PickupXp f;

		PickupXp(net.minecraftforge.event.entity.player.PlayerXpEvent.PickupXp f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}

		public net.minecraft.world.entity.ExperienceOrb getOrb() {
			return f.getOrb();
		}
	}
}
