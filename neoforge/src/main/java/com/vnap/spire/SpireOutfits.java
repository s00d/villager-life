package com.vnap.spire;

import com.vnap.entity.VillagerLifeData;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import com.vnap.village.town.TownRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import java.util.Random;

/**
 * Clothes from the spire (DESIGN §8–9): a villager back from a trip now and then wears something it found up there,
 * one of six by the chapter it reached; one with coins to spare buys one from the stall's tailor (money goes round the
 * town, not out of it). Drawn by client/VillagerOutfitLayer over its robe (tools/art/paint_spire_outfits.py).
 */
public final class SpireOutfits {
	/** In the order of their number (1 the first): what VillagerOutfitLayer draws for it. */
	public static final java.util.List<String> NAMES = java.util.List.of("cloak", "scarf", "apron", "sash", "coat", "mantle");
	/** What the tailor asks for a set, in copper. */
	public static final int PRICE = 40;

	private SpireOutfits() {
	}

	/** The clothes of the chapter a trip reached: roots and archive give the cloak and the scarf, and so on round. */
	public static int ofFloor(int floor, Random random) {
		int chapter = Math.max(0, (floor - 1) / 10);
		return 1 + Math.floorMod(chapter + random.nextInt(2), NAMES.size());
	}

	public static int of(Villager villager) {
		return villager instanceof VillagerLifeData data ? data.vnap$spireOutfit() : 0;
	}

	public static void wear(Villager villager, int outfit) {
		if (villager instanceof VillagerLifeData data) data.vnap$setSpireOutfit(Math.floorMod(outfit, NAMES.size() + 1));
	}

	/** A grown-up with coins to spare and none of the spire's clothes buys a set at its stall; false when it does not. */
	public static boolean buy(ServerLevel level, Villager villager, Random random) {
		if (villager.isBaby() || of(villager) != 0 || Market.funds(level, villager) < PRICE + TownRules.RESERVE * 2) return false;
		Shops.Shop shop = Shops.of(level, villager);
		if (shop == null || !Shops.open(level, shop)) return false;
		Market.charge(level, villager, PRICE);
		shop.till += PRICE;
		Shops.get(level.getServer()).setDirty();
		wear(villager, 1 + random.nextInt(NAMES.size()));
		return true;
	}
}
