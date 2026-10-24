package com.vnap.village.life;

import com.vnap.village.town.TownRules;
import com.google.common.collect.ImmutableMap;
import com.vnap.dialogue.Family;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.town.Bakehouse;
import com.vnap.village.town.Roads;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A town job: a villager without a trade bakes the town's bread. While the stall is short of bread and has wheat, it
 * takes a batch's wheat from the counter (the treasury pays), bakes it at the bakehouse's oven and brings the loaves
 * back to the counter (the stall pays the treasury wholesale); a few coppers' wage from the treasury.
 */
final class BakeBehavior extends Behavior<Villager> {
	private static final int CHECK_EVERY = 400;
	private static final int BAKE = 200;
	/** Bakehouses someone is baking at: one baker each. */
	private static final Claims CLAIMED = new Claims();

	private final LifeGate gate;
	private final ErrandRunner runner = new ErrandRunner();
	private final Deque<Errand> queue = new ArrayDeque<>();
	private @Nullable BlockPos claimed;
	private Shops.@Nullable Shop shop;
	private @Nullable BlockPos bell;
	/** Loaves whose wheat was taken and that are not on the shelf yet. */
	private int baking;
	private boolean finished;
	private long nextCheck;

	BakeBehavior(LifeGate gate) {
		super(ImmutableMap.of(), 1200, 3600);
		this.gate = gate;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
		nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
		if (villager.isBaby() || villager.isSleeping() || villager.isNoAi() || !Workshops.workTime(level)
				|| !com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
				|| villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)
				|| Life.official(villager) || Hamlets.leaving(villager) || ErrandRunner.goingToAJob(villager)) {
			return false;
		}
		Roads.Network network = Roads.networkOf(level, villager);
		BlockPos oven = network == null ? null : Bakehouse.baker(network);
		Shops.Shop shop = Shops.of(level, villager);
		if (oven == null || shop == null || shop.counter == null || !Shops.open(level, shop) || CLAIMED.taken(level, oven, villager)
				|| !level.isPositionEntityTicking(oven) || !oven.closerThan(villager.blockPosition(), TownRules.WORK_REACH)) {
			return false;
		}
		int loaves = Bakehouse.batch(shop);
		if (loaves <= 0) return false;
		BlockPos counter = shop.counter;
		queue.clear();
		this.shop = shop;
		this.bell = shop.bell;
		baking = 0;
		queue.add(Errand.at("bake_fetch", counter, new ItemStack(Items.WHEAT), 30, SoundEvents.BUNDLE_INSERT, null, () -> {
			if (Bakehouse.takeWheat(level, shop, loaves)) baking = loaves;
		}));
		queue.add(Errand.at("bake", oven, new ItemStack(Items.WHEAT), BAKE, SoundEvents.FIRE_AMBIENT, net.minecraft.core.particles.ParticleTypes.SMOKE, () -> {
		}));
		queue.add(Errand.at("bake_sell", counter, new ItemStack(Items.BREAD), 30, SoundEvents.BUNDLE_INSERT, null, () -> sell(level, villager)));
		claimed = oven.immutable();
		CLAIMED.take(claimed, villager);
		return true;
	}

	private void sell(ServerLevel level, Villager villager) {
		Shops.Shop at = shop;
		BlockPos town = bell;
		if (baking <= 0 || at == null || town == null) return;
		Bakehouse.sellBread(level, at, baking);
		Market.townWage(level, villager, town, TownRules.WAGE);
		baking = 0;
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = true;
		finished = false;
		runner.reset();
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Errand current = queue.peek();
		if (current == null) {
			finished = true;
			return;
		}
		ErrandRunner.Result result = runner.tick(level, villager, current);
		if (result == ErrandRunner.Result.WORKING) return;
		queue.poll();
		runner.reset();
		if (result == ErrandRunner.Result.FAILED || queue.isEmpty()) finished = true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !finished && villager.isAlive() && !villager.isSleeping() && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = false;
		// the wheat was taken: the bread reaches the shelf even when the trip broke off (night, a scare)
		sell(level, villager);
		shop = null;
		bell = null;
		BlockPos held = claimed;
		if (held != null) CLAIMED.release(held);
		claimed = null;
		queue.clear();
		WorkerControl.hold(villager, ItemStack.EMPTY);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		nextCheck = gameTime + CHECK_EVERY;
	}
}
