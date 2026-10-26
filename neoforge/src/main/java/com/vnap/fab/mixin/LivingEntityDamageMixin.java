package com.vnap.fab.mixin;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.living.LivingDamageEvent;
import com.vnap.fab.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's damage events where NeoForge fires them: LivingIncomingDamageEvent after hurt's checks (cancel: no damage;
 * its amount is the damage from there on), LivingDamageEvent.Post at the end of actuallyHurt with what was taken (the
 * health it cost).
 */
@SuppressWarnings("unused")
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin implements com.vnap.fab.DamageTaken {
	@Unique
	private float vnap$incoming = Float.NaN;
	@Unique
	private float vnap$original;
	@Unique
	private float vnap$taken;

	// a server's damage is hurtServer from 1.21.2
	//? if >=1.21.2 {
	/*@Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z", ordinal = 0), cancellable = true)
	private void vnap$incoming(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
	*///?} else {
	@Inject(method = "hurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z", ordinal = 0), cancellable = true)
	private void vnap$incoming(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
	//?}
		if (!((Object) this instanceof LivingEntity self)) return;
		LivingIncomingDamageEvent event = Bridge.fire(new LivingIncomingDamageEvent(self, source, amount));
		vnap$original = amount;
		vnap$taken = 0;
		if (((Event) event).vnap$canceled()) {
			cir.setReturnValue(false);
			return;
		}
		vnap$incoming = event.getAmount();
	}

	//? if >=1.21.2 {
	/*@ModifyVariable(method = "hurtServer", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;noActionTime:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD, ordinal = 0),
			argsOnly = true)
	*///?} else {
	@ModifyVariable(method = "hurt", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;noActionTime:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD, ordinal = 0),
			argsOnly = true)
	//?}
	private float vnap$amount(float amount) {
		float incoming = vnap$incoming;
		vnap$incoming = Float.NaN;
		return Float.isNaN(incoming) ? amount : incoming;
	}

	@Unique
	private float vnap$health;

	@Inject(method = "actuallyHurt", at = @At("HEAD"))
	//? if >=1.21.2 {
	/*private void vnap$before(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
	*///?} else
	private void vnap$before(DamageSource source, float amount, CallbackInfo ci) {
		vnap$health = ((LivingEntity) (Object) this).getHealth();
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	//? if >=1.21.2 {
	/*private void vnap$post(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof LivingEntity self && !self.isInvulnerableTo(level, source)) {
			vnap$taken = Math.max(0, vnap$health - self.getHealth());
			vnap$posted(source);
		}
	}
	*///?} else {
	private void vnap$post(DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof LivingEntity self && !self.isInvulnerableTo(source)) {
			vnap$taken = Math.max(0, vnap$health - self.getHealth());
			vnap$posted(source);
		}
	}
	//?}

	@Override
	public void vnap$posted(DamageSource source) {
		if (!((Object) this instanceof LivingEntity self)) return;
		Bridge.fire(new LivingDamageEvent.Post(self, source, vnap$original, vnap$taken));
		vnap$taken = 0;
	}

	@Override
	public void vnap$taken(float taken) {
		vnap$taken = taken;
	}
}
