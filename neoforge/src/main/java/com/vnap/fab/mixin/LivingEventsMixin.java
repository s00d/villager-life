package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.living.LivingExperienceDropEvent;
import com.vnap.fab.neoforge.event.entity.living.LivingHealEvent;
import com.vnap.fab.neoforge.event.entity.living.LivingKnockBackEvent;
import com.vnap.fab.neoforge.event.entity.living.LivingShieldBlockEvent;
import com.vnap.fab.neoforge.event.entity.living.LivingUseTotemEvent;
import com.vnap.fab.neoforge.event.entity.living.MobEffectEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's events of a living entity where NeoForge fires them: an effect added, the experience it drops, healing, knock-back, a shield's block, a totem. */
@SuppressWarnings("unused")
@Mixin(LivingEntity.class)
public abstract class LivingEventsMixin {
	//? if <1.21.5 {
	@Shadow
	protected @Nullable Player lastHurtByPlayer;
	//?}

	@Shadow
	public abstract @Nullable MobEffectInstance getEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect);

	@WrapMethod(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z")
	private boolean vnap$effect(MobEffectInstance effect, @Nullable Entity source, Operation<Boolean> vanilla) {
		MobEffectInstance old = getEffect(effect.getEffect());
		MobEffectInstance before = old == null ? null : new MobEffectInstance(old);
		boolean changed = vanilla.call(effect, source);
		if (changed) Bridge.fire(new MobEffectEvent.Added((LivingEntity) (Object) this, effect, before, source));
		return changed;
	}

	@WrapOperation(method = "dropExperience", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getExperienceReward(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)I"))
	private int vnap$experience(LivingEntity self, ServerLevel level, @Nullable Entity killer, Operation<Integer> vanilla) {
		// the player who last hurt it (from 1.21.5 kept as a reference to look up)
		//? if >=1.21.5 {
		/*Player player = self.getLastHurtByPlayer();
		*///?} else
		Player player = lastHurtByPlayer;
		var event = Bridge.fire(new LivingExperienceDropEvent(self, player, vanilla.call(self, level, killer)));
		return event.isCanceled() ? 0 : event.getDroppedExperience();
	}

	@WrapMethod(method = "heal")
	private void vnap$heal(float amount, Operation<Void> vanilla) {
		var event = Bridge.fire(new LivingHealEvent((LivingEntity) (Object) this, amount));
		if (event.isCanceled() || event.getAmount() <= 0) return;
		vanilla.call(event.getAmount());
	}

	// from 26.2 with the damage that knocks back (the shorter one calls it)
	//? if >=26.2 {
	/*@WrapMethod(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
	private void vnap$knockback(double strength, double x, double z, DamageSource source, float damage, boolean blocked, Operation<Void> vanilla) {
		var event = Bridge.fire(new LivingKnockBackEvent((LivingEntity) (Object) this, (float) strength));
		if (event.isCanceled()) return;
		vanilla.call((double) event.getStrength(), x, z, source, damage, blocked);
	}
	*///?} else {
	@WrapMethod(method = "knockback")
	private void vnap$knockback(double strength, double x, double z, Operation<Void> vanilla) {
		var event = Bridge.fire(new LivingKnockBackEvent((LivingEntity) (Object) this, (float) strength));
		if (event.isCanceled()) return;
		vanilla.call((double) event.getStrength(), x, z);
	}
	//?}

	//? if >=1.21.5 {
	/*// a blocking item's share of the damage (from 1.21.5 any item that blocks attacks); a cancel blocks none
	@WrapOperation(method = "applyItemBlocking", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/component/BlocksAttacks;resolveBlockedDamage(Lnet/minecraft/world/damagesource/DamageSource;FD)F"))
	private float vnap$blocked(net.minecraft.world.item.component.BlocksAttacks blocks, DamageSource source, float amount, double angle, Operation<Float> vanilla) {
		float blocked = vanilla.call(blocks, source, amount, angle);
		if (blocked <= 0) return blocked;
		return Bridge.fire(new LivingShieldBlockEvent((LivingEntity) (Object) this, source, blocked)).isCanceled() ? 0.0F : blocked;
	}
	*///?} else if >=1.21.2 {
	/*@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
	private boolean vnap$shield(LivingEntity self, DamageSource source, Operation<Boolean> vanilla, @Local(argsOnly = true) float amount) {
		if (!vanilla.call(self, source)) return false;
		return !Bridge.fire(new LivingShieldBlockEvent(self, source, amount)).isCanceled();
	}
	*///?} else {
	@WrapOperation(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
	private boolean vnap$shield(LivingEntity self, DamageSource source, Operation<Boolean> vanilla, @Local(argsOnly = true) float amount) {
		if (!vanilla.call(self, source)) return false;
		return !Bridge.fire(new LivingShieldBlockEvent(self, source, amount)).isCanceled();
	}
	//?}

	// the totem in a hand (from 1.21.2 anything that protects from death)
	//? if >=1.21.2 {
	/*@WrapOperation(method = "checkTotemDeathProtection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
	private @Nullable Object vnap$totem(ItemStack stack, net.minecraft.core.component.DataComponentType<?> type, Operation<Object> vanilla, @Local(argsOnly = true) DamageSource source,
			@Local InteractionHand hand) {
		Object protection = vanilla.call(stack, type);
		if (protection == null) return null;
		return Bridge.fire(new LivingUseTotemEvent((LivingEntity) (Object) this, source, stack, hand)).isCanceled() ? null : protection;
	}
	*///?} else {
	@WrapOperation(method = "checkTotemDeathProtection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
	private boolean vnap$totem(ItemStack stack, Item item, Operation<Boolean> vanilla, @Local(argsOnly = true) DamageSource source, @Local InteractionHand hand) {
		if (!vanilla.call(stack, item)) return false;
		return !Bridge.fire(new LivingUseTotemEvent((LivingEntity) (Object) this, source, stack, hand)).isCanceled();
	}
	//?}
}
