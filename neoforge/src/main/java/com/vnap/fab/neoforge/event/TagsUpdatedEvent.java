package com.vnap.fab.neoforge.event;

import com.vnap.fab.bus.api.Event;

/** Tags loaded on the server or received by a client (Fabric API CommonLifecycleEvents.TAGS_LOADED). */
public final class TagsUpdatedEvent extends Event {
	private final UpdateCause cause;

	public TagsUpdatedEvent(UpdateCause cause) {
		this.cause = cause;
	}

	public UpdateCause getUpdateCause() {
		return cause;
	}

	public enum UpdateCause {
		SERVER_DATA_LOAD, CLIENT_PACKET_RECEIVED
	}
}
