package com.vnap.neo.neoforge.common.extensions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public interface IMenuTypeExtension {
	static <T extends AbstractContainerMenu> MenuType<T> create(Factory<T> factory) {
		// Forge hands the opening data over as a plain buffer: what reads items from it needs the client's registries
		return net.minecraftforge.common.extensions.IForgeMenuType.create((id, inventory, data) -> factory.create(id, inventory,
				data instanceof RegistryFriendlyByteBuf registry ? registry : new RegistryFriendlyByteBuf(data,
						java.util.Objects.requireNonNull(com.vnap.Mc.clientLevel.get(), "the client's level").registryAccess())));
	}

	@FunctionalInterface
	interface Factory<T extends AbstractContainerMenu> {
		T create(int windowId, Inventory inventory, RegistryFriendlyByteBuf data);
	}
}
