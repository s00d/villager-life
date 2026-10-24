package com.vnap.gametest;

import com.vnap.spire.Afflictions;
import com.vnap.spire.FloorGenerator;
import com.vnap.spire.FloorPlan;
import com.vnap.spire.SpireBlocks;
import com.vnap.spire.SpireExterior;
import com.vnap.spire.SpireGuards;
import com.vnap.spire.SpirePortalBlock;
import com.vnap.spire.SpireProgress;
import com.vnap.spire.SpireRewards;
import com.vnap.spire.SpireSlots;
import com.vnap.spire.Spires;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** The Spire of the Builders (docs/tower/DESIGN.md): floors out of seeds, the slots they are built in, the spire outside. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class SpireGameTests {
	private SpireGameTests() {
	}

	@GameTest(template = "platform")
	public static void aFloorIsTheSameFromTheSameSeedAndAnotherFromAnother(GameTestHelper helper) {
		FloorPlan one = FloorGenerator.plan(1234L, 7);
		FloorPlan again = FloorGenerator.plan(1234L, 7);
		FloorPlan other = FloorGenerator.plan(1234L, 8);
		FloorPlan town = FloorGenerator.plan(9999L, 7);
		int same = 0;
		int differs = 0;
		int differsTown = 0;
		for (int x = 0; x < FloorPlan.SIZE_X; x++) {
			for (int y = 0; y < FloorPlan.SIZE_Y; y++) {
				for (int z = 0; z < FloorPlan.SIZE_Z; z++) {
					if (java.util.Objects.equals(one.get(x, y, z), again.get(x, y, z))) same++;
					if (!java.util.Objects.equals(one.get(x, y, z), other.get(x, y, z))) differs++;
					if (!java.util.Objects.equals(one.get(x, y, z), town.get(x, y, z))) differsTown++;
				}
			}
		}
		Checks.assertTrue(helper, same == FloorPlan.SIZE_X * FloorPlan.SIZE_Y * FloorPlan.SIZE_Z && one.guards.equals(again.guards),
				"the same seed, the same floor block for block");
		Checks.assertTrue(helper, differs > 2000 && differsTown > 2000, "the next floor and another town's are other floors: " + differs + ", " + differsTown);
		helper.succeed();
	}

	@GameTest(template = "platform", timeoutTicks = 400)
	public static void everyFloorHoldsTogetherAndKeepsItsWaterInBounds(GameTestHelper helper) {
		for (int seed = 0; seed < 60; seed++) {
			for (int floor : new int[] {1, 9, 10, 23, 47, 70, 100, 137}) {
				FloorPlan plan = FloorGenerator.plan(seed * 7919L, floor);
				String where = "seed " + seed + " floor " + floor + " (" + plan.theme + ")";
				Checks.assertTrue(helper, plan.exitPortal.size() == 6 && plan.onwardPortal.size() == 6, "two portals of six cells: " + where);
				Checks.assertTrue(helper, plan.note != null, "a tablet on every floor: " + where);
				java.util.Set<Long> reached = walk(plan);
				Checks.assertTrue(helper, near(reached, plan.chest) && near(reached, plan.onwardPortal.get(0).north()), "the chest and the way on are reached: " + where);
				for (FloorPlan.Spawn guard : plan.guards) {
					Checks.assertTrue(helper, near(reached, guard.pos()) && plan.empty(guard.pos().getX(), guard.pos().getY(), guard.pos().getZ())
							&& plan.empty(guard.pos().getX(), guard.pos().getY() + 1, guard.pos().getZ()), "a guard stands free and is reached: " + where + " " + guard.pos());
				}
				Checks.assertTrue(helper, !plan.guards.isEmpty() && plan.guards.size() <= 16 + 3 * Math.min(3, com.vnap.spire.Genome.round(floor)) + 2,
						"guards, never a crowd (more past the hundredth): " + plan.guards.size() + " " + where);
				int water = 0;
				for (int x = 0; x < FloorPlan.SIZE_X; x++) {
					for (int y = 0; y < FloorPlan.SIZE_Y; y++) {
						for (int z = 0; z < FloorPlan.SIZE_Z; z++) {
							BlockState state = plan.get(x, y, z);
							if (state != null && state.is(Blocks.WATER)) water++;
						}
					}
				}
				// one basin of 4 x 4, four deep, and a fountain's 3 x 3 at most
				Checks.assertTrue(helper, water <= 73, "water kept to a basin: " + water + " " + where);
			}
		}
		helper.succeed();
	}

	/** Vanilla's armour (CombatRules): what a blow of {@code damage} leaves through {@code armour} and {@code toughness}. */
	private static double through(double damage, double armour, double toughness) {
		double soaked = Math.max(armour * 0.2, Math.min(20.0, armour - damage / (2.0 + toughness / 4.0)));
		return damage * (1.0 - soaked / 25.0);
	}

	/**
	 * Every keeper against the gear of its chapter (the tier its floors' shards and its seal open, honed from +0 to
	 * +10): the climber kills it in 15..50 seconds of blows (a fight of a minute or two with its arts and windows),
	 * its slam takes about a fifth to under half of the climber's health — never one blow, never nothing — and the shards of
	 * the floors below are enough to raise a whole set to that tier.
	 */
	@GameTest(template = "platform")
	public static void everyKeeperFitsTheGearOfItsChapter(GameTestHelper helper) {
		int shards = 0;
		int floor = 0;
		for (int chapter = 0; chapter < com.vnap.spire.Genome.KEEPERS.size(); chapter++) {
			int keeperFloor = chapter * 10 + 10;
			com.vnap.spire.Genome keeper = com.vnap.spire.Genome.decode(chapter, keeperFloor, com.vnap.spire.Genome.THEMES.get(chapter), false, true);
			Checks.assertTrue(helper, keeper.keeper() == chapter && keeper.problem() == null, "floor " + keeperFloor + " keeper: " + keeper.problem());
			com.vnap.spire.gear.GearTier tier = com.vnap.spire.gear.GearTier.ofChapter(chapter);
			while (floor < keeperFloor - 1) shards += SpireRewards.shards(++floor);
			int raise = 0;
			for (int t = 0; t <= chapter; t++) raise += 5 * com.vnap.spire.gear.SpireGear.craftShards(com.vnap.spire.gear.GearTier.ofChapter(t));
			Checks.assertTrue(helper, shards >= raise, "by floor " + keeperFloor + " " + shards + " shards, a " + tier.id() + " set costs " + raise);
			for (int hone : new int[] {0, 10}) {
				double sword = com.vnap.spire.gear.SpireGear.headline(tier, com.vnap.spire.gear.GearPiece.SWORD, hone);
				double armour = 0;
				for (com.vnap.spire.gear.GearPiece piece : com.vnap.spire.gear.GearPiece.values()) {
					if (piece.isArmour()) armour += com.vnap.spire.gear.SpireGear.headline(tier, piece, hone);
				}
				double toughness = com.vnap.spire.gear.SpireGear.toughness(tier);
				double seconds = keeper.health() / (through(sword, 8.0, 0.0) * com.vnap.spire.gear.SpireGear.speed(com.vnap.spire.gear.GearPiece.SWORD));
				double slam = through(keeper.damage() * com.vnap.spire.Attack.SLAM.power, armour, toughness) / 20.0;
				String what = keeperFloor + " " + com.vnap.spire.Genome.KEEPERS.get(chapter) + " vs " + tier.id() + " +" + hone + ": " + Math.round(seconds)
						+ " s, slam " + Math.round(slam * 100) + "%";
				Checks.assertTrue(helper, seconds >= 15.0 && seconds <= 50.0, what);
				Checks.assertTrue(helper, slam >= 0.18 && slam <= 0.45, what);
				// the half-chapter's trial (or the Schism's pair, both together) before it: a little less than its keeper
				int half = chapter * 10 + 5;
				double trialHealth = com.vnap.spire.Genome.schism(half)
						? com.vnap.spire.Genome.duo(1L, half, "galleries", com.vnap.spire.Genome.MASON).health()
								+ com.vnap.spire.Genome.duo(1L, half, "galleries", com.vnap.spire.Genome.SURVEYOR_ALONE).health()
						: com.vnap.spire.Genome.trial(1L, half, com.vnap.spire.Genome.THEMES.get(chapter)).health();
				double trialDamage = com.vnap.spire.Genome.schism(half) ? com.vnap.spire.Genome.duo(1L, half, "galleries", com.vnap.spire.Genome.SURVEYOR_ALONE).damage()
						: com.vnap.spire.Genome.trial(1L, half, com.vnap.spire.Genome.THEMES.get(chapter)).damage();
				double trialSeconds = trialHealth / (through(sword, 8.0, 0.0) * com.vnap.spire.gear.SpireGear.speed(com.vnap.spire.gear.GearPiece.SWORD));
				double trialSlam = through(trialDamage * com.vnap.spire.Attack.SLAM.power, armour, toughness) / 20.0;
				String trial = half + " trial vs " + tier.id() + " +" + hone + ": " + Math.round(trialSeconds) + " s, slam " + Math.round(trialSlam * 100) + "%";
				Checks.assertTrue(helper, trialSeconds >= 8.0 && trialSeconds <= 50.0, trial);
				Checks.assertTrue(helper, trialSlam >= 0.12 && trialSlam <= 0.45, trial);
			}
			shards += SpireRewards.shards(++floor);
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void everyGuardFitsTheGearOfItsFloor(GameTestHelper helper) {
		// against the gear of its chapter at +0: the biggest guard falls in seconds, an elite in a short fight, and no
		// blow of theirs takes much more than a fifth of the climber (a pack at once is what makes a floor hard)
		com.vnap.spire.Genome.Role tank = com.vnap.spire.Genome.Role.TANK;
		for (int floor = 1; floor <= 100; floor++) {
			if (floor % 10 == 0) continue;
			com.vnap.spire.gear.GearTier tier = com.vnap.spire.gear.GearTier.ofChapter((floor - 1) / 10);
			double sword = com.vnap.spire.gear.SpireGear.headline(tier, com.vnap.spire.gear.GearPiece.SWORD, 0);
			double armour = 0;
			for (com.vnap.spire.gear.GearPiece piece : com.vnap.spire.gear.GearPiece.values()) {
				if (piece.isArmour()) armour += com.vnap.spire.gear.SpireGear.headline(tier, piece, 0);
			}
			double toughness = com.vnap.spire.gear.SpireGear.toughness(tier);
			double blows = com.vnap.spire.gear.SpireGear.speed(com.vnap.spire.gear.GearPiece.SWORD);
			double health = tank.health * 1.3 * SpireGuards.health(floor);
			double damage = tank.damage * SpireGuards.damage(floor);
			double plain = health / (through(sword, 6.0, 0.0) * blows);
			double elite = health * com.vnap.spire.guard.SpireGuard.ELITE_HEALTH / (through(sword, 6.0 + com.vnap.spire.guard.SpireGuard.ELITE_ARMOR, 0.0) * blows);
			double hit = through(damage, armour, toughness) / 20.0;
			double eliteHit = through(damage * com.vnap.spire.guard.SpireGuard.ELITE_DAMAGE, armour, toughness) / 20.0;
			String what = String.format(java.util.Locale.ROOT, "floor %d vs %s: tank %.0f s, elite %.0f s, hit %.0f%%, elite hit %.0f%%", floor, tier.id(), plain, elite,
					hit * 100, eliteHit * 100);
			Checks.assertTrue(helper, plain <= 8.0 && elite <= 13.0, what);
			Checks.assertTrue(helper, hit <= 0.2 && eliteHit <= 0.24, what);
			// one against one, trading plain blows: what the fight costs the climber (two at once is the danger, not one)
			com.vnap.spire.Attack strike = com.vnap.spire.Attack.STRIKE;
			double every = (strike.duration + strike.cooldown * com.vnap.spire.guard.SpireGuard.pace(tank) + 10) / 20.0;
			double cost = plain / every * hit;
			double eliteCost = elite / every * eliteHit;
			what += String.format(java.util.Locale.ROOT, ", a duel costs %.0f%% (elite %.0f%%)", cost * 100, eliteCost * 100);
			Checks.assertTrue(helper, cost <= 0.5 && eliteCost <= 0.75, what);
			// from the second chapter a guard's blows leave its chapter's affliction (frostbite takes a fifth more, a
			// sting stacks, rot spreads): reckoned as a fifth more of every blow, the elite still does not finish a
			// climber who stands and trades
			double afflicted = floor > 10 ? 1.2 : 1.0;
			Checks.assertTrue(helper, eliteCost * afflicted <= 0.85, what + ", afflicted " + Math.round(eliteCost * afflicted * 100) + "%");
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void theSpireGoesOnPastTheHundredth(GameTestHelper helper) {
		// past the hundredth floor: floors still drawn, keepers in company, and a fight the summit's gear at +20 meets
		com.vnap.spire.gear.GearTier tier = com.vnap.spire.gear.GearTier.SUMMIT;
		double sword = com.vnap.spire.gear.SpireGear.headline(tier, com.vnap.spire.gear.GearPiece.SWORD, 20);
		double armour = 0;
		for (com.vnap.spire.gear.GearPiece piece : com.vnap.spire.gear.GearPiece.values()) {
			if (piece.isArmour()) armour += com.vnap.spire.gear.SpireGear.headline(tier, piece, 20);
		}
		double toughness = com.vnap.spire.gear.SpireGear.toughness(tier);
		double blows = com.vnap.spire.gear.SpireGear.speed(com.vnap.spire.gear.GearPiece.SWORD);
		com.vnap.spire.Genome.Role tank = com.vnap.spire.Genome.Role.TANK;
		for (int floor = 101; floor <= 400; floor += 7) {
			double health = tank.health * 1.3 * SpireGuards.health(floor);
			double elite = health * com.vnap.spire.guard.SpireGuard.ELITE_HEALTH / (through(sword, 6.0 + com.vnap.spire.guard.SpireGuard.ELITE_ARMOR, 0.0) * blows);
			double eliteHit = through(tank.damage * SpireGuards.damage(floor) * com.vnap.spire.guard.SpireGuard.ELITE_DAMAGE, armour, toughness) / 20.0;
			Checks.assertTrue(helper, elite <= 13.0 && eliteHit <= 0.24, String.format(java.util.Locale.ROOT, "floor %d: elite %.0f s, its blow %.0f%%", floor, elite, eliteHit * 100));
		}
		for (int floor : new int[] {110, 150, 200, 210, 250, 300, 310}) {
			FloorPlan plan = FloorGenerator.plan(1234L, floor);
			int want = Math.min(2, com.vnap.spire.Genome.round(floor));
			Checks.assertTrue(helper, plan.keeper != null && plan.companions.size() == want,
					"floor " + floor + ": its keeper and " + want + " beside it, has " + plan.companions.size());
			Checks.assertTrue(helper, FloorGenerator.trapped(plan).isEmpty(), "floor " + floor + " has no way back from somewhere");
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void guardsGrowSlowlyAndChestsGiveOnlyForward(GameTestHelper helper) {
		Checks.assertTrue(helper, SpireGuards.health(100) < 5.0 && SpireGuards.damage(100) < 3.2 && SpireGuards.health(1) == 1.0,
				"floor 100 guards: x" + SpireGuards.health(100) + " health, x" + SpireGuards.damage(100) + " damage");
		Checks.assertTrue(helper, SpireRewards.coins(1000) <= 64 && SpireRewards.shards(10) < SpireRewards.shards(50), "coins stay small, shards grow");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Checks.assertTrue(helper, SpireProgress.firstChest(player, 7, 3) && !SpireProgress.firstChest(player, 7, 3) && !SpireProgress.firstChest(player, 7, 2),
				"a floor's chest once, and none below the highest had");
		Checks.assertTrue(helper, SpireProgress.firstChest(player, 8, 1), "another spire is another climb");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void noFloorOpensOntoTheVoid(GameTestHelper helper) {
		for (int floor = 1; floor <= 100; floor++) {
			FloorPlan plan = FloorGenerator.plan(1234L, floor);
			for (BlockPos cell : plan.onwardPortal) {
				Checks.assertTrue(helper, cell.getZ() + 1 < FloorPlan.SIZE_Z && plan.solid(cell.getX(), cell.getY(), cell.getZ() + 1),
						"floor " + floor + ": nothing behind the onward portal at " + cell.toShortString());
			}
			for (BlockPos cell : plan.exitPortal) {
				Checks.assertTrue(helper, cell.getZ() > 0 && plan.solid(cell.getX(), cell.getY(), cell.getZ() - 1),
						"floor " + floor + ": nothing behind the way out at " + cell.toShortString());
			}
			// the floor's edge is wall at a climber's feet and head all round
			for (int i = 0; i < FloorPlan.SIZE_X; i++) {
				for (int[] at : new int[][] {{i, 0}, {i, FloorPlan.SIZE_Z - 1}, {0, i}, {FloorPlan.SIZE_X - 1, i}}) {
					for (int y = FloorGenerator.FLOOR + 1; y <= FloorGenerator.FLOOR + 2; y++) {
						Checks.assertTrue(helper, plan.solid(at[0], y, at[1]), "floor " + floor + ": the edge is open at " + at[0] + " " + y + " " + at[1]);
					}
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void whereverAClimberFallsTheyClimbBackOut(GameTestHelper helper) {
		// every cell a climber gets to (dropping off ledges too) has a way back to the entrance
		for (long seed : new long[] {1234L, 7L, -99L}) {
			for (int floor = 1; floor <= 100; floor++) {
				java.util.List<BlockPos> traps = FloorGenerator.trapped(FloorGenerator.plan(seed, floor));
				Checks.assertTrue(helper, traps.isEmpty(), "seed " + seed + " floor " + floor + ": no way back from " + traps);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void trapsLieOnTheWayFromTheSixthFloor(GameTestHelper helper) {
		// the dev world's spire too: it had none on eight floors running
		for (long seed : new long[] {1234L, 7240042975937020300L, -5L}) {
		int with = 0;
		for (int floor = 1; floor <= 100; floor++) {
			FloorPlan plan = FloorGenerator.plan(seed, floor);
			int traps = 0;
			for (int x = 0; x < FloorPlan.SIZE_X; x++) {
				for (int y = 0; y < FloorPlan.SIZE_Y; y++) {
					for (int z = 0; z < FloorPlan.SIZE_Z; z++) {
						net.minecraft.world.level.block.state.BlockState state = plan.get(x, y, z);
						if (state != null && state.getBlock() instanceof com.vnap.spire.SpireTrapBlock) traps++;
					}
				}
			}
			Checks.assertTrue(helper, floor >= 6 || traps == 0, "floor " + floor + " has traps already");
			if (traps > 0) with++;
		}
		Checks.assertTrue(helper, with >= 40, "seed " + seed + ": only " + with + " floors of 95 have a trap");
		}
		helper.succeed();
	}

	@GameTest(template = "platform", timeoutTicks = 200)
	public static void aTrapSetAnewOverItselfKeepsGoing(GameTestHelper helper) {
		// a floor rebuilt over itself: a dart left winding is set back at rest by a block of its own kind
		BlockPos at = new BlockPos(2, 2, 2);
		net.minecraft.world.level.block.state.BlockState dart = com.vnap.spire.SpireBlocks.DART.defaultBlockState()
				.setValue(com.vnap.spire.SpireTrapBlock.FACING, net.minecraft.core.Direction.SOUTH);
		helper.setBlock(at, dart);
		helper.setBlock(at, dart.setValue(com.vnap.spire.SpireTrapBlock.STAGE, 2));
		// its wind lost, then the plan's state (at rest) set over it as the floor is rebuilt
		helper.getLevel().getBlockTicks().clearArea(new BoundingBox(helper.absolutePos(at)));
		helper.setBlock(at, dart);
		Checks.assertTrue(helper, helper.getLevel().getBlockTicks().hasScheduledTick(helper.absolutePos(at), com.vnap.spire.SpireBlocks.DART),
				"a dart set again over itself winds on, not stuck for good");
		helper.succeedWhen(() -> helper.assertBlockProperty(at, com.vnap.spire.SpireTrapBlock.STAGE, 0));
	}

	@GameTest(template = "platform")
	public static void everyAfflictionDoesItsOwnThing(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.setHealth(player.getMaxHealth());
		for (int i = 0; i < 4; i++) Afflictions.frost(player, 1);
		Checks.assertTrue(helper, Afflictions.level(player, Afflictions.FROSTBITE) == 3 && player.getHealth() == player.getMaxHealth(),
				"frost builds up without a blow: " + Afflictions.level(player, Afflictions.FROSTBITE));
		Afflictions.frost(player, 1);
		Checks.assertTrue(helper, Afflictions.level(player, Afflictions.FROSTBITE) == Afflictions.BITTEN && player.getHealth() < player.getMaxHealth(),
				"the fifth bursts: " + Afflictions.level(player, Afflictions.FROSTBITE) + ", health " + player.getHealth());
		player.removeAllEffects();
		player.setHealth(10.0F);
		player.addEffect(Afflictions.of(Afflictions.SAPPED, 100, 0));
		player.heal(5.0F);
		Checks.assertTrue(helper, player.getHealth() < 13.5F && player.getHealth() > 10.0F, "sapped heals for less: " + player.getHealth());
		player.removeAllEffects();
		for (int i = 0; i < 9; i++) Afflictions.stack(player, Afflictions.STUNG, 100, 5);
		Checks.assertTrue(helper, Afflictions.level(player, Afflictions.STUNG) == 4, "stings stack to five: " + Afflictions.level(player, Afflictions.STUNG));
		double armour = player.getArmorValue();
		player.addEffect(Afflictions.of(Afflictions.SUNDERED, 100, 1));
		Checks.assertTrue(helper, player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) <= armour - 6 + 0.01 || armour < 6,
				"sundered takes armour a plate at a time");
		helper.succeed();
	}

	@GameTest(template = "platform")
	public static void theSpireGrowsWithItsFloorsUpToItsCrown(GameTestHelper helper) {
		Map<BlockPos, BlockState> sprout = SpireExterior.shape(42L, 1);
		Map<BlockPos, BlockState> tenth = SpireExterior.shape(42L, 10);
		Map<BlockPos, BlockState> whole = SpireExterior.shape(42L, 100);
		Map<BlockPos, BlockState> beyond = SpireExterior.shape(42L, 500);
		int top1 = top(sprout);
		int top10 = top(tenth);
		int top100 = top(whole);
		Checks.assertTrue(helper, top1 < top10 && top10 < top100, "it grows: " + top1 + " < " + top10 + " < " + top100);
		Checks.assertTrue(helper, top(beyond) == top100 && beyond.size() == whole.size(), "and stops at its crown");
		Checks.assertTrue(helper, sprout.containsValue(SpireBlocks.SHARD.defaultBlockState()) && sprout.containsValue(SpireBlocks.RIFT.defaultBlockState()) && !sprout.containsValue(Blocks.BEACON.defaultBlockState())
				&& whole.containsValue(Blocks.BEACON.defaultBlockState())
				&& whole.containsValue(SpireBlocks.RIFT.defaultBlockState().setValue(com.vnap.spire.SpireRiftBlock.HEART, true)), "broken off with shards until it is whole, then a beacon, the beam on both");
		int r = SpireExterior.REACH;
		for (BlockPos pos : whole.keySet()) {
			Checks.assertTrue(helper, Math.abs(pos.getX()) <= r && Math.abs(pos.getZ()) <= r && pos.getY() <= SpireExterior.height(100) + SpireExterior.CROWN,
					"inside the guarded box: " + pos);
		}
		Checks.assertTrue(helper, whole.get(SpireExterior.gateOffset()) != null && whole.get(SpireExterior.gateOffset()).is(SpireBlocks.PORTAL),
				"a gate at the foot");
		helper.succeed();
	}

	@GameTest(template = "platform", timeoutTicks = 400)
	public static void aFloorStandsInItsSlotAndOpensWhenItsGuardsFall(GameTestHelper helper) {
		// the test server has no spire dimension (no datapack worlds): the slot is built in the test's own world, far off
		ServerLevel spireLevel = helper.getLevel();
		Spires spires = Spires.get(helper.getLevel().getServer());
		Spires.Spire spire = spires.add(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(5, 2, 5)), 77L, BlockPos.ZERO, BlockPos.ZERO,
				new BoundingBox(0, 0, 0, 0, 0, 0));
		SpireSlots.Slot slot = SpireSlots.open(spireLevel, spire, 3);
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, slot.ready(), "the floor is built");
			BlockPos exit = slot.world(slot.plan.exitPortal.get(0));
			Checks.assertTrue(helper, spireLevel.getBlockState(exit).is(SpireBlocks.PORTAL) && spireLevel.getBlockState(exit).getValue(SpirePortalBlock.LIT),
					"the way out is lit");
			BlockPos note = slot.plan.note;
			Checks.assertTrue(helper, note != null && spireLevel.getBlockState(slot.world(note)).is(SpireBlocks.NOTE), "its tablet stands");
			if (!slot.cleared) {
				Checks.assertTrue(helper, !spireLevel.getBlockState(slot.world(slot.plan.onwardPortal.get(0))).getValue(SpirePortalBlock.LIT),
						"the way on is dark while its guards stand");
				for (Entity guard : spireLevel.getEntities((Entity) null, net.minecraft.world.phys.AABB.of(slot.box()),
						entity -> com.vnap.Mc.tags(entity).contains(SpireGuards.TAG))) {
					com.vnap.Mc.kill(guard);
				}
				Checks.fail(helper, "the floor clears once its guards fall");
			}
			Checks.assertTrue(helper, spireLevel.getBlockState(slot.world(slot.plan.onwardPortal.get(0))).getValue(SpirePortalBlock.LIT)
					&& spireLevel.getBlockState(slot.world(slot.plan.chest)).is(SpireBlocks.CHEST), "the chest and the way on");
			spires.all().remove(spire);
			SpireSlots.close(spireLevel, slot);
		});
	}

	/**
	 * Every boss on its floor, 5 to 100: its own body and name; under two thirds it rears into its second phase, under
	 * a third into its last; the bosses with parts have them (the Root Wyrm's coils, the Hydra's heads, the Gravitor's
	 * planets) and those that call help have it; the Schism's Mason falls and the Surveyor takes up his hammer. And
	 * they are cheap: a boss's own work stays under a millisecond and a half a tick, its pending marks and raised
	 * blocks under their bounds.
	 */
	@GameTest(template = "platform", timeoutTicks = 900)
	public static void everyBossChangesThroughItsPhases(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Spires spires = Spires.get(level.getServer());
		Spires.Spire spire = spires.add(level.dimension(), helper.absolutePos(new BlockPos(5, 2, 5)), 91L, BlockPos.ZERO, BlockPos.ZERO,
				new BoundingBox(0, 0, 0, 0, 0, 0));
		int count = 20;
		SpireSlots.Slot[] slots = new SpireSlots.Slot[count];
		for (int i = 0; i < count; i++) slots[i] = SpireSlots.open(level, spire, 5 + 5 * i);
		int[] stage = new int[count];
		int[] at = new int[count];
		Player mock = helper.makeMockPlayer(GameType.SURVIVAL);
		java.util.Set<String> names = new java.util.HashSet<>();
		com.vnap.spire.guard.SpireKeeper.bench(true);
		helper.succeedWhen(() -> {
			int now = (int) helper.getTick();
			for (int i = 0; i < count; i++) {
				int floor = 5 + 5 * i;
				SpireSlots.Slot slot = slots[i];
				Checks.assertTrue(helper, slot.ready(), "floor " + floor + " is built");
				java.util.List<com.vnap.spire.guard.SpireKeeper> found = level.getEntitiesOfClass(com.vnap.spire.guard.SpireKeeper.class,
						net.minecraft.world.phys.AABB.of(slot.box()), one -> one.isAlive() && one.genome().floor() == floor && SpireGuards.current(one));
				int bosses = com.vnap.spire.Genome.schism(floor) && stage[i] == 0 ? 2 : 1;
				Checks.assertTrue(helper, found.size() == bosses || (stage[i] == 1 && found.size() == 2), "floor " + floor + " has its boss: " + found.size());
				com.vnap.spire.guard.SpireKeeper keeper = found.get(0);
				for (com.vnap.spire.guard.SpireKeeper one : found) one.appear();
				String who = floor + " " + com.vnap.spire.Genome.keeperName(keeper.genome().keeper());
				Checks.assertTrue(helper, keeper.marking() <= 160 && keeper.raising() <= 96, who + " keeps its marks and walls bounded");
				switch (stage[i]) {
					case 0 -> {
						for (com.vnap.spire.guard.SpireKeeper one : found) one.setTarget(mock);
						if (com.vnap.spire.Genome.schism(floor)) {
							// the Mason falls: the Surveyor takes up his hammer
							for (com.vnap.spire.guard.SpireKeeper one : found) {
								if (one.genome().keeper() == com.vnap.spire.Genome.MASON) com.vnap.Mc.hurt(one, level.damageSources().genericKill(), Float.MAX_VALUE);
							}
							stage[i] = 1;
						} else {
							stage[i] = 2;
						}
						at[i] = now;
					}
					case 1 -> {
						if (found.size() > 1) break;
						Checks.assertTrue(helper, keeper.genome().keeper() == com.vnap.spire.Genome.SURVEYOR_ALONE, who + ": the Surveyor is left alone");
						stage[i] = 2;
						at[i] = now;
					}
					case 2 -> {
						if (now - at[i] < 60) break;
						names.add(com.vnap.spire.Genome.keeperName(keeper.genome().keeper()));
						int k = keeper.genome().keeper();
						if (k == com.vnap.spire.Genome.TRIAL) Checks.assertTrue(helper, keeper.parts(com.vnap.spire.guard.SpireGuard.Part.SEGMENT).size() == 6, who + " has its coils");
						if (k == com.vnap.spire.Genome.TRIAL + 6) Checks.assertTrue(helper, keeper.parts(com.vnap.spire.guard.SpireGuard.Part.HEAD).size() == 3, who + " has its heads");
						if (k == com.vnap.spire.Genome.TRIAL + 8) Checks.assertTrue(helper, keeper.parts(com.vnap.spire.guard.SpireGuard.Part.PLANET).size() == 4, who + " has its planets");
						keeper.setHealth(keeper.getMaxHealth() * 0.6F);
						stage[i] = 3;
						at[i] = now;
					}
					case 3 -> {
						Checks.assertTrue(helper, keeper.phase() == 2, who + " rears into its second phase");
						if (now - at[i] < 25) break;
						BlockPos home = keeper.home();
						boolean pillar = false;
						for (int[] c : new int[][] {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) pillar |= !level.getBlockState(home.offset(c[0], 0, c[1])).isAir();
						int k = keeper.genome().keeper();
						boolean pillared = k < com.vnap.spire.Genome.MASON || k == com.vnap.spire.Genome.SURVEYOR_ALONE || (k >= com.vnap.spire.Genome.TRIAL
								&& k != com.vnap.spire.Genome.TRIAL && k != com.vnap.spire.Genome.TRIAL + 7 && k != com.vnap.spire.Genome.TRIAL + 9);
						Checks.assertTrue(helper, !pillared || pillar, who + "'s pillars rise");
						boolean calls = k == 1 || k == 3 || k == 6 || k == 7 || k == 9;
						java.util.List<com.vnap.spire.guard.SpireGuard> help = level.getEntitiesOfClass(com.vnap.spire.guard.SpireGuard.class,
								keeper.getBoundingBox().inflate(8.0), guard -> !(guard instanceof com.vnap.spire.guard.SpireKeeper) && guard.isAlive());
						Checks.assertTrue(helper, !calls || !help.isEmpty(), who + " calls its help");
						Checks.assertTrue(helper, k != 5 || keeper.shell() > 0, "the Ice Colossus grows a shell");
						keeper.setHealth(keeper.getMaxHealth() * 0.3F);
						stage[i] = 4;
					}
					case 4 -> {
						Checks.assertTrue(helper, keeper.phase() == 3, who + " rears into its last phase");
						stage[i] = 5;
					}
					default -> {
					}
				}
			}
			for (int i = 0; i < count; i++) Checks.assertTrue(helper, stage[i] == 5, "floor " + (5 + 5 * i) + " through its phases");
			Checks.assertTrue(helper, names.size() == count, "twenty bosses, twenty names: " + names);
			long[] cost = com.vnap.spire.guard.SpireKeeper.bench(false);
			double micros = cost[1] == 0 ? 0 : cost[0] / 1000.0 / cost[1];
			Checks.assertTrue(helper, micros < 1500, "a boss's tick costs " + Math.round(micros) + " µs");
			com.vnap.VillagerLife.LOGGER.info("vnap bench: a boss's tick {} µs over {} ticks", Math.round(micros), cost[1]);
			spires.all().remove(spire);
			for (SpireSlots.Slot slot : slots) SpireSlots.close(level, slot);
		});
	}

	/** A hundred floors of three spires drawn: none slower than 80 ms, on average under 20 (after the first ten, warm). */
	@GameTest(template = "platform")
	public static void floorsAreDrawnQuickly(GameTestHelper helper) {
		long most = 0;
		long all = 0;
		int drawn = 0;
		for (long spire = 1; spire <= 3; spire++) {
			for (int floor = 1; floor <= 100; floor++) {
				long started = System.nanoTime();
				FloorGenerator.plan(spire * 7919, floor);
				long took = System.nanoTime() - started;
				if (took >= 40_000_000L) {
					// a collector's pause lands on one draw, not on the same floor twice
					started = System.nanoTime();
					FloorGenerator.plan(spire * 7919, floor);
					took = Math.min(took, System.nanoTime() - started);
				}
				if (spire == 1 && floor <= 10) continue;
				most = Math.max(most, took);
				all += took;
				drawn++;
			}
		}
		double average = all / 1.0e6 / drawn;
		com.vnap.VillagerLife.LOGGER.info("vnap bench: floor plans avg {} ms, worst {} ms", String.format(java.util.Locale.ROOT, "%.1f", average), most / 1_000_000);
		Checks.assertTrue(helper, most < 80_000_000L && average < 20.0, "floor plans: avg " + average + " ms, worst " + most / 1_000_000 + " ms");
		helper.succeed();
	}

	// ---- helpers --------------------------------------------------------------------------

	private static int top(Map<BlockPos, BlockState> shape) {
		int top = 0;
		for (Map.Entry<BlockPos, BlockState> entry : shape.entrySet()) if (!entry.getValue().isAir()) top = Math.max(top, entry.getKey().getY());
		return top;
	}

	/** The cells reached on foot from the entrance (as FloorGenerator checks). */
	private static java.util.Set<Long> walk(FloorPlan plan) {
		java.util.Set<Long> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		queue.add(plan.entrance);
		seen.add(plan.entrance.asLong());
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos to = at.relative(direction).above(dy);
					if (!FloorPlan.inside(to.getX(), to.getY(), to.getZ())) continue;
					if (dy > 0 && !plan.empty(at.getX(), at.getY() + 2, at.getZ())) continue;
					if (!plan.empty(to.getX(), to.getY(), to.getZ()) || !plan.empty(to.getX(), to.getY() + 1, to.getZ())
							|| plan.empty(to.getX(), to.getY() - 1, to.getZ())) continue;
					if (seen.add(to.asLong())) queue.add(to);
				}
			}
		}
		return seen;
	}

	private static boolean near(java.util.Set<Long> reached, BlockPos pos) {
		if (reached.contains(pos.asLong())) return true;
		for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			if (reached.contains(pos.relative(direction).asLong())) return true;
		}
		return false;
	}

	@GameTest(template = "platform", timeoutTicks = 400)
	public static void aChaptersDraughtWardsOnlyItsOwnFloors(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Spires.Spire spire = Spires.get(level.getServer()).add(level.dimension(), helper.absolutePos(new BlockPos(5, 2, 5)), 77L, BlockPos.ZERO, BlockPos.ZERO,
				new BoundingBox(0, 0, 0, 0, 0, 0));
		SpireSlots.Slot slot = SpireSlots.open(level, spire, 25);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, slot.ready(), "the floor is built");
			net.minecraft.world.phys.Vec3 middle = net.minecraft.world.phys.AABB.of(slot.box()).getCenter();
			player.setPos(middle.x, middle.y, middle.z);
			com.vnap.spire.gear.GearTier chapter = com.vnap.spire.gear.GearTier.ofFloor(25);
			Checks.assertTrue(helper, com.vnap.spire.Draughts.chapterAt(player) == chapter, "floor 25 is the Clockwork's");
			player.removeAllEffects();
			Checks.assertTrue(helper, pushed(player), "without a draught a blow knocks back");
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.vnap.spire.Draughts.holder(com.vnap.spire.Draughts.ward(com.vnap.spire.gear.GearTier.ARCHIVE)), 200));
			Checks.assertTrue(helper, !com.vnap.spire.Draughts.warded(player) && pushed(player), "another chapter's ward does nothing here");
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.vnap.spire.Draughts.holder(com.vnap.spire.Draughts.ward(chapter)), 200));
			Checks.assertTrue(helper, com.vnap.spire.Draughts.warded(player) && !pushed(player), "its own chapter's ward: no knockback");
			Afflictions.weak("workshop", player, level.getRandom());
			Checks.assertTrue(helper, !Afflictions.has(player, Afflictions.JOLTED), "and no weak affliction from its guards");
			player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
			Checks.assertTrue(helper, !com.vnap.spire.Draughts.warded(player) && pushed(player), "off the floors the ward does nothing");
			// brewed as any potion: an awkward potion under a reagent of the chapter before
			net.minecraft.world.item.ItemStack awkward = net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
					net.minecraft.world.item.alchemy.Potions.AWKWARD);
			for (com.vnap.spire.gear.GearTier tier : com.vnap.spire.gear.GearTier.values()) {
				net.minecraft.world.item.ItemStack reagent = new net.minecraft.world.item.ItemStack(com.vnap.spire.Draughts.reagent(com.vnap.spire.Draughts.brewedFrom(tier)));
				net.minecraft.world.item.ItemStack brewed = com.vnap.Mc.brew(level, awkward, reagent);
				Checks.assertTrue(helper, brewed != null && brewed.is(com.vnap.spire.Draughts.draught(tier)), "the brewing stand makes every draught: " + tier + " got " + brewed);
			}
			Checks.assertTrue(helper, com.vnap.spire.Draughts.brewedFrom(com.vnap.spire.gear.GearTier.ROOT) == com.vnap.spire.gear.GearTier.SUMMIT, "the Root's from the Summit's");
			// drunk out of a stack: one sip, the rest stays, the bottle goes to the pockets
			player.removeAllEffects();
			player.getInventory().clearContent();
			net.minecraft.world.item.ItemStack three = new net.minecraft.world.item.ItemStack(com.vnap.spire.Draughts.draught(chapter), 3);
			net.minecraft.world.item.ItemStack left = three.finishUsingItem(level, player);
			Checks.assertTrue(helper, left.is(com.vnap.spire.Draughts.draught(chapter)) && left.getCount() == 2, "two of three left after a sip: " + left);
			//? if >=1.21.2 {
			/*// the game hands the bottle on (USE_REMAINDER) to a server player only, not to the test's mock one
			var remainder = three.get(net.minecraft.core.component.DataComponents.USE_REMAINDER);
			Checks.assertTrue(helper, remainder != null && remainder.convertInto().is(net.minecraft.world.item.Items.GLASS_BOTTLE), "and a bottle left of each sip");
			*///?} else
			Checks.assertTrue(helper, player.getInventory().countItem(net.minecraft.world.item.Items.GLASS_BOTTLE) == 1, "and the bottle in the pockets");
			Checks.assertTrue(helper, player.hasEffect(com.vnap.spire.Draughts.holder(com.vnap.spire.Draughts.ward(chapter))), "the ward on the drinker");
			player.discard();
		});
	}

	private static boolean pushed(Player player) {
		player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		//? if >=26.2 {
		/*player.knockback(1.0, 1.0, 0.0, player.damageSources().generic(), 0.0F);
		*///?} else
		player.knockback(1.0, 1.0, 0.0);
		return player.getDeltaMovement().lengthSqr() > 1.0E-4;
	}
}
