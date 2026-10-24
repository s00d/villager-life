package com.vnap.item;

import com.vnap.dialogue.FamilyHome;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Neighbours;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Market;
import com.vnap.village.town.Roads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;


/**
 * A blank deed: used inside an empty house the villagers built, it buys the house for the player at what its
 * materials cost, the money to the town's treasury. Its beds are the player's then (no villager moves in) and the
 * player is the village's neighbour (its letters, its weddings).
 */
public final class HouseDeedItem extends Item {
	/** A house goes for this share of its materials' worth (it is not new any more). */

	public HouseDeedItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (!(context.getLevel() instanceof ServerLevel level) || player == null) return InteractionResult.SUCCESS;
		HouseProject house = houseAt(level, context.getClickedPos());
		if (house == null || !HomeProjects.emptyHouses(level, house.bell).contains(house)) {
			// a house the villagers built is rarely empty for long (the homeless move in the same day); the world's
			// village has empty ones, its household dead or gone: one of those
			if (house == null && buyWorldHouse(level, player, context.getClickedPos())) {
				if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
				return InteractionResult.CONSUME;
			}
			com.vnap.Mc.say(player, Component.translatable("item.villager_life.house_deed.not_for_sale"), true);
			return InteractionResult.CONSUME;
		}
		int price = price(level, house);
		if (!Coins.pay(player, price)) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.no_money", Coins.text(price)), true);
			return InteractionResult.CONSUME;
		}
		house.player = player.getUUID();
		VillageMoodSavedData.get(level).markDirty();
		// the beds are kept for the player: a homeless villager does not lie down in them
		BoundingBox box = house.footprint(level);
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) FamilyHome.take(level, pos.immutable());
		}
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), house.bell);
		Market.deposit(level, office, price);
		Neighbours.get(level.getServer()).add(level, office.bell, player.getUUID());
		VillageMoodApi.applyAt(level, house.bell, 5.0F, 2.0F, "house_bought");
		if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
		com.vnap.Mc.say(player, Component.translatable("item.villager_life.house_deed.bought", Coins.text(price)), false);
		com.vnap.progress.VillageAdvancements.award(player, "town/deed");
		return InteractionResult.CONSUME;
	}

	/** An empty house of the world's village a town has (no one's bed, no sign, no job site, no full chest): bought. */
	private static boolean buyWorldHouse(ServerLevel level, Player player, BlockPos pos) {
		BoundingBox box = HomeProjects.piece(level, pos);
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), pos);
		if (box == null || network == null || !com.vnap.village.home.Towns.ours(level, network.bell, pos) || !HomeProjects.canClear(level, box, null)) return false;
		int price = com.vnap.village.market.Housing.price(level, box);
		if (!Coins.pay(player, price)) {
			com.vnap.Mc.say(player, Component.translatable("vnap.shop.no_money", Coins.text(price)), true);
			return true;
		}
		network.boughtHouses.put(box, player.getUUID());
		Roads.get(level.getServer()).setDirty();
		for (BlockPos cell : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(cell);
			if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) FamilyHome.take(level, cell.immutable());
		}
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), network.bell);
		Market.deposit(level, office, price);
		Neighbours.get(level.getServer()).add(level, office.bell, player.getUUID());
		VillageMoodApi.applyAt(level, network.bell, 5.0F, 2.0F, "house_bought");
		com.vnap.Mc.say(player, Component.translatable("item.villager_life.house_deed.bought", Coins.text(price)), false);
		com.vnap.progress.VillageAdvancements.award(player, "town/deed");
		return true;
	}

	static int price(ServerLevel level, HouseProject house) {
		return com.vnap.village.market.Housing.price(level, house);
	}

	/** The house of the villagers this block is part of, or null. */
	static @Nullable HouseProject houseAt(ServerLevel level, BlockPos pos) {
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (house.done && house.footprint(level).isInside(pos)) return house;
		}
		return null;
	}
}
