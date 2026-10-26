package com.vnap.neo.bus.api;

import org.jspecify.annotations.Nullable;

/** NeoForge's event: on Forge each one wraps the Forge event it stands for (com.vnap.neo.Bridge). */
public abstract class Event {
	boolean canceled;
	private final @Nullable Object forge;

	protected Event() {
		this.forge = null;
	}

	protected Event(Object forge) {
		this.forge = forge;
	}

	/** The Forge event this one stands for (null for one the mod made with nothing behind it). */
	public final @Nullable Object vnap$forge() {
		return forge;
	}

	public final boolean vnap$canceled() {
		return canceled;
	}

	public final void vnap$cancel(boolean canceled) {
		this.canceled = canceled;
	}
}
