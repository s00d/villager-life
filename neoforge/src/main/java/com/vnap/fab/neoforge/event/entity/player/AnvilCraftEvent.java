package com.vnap.fab.neoforge.event.entity.player;

/** An item taken from an anvil (com.vnap.fab.mixin.AnvilMixin). */
public final class AnvilCraftEvent {
	private AnvilCraftEvent() {
	}

	public static class Post extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;
		private final net.minecraft.world.item.ItemStack output;

		public Post(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack output) {
			this.player = player;
			this.output = output;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}

		public net.minecraft.world.item.ItemStack getOutput() {
			return output;
		}
	}
}
