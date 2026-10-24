package com.vnap.village.life;

import com.vnap.village.town.TownRules;
import com.google.common.collect.ImmutableMap;
import com.vnap.sound.GolemSounds;
import com.vnap.village.market.Market;
import com.vnap.village.town.Roads;
import com.vnap.village.town.Watch;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The night watch (Watch): the villager on duty stays up, walks from gate to gate with a lantern in hand and rings
 * the town's bell when it sees a monster inside the wall or at a gate (the bell sends the villagers home, as vanilla's
 * does); paid at dawn. Runs in the rest package; its gate keeps vanilla's walk home and sleep aside meanwhile.
 */
final class WatchBehavior extends Behavior<Villager> {
	private static final int POST_TICKS = 200;
	private static final double SEE = 16.0;
	/** A town's bell rings for the watch at most this often (game ticks). */
	private static final long ALARM_EVERY = 600L;

	private final LifeGate gate;
	private int post;
	private int timer;
	private int served;

	WatchBehavior(LifeGate gate) {
		super(ImmutableMap.of(), 24000);
		this.gate = gate;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return level.getGameTime() % 40 == 0 && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) && Watch.onDuty(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = true;
		if (villager.isSleeping()) villager.stopSleeping();
		post = villager.getRandom().nextInt(8);
		timer = 0;
		served = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Roads.Network network = Watch.walled(level, villager);
		if (network == null) return;
		served++;
		WorkerControl.hold(villager, new ItemStack(Items.LANTERN));
		List<BlockPos> posts = Watch.posts(network);
		if (!posts.isEmpty() && (timer++ % POST_TICKS == 0 || !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET))) {
			if (timer > 1) post = (post + 1) % posts.size();
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(posts.get(post % posts.size()), 0.5F, 2));
		}
		if (gameTime % 20 != 0) return;
		boolean seen = !level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(villager.blockPosition()).inflate(SEE),
				mob -> mob instanceof Enemy && mob.isAlive() && villager.hasLineOfSight(mob)).isEmpty();
		if (seen && gameTime - network.alarmAt >= ALARM_EVERY && level.isLoaded(network.bell)
				&& level.getBlockState(network.bell).getBlock() instanceof BellBlock bell) {
			network.alarmAt = gameTime;
			level.playSound(null, villager.blockPosition(), GolemSounds.WATCH_ALARM, SoundSource.NEUTRAL, 1.2F, 1.0F);
			bell.attemptToRing(villager, level, network.bell, Direction.NORTH);
		}
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return villager.isAlive() && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) && Watch.onDuty(level, villager);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = false;
		WorkerControl.hold(villager, ItemStack.EMPTY);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		// a night's watch kept (most of it) is paid: the morning's wage from the treasury
		Roads.Network network = Watch.walled(level, villager);
		if (network != null && served >= 6000) Market.townWage(level, villager, network.bell, TownRules.WAGE);
		served = 0;
	}
}
