package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;

public final class RegisterColorHandlersEvent {
	private RegisterColorHandlersEvent() {
	}

	//? if >=26.1 {
	/*/^* A block's tints from 26.1 (a list of sources, one a tint index): Forge's Block event. ^/
	public static final class BlockTintSources extends Event {
		static {
			Bridge.bind(BlockTintSources.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block.class, priority,
					BlockTintSources::new, sink));
		}

		private final net.minecraftforge.client.event.RegisterColorHandlersEvent.Block f;

		BlockTintSources(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block f) {
			super(f);
			this.f = f;
		}

		public void register(java.util.List<net.minecraft.client.color.block.BlockTintSource> sources, net.minecraft.world.level.block.Block... blocks) {
			f.register(sources, blocks);
		}
	}
	*///?} else {
	public static final class Block extends Event {
		static {
			Bridge.bind(Block.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block.class, priority, Block::new, sink));
		}

		private final net.minecraftforge.client.event.RegisterColorHandlersEvent.Block f;

		Block(net.minecraftforge.client.event.RegisterColorHandlersEvent.Block f) {
			super(f);
			this.f = f;
		}

		public void register(net.minecraft.client.color.block.BlockColor color, net.minecraft.world.level.block.Block... blocks) {
			f.register(color, blocks);
		}
	}
	//?}
}
