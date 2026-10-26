package com.vnap.fab.neoforge.event.entity.living;

/** A totem about to save its holder (com.vnap.fab.mixin.TotemMixin); a cancel lets it die. */
public class LivingUseTotemEvent extends com.vnap.fab.bus.api.Event implements com.vnap.fab.bus.api.ICancellableEvent {
	private final net.minecraft.world.entity.LivingEntity entity;
	private final net.minecraft.world.damagesource.DamageSource source;
	private final net.minecraft.world.item.ItemStack totem;
	private final net.minecraft.world.InteractionHand handHolding;

	public LivingUseTotemEvent(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.damagesource.DamageSource source, net.minecraft.world.item.ItemStack totem, net.minecraft.world.InteractionHand handHolding) {
		this.entity = entity;
		this.source = source;
		this.totem = totem;
		this.handHolding = handHolding;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return entity;
	}

	public net.minecraft.world.damagesource.DamageSource getSource() {
		return source;
	}

	public net.minecraft.world.item.ItemStack getTotem() {
		return totem;
	}

	public net.minecraft.world.InteractionHand getHandHolding() {
		return handHolding;
	}
}
