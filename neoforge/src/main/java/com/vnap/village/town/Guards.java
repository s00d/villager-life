package com.vnap.village.town;

import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The town's iron golems. Vanilla summons one only when three to five villagers stand within ten blocks of each
 * other, which a village of the mod (houses on lots along streets) never has: its villages lived with no golem at
 * all, and a husk at dawn or one zombie villager went through them house by house. The town keeps one golem for
 * every TownRules.PEOPLE_PER_GOLEM people (at least one): the treasury buys four iron blocks and a carved pumpkin,
 * the villagers set the iron down by hand (Placements) and then the pumpkin, which raises the golem the vanilla way.
 */
public final class Guards {
	public static final String MARK = "#golem";
	/** How far round the bell the golem is set up. */
	private static final int NEAR = 4;
	private static final int FAR = 16;

	private Guards() {
	}

	/** Daily (Roads.plan): a golem short sets up the next one; the iron done, the pumpkin goes on. */
	public static void plan(ServerLevel level, Roads roads, Roads.Network network) {
		Mayors.Office office = Towns.of(level, network.bell);
		REFUSED.clear();
		if (office == null || office.mayor == null || !level.isPositionEntityTicking(network.bell)) {
			REFUSED.put(office == null ? "no office" : office.mayor == null ? "no mayor" : "bell not ticking", 1);
			return;
		}
		BlockPos base = network.golem;
		if (base != null) {
			REFUSED.put("already setting one up at " + base.toShortString(), 1);
			Direction arm = arm(level, base);
			if (arm == null) {
				// the iron is not all down yet, or someone took it: what is missing goes back on the list
				if (!List.of(base, base.above(), base.above().east(), base.above().west(), base.above().north(), base.above().south())
						.stream().anyMatch(at -> Placements.pending(level, at))) {
					network.golem = null;
					roads.setDirty();
				}
				return;
			}
			BlockPos head = base.above(2);
			if (Placements.pending(level, head)) return;
			if (level.getBlockState(head).canBeReplaced()) {
				Placements.add(level, head, Blocks.CARVED_PUMPKIN.defaultBlockState(), office.mayor, MARK);
			} else if (level.isLoaded(head)) {
				// something set on the head's cell (a torch, a block): this golem will not stand, one is set up elsewhere
				network.golem = null;
				roads.setDirty();
			}
			return;
		}
		int people = Towns.population(level, network.bell);
		int wanted = Math.min(TownRules.GOLEMS_MAX, Math.max(1, people / TownRules.PEOPLE_PER_GOLEM));
		if (golems(level, network.bell) >= wanted) {
			REFUSED.put("golems " + golems(level, network.bell) + " of " + wanted, 1);
			return;
		}
		BlockPos spot = spot(level, network);
		if (spot == null) return;
		for (int i = 0; i < 4; i++) Market.order(level, null, network.bell, Items.IRON_BLOCK);
		Market.order(level, null, network.bell, Items.CARVED_PUMPKIN);
		for (BlockPos at : List.of(spot, spot.above(), spot.above().east(), spot.above().west())) {
			Placements.add(level, at, Blocks.IRON_BLOCK.defaultBlockState(), office.mayor);
		}
		network.golem = spot;
		roads.setDirty();
	}

	/** The town's iron golems about (loaded). */
	public static int golems(ServerLevel level, BlockPos bell) {
		double reach = Towns.reach(level, bell);
		return level.getEntitiesOfClass(IronGolem.class, new AABB(bell).inflate(reach, 32, reach), IronGolem::isAlive).size();
	}

	/** The pumpkin is on (Placements): vanilla raised the golem out of the iron; it is the village's, not a player's. */
	public static void raised(ServerLevel level, BlockPos head) {
		for (IronGolem golem : level.getEntitiesOfClass(IronGolem.class, new AABB(head).inflate(2))) golem.setPlayerCreated(false);
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.near(level.dimension(), head);
		if (network != null && head.equals(network.golem == null ? null : network.golem.above(2))) {
			network.golem = null;
			roads.setDirty();
		}
	}

	/** The way the arms of the iron at {@code base} run, once the four blocks of the golem's body are all down. */
	private static @Nullable Direction arm(ServerLevel level, BlockPos base) {
		if (!level.getBlockState(base).is(Blocks.IRON_BLOCK) || !level.getBlockState(base.above()).is(Blocks.IRON_BLOCK)) return null;
		for (Direction side : List.of(Direction.EAST, Direction.SOUTH)) {
			if (level.getBlockState(base.above().relative(side)).is(Blocks.IRON_BLOCK) && level.getBlockState(base.above().relative(side.getOpposite())).is(Blocks.IRON_BLOCK)) {
				return side;
			}
		}
		return null;
	}

	/** Why the last search found no place (debug: what refused how many points). */
	public static final java.util.Map<String, Integer> REFUSED = new java.util.TreeMap<>();

	/**
	 * Open level ground near the bell, the town's own and nobody's, with room for the iron T and the air the vanilla
	 * pattern wants either side of its foot.
	 */
	private static @Nullable BlockPos spot(ServerLevel level, Roads.Network network) {
		REFUSED.clear();
		Land land = Land.around(level, network.bell);
		for (BlockPos at : Rings.around(network.bell, NEAR, FAR, 2, 12)) {
			if (!level.isPositionEntityTicking(at)) {
				REFUSED.merge("unloaded", 1, Integer::sum);
				continue;
			}
			BlockPos ground = Ground.top(level, at.getX(), at.getZ());
			if (ground == null || !level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
				REFUSED.merge("ground", 1, Integer::sum);
				continue;
			}
			BlockPos base = ground.above();
			if (land.blocked(base, 1) || !Towns.ours(level, network.bell, base)) {
				REFUSED.merge(land.blocked(base, 1) ? "land" : "not ours", 1, Integer::sum);
				continue;
			}
			boolean free = true;
			for (int dx = -1; dx <= 1 && free; dx++) {
				for (int dy = 0; dy <= 2 && free; dy++) free = level.getBlockState(base.offset(dx, dy, 0)).isAir() && !Placements.pending(level, base.offset(dx, dy, 0));
			}
			if (free) return base;
			REFUSED.merge("no room", 1, Integer::sum);
		}
		return null;
	}
}
