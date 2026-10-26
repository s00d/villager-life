package com.vnap.spire.outer;

import com.vnap.spire.FloorGenerator;
import com.vnap.spire.Genome;
import com.vnap.spire.SpireBlocks;
import com.vnap.spire.SpireGuards;
import com.vnap.spire.guard.SpireGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;

/**
 * The Colosseum of the Builders: a round arena forty across, four columns to hide behind from shooters, four arches
 * the guards come in by. Waves come one after another (Risk of Rain's Simulacrum the model): each wave a budget the
 * director spends on the guards of the wave's chapter — every fifth with a keeper, every tenth with two — and from the
 * sixth wave on, every third adds a hardship that stays (Hades's Heat): heavier guards, faster, fiercer, a ring of
 * fire round the edge, the dark, no healing, the columns down. What comes is drawn against the weapon it is met with:
 * a sword meets more shooters and flyers, a bow more who close in fast.
 */
public final class Arena {
	static final int RADIUS = 20;
	private static final int WALL = 6;
	/** Ticks between a wave's fall and the next one's coming. */
	private static final int BREAK = 200;
	private static final Direction[] FAR_ARCHES = {Direction.SOUTH, Direction.WEST, Direction.EAST};
	private static final int CAP = 22;
	/** A keeper's health in a wave against on its floor. */
	public static final double KEEPER_SHARE = 0.85;
	/** The hardships in the order they come. */
	static final List<String> HARDSHIPS = List.of("heavy", "swift", "fire", "fury", "dark", "barren", "fallen");

	private Arena() {
	}

	static BlockPos start(Outer.Run run) {
		return run.origin.offset(0, 1, -RADIUS + 4);
	}

	static void build(Outer.Run run) {
		ServerLevel level = run.level;
		BlockPos o = run.origin;
		for (int x = -STANDS; x <= STANDS; x++) {
			for (int z = -STANDS; z <= STANDS; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > STANDS + 0.5) continue;
				for (int y = -1; y <= WALL + 8; y++) level.setBlock(o.offset(x, y, z), at(d, y, x, z), 2);
			}
		}
		// the arches the guards come by: dark, tall, at the four quarters, red banners either side
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos arch = o.relative(side, RADIUS + 1);
			Direction across = side.getClockWise();
			for (int a = -1; a <= 1; a++) for (int y = 1; y <= 4; y++) level.setBlock(arch.relative(across, a).above(y), Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
			for (int a = -2; a <= 2; a += 4) for (int y = 1; y <= 6; y++) level.setBlock(arch.relative(across, a).above(y), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 2);
			for (int a = -1; a <= 1; a++) level.setBlock(arch.relative(across, a).above(5), Blocks.STONE_BRICK_SLAB.defaultBlockState()
					.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP), 2);
			for (int a = -4; a <= 4; a += 8) level.setBlock(arch.relative(side.getOpposite()).relative(across, a).above(4), Blocks.RED_WALL_BANNER.defaultBlockState()
					.setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, side.getOpposite()), 2);
		}
		columns(run, true);
		// fire round the ring: braziers by the wall, between the arches
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2 * i / 12 + Math.PI / 12;
			level.setBlock(o.offset((int) Math.round(Math.cos(angle) * (RADIUS - 1)), 1, (int) Math.round(Math.sin(angle) * (RADIUS - 1))),
					SpireBlocks.BRAZIER.defaultBlockState(), 2);
		}
		// the way out, by where one comes in
		BlockPos exit = o.offset(0, 1, -RADIUS);
		for (int x = -1; x <= 1; x++) for (int y = 0; y <= 4; y++) level.setBlock(exit.offset(x, y, 0),
				(Math.abs(x) == 1 || y == 0 || y == 4) ? com.vnap.mega.MegaBlocks.PART_COLOSSEUM.defaultBlockState() : OuterGateBlock.exit(Direction.Axis.X), 2);
		// the first wave waits as long as others may still come in through the gate (Outer.GATHER)
		run.timer = Outer.GATHER;
	}

	/** The stands behind the wall rise from its top to this far out. */
	private static final int STANDS = RADIUS + 7;

	/**
	 * A block of the Colosseum by its distance from the middle and height: a sand floor (an arena is sand) with rings
	 * of cut stone and the Builders' runes round its middle, a stone wall with a parapet, and stands going up behind it.
	 */
	private static BlockState at(double d, int y, int x, int z) {
		if (y == -1) return Blocks.STONE_BRICKS.defaultBlockState();
		if (d <= RADIUS + 0.5) {
			if (y > 0) return Blocks.AIR.defaultBlockState();
			if (d < 2.5) return Blocks.CHISELED_SANDSTONE.defaultBlockState();
			if (Math.round(d) == 6 && d > 1) return SpireBlocks.RUNE.defaultBlockState();
			if (Math.round(d) % 5 == 0) return Blocks.CUT_SANDSTONE.defaultBlockState();
			return ((x * 3 + z * 7) & 7) == 0 ? Blocks.SAND.defaultBlockState() : Blocks.SMOOTH_SANDSTONE.defaultBlockState();
		}
		if (d <= RADIUS + 1.5) {
			if (y > WALL + 1) return Blocks.AIR.defaultBlockState();
			if (y == WALL + 1) return ((x + z) & 1) == 0 ? Blocks.STONE_BRICK_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState();
			if (y == WALL) return Blocks.POLISHED_ANDESITE.defaultBlockState();
			return y == 3 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
		}
		// the stands: a step a block up for every block out, seats of stairs looking in
		int step = WALL - 1 + (int) Math.floor(d - RADIUS - 1.5);
		if (y < step) return Blocks.STONE_BRICKS.defaultBlockState();
		if (y == step) return Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,
				Direction.getNearest(-x, 0, -z));
		return Blocks.AIR.defaultBlockState();
	}

	/** The four columns at the quarter points between the arches (put up, or knocked down by the last hardship). */
	private static void columns(Outer.Run run, boolean up) {
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 4 + i * Math.PI / 2;
			BlockPos base = run.origin.offset((int) Math.round(Math.cos(angle) * 10), 0, (int) Math.round(Math.sin(angle) * 10));
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					for (int y = 1; y <= 5; y++) {
						BlockState stone = !up ? Blocks.AIR.defaultBlockState()
								: y == 5 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
								: (x == 0 || z == 0) ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
						if (!up && y == 1) stone = Blocks.COBBLESTONE.defaultBlockState();
						run.level.setBlock(base.offset(x, y, z), stone, 2);
					}
				}
			}
		}
	}

	static void tick(Outer.Run run, List<ServerPlayer> on) {
		ServerLevel level = run.level;
		int standing = run.livingGuards();
		boolean fighting = run.fighting;
		if (fighting && standing == 0) {
			run.fighting = false;
			run.timer = BREAK;
			int strength = run.loadout.floor() + run.stage;
			if (run.stage % 5 == 0) Outer.reward(run, on, strength, run.stage >= 20 ? 2 : run.stage >= 10 ? 1 : 0);
			else for (ServerPlayer player : on) Outer.record(player, run);
			for (ServerPlayer player : on) com.vnap.Mc.say(player, Component.translatable("outer.villager_life.arena.cleared", run.stage), true);
			level.playSound(null, run.origin, com.vnap.sound.SpireSounds.PUZZLE_SOLVED, SoundSource.HOSTILE, 1.4F, 1.0F);
		}
		if (!fighting && --run.timer <= 0) wave(run, on);
		if (level.getGameTime() % 20 == 0) Outer.tell(run, on, standing, Math.max(standing, run.total), !fighting, false);
		// the dark and the barren hold on the players while they last
		if (level.getGameTime() % 40 == 0) {
			for (ServerPlayer player : on) {
				if (run.mods.contains("dark")) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, true, false));
				if (run.mods.contains("barren")) player.addEffect(com.vnap.spire.Afflictions.of(com.vnap.spire.Afflictions.SAPPED, 80, 0));
			}
		}
	}

	/** The next wave in: its budget spent on the guards of its chapter, a keeper on every fifth. */
	static void wave(Outer.Run run, List<ServerPlayer> on) {
		ServerLevel level = run.level;
		run.stage++;
		int w = run.stage;
		if (w >= 6 && (w - 6) % 3 == 0 && run.mods.size() < HARDSHIPS.size()) hardship(run, on, HARDSHIPS.get(run.mods.size()));
		// a floor further every third wave: the crowd grows as well (crowd), and a floor a wave on top of it ended
		// every run by the eighth
		int floor = run.loadout.floor() + w / 3;
		String theme = Genome.THEMES.get(Math.floorMod(w - 1, Genome.THEMES.size()));
		Random random = new Random(FloorGenerator.mix(run.seed, w));
		// more players, a bigger wave (half again for each beyond the first; the keepers grow on their own: SpireKeeper.group)
		double credits = (50 + 12 * Math.pow(w, 1.15)) * (1.0 + 0.5 * (run.party() - 1));
		AABB room = new AABB(run.origin).inflate(RADIUS - 1, 0, RADIUS - 1).expandTowards(0, 10, 0);
		AABB box = room.inflate(6);
		int count = 0;
		if (w % 5 == 0) {
			int keepers = w % 10 == 0 ? 2 : 1;
			for (int i = 0; i < keepers; i++) {
				int which = Math.floorMod(w / 5 - 1 + i * 3, Genome.KEEPERS.size());
				SpireGuard keeper = SpireGuards.make(level, Genome.companion(random.nextLong(), floor, theme, which), theme,
						run.origin.offset(i == 0 ? -3 : 3, 1, 6), room, box);
				if (keeper != null) {
					dress(run, keeper);
					// a keeper here has a crowd round it besides: a little less of it than on its own floor
					scale(keeper, Attributes.MAX_HEALTH, KEEPER_SHARE);
					keeper.setHealth(keeper.getMaxHealth());
					if (keeper instanceof com.vnap.spire.guard.SpireKeeper k) k.appear();
					run.adopt(keeper);
					count++;
				}
				credits -= 40;
			}
		}
		int arch = 0;
		int counters = 0;
		while (credits > 0 && count < Math.min(CAP + 6 * (run.party() - 1), 4 + w / 3 + 2 * (run.party() - 1))) {
			// the guards that answer the player's weapon (shooters and flyers for a blade) grow as a share of the wave: a
			// quarter at first, two thirds by the tenth; a whole first wave of them killed a geared climber with a sword
			// in the open sand before it reached one
			boolean answers = counters < Math.ceil((count + 1) * Math.min(0.65, 0.25 + 0.045 * (w - 1)));
			Genome best = null;
			double score = -100;
			// four to choose from; past the share, more until one that does not answer (a theme of flyers drew only them)
			for (int i = 0; i < 4 || (!answers && i < 16 && best != null && fits(best, run.loadout.style()) > 0); i++) {
				Genome g = Genome.decode(random.nextLong(), floor, theme, false, false);
				boolean counter = fits(g, run.loadout.style()) > 0;
				double s = (counter ? (answers ? 2.0 : -5.0) : 0.0) + random.nextDouble();
				if (s > score) {
					score = s;
					best = g;
				}
			}
			if (best == null) break;
			if (fits(best, run.loadout.style()) > 0) counters++;
			// the three far arches: the north one is where the players come in, and a guard set down two steps off
			// whoever just came through cut them down before they could turn (dead within a second, wave after wave)
			Direction side = FAR_ARCHES[arch++ % FAR_ARCHES.length];
			BlockPos at = run.origin.relative(side, RADIUS - 2).relative(side.getClockWise(), random.nextInt(3) - 1).above(best.movement() == Genome.Movement.FLY ? 3 : 1);
			SpireGuard guard = SpireGuards.make(level, best, theme, at, room, box);
			if (guard == null) break;
			if (w >= 10 && random.nextInt(10) < Math.min(5, 1 + (w - 10) / 5)) guard.elite(true);
			dress(run, guard);
			run.adopt(guard);
			credits -= Math.max(6, best.threat());
			count++;
		}
		run.fighting = true;
		run.total = count;
		level.playSound(null, run.origin, com.vnap.sound.SpireSounds.GONG, SoundSource.HOSTILE, 2.0F, w % 5 == 0 ? 0.7F : 1.0F);
		for (ServerPlayer player : on) com.vnap.Mc.say(player, Component.translatable(w % 5 == 0 ? "outer.villager_life.arena.boss_wave"
				: "outer.villager_life.arena.wave", w), true);
		Outer.tell(run, on, count, count, false, true);
		com.vnap.VillagerLife.trace("vnap outer: arena run " + run.index + " wave " + w + " floor " + floor + " " + count + " guards, hardships " + run.mods);
	}

	/** Against what the player brings: a sword is met by shooters and flyers, a bow by who closes fast. */
	static double fits(Genome g, Loadout.Style style) {
		return switch (style) {
			case MELEE -> g.role() == Genome.Role.SHOOTER || g.movement() == Genome.Movement.FLY ? 2.0 : 0.0;
			case RANGED -> g.role() == Genome.Role.FIGHTER || g.role() == Genome.Role.LEAPER || g.role() == Genome.Role.SWARM ? 2.0 : 0.0;
			case OTHER -> 0.0;
		};
	}

	/**
	 * A wave's guards against one of the floors' (health, damage): a floor's guards are met one or two at a time, a
	 * wave's three or four at once, so the first waves are a crowd of lighter ones that grow, past the floors' own
	 * by the fifteenth wave and on without end (OuterGameTests: won with health to spare at first, lost in the end).
	 */
	public static double[] crowd(int wave) {
		return new double[] {0.25 + 0.025 * wave, 0.35 + 0.02 * wave};
	}

	/** The hardships so far on a guard just come in. */
	private static void dress(Outer.Run run, SpireGuard guard) {
		double[] crowd = crowd(run.stage);
		scale(guard, Attributes.MAX_HEALTH, crowd[0]);
		scale(guard, Attributes.ATTACK_DAMAGE, crowd[1]);
		if (run.mods.contains("heavy")) scale(guard, Attributes.MAX_HEALTH, 1.3);
		if (run.mods.contains("swift")) scale(guard, Attributes.MOVEMENT_SPEED, 1.2);
		if (run.mods.contains("fury")) scale(guard, Attributes.ATTACK_DAMAGE, 1.25);
		guard.setHealth(guard.getMaxHealth());
	}

	private static void scale(SpireGuard guard, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double by) {
		AttributeInstance instance = guard.getAttribute(attribute);
		if (instance != null) instance.setBaseValue(instance.getBaseValue() * by);
	}

	/** A hardship comes and stays for the rest of the run. */
	private static void hardship(Outer.Run run, List<ServerPlayer> on, String which) {
		run.mods.add(which);
		if (which.equals("fire")) {
			for (int x = -RADIUS; x <= RADIUS; x++) {
				for (int z = -RADIUS; z <= RADIUS; z++) {
					double d = Math.sqrt(x * x + z * z);
					if (d >= RADIUS - 2.5 && d <= RADIUS + 0.5) run.level.setBlock(run.origin.offset(x, 0, z), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
				}
			}
		}
		if (which.equals("fallen")) columns(run, false);
		run.level.playSound(null, run.origin, com.vnap.sound.SpireSounds.MEGA_BROKEN, SoundSource.HOSTILE, 1.6F, 0.6F);
		for (ServerPlayer player : on) com.vnap.Mc.say(player, Component.translatable("outer.villager_life.hardship." + which), false);
	}

	static void clear(Outer.Run run) {
		AABB box = new AABB(run.origin).inflate(STANDS + 2, 16, STANDS + 2);
		for (net.minecraft.world.entity.Entity left : run.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
				entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) left.discard();
	}
}
