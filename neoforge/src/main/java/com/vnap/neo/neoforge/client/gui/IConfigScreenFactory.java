package com.vnap.neo.neoforge.client.gui;

import com.vnap.neo.fml.ModContainer;
import net.minecraft.client.gui.screens.Screen;

@FunctionalInterface
public interface IConfigScreenFactory {
	Screen createScreen(ModContainer container, Screen modListScreen);
}
