package com.vnap.gametest;

import com.vnap.item.LifeItems;
import com.vnap.spire.SpireGrowth;
import com.vnap.village.home.Mayors;
import com.vnap.spire.SpireWritItem;
import com.vnap.spire.Spires;
import com.vnap.village.market.Prices;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class SpireWritGameTests {
	private SpireWritGameTests() {
	}

	/**
	 * Read in the open, away from any town, the writ puts the spire on flat ground ahead of the reader (beyond the
	 * spire's own box, in the way it looks); a second writ read there stays silent, and the writ costs some ten diamonds.
	 */
	@GameTest(template = "platform", timeoutTicks = 200)
	public static void aWritCallsTheSpireUpAheadOfItsReaderAndOnlyOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		// no spire of an earlier batch near this ground, the ground ahead loaded
		Spires spires = Spires.get(level.getServer());
		spires.all().removeIf(spire -> spire.dimension.equals(level.dimension()) && spire.gate.closerThan(at, 600));
		for (int cx = (at.getX() >> 4) - 1; cx <= (at.getX() >> 4) + 7; cx++) {
			for (int cz = (at.getZ() >> 4) - 4; cz <= (at.getZ() >> 4) + 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz);
			}
		}
		// looking east (yaw -90: +x)
		Spires.Spire[] placed = new Spires.Spire[1];
		SpireWritItem.Outcome outcome = SpireWritItem.place(level, at, -90.0F, placed);
		Spires.Spire spire = placed[0];
		try {
			Checks.assertTrue(helper, outcome == SpireWritItem.Outcome.AHEAD && spire != null, "the writ placed nothing: " + outcome);
			if (spire == null) return;
			int ahead = spire.centre.getX() - at.getX();
			int aside = Math.abs(spire.centre.getZ() - at.getZ());
			Checks.assertTrue(helper, ahead >= 24 && ahead <= 72 && aside <= ahead, "the spire stands at " + ahead + " ahead and " + aside + " aside");
			Checks.assertTrue(helper, SpireWritItem.place(level, at, -90.0F, new Spires.Spire[1]) == SpireWritItem.Outcome.NEAR_SPIRE,
					"a second writ by the first spire called another");
			int writ = Prices.price(new ItemStack(LifeItems.SPIRE_WRIT));
			int diamond = Prices.price(new ItemStack(Items.DIAMOND));
			int trade = 6 * diamond + 16 * Prices.price(new ItemStack(Items.EMERALD));
			Checks.assertTrue(helper, writ >= 9 * diamond && writ <= 11 * diamond && Math.abs(trade - writ) <= diamond,
					"the writ costs " + writ + " (a diamond " + diamond + ", the trader's price " + trade + ")");
		} finally {
			spires.all().remove(spire);
			for (int cx = (at.getX() >> 4) - 1; cx <= (at.getX() >> 4) + 7; cx++) {
				for (int cz = (at.getZ() >> 4) - 4; cz <= (at.getZ() >> 4) + 4; cz++) level.setChunkForced(cx, cz, false);
			}
		}
		helper.succeed();
	}

	/** Chunks loaded from the bell eastward, where a town's spire is looked for first (Rings start east). */
	private static void load(ServerLevel level, BlockPos from, boolean on) {
		for (int cx = (from.getX() >> 4) - 1; cx <= (from.getX() >> 4) + 8; cx++) {
			for (int cz = (from.getZ() >> 4) - 8; cz <= (from.getZ() >> 4) + 8; cz++) {
				level.setChunkForced(cx, cz, on);
				if (on) level.getChunk(cx, cz);
			}
		}
	}

	/**
	 * A writ read in a town without a spire calls the town's own (outside it, the town owns it); a second writ in the
	 * same town stays silent, one bought in another town far off calls that town its own; and when a town grows done
	 * by a spire a writ called up in the wild beside it, it takes that one and grows no second.
	 */
	@GameTest(template = "platform", timeoutTicks = 400)
	public static void oneSpireATownWhetherAWritOrTheTownCalledIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPos first = home.offset(0, 0, 0);
		BlockPos second = home.offset(0, 0, 1200);
		BlockPos third = home.offset(0, 0, 2400);
		Spires spires = Spires.get(level.getServer());
		Mayors mayors = Mayors.get(level.getServer());
		spires.all().removeIf(spire -> spire.dimension.equals(level.dimension()) && spire.gate.closerThan(home, 3000));
		for (BlockPos bell : new BlockPos[] {first, second, third}) load(level, bell, true);
		Mayors.Office one = mayors.officeFor(level.dimension(), first);
		Mayors.Office two = mayors.officeFor(level.dimension(), second);
		try {
			Spires.Spire[] placed = new Spires.Spire[1];
			Checks.assertTrue(helper, SpireWritItem.place(level, first, 0.0F, placed) == SpireWritItem.Outcome.TOWN && placed[0] != null,
					"the first town's writ placed no spire of its own");
			Checks.assertTrue(helper, SpireGrowth.claimed(level, first), "the first town does not count the writ's spire as its own");
			Checks.assertTrue(helper, SpireWritItem.place(level, first, 0.0F, new Spires.Spire[1]) == SpireWritItem.Outcome.NEAR_SPIRE,
					"a second writ in the same town called another spire");
			Spires.Spire[] other = new Spires.Spire[1];
			Checks.assertTrue(helper, SpireWritItem.place(level, second, 0.0F, other) == SpireWritItem.Outcome.TOWN && other[0] != null,
					"a writ bought in another town far off called nothing there");
			Checks.assertTrue(helper, spires.ofTown(level.dimension(), first) != spires.ofTown(level.dimension(), second), "the two towns share a spire");
			// a spire a writ called up in the wild (no town yet), and then a town grows beside it: the town takes that one
			Spires.Spire[] wild = new Spires.Spire[1];
			Checks.assertTrue(helper, SpireWritItem.place(level, third, -90.0F, wild) == SpireWritItem.Outcome.AHEAD && wild[0] != null,
					"the writ in the wild placed nothing");
			Mayors.Office grown = mayors.officeFor(level.dimension(), third);
			Checks.assertTrue(helper, SpireGrowth.claimed(level, grown.bell), "a town grown by a writ's spire would grow a second one");
			mayors.offices().remove(grown);
		} finally {
			spires.all().removeIf(spire -> spire.dimension.equals(level.dimension()) && spire.gate.closerThan(home, 3000));
			mayors.offices().remove(one);
			mayors.offices().remove(two);
			for (BlockPos bell : new BlockPos[] {first, second, third}) load(level, bell, false);
		}
		helper.succeed();
	}

	/** Every wandering trader has the writ, not by chance. */
	@GameTest(template = "platform")
	public static void everyWanderingTraderSellsTheWrit(GameTestHelper helper) {
		for (int i = 0; i < 4; i++) {
			net.minecraft.world.entity.npc.WanderingTrader trader = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.WANDERING_TRADER, 1 + i, 2, 1);
			Checks.assertTrue(helper, trader.getOffers().stream().anyMatch(offer -> offer.getResult().is(LifeItems.SPIRE_WRIT)
					&& offer.getBaseCostA().is(Items.EMERALD) && offer.getCostB().is(Items.DIAMOND)), "a wandering trader without the writ");
			trader.discard();
		}
		helper.succeed();
	}
}
