package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** The player's chronicle of the spires (client/spire/SpireJournalScreen): for each spire climbed, its seed and the notes read. */
public record SpireJournalPayload(List<Climb> climbs) implements CustomPacketPayload {
	/** A spire: its town's name for it (its bell), its seed, how many of its floors' notes were read. */
	public record Climb(String town, long seed, int read) {
	}

	public static final Type<SpireJournalPayload> TYPE = new Type<>(VillagerLife.id("spire_journal"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SpireJournalPayload> CODEC = new StreamCodec<>() {
		@Override
		public SpireJournalPayload decode(RegistryFriendlyByteBuf buffer) {
			int count = buffer.readVarInt();
			List<Climb> climbs = new ArrayList<>();
			for (int i = 0; i < count; i++) climbs.add(new Climb(buffer.readUtf(), buffer.readLong(), buffer.readVarInt()));
			return new SpireJournalPayload(climbs);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, SpireJournalPayload payload) {
			buffer.writeVarInt(payload.climbs().size());
			for (Climb climb : payload.climbs()) {
				buffer.writeUtf(climb.town());
				buffer.writeLong(climb.seed());
				buffer.writeVarInt(climb.read());
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
