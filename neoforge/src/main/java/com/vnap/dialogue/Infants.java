package com.vnap.dialogue;

import com.vnap.village.town.TownRules;
import com.vnap.block.DecorBlock;
import com.vnap.mood.MoodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A newborn's first day: it lies in a cradle in its parents' room (bought at the stall on the day it is born; the
 * mayor gives the household a little for it) and its mother does not leave it. It cries and coos, its mother rocks
 * it and hushes it, its father comes to it after work, its brothers and sisters peek in. The next morning it is out
 * of the cradle, a child like the others. Its cradle cannot be broken while it lies there.
 */
public final class Infants {
	/** A mother further than this from the cradle goes back to it. */
	public static final double NEAR = 4.0;
	/** The family's home this far from the cradle: they moved house, the baby goes with them. */
	static final double MOVED_AWAY = 24.0;
	public static final String CRADLE = "cradle";
	/** Who minds an infant (its mother, its father if she is gone) to that infant; filled by the infant's own tick. */
	private static final Map<UUID, UUID> CARERS = new ConcurrentHashMap<>();

	private Infants() {
	}

	/**
	 * Invisibility is not saved with an entity (only an armour stand keeps it): a newborn loaded back into the world
	 * is laid in its cradle again at once, not left standing there for the family tick to find.
	 */
	public static void register() {
		// children cannot be hurt, by anyone (only /kill and the void take them); lightning turns no child into a witch
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class, (net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) -> {
			if (event.getEntity() instanceof Villager child && child.isBaby()
					&& !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
		});
		// a builder at work (a storey, a roof, a stair flight going up by hand) does not die of a fall off it or of a block set into it
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class, (net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) -> {
			if (event.getEntity() instanceof Villager builder && !builder.isBaby()
					&& (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_FALL) || event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL))
					&& com.vnap.village.life.LifeBrain.building(builder)) event.setCanceled(true);
			// one up on a bed (half a block high) under a ceiling two over the floor has its head in it: a bed under a
			// roof's slope, a vanilla house's slab ceiling (a villager choked so in a panic, live in the dev world)
			// a villager caught in a wild berry bush of its village (a taiga's) stood in it pricked to death: it knows
			// the bush, it is only slowed by it (two died so in one sweep, 22.10.2026)
			if (event.getEntity() instanceof Villager && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.SWEET_BERRY_BUSH)) event.setCanceled(true);
			if (event.getEntity() instanceof Villager sleeper && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
					&& sleeper.level().getBlockState(sleeper.blockPosition()).getBlock() instanceof net.minecraft.world.level.block.BedBlock) event.setCanceled(true);
		});
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent.class, (net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent event) -> {
			if (event.getEntity() instanceof Villager child && child.isBaby()) event.setCanceled(true);
		});
		net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, (net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Villager baby && infant(baby)) {
				BlockPos cradle = Family.state(baby).cradle;
				if (cradle == null) return;
				// its cradle's chunk still being made (a look at it would wait for it for ever): laid down the next tick
				if (level.getChunkSource().getChunkNow(cradle.getX() >> 4, cradle.getZ() >> 4) == null) {
					com.vnap.Mc.later(level.getServer(), new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
						if (baby.isAlive() && level.isLoaded(cradle) && isCradle(level.getBlockState(cradle))) lay(baby, cradle);
					}));
				} else if (isCradle(level.getBlockState(cradle))) {
					lay(baby, cradle);
				}
			}
		});
	}

	public static boolean infant(Villager villager) {
		return villager.isBaby() && Family.state(villager).infantUntil >= 0;
	}

	/**
	 * A newborn as it lies in its cradle, as both sides see it: invisible, no brain, a baby. The cradle draws it
	 * (DecorRenderer); the entity itself is kept for its family, name, voice and the village's head count.
	 */
	public static boolean lying(Villager villager) {
		return villager.isBaby() && villager.isNoAi() && villager.isInvisible();
	}

	/** The infant this mother is minding, or null. */
	public static @Nullable Villager babyOf(ServerLevel level, Villager mother) {
		UUID id = CARERS.get(mother.getUUID());
		if (id == null) return null;
		if (level.getEntity(id) instanceof Villager baby && baby.isAlive() && infant(baby)) return baby;
		CARERS.remove(mother.getUUID());
		return null;
	}

	/** Minding a newborn now: every other errand of its life waits (it stays by the cradle, NursingBehavior). */
	public static boolean minding(ServerLevel level, Villager villager) {
		return CARERS.containsKey(villager.getUUID()) && babyOf(level, villager) != null;
	}

	/** The cradle this mother keeps to, or null when she minds no infant. */
	public static @Nullable BlockPos cradleOf(ServerLevel level, Villager mother) {
		Villager baby = babyOf(level, mother);
		return baby == null ? null : Family.state(baby).cradle;
	}

	public static boolean isCradle(BlockState state) {
		return state.getBlock() instanceof DecorBlock decor && decor.name().equals(CRADLE);
	}

	/** Born: the mayor's gift, a cradle in the parents' room, the baby laid in it. Without room it skips the cradle. */
	static void onBorn(ServerLevel level, Villager mother, Villager child, long day) {
		com.vnap.village.market.Market.birthGift(level, mother, TownRules.BIRTH_GIFT);
		if (MoodConfig.infantDays() <= 0) return;
		BlockPos anchor = Family.home(mother);
		BlockPos cradle = anchor == null ? null : cradleFor(level, mother, anchor);
		if (cradle == null) return;
		FamilyState family = Family.state(child);
		// a whole day of ticks: the day's number and the time of day part ways whenever a player sleeps the night away
		family.infantUntil = level.getGameTime() + 24000L * MoodConfig.infantDays();
		family.cradle = cradle;
		CARERS.put(mother.getUUID(), child.getUUID());
		lay(child, cradle);
	}

	/** A cradle in the room no infant lies in, or null. */
	public static @Nullable BlockPos freeCradle(ServerLevel level, Villager mother, BlockPos anchor) {
		Set<BlockPos> room = FamilyHome.room(level, anchor);
		Set<BlockPos> taken = new java.util.HashSet<>();
		for (Villager baby : level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(16.0), Infants::infant)) {
			BlockPos used = Family.state(baby).cradle;
			if (used != null) taken.add(used);
		}
		// a cradle stands on the floor (no longer part of the walkable room) or beside it
		for (BlockPos floor : room) {
			for (BlockPos cell : new BlockPos[]{floor, floor.north(), floor.south(), floor.east(), floor.west()}) {
				if (isCradle(level.getBlockState(cell)) && !taken.contains(cell)) return cell.immutable();
			}
		}
		return null;
	}

	/** The mother set a cradle down in the room by hand, ahead of the birth (HomeChoreBehavior). */
	public static @Nullable BlockPos placeCradle(ServerLevel level, Villager mother, BlockPos anchor) {
		Family.nurseryChanged(level, anchor);
		return cradleFor(level, mother, anchor);
	}

	/** A free cradle in the room, else a new one bought at the stall and set down by the parents' bed. */
	static @Nullable BlockPos cradleFor(ServerLevel level, Villager mother, BlockPos anchor) {
		BlockPos free = freeCradle(level, mother, anchor);
		if (free != null) return free;
		Set<BlockPos> room = FamilyHome.room(level, anchor);
		List<BlockPos> cells = room.stream().sorted(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(anchor))
				.thenComparingLong(BlockPos::asLong)).toList();
		Item item = com.vnap.village.market.Market.decor(CRADLE);
		if (!(Block.byItem(item) instanceof DecorBlock block)) return null;
		Set<BlockPos> doorway = FamilyHome.doorwayOf(level, anchor);
		for (BlockPos cell : cells) {
			if (!level.getBlockState(cell).isAir() || !FamilyHome.placeable(level, room, doorway, cell)
					|| !FamilyHome.keepsOpen(level, anchor, room, cell)) continue;
			// the foot end towards open floor, where the mother stands; the pillow and canopy at the back
			Direction facing = Direction.NORTH;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (room.contains(cell.relative(side)) && level.getBlockState(cell.relative(side)).isAir()) {
					facing = side;
					break;
				}
			}
			com.vnap.village.market.Market.order(level, mother, cell, item);
			level.setBlock(cell, block.defaultBlockState().setValue(DecorBlock.FACING, facing), Block.UPDATE_ALL);
			return cell.immutable();
		}
		// no free floor: a little clutter goes for the cradle (its things drop)
		for (BlockPos cell : FamilyHome.clutterAround(level, room, doorway)) {
			if (!FamilyHome.keepsOpen(level, anchor, room, cell)) continue;
			FamilyHome.clear(level, cell);
			if (!level.getBlockState(cell).isAir()) continue;
			Direction facing = Direction.NORTH;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (room.contains(cell.relative(side))) {
					facing = side;
					break;
				}
			}
			com.vnap.village.market.Market.order(level, mother, cell, item);
			level.setBlock(cell, block.defaultBlockState().setValue(DecorBlock.FACING, facing), Block.UPDATE_ALL);
			return cell.immutable();
		}
		return null;
	}

	/** Debug: a cradle on this cell (foot end towards {@code foot}) with a newborn of no family in it, for a day. */
	public static @Nullable Villager spawn(ServerLevel level, BlockPos cell, Direction foot, boolean female) {
		if (!(Block.byItem(com.vnap.village.market.Market.decor(CRADLE)) instanceof DecorBlock block)) return null;
		Villager baby = com.vnap.Mc.create(net.minecraft.world.entity.EntityType.VILLAGER, level);
		if (baby == null) return null;
		level.setBlock(cell, block.defaultBlockState().setValue(DecorBlock.FACING, foot), Block.UPDATE_ALL);
		baby.setAge(-24000 * (MoodConfig.childhoodDays() + MoodConfig.infantDays()));
		com.vnap.Mc.snap(baby, cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
		((com.vnap.entity.VillagerLifeData) baby).vnap$setFemale(female);
		FamilyState family = Family.state(baby);
		family.genderKnown = true;
		family.cradle = cell.immutable();
		family.infantUntil = level.getGameTime() + 24000L * MoodConfig.infantDays();
		lay(baby, cell);
		level.addFreshEntity(baby);
		return baby;
	}

	/** Into the cradle: out of sight (the cradle draws it), out of harm's and anyone's way, still where it lies. */
	private static void lay(Villager baby, BlockPos cradle) {
		baby.setNoAi(true);
		baby.setInvisible(true);
		com.vnap.Mc.invulnerable(baby, true);
		baby.setNoGravity(true);
		baby.setSilent(true);
		baby.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		com.vnap.Mc.snap(baby, cradle.getX() + 0.5, cradle.getY() + 0.25, cradle.getZ() + 0.5, baby.getYRot(), 0.0F);
	}

	/** The infant's tick (in place of the family's: it does nothing a child does); true while it is an infant. */
	public static boolean tick(ServerLevel level, Villager baby, long day) {
		FamilyState family = Family.state(baby);
		if (family.infantUntil < 0) return false;
		BlockPos home = Family.home(baby);
		boolean moved = home != null && family.cradle != null && !home.closerThan(family.cradle, MOVED_AWAY);
		// out on the next morning; at once when it grew up by command or its family moved to another house
		if (!baby.isBaby() || moved || (level.getGameTime() >= family.infantUntil && Family.timeOfDay(level) < 12000L)) {
			wake(level, baby, family);
			return false;
		}
		Villager carer = carer(level, family);
		CARERS.values().remove(baby.getUUID());
		if (carer != null) CARERS.put(carer.getUUID(), baby.getUUID());
		BlockPos cradle = family.cradle;
		if (cradle == null || !isCradle(level.getBlockState(cradle))) {
			// the cradle is gone (a creeper): the parent sets down another at once, or the baby is up early
			BlockPos anchor = Family.home(baby);
			cradle = carer == null || anchor == null ? null : cradleFor(level, carer, anchor);
			if (cradle == null) {
				wake(level, baby, family);
				return false;
			}
			family.cradle = cradle;
		}
		if (!lying(baby) || baby.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(cradle)) > 0.25) lay(baby, cradle);
		voice(level, baby, carer, cradle);
		return true;
	}

	/** Who minds the newborn: its mother, else (she died, or left) its father; null with neither alive here. */
	private static @Nullable Villager carer(ServerLevel level, FamilyState family) {
		for (UUID parent : new UUID[]{family.mother, family.father}) {
			if (parent != null && level.getEntity(parent) instanceof Villager villager && villager.isAlive()) return villager;
		}
		return null;
	}

	/** Now and then, with someone to hear: a cry (at night, or with nobody by it) or a coo; the parent answers. */
	private static void voice(ServerLevel level, Villager baby, @Nullable Villager carer, BlockPos cradle) {
		if (!level.hasNearbyAlivePlayer(baby.getX(), baby.getY(), baby.getZ(), 48.0) || level.getRandom().nextInt(10) != 0) return;
		boolean alone = carer == null || !carer.blockPosition().closerThan(cradle, NEAR);
		boolean cries = alone || Family.timeOfDay(level) >= 12000L || level.getRandom().nextInt(3) == 0;
		Speech.queue(baby, cries ? "Baby Cries" : "Baby Coos", null, 1L);
		if (cries && carer != null && !alone && !carer.isSleeping()) {
			Speech.queue(carer, "Hushes the Baby", baby, 60L);
		}
	}

	/** Out of the cradle: a child like the others (it keeps its childhood days), set down on the floor beside it. */
	private static void wake(ServerLevel level, Villager baby, FamilyState family) {
		family.infantUntil = -1L;
		CARERS.values().remove(baby.getUUID());
		baby.setInvisible(false);
		com.vnap.Mc.invulnerable(baby, false);
		baby.setNoGravity(false);
		baby.setSilent(false);
		baby.setNoAi(false);
		BlockPos cradle = family.cradle;
		if (cradle != null) {
			BlockPos floor = null;
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos cell = cradle.relative(side);
				if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
						&& level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
						&& level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
					floor = cell;
					break;
				}
			}
			BlockPos at = floor != null ? floor : cradle.above();
			com.vnap.Mc.snap(baby, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, baby.getYRot(), 0.0F);
			// the cradle was the newborn's: put away once it is up (unless another newborn lies in it)
			family.cradle = null;
			if (isCradle(level.getBlockState(cradle)) && !occupied(level, cradle)) level.removeBlock(cradle, false);
		}
		Villager carer = carer(level, family);
		if (carer != null) Speech.queue(carer, "Baby Out of the Cradle", baby, 20L);
	}

	/** The rest of the family: the mother keeps by the cradle, the father comes after work, the children peek in. */
	static void parentTick(ServerLevel level, Villager villager, FamilyState family, long day) {
		BlockPos cradle = cradleOf(level, villager);
		if (cradle != null) {
			// a mother does not go off while her baby lies in its cradle, whatever the hour calls her to (by day: at
			// night she sleeps in her bed by it; frightened, she runs)
			boolean daytime = Family.timeOfDay(level) < 12000L;
			if (daytime && !villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY)
					&& !villager.blockPosition().closerThan(cradle, NEAR)) {
				villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
						new net.minecraft.world.entity.ai.memory.WalkTarget(cradle, 0.6F, 2));
			}
			return;
		}
		if (family.babyVisitDay == day) return;
		long time = Family.timeOfDay(level);
		boolean free = villager.isBaby() ? time >= 6000L && time < 11000L : time >= 9000L && time < 11500L;
		if (!free) return;
		for (Villager baby : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(32.0), Infants::infant)) {
			FamilyState own = Family.state(baby);
			boolean kin = villager.getUUID().equals(own.father) || (villager.isBaby() && own.mother != null
					&& own.mother.equals(family.mother));
			BlockPos at = own.cradle;
			if (!kin || at == null) continue;
			family.babyVisitDay = day;
			com.vnap.village.life.Agenda.add(villager, new com.vnap.village.life.Agenda.Item("baby_visit", at, com.vnap.village.life.Agenda.VISIT, 1, 0.6F,
					null, villager.isBaby() ? "Kid Peeks at the Baby" : "Visits the Baby", baby.getUUID()));
			return;
		}
	}

	/** The parent minding it rocks the cradle (its nursing behaviour, now and then). */
	public static void rock(ServerLevel level, Villager mother, BlockPos cradle) {
		com.vnap.Mc.swing(mother, net.minecraft.world.InteractionHand.MAIN_HAND);
		level.playSound(null, cradle, net.minecraft.sounds.SoundEvents.WOOD_STEP, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.8F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, cradle.getX() + 0.5, cradle.getY() + 0.9, cradle.getZ() + 0.5,
				1, 0.2, 0.1, 0.2, 0.0);
		Villager baby = babyOf(level, mother);
		if (level.getRandom().nextInt(3) == 0) Speech.queue(mother, "Rocks the Cradle", baby, 1L);
		else if (baby != null && level.getRandom().nextInt(2) == 0) Speech.queue(baby, "Baby Coos", null, 10L);
	}

	/** A newborn lies in this cradle: nobody breaks it (the break is refused, MoodHooks.onBreak). */
	public static boolean occupied(ServerLevel level, BlockPos pos) {
		if (!isCradle(level.getBlockState(pos))) return false;
		for (Villager baby : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(2.0), Infants::infant)) {
			if (pos.equals(Family.state(baby).cradle)) return true;
		}
		return false;
	}
}
