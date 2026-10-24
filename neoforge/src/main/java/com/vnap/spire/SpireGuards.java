package com.vnap.spire;

import com.vnap.entity.VillageEntities;
import com.vnap.spire.guard.SpireGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A floor's guards: each spawn of the plan made the guard its genome says (Genome), as strong as the floor is high:
 * health and damage grow slowly (DESIGN §5), what makes a floor harder is who stands where. A guard keeps to its
 * room (a flyer circles in it) and never leaves the floor's box.
 */
public final class SpireGuards {
	public static final String TAG = "vnap_spire_guard";
	/** Persistent: the server run a guard was made in. One of an earlier run (saved with its slot's chunks) is gone on load. */
	public static final String RUN = "vnap_spire_run";
	/**
	 * The Outer run a guard is of (Outer.Run.adopt; its brood takes it too). Closing a run discards only what is loaded:
	 * the rest, out in unloaded chunks once its players died or left, came back with the next run on the same ground —
	 * eleven guards on the gate before the first wave.
	 */
	public static final String OUTER = "vnap_outer_run";
	private static long run = java.util.concurrent.ThreadLocalRandom.current().nextLong();

	private SpireGuards() {
	}

	/** A new server run: guards made before it are stale. */
	public static void newRun() {
		run = java.util.concurrent.ThreadLocalRandom.current().nextLong();
	}

	/** Whether this guard (or its bolt, its puddle, its little ones) belongs to a floor drawn in this run. */
	public static boolean current(net.minecraft.world.entity.Entity entity) {
		return com.vnap.Nbt.getLong(entity.getPersistentData(), RUN) == run;
	}

	/** How many times its base health a guard of this floor has. */
	public static double health(int floor) {
		return Math.pow(1.0 + 0.06 * (strength(floor) - 1), 0.8);
	}

	public static double damage(int floor) {
		return Math.pow(1.0 + 0.04 * (strength(floor) - 1), 0.7);
	}

	/** A keeper's own, slower than its guards' (it fights the chapter's gear: docs/tower/DESIGN.md §5, SpireGameTests). */
	public static double keeperHealth(int floor) {
		return Math.pow(1.0 + 0.06 * (strength(floor) - 1), 0.75);
	}

	public static double keeperDamage(int floor) {
		return Math.pow(1.0 + 0.03 * (strength(floor) - 1), 0.5);
	}

	/**
	 * The floor its numbers are of: the floor itself to the hundredth, then ten more each hundred. Past the hundredth
	 * the Spire is harder by who stands in it (keepers together, elite packs, more of them: FloorGenerator), not by
	 * numbers the gear (its honing tops out) could never meet.
	 */
	public static int strength(int floor) {
		return Math.min(floor, 100) + 10 * Genome.round(floor);
	}

	static @Nullable SpireGuard spawn(ServerLevel level, SpireSlots.Slot slot, FloorPlan.Spawn spawn) {
		FloorPlan plan = slot.plan;
		boolean keeper = spawn.equals(plan.keeper);
		boolean water = plan.swimmers.contains(spawn);
		int pair = plan.duo.indexOf(spawn);
		Integer companion = plan.companions.get(spawn);
		Genome genome = companion != null ? Genome.companion(spawn.genome(), plan.floor, plan.theme, companion)
				: pair >= 0 ? Genome.duo(spawn.genome(), plan.floor, plan.theme, Genome.MASON + pair)
				: keeper && Genome.trial(plan.floor) ? Genome.trial(spawn.genome(), plan.floor, plan.theme)
				: Genome.decode(spawn.genome(), plan.floor, plan.theme, water, keeper);
		SpireGuard guard = make(level, genome, plan.theme, slot.world(spawn.pos()), room(slot, spawn.pos()), AABB.of(slot.box()));
		if (guard != null && plan.elites.contains(spawn)) guard.elite(true);
		if (guard != null && companion != null) {
			// a companion keeper is the lesser of the two
			var health = guard.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
			if (health != null) health.setBaseValue(health.getBaseValue() * 0.7);
			guard.setHealth(guard.getMaxHealth());
		}
		return guard;
	}

	/** The guard of this genome at this spot (its feet), kept to the room and the box. */
	public static @Nullable SpireGuard make(ServerLevel level, Genome genome, String theme, BlockPos at, AABB room, AABB box) {
		EntityType<? extends SpireGuard> type = genome.isKeeper() ? VillageEntities.SPIRE_KEEPER : switch (genome.movement()) {
			case FLY -> VillageEntities.SPIRE_FLYER;
			case SWIM -> VillageEntities.SPIRE_SWIMMER;
			default -> VillageEntities.SPIRE_WALKER;
		};
		SpireGuard guard = com.vnap.Mc.create(type, level);
		if (guard == null) return null;
		com.vnap.Mc.snap(guard, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float) Math.floorMod(genome.seed(), 360L), 0.0F);
		guard.setup(genome, theme, at, room, box);
		guard.getPersistentData().putLong(RUN, run);
		level.addFreshEntity(guard);
		return guard;
	}

	/** The inside of the room a spawn stands in: its cell of the grid, from the floor up to the ceiling. */
	static AABB room(SpireSlots.Slot slot, BlockPos local) {
		int x0 = local.getX() / FloorGenerator.CELL * FloorGenerator.CELL;
		int z0 = local.getZ() / FloorGenerator.CELL * FloorGenerator.CELL;
		int top = FloorGenerator.FLOOR + 3;
		while (top < FloorPlan.SIZE_Y - 1 && !slot.plan.solid(x0 + 1, top, z0 + 1)) top++;
		BlockPos from = slot.world(new BlockPos(x0 + 1, FloorGenerator.FLOOR - 4, z0 + 1));
		BlockPos to = slot.world(new BlockPos(x0 + FloorGenerator.CELL - 1, top, z0 + FloorGenerator.CELL - 1));
		return new AABB(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
	}
}
