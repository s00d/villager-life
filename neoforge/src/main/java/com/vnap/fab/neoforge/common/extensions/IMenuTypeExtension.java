package com.vnap.fab.neoforge.common.extensions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** A menu opened with data from the server (Fabric API's extended screen handler type, its data a byte payload). */
public interface IMenuTypeExtension {
	static <T extends AbstractContainerMenu> MenuType<T> create(Factory<T> factory) {
		return new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>((id, inventory, data) -> {
			var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(data.bytes()), inventory.player.registryAccess());
			try {
				return factory.create(id, inventory, buffer);
			} finally {
				buffer.release();
			}
		}, com.vnap.fab.network.MenuData.STREAM_CODEC);
	}

	@FunctionalInterface
	interface Factory<T extends AbstractContainerMenu> {
		T create(int windowId, Inventory inventory, RegistryFriendlyByteBuf data);
	}
}
