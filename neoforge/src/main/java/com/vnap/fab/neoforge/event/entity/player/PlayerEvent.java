package com.vnap.fab.neoforge.event.entity.player;

/** A player's moments (Fabric API: ServerPlayerEvents, ServerPlayConnectionEvents; the rest com.vnap.fab.mixin). */
public final class PlayerEvent {
	private PlayerEvent() {
	}

	public static class Clone extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;
		private final net.minecraft.world.entity.player.Player original;
		private final boolean wasDeath;

		public Clone(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.player.Player original, boolean wasDeath) {
			this.player = player;
			this.original = original;
			this.wasDeath = wasDeath;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}

		public net.minecraft.world.entity.player.Player getOriginal() {
			return original;
		}

		public boolean isWasDeath() {
			return wasDeath;
		}
	}

	public static class PlayerLoggedInEvent extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;

		public PlayerLoggedInEvent(net.minecraft.world.entity.player.Player player) {
			this.player = player;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}
	}

	public static class PlayerLoggedOutEvent extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;

		public PlayerLoggedOutEvent(net.minecraft.world.entity.player.Player player) {
			this.player = player;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}
	}

	public static class PlayerRespawnEvent extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;
		private final boolean endConquered;

		public PlayerRespawnEvent(net.minecraft.world.entity.player.Player player, boolean endConquered) {
			this.player = player;
			this.endConquered = endConquered;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}

		public boolean isEndConquered() {
			return endConquered;
		}
	}

	public static class PlayerChangedDimensionEvent extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;
		private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> from;
		private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> to;

		public PlayerChangedDimensionEvent(net.minecraft.world.entity.player.Player player, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> from, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> to) {
			this.player = player;
			this.from = from;
			this.to = to;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}

		public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getFrom() {
			return from;
		}

		public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getTo() {
			return to;
		}
	}

	public static class ItemCraftedEvent extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;
		private final net.minecraft.world.item.ItemStack crafting;
		private final net.minecraft.world.Container inventory;

		public ItemCraftedEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack crafting, net.minecraft.world.Container inventory) {
			this.player = player;
			this.crafting = crafting;
			this.inventory = inventory;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}

		public net.minecraft.world.item.ItemStack getCrafting() {
			return crafting;
		}

		public net.minecraft.world.Container getInventory() {
			return inventory;
		}
	}

	public static class ItemSmeltedEvent extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;
		private final net.minecraft.world.item.ItemStack smelting;

		public ItemSmeltedEvent(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack smelting) {
			this.player = player;
			this.smelting = smelting;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return player;
		}

		public net.minecraft.world.item.ItemStack getSmelting() {
			return smelting;
		}
	}
}
