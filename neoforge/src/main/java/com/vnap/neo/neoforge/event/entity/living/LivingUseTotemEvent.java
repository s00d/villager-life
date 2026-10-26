package com.vnap.neo.neoforge.event.entity.living;

public class LivingUseTotemEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(LivingUseTotemEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.LivingUseTotemEvent.class, priority, LivingUseTotemEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.LivingUseTotemEvent f;

	LivingUseTotemEvent(net.minecraftforge.event.entity.living.LivingUseTotemEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.damagesource.DamageSource getSource() {
		return f.getSource();
	}

	public net.minecraft.world.item.ItemStack getTotem() {
		return f.getTotem();
	}

	public net.minecraft.world.InteractionHand getHandHolding() {
		return f.getHandHolding();
	}
}
