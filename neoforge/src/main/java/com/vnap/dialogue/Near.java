package com.vnap.dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Who and what is around a spot, for the villagers' remarks. */
final class Near {
	private Near() {
	}

	/** Living villagers in a box {@code range} out and half of it up and down; the /dialoguetest props are not. */
	static List<Villager> villagers(ServerLevel level, Vec3 at, double range) {
		return level.getEntitiesOfClass(Villager.class, AABB.ofSize(at, range * 2.0, range, range * 2.0),
				v -> v.isAlive() && !Speech.prop(v));
	}

	/** The closest villager near {@code at} that passes {@code which}. */
	static @Nullable Villager villager(ServerLevel level, Vec3 at, double range, Predicate<Villager> which) {
		return closest(villagers(level, at, range).stream().filter(which).toList(), at);
	}

	static <T extends Entity> @Nullable T closest(Collection<T> entities, Vec3 to) {
		return entities.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(to))).orElse(null);
	}

	/** A spectator is someone looking on, not a guest: the village does not answer it. */
	static @Nullable ServerPlayer player(ServerLevel level, Vec3 at, double range) {
		return closest(level.players().stream()
				.filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(at) <= range * range).toList(), at);
	}

	/** A grown-up awake villager who can see {@code what}. */
	static boolean awakeAdultSees(Villager villager, Entity what) {
		return !villager.isBaby() && !villager.isSleeping() && villager.hasLineOfSight(what);
	}

	static boolean homeWithin(Villager villager, BlockPos pos, int range) {
		return villager.getBrain().getMemory(MemoryModuleType.HOME)
				.filter(home -> home.dimension().equals(villager.level().dimension()))
				.map(home -> home.pos().closerThan(pos, range)).orElse(false);
	}

	/** A block whose id contains {@code part} within {@code range} across and two up or down. */
	static boolean block(Level level, BlockPos origin, String part, int range) {
		return anyBlock(level, origin, range, 2, state -> path(state).contains(part));
	}

	static boolean anyBlock(Level level, BlockPos origin, int across, int up, Predicate<BlockState> test) {
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-across, -up, -across), origin.offset(across, up, across))) {
			if (test.test(level.getBlockState(pos))) return true;
		}
		return false;
	}

	/** A bed or a villager's home near the spot: "the village" for grief lines. */
	static boolean homely(ServerLevel level, BlockPos pos, double villagersWithin, int homeRange, int bedRange) {
		List<Villager> around = villagers(level, Vec3.atCenterOf(pos), villagersWithin);
		return !around.isEmpty() && (around.stream().anyMatch(v -> homeWithin(v, pos, homeRange)) || block(level, pos, "bed", bedRange));
	}

	static String path(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	static String path(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	static String path(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
	}

	static boolean moving(Entity entity) {
		return entity.getDeltaMovement().horizontalDistanceSqr() > 0.0004;
	}

	static boolean crop(String path) {
		return switch (path) {
			case "wheat", "carrots", "potatoes", "beetroots", "torchflower_crop", "pitcher_crop" -> true;
			default -> false;
		};
	}
}
