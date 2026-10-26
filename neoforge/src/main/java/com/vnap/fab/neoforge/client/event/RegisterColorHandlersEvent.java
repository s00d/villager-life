package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;

/** The tints of the mod's blocks: Fabric API's colour providers. */
public final class RegisterColorHandlersEvent {
	private RegisterColorHandlersEvent() {
	}

	//? if >=26.1 {
	/*public static final class BlockTintSources extends Event {
		public BlockTintSources() {
		}

		public void register(java.util.List<net.minecraft.client.color.block.BlockTintSource> sources, net.minecraft.world.level.block.Block... blocks) {
			net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(sources, blocks);
		}
	}
	*///?} else {
	public static final class Block extends Event {
		public Block() {
		}

		public void register(net.minecraft.client.color.block.BlockColor color, net.minecraft.world.level.block.Block... blocks) {
			net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(color, blocks);
		}
	}
	//?}
}
