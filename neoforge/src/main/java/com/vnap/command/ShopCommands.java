package com.vnap.command;

import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Caravan;
import com.vnap.village.market.Coins;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.Comparator;

/** {@code /vnap shop info|choose|stall|caravan|wants|coins <n>}: the stall of the village around the player. */
final class ShopCommands {
	private ShopCommands() {
	}

	/** Every item of the game with its worth and where that comes from, into prices_audit.tsv by the world. */
	static int prices(CommandSourceStack source) {
		java.nio.file.Path file = source.getServer().getServerDirectory().resolve("prices_audit.tsv");
		StringBuilder out = new StringBuilder("item\tworth\tsource\tmax_stack\trarity\n");
		int none = 0;
		for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
			var stack = new net.minecraft.world.item.ItemStack(item);
			double worth = com.vnap.village.market.Prices.worth(stack);
			String from = com.vnap.village.market.Prices.source(stack);
			if (worth <= 0.0) none++;
			out.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).append('\t').append(String.format(java.util.Locale.ROOT, "%.2f", worth))
					.append('\t').append(from).append('\t').append(stack.getMaxStackSize()).append('\t').append(stack.getRarity()).append('\n');
		}
		try {
			java.nio.file.Files.writeString(file, out);
		} catch (java.io.IOException error) {
			source.sendFailure(Component.literal("could not write " + file + ": " + error.getMessage()));
			return 0;
		}
		int unpriced = none;
		source.sendSuccess(() -> Component.literal("prices: " + file + ", " + unpriced + " items with no price"), false);
		return 1;
	}

	static int run(CommandSourceStack source, String action, int amount) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return 0;
		ServerLevel level = source.getLevel();
		long day = Family.day(level);
		if (action.equals("coins")) {
			Coins.give(player, amount);
			source.sendSuccess(() -> Component.literal("gave ").append(Coins.text(amount)), false);
			return 1;
		}
		Villager nearest = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48.0),
						villager -> villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT))
				.stream().min(Comparator.comparingDouble(villager -> villager.distanceToSqr(player))).orElse(null);
		BlockPos bell = nearest == null ? null : com.vnap.village.home.Towns.bellOf(nearest);
		if (nearest == null || bell == null) {
			source.sendFailure(Component.literal("No villager with a bell nearby"));
			return 0;
		}
		Shops shops = Shops.get(level.getServer());
		Shops.Shop shop = shops.shopNear(level.dimension(), bell);
		if (action.equals("wants")) {
			FamilyState family = Family.state(nearest);
			StringBuilder text = new StringBuilder(VillagerHome.nameOf(nearest)).append(": ").append(family.coins).append("c own, ")
					.append(Market.funds(level, nearest)).append("c to spend, owes ").append(Market.debt(level, nearest)).append("c, wage ").append(Market.wage(nearest))
					.append(", paid day ").append(family.paidDay).append("; wants");
			if (family.wants.isEmpty()) text.append(" nothing");
			for (FamilyState.Want want : family.wants) {
				text.append(' ').append(want.item).append('x').append(want.count).append(" since ").append(want.since)
						.append(" offered ").append(want.offered);
			}
			source.sendSuccess(() -> Component.literal(text.toString()), false);
			return 1;
		}
		if (action.equals("choose")) {
			if (shop == null) {
				shop = new Shops.Shop(level.dimension(), bell);
				shops.shops().add(shop);
			}
			if (shop.keeper != null) {
				source.sendFailure(Component.literal("The stall is kept by " + shop.name));
				return 0;
			}
			Villager chosen = shops.choose(level, shop, day, null);
			if (chosen == null) {
				source.sendFailure(Component.literal("No shopkeeper: no ground by the bell for one to arrive"));
				return 0;
			}
			source.sendSuccess(() -> Component.literal("chose " + VillagerHome.nameOf(chosen)), true);
			return 1;
		}
		if (shop == null) {
			source.sendFailure(Component.literal("No stall in this village yet: it opens on the village's next day"));
			return 0;
		}
		Villager keeper = Shops.keeper(level, shop);
		if (action.equals("stall")) {
			if (keeper == null) {
				source.sendFailure(Component.literal("No shopkeeper loaded here"));
				return 0;
			}
			HouseProject stall = Shops.stallOf(level, shop);
			if (stall == null) stall = HomeProjects.start(level, keeper, day, Shops.STALLS);
			if (stall == null) {
				source.sendFailure(Component.literal("No site for the stall near the bell"));
				return 0;
			}
			HomeProjects.buildNow(level, stall);
			HouseProject built = stall;
			source.sendSuccess(() -> Component.literal("stall at " + built.origin.toShortString()), true);
			return 1;
		}
		if (action.equals("caravan")) {
			if (!shop.stocked) {
				source.sendFailure(Component.literal("The stall has not opened yet"));
				return 0;
			}
			int cost = Caravan.arrive(level, shop, day);
			source.sendSuccess(() -> Component.literal("caravan delivered for " + cost + "c"), true);
			return 1;
		}
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), shop.bell);
		int goods = shop.stock.stream().mapToInt(lot -> lot.count).sum();
		String text = "stall " + shop.bell.toShortString() + ": " + (shop.keeper == null ? "no keeper" : shop.name
				+ (keeper == null ? " (not loaded)" : "")) + (shop.counter == null ? ", not built" : ", counter " + shop.counter.toShortString())
				+ (Shops.open(level, shop) ? ", OPEN" : ", shut") + "; till " + shop.till + "c, " + shop.stock.size() + " lots, "
				+ goods + " goods; caravan day " + shop.caravanDay + "; treasury "
				+ (office == null || office.treasury < 0 ? "not opened" : office.treasury + "c (made up " + office.minted + "c)")
				+ (shop.orders.isEmpty() ? "" : "; ordered " + shop.orders.size())
				+ "; cradles " + shop.count(Market.decor("cradle"));
		source.sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
