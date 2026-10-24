package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.dialogue.Speech;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Carries out the top of the villager's Agenda: walks there, says its word on arrival, stays while its reason holds
 * (looking about, sitting at a table), then takes it off. Holds the life gate meanwhile, so the vanilla strolls and work walks
 * do not pull the villager away; a more pressing entry put on the agenda takes over at the next look. A place it
 * cannot reach (no path for UNREACHABLE ticks) is given up.
 */
final class AgendaBehavior extends net.minecraft.world.entity.ai.behavior.Behavior<Villager> {
	private static final int CHECK_EVERY = 40;
	private static final int UNREACHABLE = 400;
	private static final double FAR = 24.0;
	private static final double LEG = 14.0;

	private final LifeGate gate;
	private Agenda.@Nullable Item item;
	private long arrived = -1;
	private long nextCheck;

	/** In the night's package only this reason is carried out (the shelter): the rest of the agenda waits for the day. */
	private final @Nullable String only;

	AgendaBehavior(LifeGate gate) {
		this(gate, null);
	}

	AgendaBehavior(LifeGate gate, @Nullable String only) {
		super(ImmutableMap.of(), 200, 24000);
		this.gate = gate;
		this.only = only;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		if (now < nextCheck || gate.busy || villager.isSleeping() || villager.isNoAi() || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)) return false;
		nextCheck = now + CHECK_EVERY;
		// the night's package: its own entry, wherever it stands (a day's event put earlier above it stood all night)
		return next(level, villager) != null;
	}

	private Agenda.@Nullable Item next(ServerLevel level, Villager villager) {
		String own = only;
		return own == null ? Agenda.top(level, villager) : Agenda.of(level, villager, own);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = true;
		item = next(level, villager);
		arrived = -1;
		// only a way to this place that cannot be found gives it up: a "could not reach" left from the walk before (a bed
		// behind a walled-up door) dropped the shelter the moment it was set out for
		villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		Agenda.Item current = item;
		if (current != null && !current.carry().isEmpty()) com.vnap.village.script.WorkerControl.hold(villager, current.carry());
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Agenda.Item current = item;
		if (current == null) return;
		if (villager.blockPosition().closerThan(current.where(), current.near() + 0.5)) {
			if (arrived < 0) {
				arrived = gameTime;
				villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				var does = current.does();
				if (does != null) {
					com.vnap.village.script.WorkerControl.forceIdleLook(villager, current.where());
					com.vnap.Mc.swing(villager, net.minecraft.world.InteractionHand.MAIN_HAND);
					does.accept(level, villager);
					com.vnap.village.script.WorkerControl.hold(villager, ItemStack.EMPTY);
				}
				String say = current.say();
				if (say != null) {
					Entity to = current.to() == null ? null : level.getEntity(current.to());
					Speech.queue(villager, say, to, 10L);
				}
			}
			return;
		}
		arrived = -1;
		if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, leg(villager, current));
		}
	}

	/**
	 * A path reaches only so far: a far place is walked to in legs of about LEG blocks towards it, each leg a spot on
	 * land a villager stands on (vanilla's own way to a far job site or home); a point straight ahead was often inside
	 * a hill or a lake, the leg could not be walked and the entry was given up though a way round was there.
	 */
	private static WalkTarget leg(Villager villager, Agenda.Item item) {
		net.minecraft.world.phys.Vec3 from = villager.position();
		net.minecraft.world.phys.Vec3 there = net.minecraft.world.phys.Vec3.atBottomCenterOf(item.where());
		net.minecraft.world.phys.Vec3 way = there.subtract(from);
		if (way.horizontalDistance() <= FAR) return new WalkTarget(item.where(), item.speed(), item.near());
		net.minecraft.world.phys.Vec3 land = net.minecraft.world.entity.ai.util.LandRandomPos.getPosTowards(villager, (int) LEG, 7, there);
		return new WalkTarget(land != null ? land : from.add(way.scale(LEG / way.horizontalDistance())), item.speed(), 2);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		Agenda.Item current = item;
		if (current == null || villager.isSleeping() || !villager.isAlive() || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)) return false;
		// something more pressing (or the entry gone, done elsewhere, expired): the next look picks it up
		if (!current.equals(next(level, villager))) return false;
		// there, and nothing to wait for (or its reason is over: then top() dropped it already)
		if (arrived >= 0 && current.lasts() == null) {
			Agenda.done(villager, current);
			return false;
		}
		Long since = villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).orElse(null);
		if (arrived < 0 && since != null && gameTime - since > UNREACHABLE) {
			Agenda.done(villager, current);
			return false;
		}
		return true;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = false;
		Agenda.Item current = item;
		if (current != null && !current.carry().isEmpty()) com.vnap.village.script.WorkerControl.hold(villager, ItemStack.EMPTY);
		item = null;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		nextCheck = gameTime;
	}
}
