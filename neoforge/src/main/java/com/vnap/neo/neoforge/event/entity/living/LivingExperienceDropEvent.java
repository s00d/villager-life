package com.vnap.neo.neoforge.event.entity.living;

public class LivingExperienceDropEvent extends com.vnap.neo.bus.api.Event implements com.vnap.neo.bus.api.ICancellableEvent {
	static {
		com.vnap.neo.Bridge.bind(LivingExperienceDropEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.entity.living.LivingExperienceDropEvent.class, priority, LivingExperienceDropEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.LivingExperienceDropEvent f;

	LivingExperienceDropEvent(net.minecraftforge.event.entity.living.LivingExperienceDropEvent f) {
		super(f);
		this.f = f;
	}

	public net.minecraft.world.entity.LivingEntity getEntity() {
		return f.getEntity();
	}

	public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player getAttackingPlayer() {
		return f.getAttackingPlayer();
	}

	public int getDroppedExperience() {
		return f.getDroppedExperience();
	}

	public void setDroppedExperience(int experience) {
		f.setDroppedExperience(experience);
	}
}
