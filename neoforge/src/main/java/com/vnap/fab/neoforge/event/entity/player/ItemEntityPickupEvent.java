package com.vnap.fab.neoforge.event.entity.player;

/** An item picked up off the ground (com.vnap.fab.mixin.ItemPickupMixin). */
public final class ItemEntityPickupEvent {
	private ItemEntityPickupEvent() {
	}

	public static class Post extends com.vnap.fab.bus.api.Event {
		private final net.minecraft.world.entity.player.Player player;
		private final net.minecraft.world.entity.item.ItemEntity itemEntity;
		private final net.minecraft.world.item.ItemStack originalStack;

		public Post(net.minecraft.world.entity.player.Player player, net.minecraft.world.entity.item.ItemEntity itemEntity, net.minecraft.world.item.ItemStack originalStack) {
			this.player = player;
			this.itemEntity = itemEntity;
			this.originalStack = originalStack;
		}

		public net.minecraft.world.entity.player.Player getPlayer() {
			return player;
		}

		public net.minecraft.world.entity.item.ItemEntity getItemEntity() {
			return itemEntity;
		}

		public net.minecraft.world.item.ItemStack getOriginalStack() {
			return originalStack;
		}
	}
}
