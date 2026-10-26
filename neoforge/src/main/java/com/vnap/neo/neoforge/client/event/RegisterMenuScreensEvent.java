package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Forge has no event for it: the screens go to vanilla's MenuScreens in the client setup, on the main thread. */
public final class RegisterMenuScreensEvent extends Event {
	static {
		Bridge.bind(RegisterMenuScreensEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent.class, priority,
				setup -> new RegisterMenuScreensEvent(setup), event -> Bridge.logged(event.setup.enqueueWork(() -> sink.accept(event)), "the menu screens")));
	}

	private final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent setup;

	RegisterMenuScreensEvent(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent setup) {
		super(setup);
		this.setup = setup;
	}

	public <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type, MenuScreens.ScreenConstructor<M, U> factory) {
		MenuScreens.register(type, factory);
	}
}
