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
 * The Nameless: a round hall, and bosses one after another without end, each made for the run out of the same pieces
 * (Genome.nameless: a keeper's body in another chapter's colours, its blows; KeeperArts.Patchwork: its arts) and given
 * a name of two words. Each one felled makes the next stronger (Bounty Boys' Endless Rush) and the players a little
 * stronger too; between two of them the players are healed.
 */
public final class Rush {
	static final int RADIUS = 16;
	private static final int BREATH = 160;
	/** Two words of a name each, by number (outer.villager_life.nameless.a.N / b.N). */
	static final int WORDS = 12;

	private Rush() {
	}

	static BlockPos start(Outer.Run run) {
		return run.origin.offset(0, 1, -RADIUS + 3);
	}

	static void build(Outer.Run run) {
		ServerLevel level = run.level;
		BlockPos o = run.origin;
		for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
			for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > RADIUS + 1.5) continue;
				boolean wall = d > RADIUS + 0.5;
				BlockState floor = Math.round(d) % 5 == 0 && d > 1 ? Blocks.AMETHYST_BLOCK.defaultBlockState()
						: ((x + z) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
				level.setBlock(o.offset(x, 0, z), wall ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : floor, 2);
				level.setBlock(o.offset(x, -1, z), Blocks.BLACKSTONE.defaultBlockState(), 2);
				for (int y = 1; y <= 14; y++) {
					BlockState at = Blocks.AIR.defaultBlockState();
					if (wall && y <= 9) at = y % 4 == 0 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
					level.setBlock(o.offset(x, y, z), at, 2);
				}
			}
		}
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2 * i / 12;
			BlockPos wall = o.offset((int) Math.round(Math.cos(angle) * RADIUS), 5, (int) Math.round(Math.sin(angle) * RADIUS));
			level.setBlock(wall, SpireBlocks.SCONCE.defaultBlockState().setValue(com.vnap.spire.SpireFixtureBlock.FACING,
					Direction.getNearest(-Math.cos(angle), 0, -Math.sin(angle))), 2);
		}
		BlockPos exit = o.offset(0, 1, -RADIUS);
		for (int x = -1; x <= 1; x++) for (int y = 0; y <= 4; y++) level.setBlock(exit.offset(x, y, 0),
				(Math.abs(x) == 1 || y == 0 || y == 4) ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : OuterGateBlock.exit(Direction.Axis.X), 2);
		// the first boss waits as long as others may still come in (Outer.GATHER)
		run.timer = Outer.GATHER;
	}

	static void tick(Outer.Run run, List<ServerPlayer> on) {
		ServerLevel level = run.level;
		int standing = run.livingGuards();
		boolean fighting = run.fighting;
		if (fighting && standing == 0) {
			run.fighting = false;
			run.felled++;
			run.stage = run.felled;
			run.timer = BREATH;
			int strength = run.loadout.floor() + FLOORS_EACH * run.felled;
			if (run.felled % 3 == 0) Outer.reward(run, on, strength, run.felled >= 15 ? 2 : 1);
			else for (ServerPlayer player : on) Outer.record(player, run);
			for (ServerPlayer player : on) {
				player.setHealth(player.getMaxHealth());
				com.vnap.Mc.say(player, Component.translatable("outer.villager_life.rush.felled", run.felled), true);
			}
		}
		if (!fighting && --run.timer <= 0) boss(run, on);
		if (level.getGameTime() % 20 == 0) {
			Outer.tell(run, on, standing, 1, !fighting, false);
			// a strength the felled lend: a step of it for every four
			if (run.felled >= 4) for (ServerPlayer player : on) player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, run.felled / 4 - 1, true, false));
		}
	}

	/** Floors further for each Nameless before it. */
	public static final int FLOORS_EACH = 2;

	/**
	 * The n-th Nameless's health and blows against a keeper of its floor: the first lighter (a keeper is a whole floor's
	 * fight, the rush has no break but the one between), each after a little more, with no end (OuterGameTests).
	 */
	public static double[] grown(int n) {
		return new double[] {0.55 + 0.1 * n, 0.7 + 0.05 * n};
	}

	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same boss")
	static void boss(Outer.Run run, List<ServerPlayer> on) {
		ServerLevel level = run.level;
		int n = run.felled;
		Random random = new Random(FloorGenerator.mix(run.seed, 1000 + n));
		int floor = run.loadout.floor() + FLOORS_EACH * n;
		Genome genome = Genome.nameless(random.nextLong(), floor);
		String theme = Genome.THEMES.get(genome.palette());
		AABB room = new AABB(run.origin).inflate(RADIUS - 1, 0, RADIUS - 1).expandTowards(0, 12, 0);
		SpireGuard boss = SpireGuards.make(level, genome, theme, run.origin.offset(0, 1, 5), room, room.inflate(6));
		if (boss == null) return;
		AttributeInstance health = boss.getAttribute(Attributes.MAX_HEALTH);
		double[] grown = grown(n);
		if (health != null) health.setBaseValue(health.getBaseValue() * grown[0]);
		AttributeInstance damage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) damage.setBaseValue(damage.getBaseValue() * grown[1]);
		boss.setHealth(boss.getMaxHealth());
		Component name = name(genome.seed());
		boss.setCustomName(name);
		run.adopt(boss);
		run.fighting = true;
		level.playSound(null, run.origin, com.vnap.sound.SpireSounds.KEEPER_APPEAR, SoundSource.HOSTILE, 2.0F, 0.8F + random.nextFloat() * 0.4F);
		for (ServerPlayer player : on) com.vnap.Mc.say(player, Component.translatable("outer.villager_life.rush.comes", n + 1, name), false);
		Outer.tell(run, on, 1, 1, false, true);
		com.vnap.VillagerLife.trace("vnap outer: rush run " + run.index + " boss " + (n + 1) + " floor " + floor + " " + genome.body() + " " + genome.attacks());
	}

	/** Two words for a Nameless one, from its seed: "the Ashen Bellringer", "Weaver of Rusted Stars". */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded: the same seed draws the same name")
	static Component name(long seed) {
		Random random = new Random(FloorGenerator.mix(seed, 53));
		return Component.translatable("outer.villager_life.nameless.name", Component.translatable("outer.villager_life.nameless.a." + random.nextInt(WORDS)),
				Component.translatable("outer.villager_life.nameless.b." + random.nextInt(WORDS)));
	}

	static void clear(Outer.Run run) {
		AABB box = new AABB(run.origin).inflate(RADIUS + 4, 16, RADIUS + 4);
		for (net.minecraft.world.entity.Entity left : run.level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
				entity -> !(entity instanceof net.minecraft.world.entity.player.Player))) left.discard();
	}
}
