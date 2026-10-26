package com.vnap.neo.mixin;

import com.vnap.neo.bus.api.Event;
import com.vnap.neo.Bridge;
import com.vnap.neo.neoforge.event.entity.living.LivingDamageEvent;
import com.vnap.neo.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's damage events where NeoForge fires them: LivingIncomingDamageEvent after hurt's checks (cancel: no damage;
 * its amount is the damage from there on), LivingDamageEvent.Post at the end of actuallyHurt with what was taken.
 */
@SuppressWarnings("unused")
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin implements com.vnap.neo.DamageTaken {
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

	@Redirect(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeHooks;onLivingDamage(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F"),
			remap = false)
	private float vnap$damage(LivingEntity entity, DamageSource source, float amount) {
		float taken = net.minecraftforge.common.ForgeHooks.onLivingDamage(entity, source, amount);
		vnap$taken = taken;
		return taken;
	}

	@Inject(method = "actuallyHurt", at = @At("RETURN"))
	//? if >=1.21.2 {
	/*private void vnap$post(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof LivingEntity self && !self.isInvulnerableTo(level, source)) vnap$posted(source);
	}
	*///?} else {
	private void vnap$post(DamageSource source, float amount, CallbackInfo ci) {
		if ((Object) this instanceof LivingEntity self && !self.isInvulnerableTo(source)) vnap$posted(source);
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
