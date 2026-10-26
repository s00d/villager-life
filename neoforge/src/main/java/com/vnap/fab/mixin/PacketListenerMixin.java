package com.vnap.fab.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** NeoForge's getConnection of a server's packet listener: its own connection. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PacketListenerMixin implements com.vnap.fab.neoforge.common.extensions.IServerCommonPacketListenerExtension {
	@Shadow
	@Final
	protected Connection connection;

	@Override
	public Connection getConnection() {
		return connection;
	}
}
