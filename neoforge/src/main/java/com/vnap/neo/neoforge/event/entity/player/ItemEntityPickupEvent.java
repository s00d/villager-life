package com.vnap.neo.neoforge.event.entity.player;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ItemEntityPickupEvent {
	private ItemEntityPickupEvent() {
	}

	/** Items a player has just picked up (Forge's ItemPickupEvent, fired at the same moment). */
	public static final class Post extends Event {
		static {
			Bridge.bind(Post.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent.class, priority, Post::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent f;

		Post(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent f) {
			super(f);
			this.f = f;
		}

		public Player getPlayer() {
			return f.getEntity();
		}

		public ItemEntity getItemEntity() {
			return f.getOriginalEntity();
		}

		public ItemStack getOriginalStack() {
			return f.getStack();
		}
	}
}
