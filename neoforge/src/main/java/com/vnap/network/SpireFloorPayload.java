package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The floor a player stands on, for the screen (client/spire/SpireHud): its number and theme, how many of its guards
 * still stand of how many, whether it is cleared. {@code arrived} marks the moment it came in (the title card); floor 0
 * clears it (out of the spire).
 */
public record SpireFloorPayload(int floor, String theme, int guards, int total, boolean cleared, boolean arrived) implements CustomPacketPayload {
	public static final Type<SpireFloorPayload> TYPE = new Type<>(VillagerLife.id("spire_floor"));
	public static final SpireFloorPayload NONE = new SpireFloorPayload(0, "", 0, 0, false, false);
	public static final StreamCodec<RegistryFriendlyByteBuf, SpireFloorPayload> CODEC = new StreamCodec<>() {
		@Override
		public SpireFloorPayload decode(RegistryFriendlyByteBuf buffer) {
			return new SpireFloorPayload(buffer.readVarInt(), buffer.readUtf(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, SpireFloorPayload payload) {
			buffer.writeVarInt(payload.floor());
			buffer.writeUtf(payload.theme());
			buffer.writeVarInt(payload.guards());
			buffer.writeVarInt(payload.total());
			buffer.writeBoolean(payload.cleared());
			buffer.writeBoolean(payload.arrived());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
