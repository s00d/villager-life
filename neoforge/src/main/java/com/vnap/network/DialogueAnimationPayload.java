package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Server to client: {@code entityId} says take {@code variantIndex} of {@code groupId} for so many ticks; an empty group stops it. */
public record DialogueAnimationPayload(UUID entityId, String groupId, int variantIndex, int durationTicks)
		implements CustomPacketPayload {
	public static final Type<DialogueAnimationPayload> TYPE = new Type<>(VillagerLife.id("dialogue_animation"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueAnimationPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, DialogueAnimationPayload::entityId,
			ByteBufCodecs.stringUtf8(64), DialogueAnimationPayload::groupId,
			ByteBufCodecs.VAR_INT, DialogueAnimationPayload::variantIndex,
			ByteBufCodecs.VAR_INT, DialogueAnimationPayload::durationTicks,
			DialogueAnimationPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
