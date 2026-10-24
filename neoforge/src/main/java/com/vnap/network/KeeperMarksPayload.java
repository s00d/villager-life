package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Dev worlds only (debugCommands): where a keeper's marks lie on the floor (x, z, inner, outer radius), for the dev
 * bot, which reads the telegraphs a player reads by their particles (client/dev/DevBot).
 */
public record KeeperMarksPayload(List<double[]> marks) implements CustomPacketPayload {
	public static final Type<KeeperMarksPayload> TYPE = new Type<>(VillagerLife.id("keeper_marks"));
	public static final StreamCodec<RegistryFriendlyByteBuf, KeeperMarksPayload> CODEC = new StreamCodec<>() {
		@Override
		public KeeperMarksPayload decode(RegistryFriendlyByteBuf buffer) {
			int count = buffer.readVarInt();
			List<double[]> marks = new ArrayList<>();
			for (int i = 0; i < count; i++) marks.add(new double[] {buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat()});
			return new KeeperMarksPayload(marks);
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, KeeperMarksPayload payload) {
			buffer.writeVarInt(payload.marks().size());
			for (double[] mark : payload.marks()) {
				buffer.writeDouble(mark[0]);
				buffer.writeDouble(mark[1]);
				buffer.writeFloat((float) mark[2]);
				buffer.writeFloat((float) mark[3]);
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
