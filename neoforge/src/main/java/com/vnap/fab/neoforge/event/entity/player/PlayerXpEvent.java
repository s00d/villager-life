package com.vnap.fab.neoforge.event.entity.player;

/** Experience picked up (com.vnap.fab.mixin.ExperienceOrbMixin). */
public final class PlayerXpEvent {
	private PlayerXpEvent() {
	}

	public static class PickupXp extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
		private final net.minecraft.world.entity.player.Player player;
		private final net.minecraft.world.entity.ExperienceOrb orb;

		public PickupXp(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.ExperienceOrb orb) {
			this.player = player;
			this.orb = orb;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}

		public net.minecraft.world.entity.ExperienceOrb getOrb() {
			return orb;
		}
	}
}
