package com.vnap.item;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * An item with lines of its own under its name. The client's tooltip event adds them (VillagerLifeClient.onTooltip):
 * one way for every version, where Item.appendHoverText changed its form in 1.21.5 and went deprecated.
 */
public interface Tipped {
	List<Component> tips();
}
