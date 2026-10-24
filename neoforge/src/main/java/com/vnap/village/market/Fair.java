package com.vnap.village.market;

import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import net.minecraft.server.level.ServerLevel;

/**
 * The fair: the caravan's day at the village (once a week). The stall pays players a quarter more for their goods and
 * twice as much in a day; music plays on the square (TownLife). A player still cannot sell back at a profit: a
 * quarter more than 0.4-0.6 of a price stays under the 0.8 it buys at.
 */
public final class Fair {
	static final float SELL_BONUS = 1.25F;

	private Fair() {
	}

	public static boolean today(ServerLevel level, Shops.Shop shop) {
		return shop.fairDay == Family.day(level);
	}

	static int offer(ServerLevel level, Shops.Shop shop, int offer) {
		return today(level, shop) ? (int) Math.floor(offer * SELL_BONUS) : offer;
	}

	public static int budget(ServerLevel level, Shops.Shop shop) {
		int budget = today(level, shop) ? 2 * TownRules.PLAYER_BUDGET : TownRules.PLAYER_BUDGET;
		// a covered market: room for more sellers, half again of what the shop takes from them
		return com.vnap.village.town.TownPlots.has(level, shop.bell, com.vnap.village.town.TownPlots.Kind.MARKET) ? budget * 3 / 2 : budget;
	}
}
