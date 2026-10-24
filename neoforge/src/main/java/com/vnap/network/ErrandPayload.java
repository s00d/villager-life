package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** The player's errand for the hint on the screen (ErrandHud): whom to bring what, the reward, where they were last seen. */
public record ErrandPayload(boolean active, String name, ItemStack item, int reward, BlockPos target, boolean here) implements CustomPacketPayload {
	public static final Type<ErrandPayload> TYPE = new Type<>(VillagerLife.id("errand"));
	public static final ErrandPayload NONE = new ErrandPayload(false, "", ItemStack.EMPTY, 0, BlockPos.ZERO, false);
	public static final StreamCodec<RegistryFriendlyByteBuf, ErrandPayload> CODEC = new StreamCodec<>() {
		@Override
		public ErrandPayload decode(RegistryFriendlyByteBuf buffer) {
			boolean active = buffer.readBoolean();
			String name = buffer.readUtf();
			ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
			int count = buffer.readVarInt();
			return new ErrandPayload(active, name, item.isEmpty() ? item : item.copyWithCount(count), buffer.readVarInt(), buffer.readBlockPos(), buffer.readBoolean());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ErrandPayload payload) {
			buffer.writeBoolean(payload.active());
			buffer.writeUtf(payload.name());
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.item().isEmpty() ? ItemStack.EMPTY : payload.item().copyWithCount(1));
			buffer.writeVarInt(payload.item().getCount());
			buffer.writeVarInt(payload.reward());
			buffer.writeBlockPos(payload.target());
			buffer.writeBoolean(payload.here());
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
