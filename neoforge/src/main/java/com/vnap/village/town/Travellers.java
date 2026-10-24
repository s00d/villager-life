package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.VillagerHome;
import com.vnap.mood.VillageMoodApi;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Prices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Travellers at the tavern (MineColonies' tavern visitors, the village's way): now and then of an evening a stranger
 * takes a room at the tavern, and while the town has room a player may talk them into staying, with the thing they
 * ask for or its price in coins. Settled, they are the town's like anyone born there (its bell, a house in time); not
 * talked round in TownRules.TRAVELLER_STAYS days, they go on their way. A traveller is nobody's yet: no family life,
 * not counted among the town's people (Family.tickAll skips it).
 */
public final class Travellers {
	static final String TAG = "vnap_traveller";
	private static final String ITEM = "vnap_traveller_item";
	private static final String COUNT = "vnap_traveller_count";
	private static final String PRICE = "vnap_traveller_price";

	/** What a traveller may ask for to stay (and how many). */
	private record Ask(Item item, int count) {
	}

	private static final List<Ask> ASKS = List.of(new Ask(Items.HONEY_BOTTLE, 2), new Ask(Items.BOOK, 3), new Ask(Items.CAKE, 1),
			new Ask(Items.LANTERN, 2), new Ask(Items.COMPASS, 1), new Ask(Items.CLOCK, 1), new Ask(Items.WHITE_WOOL, 8),
			new Ask(Items.BREAD, 8), new Ask(Items.COOKIE, 12), new Ask(Items.PUMPKIN_PIE, 2), new Ask(Items.CANDLE, 4));

	private Travellers() {
	}

	public static boolean passing(Villager villager) {
		return villager.getPersistentData().contains(TAG);
	}

	/** Of an evening at the tavern (TownLife): now and then a traveller takes a room, one at a time, while the town has room. */
	static void arrive(ServerLevel level, Roads.Network network, long day) {
		if (day - network.travellerDay < TownRules.TRAVELLER_EVERY || Towns.full(level, network.bell)) return;
		network.travellerDay = day;
		Roads.get(level.getServer()).setDirty();
		if (!Towns.people(level, network.bell, Travellers::passing).isEmpty()) return;
		net.minecraft.util.RandomSource random = level.getRandom();
		if (random.nextFloat() >= TownRules.TRAVELLER_CHANCE) return;
		List<BlockPos> seats = TownLife.seats(level, network, TownPlots.Kind.TAVERN);
		if (seats.isEmpty() || !level.isLoaded(seats.get(0))) return;
		Villager traveller = spawn(level, seats.get(random.nextInt(seats.size())), day, random);
		if (traveller != null) Speech.queue(traveller, "Traveller Takes a Room", null, 40L);
	}

	/** A traveller in from the road at {@code at}, asking for one of ASKS to stay. */
	public static @Nullable Villager spawn(ServerLevel level, BlockPos at, long day, net.minecraft.util.RandomSource random) {
		Villager traveller = com.vnap.Mc.create(EntityType.VILLAGER, level);
		if (traveller == null) return null;
		com.vnap.Mc.snap(traveller, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		//? if >=1.21.5 {
		/*traveller.setVillagerData(traveller.getVillagerData().withType(level.registryAccess(), VillagerType.byBiome(level.getBiome(at))));
		*///?} else
		traveller.setVillagerData(traveller.getVillagerData().setType(VillagerType.byBiome(level.getBiome(at))));
		traveller.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		Ask ask = ASKS.get(random.nextInt(ASKS.size()));
		CompoundTag data = traveller.getPersistentData();
		data.putLong(TAG, day);
		data.putString(ITEM, BuiltInRegistries.ITEM.getKey(ask.item).toString());
		data.putInt(COUNT, ask.count);
		data.putInt(PRICE, Math.max(TownRules.TRAVELLER_LEAST_PRICE, 2 * Prices.price(new ItemStack(ask.item)) * ask.count));
		level.addFreshEntity(traveller);
		VillagerHome.nameOf(traveller);
		return traveller;
	}

	/** What the traveller asks to stay: the thing, how many, and its price in coins. */
	public static ItemStack asks(Villager traveller) {
		CompoundTag data = traveller.getPersistentData();
		return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(Nbt.getString(data, ITEM))), Nbt.getInt(data, COUNT));
	}

	public static int price(Villager traveller) {
		return Nbt.getInt(traveller.getPersistentData(), PRICE);
	}

	/** Every 100 ticks for a traveller in a ticking chunk: after its days, with nobody to see, it goes on its way. */
	public static void tick(ServerLevel level, Villager traveller, long day) {
		if (day - Nbt.getLong(traveller.getPersistentData(), TAG) < TownRules.TRAVELLER_STAYS) return;
		if (!com.vnap.dialogue.VillagerHome.unseen(level, traveller)) return;
		if (traveller.isSleeping()) traveller.stopSleeping();
		traveller.discard();
	}

	/** A player's word with a traveller (Family.give): the thing asked for, or its price in coins, and it stays; else it says what it asks. */
	public static boolean give(ServerLevel level, Player player, Villager traveller, ItemStack stack) {
		if (!passing(traveller)) return false;
		CompoundTag data = traveller.getPersistentData();
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(Nbt.getString(data, ITEM)));
		int count = Nbt.getInt(data, COUNT);
		int price = Nbt.getInt(data, PRICE);
		BlockPos bell = bell(level, traveller);
		if (bell == null || Towns.full(level, bell)) {
			com.vnap.Mc.say(player, Component.translatable("traveller.villager_life.no_room", VillagerHome.nameOf(traveller)), true);
			return true;
		}
		if (stack.is(item) && stack.getCount() >= count) {
			if (!player.getAbilities().instabuild) stack.shrink(count);
		} else if (!Coins.isCoin(stack) || !Coins.pay(player, price)) {
			com.vnap.Mc.say(player, Component.translatable("traveller.villager_life.asks", VillagerHome.nameOf(traveller), count,
					new ItemStack(item).getHoverName(), Coins.text(price)), false);
			Speech.queue(traveller, "Traveller Asks to Stay", player, 1L);
			return true;
		} else {
			com.vnap.village.home.Mayors.Office office = Towns.of(level, bell);
			if (office != null) com.vnap.village.market.Market.deposit(level, office, price);
		}
		settle(level, traveller, bell);
		Chronicle.note(level, bell, "traveller", VillagerHome.nameOf(traveller));
		VillageMoodApi.applyAt(level, bell, 2.0F, 1.0F, "traveller");
		com.vnap.Mc.say(player, Component.translatable("traveller.villager_life.stays", VillagerHome.nameOf(traveller)), false);
		Speech.queue(traveller, "Traveller Settles", player, 1L);
		com.vnap.progress.VillageAdvancements.award(player, "town/traveller");
		return true;
	}

	/** The traveller is the town's now: its bell, its people, a house in time like any homeless villager's. */
	static void settle(ServerLevel level, Villager traveller, BlockPos bell) {
		CompoundTag data = traveller.getPersistentData();
		data.remove(TAG);
		data.remove(ITEM);
		data.remove(COUNT);
		data.remove(PRICE);
		traveller.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
		Towns.seen(level, traveller, Family.day(level));
	}

	private static @Nullable BlockPos bell(ServerLevel level, Villager traveller) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), traveller.blockPosition());
		return network == null ? null : network.bell;
	}
}
