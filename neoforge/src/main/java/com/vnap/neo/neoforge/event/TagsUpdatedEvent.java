package com.vnap.neo.neoforge.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;

public final class TagsUpdatedEvent extends Event {
	static {
		Bridge.bind(TagsUpdatedEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.TagsUpdatedEvent.class, priority, TagsUpdatedEvent::new, sink));
	}

	private final net.minecraftforge.event.TagsUpdatedEvent f;

	TagsUpdatedEvent(net.minecraftforge.event.TagsUpdatedEvent f) {
		super(f);
		this.f = f;
	}

	public UpdateCause getUpdateCause() {
		return f.getUpdateCause() == net.minecraftforge.event.TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD ? UpdateCause.SERVER_DATA_LOAD : UpdateCause.CLIENT_PACKET_RECEIVED;
	}

	public enum UpdateCause {
		SERVER_DATA_LOAD, CLIENT_PACKET_RECEIVED
	}
}
