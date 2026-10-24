package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.spire.Heroes;
import com.vnap.village.home.Towns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A hero (spire/Heroes) goes for a monster it sees on its town's land and strikes it with its sword, as a player
 * would, once a second; it never chases off its town's land or further than it can see, leaves creepers be, and at
 * night only takes on what comes within a few blocks of it (it keeps to the doors, not to the dark). Worn down to a
 * third of its health it stops, and the panic every villager has takes it home. In the brain's core package, like
 * Doors: it runs whatever the villager was doing.
 */
public final class HeroBehavior extends Behavior<Villager> {
	private static final double SIGHT = 12.0;
	private static final double NIGHT_SIGHT = 5.0;
	private static final double REACH = 2.4;
	private static final int BLOW_EVERY = 20;
	private @Nullable Monster target;
	private long nextBlow;

	public HeroBehavior() {
		super(ImmutableMap.of(), 20 * 60);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (!Heroes.fighting(villager) || villager.isSleeping() || villager.tickCount % 10 != 0) return false;
		target = find(level, villager);
		return target != null;
	}

	private static @Nullable Monster find(ServerLevel level, Villager villager) {
		BlockPos bell = Towns.bellOf(villager);
		if (bell == null) return null;
		double sight = com.vnap.village.town.TownRules.monstersOut(level) ? NIGHT_SIGHT : SIGHT;
		Monster best = null;
		for (Monster monster : level.getEntitiesOfClass(Monster.class, villager.getBoundingBox().inflate(sight),
				m -> m.isAlive() && !(m instanceof Creeper) && villager.hasLineOfSight(m) && Towns.ours(level, bell, m.blockPosition()))) {
			if (best == null || monster.distanceToSqr(villager) < best.distanceToSqr(villager)) best = monster;
		}
		return best;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		Monster monster = target;
		BlockPos bell = Towns.bellOf(villager);
		return monster != null && monster.isAlive() && Heroes.fighting(villager) && bell != null
				&& monster.distanceToSqr(villager) < SIGHT * SIGHT * 2.25 && Towns.ours(level, bell, monster.blockPosition());
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		nextBlow = gameTime + 10;
		level.playSound(null, villager.blockPosition(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.NEUTRAL, 0.8F, 1.2F);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Monster monster = target;
		if (monster == null) return;
		villager.getLookControl().setLookAt(monster, 30.0F, 30.0F);
		if (villager.distanceTo(monster) > REACH) {
			if (gameTime % 5 == 0) BehaviorUtils.setWalkAndLookTargetMemories(villager, monster, 0.75F, 1);
			return;
		}
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (gameTime < nextBlow) return;
		nextBlow = gameTime + BLOW_EVERY;
		strike(level, villager, monster);
	}

	/** One blow: the sword's damage, a knock back, the sweep of the blade and its sound. */
	public static void strike(ServerLevel level, Villager villager, Monster monster) {
		ItemStack weapon = Heroes.weapon(villager);
		if (weapon.isEmpty()) return;
		com.vnap.Mc.hurt(monster, level.damageSources().mobAttack(villager), Heroes.damage(weapon));
		if (!monster.isAlive()) com.vnap.dialogue.Heroes.felled(level, villager);
		net.minecraft.world.phys.Vec3 away = new net.minecraft.world.phys.Vec3(monster.getX() - villager.getX(), 0.0, monster.getZ() - villager.getZ());
		if (away.lengthSqr() > 1.0E-4) {
			monster.setDeltaMovement(monster.getDeltaMovement().add(away.normalize().scale(0.4)).add(0.0, 0.2, 0.0));
			com.vnap.Mc.knocked(monster);
		}
		double x = (villager.getX() + monster.getX()) / 2;
		double z = (villager.getZ() + monster.getZ()) / 2;
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, monster.getY() + monster.getBbHeight() * 0.6, z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(com.vnap.Mc.dust(com.vnap.spire.gear.SpireGear.glow(((com.vnap.spire.gear.GearItem) weapon.getItem()).tier()), 0.9F),
				x, monster.getY() + 1.0, z, 6, 0.3, 0.3, 0.3, 0.0);
		level.playSound(null, villager.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 0.9F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		target = null;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
