package com.vnap.fab.neoforge.event.tick;

import net.minecraft.world.entity.player.Player;

/** Around a player's tick, on both sides (com.vnap.fab.mixin.PlayerTickMixin). */
public final class PlayerTickEvent {
	private PlayerTickEvent() {
	}

	public static class Pre extends com.vnap.fab.bus.api.Event {
		private final Player player;

		public Pre(Player player) {
			this.player = player;
		}

		public Player getEntity() {
			return player;
		}
	}

	public static class Post extends com.vnap.fab.bus.api.Event {
		private final Player player;

		public Post(Player player) {
			this.player = player;
		}

		public Player getEntity() {
			return player;
		}
	}
}
