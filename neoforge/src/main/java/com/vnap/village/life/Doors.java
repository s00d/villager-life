package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Villagers shut the doors they opened. Vanilla closes one only while the villager still walks a path and is within
 * three blocks of it: a villager whose walk ended just inside (a taiga house is three blocks across, a shelter is a
 * cell by the door) or who ran on past it left it open all night, and the zombie that followed it home walked in -
 * most of the taiga's deaths were in a house. A door is now shut whenever its villager is not walking through it and
 * up to FAR blocks away, once nobody but a monster stands in it.
 */
public final class Doors extends Behavior<Villager> {
	private static final double FAR = 16;

	public Doors() {
		super(ImmutableMap.of(MemoryModuleType.DOORS_TO_CLOSE, MemoryStatus.VALUE_PRESENT), 1);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		Path path = villager.getBrain().getMemory(MemoryModuleType.PATH).orElse(null);
		return (path == null || path.isDone() || path.notStarted())
				&& !villager.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE).map(Set::isEmpty).orElse(true);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE).ifPresent(doors ->
				close(level, villager, null, null, doors, villager.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)));
	}

	/** In place of InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough (vanilla calls it while walking and at bedtime). */
	public static void close(ServerLevel level, LivingEntity entity, @Nullable Node previous, @Nullable Node next, Set<GlobalPos> doors,
			Optional<List<LivingEntity>> nearest) {
		Iterator<GlobalPos> iterator = doors.iterator();
		while (iterator.hasNext()) {
			GlobalPos door = iterator.next();
			BlockPos pos = door.pos();
			if ((previous != null && previous.asBlockPos().equals(pos)) || (next != null && next.asBlockPos().equals(pos))) continue;
			if (!door.dimension().equals(level.dimension()) || !pos.closerToCenterThan(entity.position(), FAR) || !level.isLoaded(pos)) {
				iterator.remove();
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof DoorBlock block) || !state.is(BlockTags.MOB_INTERACTABLE_DOORS) || !block.isOpen(state)) {
				iterator.remove();
				continue;
			}
			// kept, and shut once clear
			if (inTheWay(level, pos) || comingThrough(entity, pos, nearest)) continue;
			block.setOpen(entity, level, state, pos, false);
			iterator.remove();
		}
	}

	private static boolean inTheWay(ServerLevel level, BlockPos door) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(door).expandTowards(0, 1, 0), standing -> !(standing instanceof Enemy)).isEmpty();
	}

	private static boolean comingThrough(LivingEntity entity, BlockPos door, Optional<List<LivingEntity>> nearest) {
		return nearest.orElse(List.of()).stream()
				.filter(other -> other.getType().equals(entity.getType()) && door.closerToCenterThan(other.position(), 2.0))
				.anyMatch(other -> other.getBrain().getMemory(MemoryModuleType.PATH).filter(path -> !path.isDone()).map(path -> {
					Node previous = path.getPreviousNode();
					return previous != null && (door.equals(previous.asBlockPos()) || door.equals(path.getNextNode().asBlockPos()));
				}).orElse(false));
	}
}
