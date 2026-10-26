package com.vnap.neo.neoforge.event.tick;

public final class PlayerTickEvent {
	private PlayerTickEvent() {
	}

	public static class Pre extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerTickEvent.Pre.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.TickEvent.PlayerTickEvent.Pre.class, priority, Pre::new, sink));
		}

		private final net.minecraftforge.event.TickEvent.PlayerTickEvent.Pre f;

		Pre(net.minecraftforge.event.TickEvent.PlayerTickEvent.Pre f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			//? if >=1.21.9 {
			/*return f.player();
			*///?} else
			return f.player;
		}
	}

	public static class Post extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerTickEvent.Post.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.TickEvent.PlayerTickEvent.Post.class, priority, Post::new, sink));
		}

		private final net.minecraftforge.event.TickEvent.PlayerTickEvent.Post f;

		Post(net.minecraftforge.event.TickEvent.PlayerTickEvent.Post f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			//? if >=1.21.9 {
			/*return f.player();
			*///?} else
			return f.player;
		}
	}
}
