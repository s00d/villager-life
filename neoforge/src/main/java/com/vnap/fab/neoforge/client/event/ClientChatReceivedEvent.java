package com.vnap.fab.neoforge.client.event;

/** A system message arriving on the client (Fabric API ClientReceiveMessageEvents.ALLOW_GAME). */
public final class ClientChatReceivedEvent {
	private ClientChatReceivedEvent() {
	}

	@SuppressWarnings("AvoidCommonTypeNames")
	public static class System extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
		private final net.minecraft.network.chat.Component message;
		private final boolean overlay;

		public System(net.minecraft.network.chat.Component message, boolean overlay) {
			this.message = message;
			this.overlay = overlay;
		}

		public net.minecraft.network.chat.Component getMessage() {
			return message;
		}

		public boolean isOverlay() {
			return overlay;
		}
	}
}
