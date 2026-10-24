package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A spire tablet to read (client/spire/SpireNoteScreen): a story note by its floor, a villager's note or a new layer's
 * note made of pieces by the seed (the texts are the client's, in its language). {@code name} signs a villager's note.
 */
public record SpireNotePayload(int kind, int floor, long seed, String theme, String name) implements CustomPacketPayload {
	public static final int STORY = 0;
	public static final int VILLAGER = 1;
	public static final int LAYER = 2;
	public static final Type<SpireNotePayload> TYPE = new Type<>(VillagerLife.id("spire_note"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SpireNotePayload> CODEC = new StreamCodec<>() {
		@Override
		public SpireNotePayload decode(RegistryFriendlyByteBuf buffer) {
			return new SpireNotePayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readLong(), buffer.readUtf(), buffer.readUtf());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, SpireNotePayload payload) {
			buffer.writeVarInt(payload.kind());
			buffer.writeVarInt(payload.floor());
			buffer.writeLong(payload.seed());
			buffer.writeUtf(payload.theme());
			buffer.writeUtf(payload.name());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
