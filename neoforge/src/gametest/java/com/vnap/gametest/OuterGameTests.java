package com.vnap.gametest;

import com.vnap.spire.Genome;
import com.vnap.spire.SpireGuards;
import com.vnap.spire.gear.GearPiece;
import com.vnap.spire.gear.GearTier;
import com.vnap.spire.gear.SpireGearItems;
import com.vnap.spire.guard.SpireGuard;
import com.vnap.spire.guard.SpireKeeper;
import com.vnap.spire.outer.Isles;
import com.vnap.spire.outer.Loadout;
import com.vnap.spire.outer.Outer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The Outer Gates: the Sky Path's every jump in reach, the Colosseum's waves, the Nameless's bosses, what a weapon makes of them. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class OuterGameTests {
	private OuterGameTests() {
	}

	@GameTest(template = "platform")
	public static void theColosseumIsWonAtFirstAndLostInTheEnd(GameTestHelper helper) {
		// a model of a wave against the gear it was drawn for (Loadout: the chapter's sword and plates, +0 and +10):
		// the wave cleared by the sword, three of it on the player at once meanwhile; between waves the player is whole
		// again (the break and a full belly). The first waves are won with health to spare, the fifth's keeper is a
		// fight but no wall, and the waves grow past any gear: an endless arena ends
		Genome.Role[] kinds = {Genome.Role.FIGHTER, Genome.Role.SHOOTER, Genome.Role.TANK, Genome.Role.LEAPER};
		StringBuilder all = new StringBuilder();
		boolean bad = false;
		for (GearTier tier : GearTier.values()) {
			for (int hone : new int[] {0, 10}) {
				double sword = com.vnap.spire.gear.SpireGear.headline(tier, GearPiece.SWORD, hone);
				double armour = 0;
				for (GearPiece piece : GearPiece.values()) if (piece.isArmour()) armour += com.vnap.spire.gear.SpireGear.headline(tier, piece, hone);
				double toughness = com.vnap.spire.gear.SpireGear.toughness(tier);
				double blows = com.vnap.spire.gear.SpireGear.speed(GearPiece.SWORD);
				int floor = 10 * tier.step() + 5 + hone / 3;
				double[] cost = new double[31];
				for (int w = 1; w <= 30; w++) {
					int at = floor + w / 3;
					int count = Math.min(22, 4 + w / 3);
					double health = 0;
					double damage = 0;
					for (Genome.Role role : kinds) {
						health += role.health * 1.3 * SpireGuards.health(at) / kinds.length;
						damage += role.damage * SpireGuards.damage(at) / kinds.length;
					}
					double[] crowd = com.vnap.spire.outer.Arena.crowd(w);
					health *= crowd[0];
					damage *= crowd[1];
					double time = count * health / (through(sword, 6.0, 0.0) * blows);
					double hit = through(damage, armour, toughness);
					com.vnap.spire.Attack strike = com.vnap.spire.Attack.STRIKE;
					double every = (strike.duration + strike.cooldown * 1.4 + 10) / 20.0;
					double taken = Math.min(3, count) * hit / every * time;
					if (w % 5 == 0) {
						int keepers = w % 10 == 0 ? 2 : 1;
						// a keeper's blows are telegraphed (marks, wind-ups): one in five seconds lands on a player who reads them
						double keeperTime = keepers * Genome.Role.KEEPER.health * crowd[0] * com.vnap.spire.outer.Arena.KEEPER_SHARE * SpireGuards.keeperHealth(at) / (through(sword, 8.0, 0.0) * blows);
						taken += keepers * through(Genome.Role.KEEPER.damage * crowd[1] * SpireGuards.keeperDamage(at), armour, toughness) / 5.0 * keeperTime;
					}
					cost[w] = taken / 20.0;
				}
				String what = String.format(java.util.Locale.ROOT, "%s +%d: wave 1 %.0f%%, 3 %.0f%%, 5 %.0f%%, 10 %.0f%%, 20 %.0f%%, 30 %.0f%%",
						tier.id(), hone, cost[1] * 100, cost[3] * 100, cost[5] * 100, cost[10] * 100, cost[20] * 100, cost[30] * 100);
				boolean ok = cost[1] <= 0.5 && cost[3] <= 0.7 && cost[5] <= 1.0 && (hone < 10 || cost[8] <= 1.0) && cost[30] > 1.0;
				all.append(ok ? "" : "FAIL ").append(what).append("; ");
				bad |= !ok;
			}
		}
		// the first waves won with health to spare, the fifth's keeper a fight but no wall, a honed set to the eighth
		// wave, and an arena without end ends
		Checks.assertFalse(helper, bad, "arena: " + all);
		com.vnap.VillagerLife.LOGGER.info("vnap test: arena model {}", all);
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void theNamelessAreBeatenAtFirstAndWinInTheEnd(GameTestHelper helper) {
		// a model of the rush (Rush.boss: the Nameless n is of the floor the weapon fits and two more for each before
		// it, its health and blows grown by Rush.grown): one at a time, its blows telegraphed (one
		// in four seconds lands), the felled lending strength (three more a blow for every four). The first is won with
		// health to spare, the third is still to be won, and in the end they win
		StringBuilder all = new StringBuilder();
		boolean bad = false;
		for (GearTier tier : GearTier.values()) {
			for (int hone : new int[] {0, 10}) {
				double sword = com.vnap.spire.gear.SpireGear.headline(tier, GearPiece.SWORD, hone);
				double armour = 0;
				for (GearPiece piece : GearPiece.values()) if (piece.isArmour()) armour += com.vnap.spire.gear.SpireGear.headline(tier, piece, hone);
				double toughness = com.vnap.spire.gear.SpireGear.toughness(tier);
				double blows = com.vnap.spire.gear.SpireGear.speed(GearPiece.SWORD);
				int floor = 10 * tier.step() + 5 + hone / 3;
				double[] cost = new double[31];
				for (int n = 0; n < 30; n++) {
					int at = floor + com.vnap.spire.outer.Rush.FLOORS_EACH * n;
					double[] grown = com.vnap.spire.outer.Rush.grown(n);
					double strength = 3.0 * Math.max(0, n / 4);
					double health = Genome.Role.KEEPER.health * SpireGuards.keeperHealth(at) * grown[0];
					double time = health / (through(sword + strength, 8.0, 0.0) * blows);
					double hit = through(Genome.Role.KEEPER.damage * SpireGuards.keeperDamage(at) * grown[1], armour, toughness);
					cost[n + 1] = hit / 4.0 * time / 20.0;
				}
				String what = String.format(java.util.Locale.ROOT, "%s +%d: 1st %.0f%%, 3rd %.0f%%, 5th %.0f%%, 10th %.0f%%, 20th %.0f%%, 30th %.0f%%",
						tier.id(), hone, cost[1] * 100, cost[3] * 100, cost[5] * 100, cost[10] * 100, cost[20] * 100, cost[30] * 100);
				boolean ok = cost[1] <= 0.6 && cost[3] <= 1.0 && cost[30] > 1.0;
				all.append(ok ? "" : "FAIL ").append(what).append("; ");
				bad |= !ok;
			}
		}
		Checks.assertFalse(helper, bad, "rush: " + all);
		helper.succeed();
	}

	private static double through(double damage, double armour, double toughness) {
		double soaked = Math.max(armour * 0.2, Math.min(20.0, armour - damage / (2.0 + toughness / 4.0)));
		return damage * (1.0 - soaked / 25.0);
	}

	@GameTest(template = "platform")
	public static void everyIslandOfTheSkyPathCanBeReachedFromTheMiddle(GameTestHelper helper) {
		for (long seed : new long[] {1L, 77L, -12345L}) {
			java.util.Set<String> forms = new java.util.HashSet<>();
			for (int cx = -12; cx <= 12; cx++) {
				for (int cz = -12; cz <= 12; cz++) {
					Isles.Cell cell = Isles.cell(seed, cx, cz, 120, 5);
					forms.add(cell.island().form().name());
					int[] in = Isles.toward(cx, cz);
					if (in.length == 0) continue;
					// the way in: from this island over its stones onto the next one toward the middle, each jump in reach both ways
					Isles.Island to = Isles.island(seed, in[0], in[1], 120);
					List<Isles.Island> way = new ArrayList<>();
					List<Isles.Island> stones = Isles.stones(cell.island(), to, new Random(0), 5);
					way.add(cell.island().at(stones.isEmpty() ? (to.x0() + to.x1()) / 2 : stones.get(0).x0(), stones.isEmpty() ? (to.z0() + to.z1()) / 2 : stones.get(0).z0()));
					way.addAll(stones);
					Isles.Island lastStone = way.get(way.size() - 1);
					way.add(to.at(lastStone.x0(), lastStone.z0()));
					for (int i = 0; i + 1 < way.size(); i++) {
						Isles.Island a = way.get(i);
						Isles.Island b = way.get(i + 1);
						Checks.assertTrue(helper, a.reaches(b) && b.reaches(a), "seed " + seed + " cell " + cx + "," + cz + " step " + i + ": " + a + " -> " + b
								+ " gap " + a.gap(b) + " rise " + (b.top() - a.top()));
					}
				}
			}
			Checks.assertTrue(helper, forms.size() >= 5, "islands of many shapes: " + forms);
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void aWeaponSetsWhatTheGatesDraw(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SpireGearItems.get(GearTier.ROOT, GearPiece.SWORD)));
		Loadout root = Loadout.of(player);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(SpireGearItems.get(GearTier.SUMMIT, GearPiece.SWORD)));
		Loadout summit = Loadout.of(player);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		Loadout bow = Loadout.of(player);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
		Loadout netherite = Loadout.of(player);
		Checks.assertTrue(helper, root.floor() <= 10 && summit.floor() >= 91, "a Root sword meets the first chapter, a Summit one the last: " + root + " " + summit);
		Checks.assertTrue(helper, root.style() == Loadout.Style.MELEE && bow.style() == Loadout.Style.RANGED, "a sword is up close, a bow from afar");
		Checks.assertTrue(helper, netherite.floor() > root.floor() && netherite.floor() < summit.floor(), "vanilla gear in between: " + netherite);
		player.discard();
		helper.succeed();
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 400)
	public static void aClosedRunLeavesNoGuardBehind(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Outer.Run run = Outer.openAt(level, Outer.Kind.ARENA, helper.absolutePos(new BlockPos(50, 2, 50)), new Loadout(20, Loadout.Style.MELEE));
		for (int i = 0; i < 300 && run.stage == 0; i++) Outer.step(run, List.of());
		// a while of the wave about (its shots in the air, a brood summoned)
		for (int i = 0; i < 100; i++) Outer.step(run, List.of());
		Checks.assertTrue(helper, run.livingGuards() >= 4, "the first wave is in: " + run.livingGuards());
		// each guard carries its run's mark: one saved out in an unloaded chunk is not let back once the run is closed
		java.util.Set<Long> marks = new java.util.HashSet<>();
		for (java.util.UUID id : run.guards()) {
			Entity guard = level.getEntity(id);
			Checks.assertTrue(helper, guard != null && guard.getPersistentData().contains(com.vnap.spire.SpireGuards.OUTER), "a guard of the run is marked as its");
			if (guard != null) marks.add(com.vnap.Nbt.getLong(guard.getPersistentData(), com.vnap.spire.SpireGuards.OUTER));
		}
		Checks.assertTrue(helper, marks.size() == 1 && Outer.open(marks.iterator().next()), "one mark, the open run's");
		Outer.end(run);
		Checks.assertTrue(helper, !Outer.open(marks.iterator().next()), "closed, its mark is no run's");
		helper.runAfterDelay(5, () -> {
			net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(50, 2, 50))).inflate(60, 30, 60);
			List<Entity> left = level.getEntities((Entity) null, around, entity -> !(entity instanceof net.minecraft.world.entity.player.Player));
			Checks.assertTrue(helper, left.isEmpty(), "a closed run leaves nothing of its own: " + left.stream().map(entity -> entity.getType().toShortString() + "@" + entity.blockPosition().toShortString()).toList());
			helper.succeed();
		});
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 200)
	public static void theColosseumSendsAWaveAndAKeeperOnTheFifth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Outer.Run run = Outer.openAt(level, Outer.Kind.ARENA, helper.absolutePos(new BlockPos(50, 2, 50)), new Loadout(20, Loadout.Style.MELEE));
		try {
			for (int i = 0; i < 300 && run.stage == 0; i++) Outer.step(run, List.of());
			Checks.assertTrue(helper, run.stage == 1 && run.livingGuards() >= 4, "the first wave comes: " + run.livingGuards() + " guards");
			// a blade meets mostly what it can reach at first: shooters and flyers a quarter of the first wave at most
			int counters = 0;
			for (java.util.UUID id : run.guards()) {
				if (level.getEntity(id) instanceof com.vnap.spire.guard.SpireGuard guard
						&& (guard.genome().role() == com.vnap.spire.Genome.Role.SHOOTER || guard.genome().movement() == com.vnap.spire.Genome.Movement.FLY)) counters++;
			}
			Checks.assertTrue(helper, counters <= Math.ceil(run.guards().size() * 0.25), "the first wave against a blade is mostly within its reach: " + counters + " of " + run.guards().size());
			// never at the gate one comes in by (a guard two steps off it killed whoever came through)
			BlockPos gate = Outer.start(run);
			for (java.util.UUID id : run.guards()) {
				Entity guard = level.getEntity(id);
				Checks.assertTrue(helper, guard == null || guard.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(gate)) > 12 * 12,
						"a guard of the wave set down by the gate: " + (guard == null ? "" : guard.blockPosition().toShortString()) + " gate " + gate.toShortString());
			}
			for (int wave = 1; wave < 5; wave++) {
				for (java.util.UUID id : List.copyOf(run.guards())) {
					Entity guard = level.getEntity(id);
					if (guard != null) guard.discard();
				}
				for (int i = 0; i < 400 && run.stage == wave; i++) Outer.step(run, List.of());
			}
			Checks.assertTrue(helper, run.stage == 5, "five waves in: " + run.stage);
			boolean keeper = false;
			for (java.util.UUID id : run.guards()) if (level.getEntity(id) instanceof SpireKeeper) keeper = true;
			Checks.assertTrue(helper, keeper, "the fifth wave has a keeper");
		} finally {
			Outer.end(run);
		}
		helper.succeed();
	}

	@GameTest(template = "workshop_platform", timeoutTicks = 400)
	public static void theNamelessComeOneByOneEachUnlikeTheLast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Outer.Run run = Outer.openAt(level, Outer.Kind.RUSH, helper.absolutePos(new BlockPos(50, 2, 50)), new Loadout(40, Loadout.Style.MELEE));
		List<String> seen = new ArrayList<>();
		try {
			for (int boss = 0; boss < 3; boss++) {
				for (int i = 0; i < 400 && run.livingGuards() == 0; i++) Outer.step(run, List.of());
				SpireGuard nameless = null;
				for (java.util.UUID id : run.guards()) if (level.getEntity(id) instanceof SpireKeeper k) nameless = k;
				if (nameless == null) {
					Checks.fail(helper, "boss " + (boss + 1) + " never came");
					return;
				}
				Checks.assertTrue(helper, nameless.getCustomName() != null, "a Nameless one has a name");
				seen.add(nameless.genome().body() + " " + nameless.genome().attacks());
				// it stands and fights a while without a fault (its arts are drawn from its seed)
				for (int i = 0; i < 100; i++) nameless.tick();
				nameless.discard();
				for (int i = 0; i < 3; i++) Outer.step(run, List.of());
			}
			Checks.assertTrue(helper, run.stage == 3, "three felled: " + run.stage);
			Checks.assertTrue(helper, new java.util.HashSet<>(seen).size() >= 2, "not all alike: " + seen);
		} finally {
			Outer.end(run);
		}
		helper.succeed();
	}
}
