package com.vnap.spire.guard;

import com.vnap.Mc;
import com.vnap.spire.Attack;
import com.vnap.entity.VillageEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A guard's shot: a mote of the Core's crystal in a shallow arc, gone in sparks on whatever it meets. */
public final class SpireBolt extends ThrowableItemProjectile {
	private float damage = 2.0F;
	private int colour = 0xC9A8FF;
	private net.minecraft.world.effect.@org.jspecify.annotations.Nullable MobEffectInstance effect;

	public SpireBolt(EntityType<? extends SpireBolt> type, Level level) {
		super(type, level);
	}

	public SpireBolt(Level level, LivingEntity owner, float damage, int colour) {
		//? if >=1.21.2 {
		/*super(VillageEntities.SPIRE_BOLT, owner, level, new net.minecraft.world.item.ItemStack(Items.AMETHYST_SHARD));
		*///?} else
		super(VillageEntities.SPIRE_BOLT, owner, level);
		this.damage = damage;
		this.colour = colour;
	}

	private net.minecraft.world.damagesource.@org.jspecify.annotations.Nullable DamageSource source;

	/** A trap's dart: no one loosed it, it strikes as the trap ({@code source}) and looks a dart, not a mote. */
	public static SpireBolt dart(Level level, float damage, net.minecraft.world.damagesource.DamageSource source) {
		SpireBolt dart = new SpireBolt(VillageEntities.SPIRE_BOLT, level);
		dart.damage = damage;
		dart.colour = 0x8A6A3A;
		dart.source = source;
		dart.setItem(new net.minecraft.world.item.ItemStack(com.vnap.spire.SpireBlocks.dart()));
		return dart;
	}

	/** The attack that loosed it (a volley's shots carry a keeper's affliction, a plain bolt's do not). */
	@org.jspecify.annotations.Nullable Attack by;

	/** What a hit leaves on whoever it meets as well (a stinger's poison, a bubble's slowness). */
	public SpireBolt with(net.minecraft.world.effect.MobEffectInstance effect) {
		this.effect = effect;
		return this;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.AMETHYST_SHARD;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.02;
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return !(entity instanceof SpireGuard) && super.canHitEntity(entity);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel level && tickCount % 2 == 0) {
			level.sendParticles(com.vnap.Mc.dust(colour, 1.0F), getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (tickCount > 100) discard();
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		Mc.hurt(result.getEntity(), source != null ? source : damageSources().thrown(this, getOwner()), damage);
		if (effect != null && result.getEntity() instanceof LivingEntity living) living.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
		if (getOwner() instanceof SpireGuard guard && result.getEntity() instanceof LivingEntity living) guard.sting(living, by);
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel level) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 8, 0.1, 0.1, 0.1, 0.1);
			discard();
		}
	}
}
