package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.jspecify.annotations.Nullable;

/**
 * A villager in a panic at night runs home to its bed rather than six blocks off into the dark: vanilla's flight
 * took it away from the monster and away from every door, and the next monster caught it in the open (a town lost its
 * shopkeeper and a grown son that way, 35 and 14 blocks from their beds). Only when home is not past the monster:
 * running towards it to get home would be worse than vanilla's flight. Panic package, after vanilla's flight, whose
 * walk target this one replaces.
 */
public final class RunHome extends Behavior<Villager> {
	private static final double FAR = 48;
	private final float speed;

	public RunHome(float speed) {
		super(ImmutableMap.of(), 1);
		this.speed = speed;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return com.vnap.village.town.TownRules.monstersOut(level) && bed(level, villager) != null;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		BlockPos bed = bed(level, villager);
		if (bed != null) villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bed, speed, 1));
	}

	private static @Nullable BlockPos bed(ServerLevel level, Villager villager) {
		LivingEntity threat = villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
				.or(() -> villager.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY)).orElse(null);
		if (threat == null) return null;
		// one sent to a shelter tonight (homeless, or its bed out of reach) runs there: towards a bed it cannot get
		// to it stood all night by its own wall, in a panic at a drowned in the harbour
		BlockPos bed = Agenda.where(villager, com.vnap.village.town.Shelter.WHY);
		if (bed == null) {
			GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
			if (home == null || !home.dimension().equals(level.dimension())) return null;
			bed = home.pos();
		}
		double fromBed = villager.blockPosition().distSqr(bed);
		if (fromBed > FAR * FAR || fromBed < 4) return null;
		return bed.distToCenterSqr(threat.position()) > villager.distanceToSqr(threat) ? bed : null;
	}
}
