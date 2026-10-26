package com.vnap.fab.bus.api;

/** NeoForge's event: on Fabric a plain object the shim's hooks fill and fire (com.vnap.fab.Bridge). */
public abstract class Event {
	boolean canceled;

	protected Event() {
	}

	public final boolean vnap$canceled() {
		return canceled;
	}

	public final void vnap$cancel(boolean canceled) {
		this.canceled = canceled;
	}
}
