package com.vnap.fab.neoforge.common.extensions;

/** The connection of a server's packet listener, as NeoForge hands it out (com.vnap.fab.mixin.PacketListenerMixin; Loom injects it). */
public interface IServerCommonPacketListenerExtension {
	default net.minecraft.network.Connection getConnection() {
		throw new AssertionError("com.vnap.fab.mixin.PacketListenerMixin");
	}
}
