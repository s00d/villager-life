package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** The screens of the mod's menus, with the client setup: vanilla's MenuScreens (Fabric API makes its register open). */
public final class RegisterMenuScreensEvent extends Event {
	public RegisterMenuScreensEvent() {
	}

	public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> factory) {
		MenuScreens.register(type, factory);
	}
}
