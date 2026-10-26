package com.vnap.fab;

import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.Nullable;

/** NeoForge's openMenu with data on Fabric: the writer's bytes go along with Fabric API's extended menu. */
public final class Menus {
	private Menus() {
	}

	/** Vanilla's own (the build sends every openMenu of the mod here). */
	public static void open(Player player, MenuProvider provider) {
		Player.class.cast(player).openMenu(provider);
	}

	public static void open(Player player, MenuProvider provider, Consumer<RegistryFriendlyByteBuf> writer) {
		if (!(player instanceof ServerPlayer server)) return;
		var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
		try {
			writer.accept(buffer);
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);
			ServerPlayer.class.cast(server).openMenu(new Extended(provider, new com.vnap.fab.network.MenuData(bytes)));
		} finally {
			buffer.release();
		}
	}

	private record Extended(MenuProvider provider, com.vnap.fab.network.MenuData data)
			implements net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<com.vnap.fab.network.MenuData> {
		@Override
		public com.vnap.fab.network.MenuData getScreenOpeningData(ServerPlayer player) {
			return data;
		}

		@Override
		public Component getDisplayName() {
			return provider.getDisplayName();
		}

		@Override
		public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
			return provider.createMenu(id, inventory, player);
		}
	}
}
