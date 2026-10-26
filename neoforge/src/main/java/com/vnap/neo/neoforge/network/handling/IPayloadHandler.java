package com.vnap.neo.neoforge.network.handling;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@FunctionalInterface
public interface IPayloadHandler<T extends CustomPacketPayload> {
	void handle(T payload, IPayloadContext context);
}
