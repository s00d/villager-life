package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** What a player has come upon so far (progress/Discoveries): the guide opens its chapters and entries by these. */
public record DiscoveriesPayload(List<String> keys) implements CustomPacketPayload {
	public static final Type<DiscoveriesPayload> TYPE = new Type<>(VillagerLife.id("discoveries"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DiscoveriesPayload> CODEC = new StreamCodec<>() {
		@Override
		public DiscoveriesPayload decode(RegistryFriendlyByteBuf buffer) {
			int count = buffer.readVarInt();
			List<String> keys = new ArrayList<>();
			for (int i = 0; i < count; i++) keys.add(buffer.readUtf());
			return new DiscoveriesPayload(keys);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, DiscoveriesPayload payload) {
			buffer.writeVarInt(payload.keys().size());
			for (String key : payload.keys()) buffer.writeUtf(key);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
