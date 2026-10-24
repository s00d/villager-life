package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A click in the stall's window: buy one or a stack of the lot at {@code index}, sell the slot ({@code index} unused),
 * order {@code index} of the sample in the slot, or take back the player's order at {@code index}.
 */
public record ShopActionPayload(int containerId, int action, int index) implements CustomPacketPayload {
	public static final int BUY = 0;
	public static final int BUY_STACK = 1;
	public static final int SELL = 2;
	public static final int ORDER = 3;
	public static final int CANCEL_ORDER = 4;
	public static final Type<ShopActionPayload> TYPE = new Type<>(VillagerLife.id("shop_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShopActionPayload> CODEC = new StreamCodec<>() {
		@Override
		public ShopActionPayload decode(RegistryFriendlyByteBuf buffer) {
			return new ShopActionPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ShopActionPayload payload) {
			buffer.writeVarInt(payload.containerId());
			buffer.writeVarInt(payload.action());
			buffer.writeVarInt(payload.index());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
