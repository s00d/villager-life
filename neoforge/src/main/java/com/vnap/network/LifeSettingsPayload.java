package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The talk settings both ways: the server tells a player them (and whether it may change them), an operator sends new ones. */
public record LifeSettingsPayload(int chattiness, int rareVoicelines, boolean canEdit) implements CustomPacketPayload {
	public static final Type<LifeSettingsPayload> TYPE = new Type<>(VillagerLife.id("settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, LifeSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, LifeSettingsPayload::chattiness,
			ByteBufCodecs.VAR_INT, LifeSettingsPayload::rareVoicelines,
			ByteBufCodecs.BOOL, LifeSettingsPayload::canEdit,
			LifeSettingsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
