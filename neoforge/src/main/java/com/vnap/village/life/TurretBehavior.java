package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.block.turret.ArrowTurretBlockEntity;
import com.vnap.dialogue.Family;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.town.Roads;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * A town job: a villager without a trade keeps the wall's crossbows loaded. It buys arrows at the stall with the
 * treasury's money (the town's armoury, Roads.Network.armoury), carries them to a crossbow running low and loads it
 * from the ground; a day labourer's copper for it.
 */
final class TurretBehavior extends Behavior<Villager> {
	/** A crossbow with fewer arrows than this is loaded. */
	static final int LOW = 16;
	static final int FULL = 64;
	private static final int CHECK_EVERY = 200;
	private static final int TRIP_LIMIT = 900;
	/** Crossbows someone is on the way to: one carrier each. */
	private static final Claims CLAIMED = new Claims();

	private final LifeGate gate;
	private @Nullable BlockPos turret;
	private @Nullable BlockPos spot;
	private boolean fetched;
	private boolean finished;
	private int timer;
	private long nextCheck;

	TurretBehavior(LifeGate gate) {
		super(ImmutableMap.of(), 1200, 2400);
		this.gate = gate;
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
		nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
		if (villager.isBaby() || villager.isSleeping() || !Workshops.workTime(level)
				|| !com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
				|| villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY) || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)
				|| Life.official(villager) || Hamlets.leaving(villager) || ErrandRunner.goingToAJob(villager)) {
			return false;
		}
		Roads.Network network = network(level, villager);
		if (network == null) return false;
		for (BlockPos pos : network.turrets) {
			if (CLAIMED.taken(level, pos, villager) || !level.isPositionEntityTicking(pos) || !pos.closerThan(villager.blockPosition(), com.vnap.village.town.TownRules.WORK_REACH)) continue;
			if (!(level.getBlockEntity(pos) instanceof ArrowTurretBlockEntity crossbow) || crossbow.arrows() >= LOW) continue;
			turret = pos.immutable();
			CLAIMED.take(turret, villager);
			return true;
		}
		return false;
	}

	private static Roads.@Nullable Network network(ServerLevel level, Villager villager) {
		return Roads.networkOf(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = true;
		finished = false;
		fetched = false;
		spot = null;
		timer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos target = turret;
		Roads.Network network = network(level, villager);
		if (target == null || network == null || !(level.getBlockEntity(target) instanceof ArrowTurretBlockEntity crossbow) || ++timer > TRIP_LIMIT) {
			finished = true;
			return;
		}
		int need = FULL - crossbow.arrows();
		Map<Item, Integer> bill = Map.of(Items.ARROW, need);
		if (!fetched && network.armoury.count(Items.ARROW) < need) {
			// to the stall first, for arrows: the treasury gives the money
			Shops.Shop shop = Shops.of(level, villager);
			if (shop == null || shop.counter == null || !Shops.open(level, shop)) {
				finished = true;
				return;
			}
			if (!villager.blockPosition().closerThan(shop.counter, 3.0)) {
				if (timer % 20 == 1) villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(shop.counter, 0.6F, 2));
				return;
			}
			if (network.armoury.funds < need) Market.grant(level, network.bell, network.armoury, bill);
			Market.supply(level, villager, shop, network.armoury, bill);
			Roads.get(level.getServer()).setDirty();
			fetched = true;
			if (network.armoury.count(Items.ARROW) == 0) finished = true;
			return;
		}
		fetched = true;
		BlockPos stand = spot;
		if (stand == null) {
			stand = Hands.findSpot(level, villager, Set.of(), target, Hands.REACH, Hands.groundUnder(level, target));
			if (stand == null) {
				finished = true;
				return;
			}
			spot = stand;
		}
		if (!villager.blockPosition().closerThan(stand, 1.5)) {
			if (timer % 20 == 1) villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stand, 0.6F, 0));
			return;
		}
		int n = Math.min(network.armoury.count(Items.ARROW), need);
		if (n > 0 && network.armoury.take(Map.of(Items.ARROW, n))) {
			crossbow.load(new ItemStack(Items.ARROW, n));
			WorkerControl.forceIdleLook(villager, target);
			com.vnap.Mc.swing(villager, InteractionHand.MAIN_HAND);
			level.playSound(null, target, com.vnap.sound.GolemSounds.TURRET_LOAD, SoundSource.BLOCKS, 1.0F, 1.0F);
			Market.dayLabour(level, villager, network.armoury);
			Roads.get(level.getServer()).setDirty();
		}
		finished = true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !finished && villager.isAlive() && !villager.isSleeping() && Workshops.workTime(level)
				&& !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		gate.busy = false;
		BlockPos target = turret;
		if (target != null) CLAIMED.release(target);
		turret = null;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		nextCheck = gameTime + CHECK_EVERY;
	}
}
