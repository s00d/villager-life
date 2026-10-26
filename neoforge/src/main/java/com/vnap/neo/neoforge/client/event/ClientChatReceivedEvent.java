package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.network.chat.Component;

public final class ClientChatReceivedEvent {
	private ClientChatReceivedEvent() {
	}

	/** A message from the game, not a player (NeoForge's name). */
	@SuppressWarnings("AvoidCommonTypeNames")
	public static final class System extends Event implements ICancellableEvent {
		static {
			Bridge.bind(System.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.SystemMessageReceivedEvent.class, priority, System::new, sink));
		}

		private final net.minecraftforge.client.event.SystemMessageReceivedEvent f;

		System(net.minecraftforge.client.event.SystemMessageReceivedEvent f) {
			super(f);
			this.f = f;
		}

		public Component getMessage() {
			return f.getMessage();
		}

		public boolean isOverlay() {
			return f.isOverlay();
		}
	}
}
