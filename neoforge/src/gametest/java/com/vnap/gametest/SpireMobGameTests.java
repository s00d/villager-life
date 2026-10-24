package com.vnap.gametest;

import com.vnap.Mc;
import com.vnap.spire.Attack;
import com.vnap.spire.Genome;
import com.vnap.spire.SpireGuards;
import com.vnap.spire.guard.SpireFlyer;
import com.vnap.spire.guard.SpireGuard;
import com.vnap.spire.guard.SpireKeeper;
import com.vnap.spire.guard.SpireSwimmer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;

/** The spire's guards (DESIGN §5): genomes, the rules they keep, how they move, the keeper's fight, what they leave. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class SpireMobGameTests {
	private SpireMobGameTests() {
	}

	@GameTest(template = "platform")
	public static void aGenomeIsTheSameGuardEveryTimeAndGuardsDiffer(GameTestHelper helper) {
		Set<Genome.Body> bodies = EnumSet.noneOf(Genome.Body.class);
		Set<Genome.Role> roles = EnumSet.noneOf(Genome.Role.class);
		// each theme its own folk (Genome.Bestiary): the hive's mostly on the wing, the roots' mostly on foot
		java.util.Map<String, Integer> flying = new java.util.HashMap<>();
		for (String theme : Genome.THEMES) {
			for (int i = 0; i < 200; i++) {
				if (Genome.decode(i * 0x2545F4914F6CDD1DL, 35, theme, false, false).movement() == Genome.Movement.FLY) flying.merge(theme, 1, Integer::sum);
			}
		}
		Checks.assertTrue(helper, flying.getOrDefault("hive", 0) > 2 * flying.getOrDefault("roots", 0), "the hive flies, the roots walk: " + flying);
		for (int i = 0; i < 300; i++) {
			long seed = i * 0x9E3779B97F4A7C15L;
			Genome one = Genome.decode(seed, 35, Genome.THEMES.get(i % Genome.THEMES.size()), false, false);
			Checks.assertTrue(helper, one.equals(Genome.decode(seed, 35, Genome.THEMES.get(i % Genome.THEMES.size()), false, false)),
					"the same genome, the same guard: " + seed);
			Checks.assertTrue(helper, Genome.decode(seed, 40, "workshop", false, true).equals(Genome.decode(seed, 40, "workshop", false, true)),
					"the same keeper: " + seed);
			Checks.assertTrue(helper, Genome.Look.of(one.look()).body() == one.body(), "the client sees its body: " + one);
			bodies.add(one.body());
			roles.add(one.role());
		}
		Checks.assertTrue(helper, bodies.size() >= 5 && roles.size() >= 6, "floors have all sorts: " + bodies + " " + roles);
		helper.succeed();
	}

	@GameTest(template = "platform", timeoutTicks = 200)
	public static void noGuardBreaksTheRulesOrItsFloorsBudget(GameTestHelper helper) {
		Random random = new Random(5);
		for (int i = 0; i < 10_000; i++) {
			int floor = 1 + random.nextInt(140);
			boolean water = random.nextInt(8) == 0;
			boolean keeper = !water && random.nextInt(12) == 0;
			Genome genome = Genome.decode(random.nextLong(), keeper ? floor / 10 * 10 + 10 : floor, Genome.THEMES.get(random.nextInt(10)), water, keeper);
			String where = genome.toString();
			Checks.assertTrue(helper, genome.problem() == null, "a guard that may not be: " + genome.problem() + " " + where);
			Checks.assertTrue(helper, genome.movement() != Genome.Movement.FLY || !genome.attacks().contains(Attack.LEAP), "a flyer leaps: " + where);
			Checks.assertTrue(helper, genome.role() != Genome.Role.SHOOTER || genome.defence() != Genome.Defence.WARD, "a warded shooter: " + where);
			Checks.assertTrue(helper, !genome.attacks().contains(Attack.BLINK) || genome.role() == Genome.Role.FIGHTER, "only a fighter blinks: " + where);
			Checks.assertTrue(helper, genome.role() != Genome.Role.HELPER || genome.attacks().stream().filter(Attack::damaging).count() <= 1,
					"a helper hits with one thing at most: " + where);
			Checks.assertTrue(helper, water == (genome.movement() == Genome.Movement.SWIM), "swimmers only in water: " + where);
			Checks.assertTrue(helper, genome.isKeeper() || genome.threat() <= Genome.budget(genome.floor()), "over its floor's budget: " + where);
			Checks.assertTrue(helper, genome.floor() > 10 || genome.isKeeper()
					|| genome.attacks().stream().allMatch(attack -> attack.chapter == 0), "chapter I knows only the first attacks: " + where);
			Checks.assertTrue(helper, genome.health() > 0 && genome.size() > 0.4F && genome.size() <= 2.8F, "numbers: " + where);
		}
		for (Attack attack : Attack.values()) {
			Checks.assertTrue(helper, attack.strong() ? attack.impact >= 15 && attack.impact <= 25 : attack.impact <= 12,
					"a strong attack shows itself 15 to 25 ticks: " + attack);
			Checks.assertTrue(helper, attack.impact < attack.duration && attack.cooldown > 0, "lands within itself: " + attack);
		}
		Checks.assertTrue(helper, Attack.BLINK.cooldown >= 120, "a blink at most every six seconds");
		helper.succeed();
	}

	@GameTest(template = "home_platform", timeoutTicks = 200)
	public static void aFlyerTakesToTheAirAndKeepsToItsRoom(GameTestHelper helper) {
		Genome genome = find(5, false, Genome.Movement.FLY);
		BlockPos at = helper.absolutePos(new BlockPos(12, 2, 12));
		AABB room = new AABB(corner(helper, 4, 1, 4), corner(helper, 20, 14, 20));
		SpireGuard guard = SpireGuards.make(helper.getLevel(), genome, "roots", at, room, room.inflate(16));
		if (!(guard instanceof SpireFlyer flyer)) {
			Checks.fail(helper, "a flyer's genome makes a flyer: " + guard);
			return;
		}
		helper.runAfterDelay(100, () -> {
			Checks.assertTrue(helper, flyer.getY() > at.getY() + 1.0, "it flies: " + flyer.getY() + " over " + at.getY());
			Checks.assertTrue(helper, room.contains(flyer.position()), "and keeps to its room: " + flyer.position());
			flyer.discard();
			helper.succeed();
		});
	}

	@GameTest(template = "home_platform", timeoutTicks = 200)
	public static void aSwimmerStaysInItsPool(GameTestHelper helper) {
		for (int x = 8; x <= 16; x++) {
			for (int z = 8; z <= 16; z++) {
				helper.setBlock(x, 1, z, Blocks.STONE);
				for (int y = 2; y <= 4; y++) {
					boolean rim = x == 8 || z == 8 || x == 16 || z == 16;
					helper.setBlock(x, y, z, rim ? Blocks.STONE : Blocks.WATER);
				}
			}
		}
		Genome genome = find(45, true, Genome.Movement.SWIM);
		BlockPos at = helper.absolutePos(new BlockPos(12, 3, 12));
		AABB room = new AABB(corner(helper, 2, 0, 2), corner(helper, 22, 10, 22));
		SpireGuard guard = SpireGuards.make(helper.getLevel(), genome, "galleries", at, room, room.inflate(8));
		if (!(guard instanceof SpireSwimmer swimmer)) {
			Checks.fail(helper, "a pool's genome makes a swimmer: " + guard);
			return;
		}
		for (int tick = 20; tick <= 140; tick += 10) {
			int when = tick;
			helper.runAfterDelay(tick, () -> Checks.assertTrue(helper, swimmer.isInWater() && swimmer.isAlive(),
					"it stays in the water: " + swimmer.position() + " at tick " + when));
		}
		helper.runAfterDelay(145, () -> {
			swimmer.discard();
			helper.succeed();
		});
	}

	@GameTest(template = "home_platform", timeoutTicks = 200)
	public static void aFelledKeeperGoesItsWayAndIsGone(GameTestHelper helper) {
		Genome genome = Genome.decode(99L, 40, "gardens", false, true);
		BlockPos at = helper.absolutePos(new BlockPos(12, 2, 12));
		AABB room = new AABB(corner(helper, 2, 1, 2), corner(helper, 22, 14, 22));
		if (!(SpireGuards.make(helper.getLevel(), genome, "gardens", at, room, room.inflate(8)) instanceof SpireKeeper keeper)) {
			Checks.fail(helper, "a keeper's genome makes a keeper");
			return;
		}
		keeper.appear();
		helper.runAfterDelay(5, () -> com.vnap.Mc.hurt(keeper, helper.getLevel().damageSources().genericKill(), 10000.0F));
		helper.runAfterDelay(30, () -> Checks.assertTrue(helper, keeper.isAlive() && keeper.isInvulnerable(), "it is still going its way (its end takes a while)"));
		helper.runAfterDelay(120, () -> {
			Checks.assertTrue(helper, !keeper.isAlive() || keeper.isRemoved(), "it is gone at the end of its way: alive " + keeper.isAlive() + ", health " + keeper.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = "home_platform", timeoutTicks = 200)
	public static void aKeeperHasItsBarAndChangesPhaseAsItWeakens(GameTestHelper helper) {
		Genome genome = Genome.decode(99L, 10, "roots", false, true);
		Checks.assertTrue(helper, genome.isKeeper() && genome.body() == Genome.Body.GATEKEEPER && genome.keeper() == 0, "chapter I's keeper: " + genome);
		BlockPos at = helper.absolutePos(new BlockPos(12, 2, 12));
		AABB room = new AABB(corner(helper, 2, 1, 2), corner(helper, 22, 14, 22));
		SpireGuard guard = SpireGuards.make(helper.getLevel(), genome, "roots", at, room, room.inflate(8));
		if (!(guard instanceof SpireKeeper keeper)) {
			Checks.fail(helper, "a keeper's genome makes a keeper: " + guard);
			return;
		}
		keeper.appear();
		Checks.assertTrue(helper, keeper.bossBar().getName().getContents() instanceof TranslatableContents name
				&& name.getKey().equals("spire.villager_life.keeper.gatekeeper"), "its bar carries its name: " + keeper.bossBar().getName());
		Checks.assertTrue(helper, keeper.phase() == 1, "it starts in its first phase");
		helper.runAfterDelay(5, () -> keeper.setHealth(keeper.getMaxHealth() * 0.6F));
		helper.runAfterDelay(10, () -> {
			Checks.assertTrue(helper, keeper.phase() == 2, "under two thirds, the second phase: " + keeper.phase());
			keeper.setHealth(keeper.getMaxHealth() * 0.3F);
		});
		helper.runAfterDelay(15, () -> {
			Checks.assertTrue(helper, keeper.phase() == 3, "under a third, the last: " + keeper.phase());
			Checks.assertTrue(helper, Math.abs(keeper.bossBar().getProgress() - 0.3F) < 0.02F, "the bar follows: " + keeper.bossBar().getProgress());
		});
		helper.runAfterDelay(40, () -> {
			boolean pillar = false;
			for (BlockPos corner : new BlockPos[] {at.offset(4, 0, 4), at.offset(-4, 0, 4), at.offset(4, 0, -4), at.offset(-4, 0, -4)}) {
				if (!helper.getLevel().getBlockState(corner).isAir()) pillar = true;
			}
			Checks.assertTrue(helper, pillar, "its room changed: pillars rose round it");
			keeper.discard();
			helper.succeed();
		});
	}

	@GameTest(template = "home_platform", timeoutTicks = 100)
	public static void aBeatenGuardLeavesNothingButLight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Genome genome = find(12, false, Genome.Movement.WALK);
		BlockPos at = helper.absolutePos(new BlockPos(12, 2, 12));
		AABB room = new AABB(corner(helper, 2, 1, 2), corner(helper, 22, 8, 22));
		SpireGuard guard = SpireGuards.make(level, genome, "archive", at, room, room.inflate(8));
		if (guard == null) {
			Checks.fail(helper, "no guard made");
			return;
		}
		Checks.assertTrue(helper, guard.isPersistenceRequired() && guard.fireImmune() && com.vnap.Mc.tags(guard).contains(SpireGuards.TAG),
				"a guard of the floor stays, never burns and is tagged");
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Mc.hurt(guard, level.damageSources().playerAttack(player), 100_000.0F);
		helper.runAfterDelay(20, () -> {
			AABB around = room.inflate(4);
			Checks.assertTrue(helper, guard.isRemoved(), "it is gone in a burst");
			Checks.assertTrue(helper, level.getEntitiesOfClass(ItemEntity.class, around).isEmpty()
					&& level.getEntitiesOfClass(ExperienceOrb.class, around).isEmpty(), "and drops nothing");
			player.discard();
			helper.succeed();
		});
	}

	@GameTest(template = "platform", timeoutTicks = 400)
	public static void aTenthFloorHasItsKeeperAndEveryGuardIsTheSpires(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		com.vnap.spire.Spires spires = com.vnap.spire.Spires.get(level.getServer());
		com.vnap.spire.Spires.Spire spire = spires.add(level.dimension(), helper.absolutePos(new BlockPos(5, 2, 5)), 31L, BlockPos.ZERO, BlockPos.ZERO,
				new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 0, 0, 0, 0, 0));
		com.vnap.spire.SpireSlots.Slot slot = com.vnap.spire.SpireSlots.open(level, spire, 10);
		helper.succeedWhen(() -> {
			Checks.assertTrue(helper, slot.ready(), "the floor is built");
			AABB box = AABB.of(slot.box());
			java.util.List<SpireGuard> guards = level.getEntitiesOfClass(SpireGuard.class, box.inflate(2));
			int expected = slot.plan.guards.size() + slot.plan.swimmers.size();
			Checks.assertTrue(helper, guards.size() == expected, "every spawn stands: " + guards.size() + " of " + expected);
			Checks.assertTrue(helper, guards.stream().filter(SpireKeeper.class::isInstance).count() == 1, "one keeper on a tenth floor");
			for (SpireGuard guard : guards) {
				Checks.assertTrue(helper, com.vnap.Mc.tags(guard).contains(SpireGuards.TAG) && box.contains(guard.position()), "a guard of the floor, in it: " + guard);
			}
			spires.all().remove(spire);
			com.vnap.spire.SpireSlots.close(level, slot);
		});
	}

	// ---- helpers --------------------------------------------------------------------------

	private static Genome find(int floor, boolean water, Genome.Movement movement) {
		for (long seed = 1; seed < 10_000; seed++) {
			Genome genome = Genome.decode(seed, floor, "roots", water, false);
			if (genome.movement() == movement) return genome;
		}
		throw new IllegalStateException("no " + movement + " genome on floor " + floor);
	}

	private static net.minecraft.world.phys.Vec3 corner(GameTestHelper helper, int x, int y, int z) {
		return net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(x, y, z)));
	}
}
