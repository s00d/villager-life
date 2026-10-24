package com.vnap.network;

import com.vnap.VillagerLife;
import com.vnap.mood.MoodThresholds;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's mood thresholds, sent on login: the bar's marks and the rites' moods in the guide and JEI/EMI are drawn
 * from them, and a client on a dedicated server had only its own config's (a server that moved them showed wrong marks).
 */
public record MoodRulesPayload(float happy, float angry, float cultReady, float festoon, float festival, float pavilion, float vigil,
							   float mourning, float pyre) implements CustomPacketPayload {
	public static final Type<MoodRulesPayload> TYPE = new Type<>(VillagerLife.id("mood_rules"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MoodRulesPayload> CODEC = new StreamCodec<>() {
		@Override
		public MoodRulesPayload decode(RegistryFriendlyByteBuf buffer) {
			return new MoodRulesPayload(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
					buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, MoodRulesPayload payload) {
			buffer.writeFloat(payload.happy());
			buffer.writeFloat(payload.angry());
			buffer.writeFloat(payload.cultReady());
			buffer.writeFloat(payload.festoon());
			buffer.writeFloat(payload.festival());
			buffer.writeFloat(payload.pavilion());
			buffer.writeFloat(payload.vigil());
			buffer.writeFloat(payload.mourning());
			buffer.writeFloat(payload.pyre());
		}
	};

	/** The thresholds this side has now (the server's, from its config). */
	public static MoodRulesPayload current() {
		return new MoodRulesPayload(MoodThresholds.happy(), MoodThresholds.angry(), MoodThresholds.cultReady(), MoodThresholds.festoon(),
				MoodThresholds.festival(), MoodThresholds.pavilion(), MoodThresholds.vigil(), MoodThresholds.mourning(), MoodThresholds.pyre());
	}

	/** On the client: the server's thresholds are the ones drawn. */
	public void apply() {
		MoodThresholds.set(happy, angry, cultReady);
		MoodThresholds.setLesser(festoon, festival, pavilion, vigil, mourning, pyre);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
