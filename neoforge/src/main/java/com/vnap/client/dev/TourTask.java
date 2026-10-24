package com.vnap.client.dev;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A town gone round as a visitor goes round it: the server names its places ({@code /vnap town tour}: the bell, the
 * notice board, the shop's counter, every door the town's roads lead to, the churchyard), the bot walks to each on
 * the client's blocks, opens the board and the shop, and writes down where it stood ({@code at <kind> x y z}, a
 * watcher outside takes a picture then) and every place it could not reach ({@code NO WAY}).
 */
final class TourTask implements DevBot.Task {
	/** What the server said of the tour, line by line (DevBot.heard). */
	static final List<String> HEARD = new ArrayList<>();
	private static final int GIVE_UP = 20 * 60;
	private static final int LOOK = 160;

	private record Place(String kind, BlockPos pos) {
	}

	private final List<Place> places = new ArrayList<>();
	/** The town's bell, as the server named it (where a visitor sets its bed down for the night: LiveTask). */
	@org.jspecify.annotations.Nullable BlockPos bell;
	private boolean asked;
	private boolean listed;
	private int index;
	private int ticks;
	private int stood = -1;
	private int reached;
	private int missed;
	private int waited;

	TourTask(DevBot bot) {
		bot.log("tour: asking the town for its places");
	}

	@Override
	public boolean tick(DevBot bot) {
		if (bot.respawning()) return true;
		if (!asked) {
			DevBot.dry(true);
			HEARD.clear();
			bot.command("vnap town tour");
			asked = true;
			return true;
		}
		if (!listed) {
			// each line once: what has come since the last tick
			List<String> lines = new ArrayList<>(HEARD);
			HEARD.clear();
			for (String line : lines) {
				String[] part = line.split(" ", -1);
				if (part.length >= 2 && part[1].equals("end")) listed = true;
				if (part.length == 5 && !part[1].equals("end")) {
					places.add(new Place(part[1], new BlockPos(Integer.parseInt(part[2]), Integer.parseInt(part[3]), Integer.parseInt(part[4]))));
					if (part[1].equals("bell")) bell = places.get(places.size() - 1).pos();
				}
			}
			if (!listed) {
				if (++waited > 200) {
					bot.log("tour: the server gave no places (no town near?)");
					return false;
				}
				return true;
			}
			bot.log("tour: " + places.size() + " places");
			return !places.isEmpty();
		}
		if (index >= places.size()) {
			bot.log("tour: done, reached " + reached + " of " + places.size() + ", no way to " + missed);
			return false;
		}
		Place place = places.get(index);
		if (stood >= 0) {
			// there: a look round (a picture is taken outside), the board or the counter opened, then on
			if (stood == 5 && (place.kind().equals("board") || place.kind().equals("counter"))) bot.use(place.pos());
			// hungry at the counter: the cheapest food on the shelf, as a player buys bread for the road
			if (stood == 15 && place.kind().equals("counter") && bot.player().getFoodData().getFoodLevel() < 18
					&& !(bot.player().containerMenu instanceof com.vnap.village.market.ShopMenu)) {
				bot.log("  hungry, and the counter did not open (" + (com.vnap.client.McClient.screen() == null ? "no screen" : com.vnap.client.McClient.screen().getClass().getSimpleName()) + ")");
			}
			if (stood == 15 && place.kind().equals("counter") && bot.player().getFoodData().getFoodLevel() < 18
					&& bot.player().containerMenu instanceof com.vnap.village.market.ShopMenu shop) {
				int cheapest = -1;
				for (int i = 0; i < shop.offers.size(); i++) {
					var offer = shop.offers.get(i);
					if (offer.item().get(net.minecraft.core.component.DataComponents.FOOD) == null) continue;
					if (cheapest < 0 || offer.price() < shop.offers.get(cheapest).price()) cheapest = i;
				}
				if (cheapest >= 0) {
					// three, a day's eating
					for (int i = 0; i < 3; i++) PacketDistributor.sendToServer(new com.vnap.network.ShopActionPayload(shop.containerId, com.vnap.network.ShopActionPayload.BUY, cheapest));
					bot.log("  bought 3 " + shop.offers.get(cheapest).item().getHoverName().getString() + " for " + shop.offers.get(cheapest).price());
				} else {
					bot.log("  hungry, and no food on the shelf");
				}
			}
			if (++stood > LOOK) {
				bot.closeScreen();
				next();
			}
			return true;
		}
		BlockPos aim = place.pos();
		boolean there = bot.player().blockPosition().distSqr(aim) <= 9;
		if (there || !bot.walk(aim, cell -> cell.distSqr(aim) <= 6)) {
			if (there || bot.player().blockPosition().distSqr(aim) <= 16) {
				bot.release();
				bot.look(net.minecraft.world.phys.Vec3.atCenterOf(aim));
				bot.log("at " + place.kind() + " " + aim.getX() + " " + aim.getY() + " " + aim.getZ());
				reached++;
				stood = 0;
				return true;
			}
		}
		if (++ticks > GIVE_UP) {
			bot.release();
			bot.log("  NO WAY to " + place.kind() + " at " + aim.toShortString() + " from " + bot.player().blockPosition().toShortString());
			missed++;
			next();
		}
		return true;
	}

	private void next() {
		index++;
		ticks = 0;
		stood = -1;
	}

	@Override
	public void stopped(DevBot bot) {
		DevBot.dry(false);
		HEARD.clear();
	}
}
