package com.vnap.network;

import com.vnap.VillagerLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The stall's shelf as the player at its counter sees it: each lot (the item, how many) and what one costs him, what
 * the stall offers for the stack in the sell slot and why it would not buy it, the keeper's name.
 */
public record ShopListingPayload(int containerId, List<Offer> offers, int sellOffer, int sellState, String keeper, Ordering ordering)
		implements CustomPacketPayload {
	public static final Type<ShopListingPayload> TYPE = new Type<>(VillagerLife.id("shop_listing"));

	/** {@code item}'s count is how many the stall has; {@code price} is for one, to this player, today. */
	public record Offer(ItemStack item, int price, boolean fromPlayer) {
	}

	/**
	 * The order tab (village/market/Orders): what one of the sample costs ordered (0: not to be had), how many may be
	 * ordered, days to the caravan, and this player's open orders here ({@code item}'s count is how many, with the price paid).
	 */
	public record Ordering(int unit, int most, int days, List<Placed> placed) {
		public static final Ordering NONE = new Ordering(0, 0, 0, List.of());
	}

	public record Placed(ItemStack item, int paid) {
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, ShopListingPayload> CODEC = new StreamCodec<>() {
		@Override
		public ShopListingPayload decode(RegistryFriendlyByteBuf buffer) {
			int id = buffer.readVarInt();
			int size = buffer.readVarInt();
			List<Offer> offers = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
				int count = buffer.readVarInt();
				int price = buffer.readVarInt();
				offers.add(new Offer(item.copyWithCount(count), price, buffer.readBoolean()));
			}
			int sellOffer = buffer.readVarInt();
			int sellState = buffer.readVarInt();
			String keeper = buffer.readUtf();
			int unit = buffer.readVarInt();
			int most = buffer.readVarInt();
			int days = buffer.readVarInt();
			int placedSize = buffer.readVarInt();
			List<Placed> placed = new ArrayList<>(placedSize);
			for (int i = 0; i < placedSize; i++) {
				ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
				int count = buffer.readVarInt();
				placed.add(new Placed(item.copyWithCount(count), buffer.readVarInt()));
			}
			return new ShopListingPayload(id, offers, sellOffer, sellState, keeper, new Ordering(unit, most, days, placed));
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ShopListingPayload payload) {
			buffer.writeVarInt(payload.containerId());
			buffer.writeVarInt(payload.offers().size());
			for (Offer offer : payload.offers()) {
				// counts above a stack would not survive the item codec
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, offer.item().copyWithCount(1));
				buffer.writeVarInt(offer.item().getCount());
				buffer.writeVarInt(offer.price());
				buffer.writeBoolean(offer.fromPlayer());
			}
			buffer.writeVarInt(payload.sellOffer());
			buffer.writeVarInt(payload.sellState());
			buffer.writeUtf(payload.keeper());
			Ordering ordering = payload.ordering();
			buffer.writeVarInt(ordering.unit());
			buffer.writeVarInt(ordering.most());
			buffer.writeVarInt(ordering.days());
			buffer.writeVarInt(ordering.placed().size());
			for (Placed placed : ordering.placed()) {
				ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, placed.item().copyWithCount(1));
				buffer.writeVarInt(placed.item().getCount());
				buffer.writeVarInt(placed.paid());
			}
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
