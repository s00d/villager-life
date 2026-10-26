package com.vnap.neo.neoforge.event.entity.player;

public final class PlayerEvent {
	private PlayerEvent() {
	}

	public static class Clone extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerEvent.Clone.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.Clone.class, priority, Clone::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.Clone f;

		Clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}

		public net.minecraft.world.entity.player.Player getOriginal() {
			return f.getOriginal();
		}

		public boolean isWasDeath() {
			return f.isWasDeath();
		}
	}

	public static class PlayerLoggedInEvent extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerEvent.PlayerLoggedInEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class, priority, PlayerLoggedInEvent::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent f;

		PlayerLoggedInEvent(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}
	}

	public static class PlayerLoggedOutEvent extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerEvent.PlayerLoggedOutEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class, priority, PlayerLoggedOutEvent::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent f;

		PlayerLoggedOutEvent(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}
	}

	public static class PlayerRespawnEvent extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerEvent.PlayerRespawnEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent.class, priority, PlayerRespawnEvent::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent f;

		PlayerRespawnEvent(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}

		public boolean isEndConquered() {
			return f.isEndConquered();
		}
	}

	public static class PlayerChangedDimensionEvent extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerEvent.PlayerChangedDimensionEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class, priority, PlayerChangedDimensionEvent::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent f;

		PlayerChangedDimensionEvent(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}

		public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getFrom() {
			return f.getFrom();
		}

		public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getTo() {
			return f.getTo();
		}
	}

	public static class ItemCraftedEvent extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerEvent.ItemCraftedEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent.class, priority, ItemCraftedEvent::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent f;

		ItemCraftedEvent(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}

		public net.minecraft.world.item.ItemStack getCrafting() {
			return f.getCrafting();
		}

		public net.minecraft.world.Container getInventory() {
			//? if >=1.21.9 {
			/*return f.getContainer();
			*///?} else
			return f.getInventory();
		}
	}

	public static class ItemSmeltedEvent extends com.vnap.neo.bus.api.Event {
		static {
			com.vnap.neo.Bridge.bind(PlayerEvent.ItemSmeltedEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent.class, priority, ItemSmeltedEvent::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent f;

		ItemSmeltedEvent(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent f) {
			super(f);
			this.f = f;
		}

		public net.minecraft.world.entity.player.Player getEntity() {
			return f.getEntity();
		}

		public net.minecraft.world.item.ItemStack getSmelting() {
			return f.getSmelting();
		}
	}
}
