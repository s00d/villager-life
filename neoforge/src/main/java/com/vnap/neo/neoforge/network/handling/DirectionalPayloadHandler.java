package com.vnap.neo.neoforge.network.handling;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A payload's handler on each side. */
public record DirectionalPayloadHandler<T extends CustomPacketPayload>(IPayloadHandler<T> clientSide, IPayloadHandler<T> serverSide) implements IPayloadHandler<T> {
	@Override
	public void handle(T payload, IPayloadContext context) {
		if (context.player().level().isClientSide()) clientSide.handle(payload, context);
		else serverSide.handle(payload, context);
	}
}
