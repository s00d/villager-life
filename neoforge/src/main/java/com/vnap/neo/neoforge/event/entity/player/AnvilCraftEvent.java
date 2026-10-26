package com.vnap.neo.neoforge.event.entity.player;

public final class AnvilCraftEvent {
	private AnvilCraftEvent() {
	}

	/** A player took what the anvil made: Forge's AnvilRepairEvent. */
	public static final class Post extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(Post.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.AnvilRepairEvent.class, priority, Post::new, sink));
		}

		private final net.minecraftforge.event.entity.player.AnvilRepairEvent f;

		Post(net.minecraftforge.event.entity.player.AnvilRepairEvent f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}

		public net.minecraft.world.item.ItemStack getOutput() {
			return f.getOutput();
		}
	}
}
