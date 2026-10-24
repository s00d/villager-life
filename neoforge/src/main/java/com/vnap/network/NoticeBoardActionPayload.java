package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What a player does at the notice board at {@code board}: buy the lot {@code id}, take the errand {@code id}, give up its errand. */
public record NoticeBoardActionPayload(BlockPos board, int action, int id) implements CustomPacketPayload {
	public static final int BUY_LOT = 0;
	public static final int TAKE_ERRAND = 1;
	public static final int CANCEL_ERRAND = 2;
	/** {@code id}: the flag's TownFlag code. */
	public static final int SET_FLAG = 3;
	public static final Type<NoticeBoardActionPayload> TYPE = new Type<>(VillagerLife.id("notice_board_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NoticeBoardActionPayload> CODEC = new StreamCodec<>() {
		@Override
		public NoticeBoardActionPayload decode(RegistryFriendlyByteBuf buffer) {
			return new NoticeBoardActionPayload(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, NoticeBoardActionPayload payload) {
			buffer.writeBlockPos(payload.board());
			buffer.writeVarInt(payload.action());
			buffer.writeVarInt(payload.id());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
