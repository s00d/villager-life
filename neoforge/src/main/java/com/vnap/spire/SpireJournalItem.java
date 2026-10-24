package com.vnap.spire;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
//? if <1.21.2
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The Chronicle of the Spire: opens the notes its holder read up the spires (SpireNotes.journal). */
public final class SpireJournalItem extends Item {
	public SpireJournalItem(Properties properties) {
		super(properties);
	}

	@Override
	//? if >=1.21.2 {
	/*public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
	*///?} else
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer reader) SpireNotes.journal(reader);
		return com.vnap.Mc.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
	}
}
