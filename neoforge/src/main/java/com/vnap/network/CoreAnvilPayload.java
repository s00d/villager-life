package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A click at the Core Anvil: do the work its slots hold (CoreAnvilMenu.WORK). */
public record CoreAnvilPayload(int containerId, int action) implements CustomPacketPayload {
	public static final Type<CoreAnvilPayload> TYPE = new Type<>(VillagerLife.id("core_anvil"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CoreAnvilPayload> CODEC = new StreamCodec<>() {
		@Override
		public CoreAnvilPayload decode(RegistryFriendlyByteBuf buffer) {
			return new CoreAnvilPayload(buffer.readVarInt(), buffer.readVarInt());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, CoreAnvilPayload payload) {
			buffer.writeVarInt(payload.containerId());
			buffer.writeVarInt(payload.action());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
