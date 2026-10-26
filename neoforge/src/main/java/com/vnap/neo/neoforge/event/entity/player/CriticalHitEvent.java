package com.vnap.neo.neoforge.event.entity.player;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class CriticalHitEvent extends Event {
	static {
		Bridge.bind(CriticalHitEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.player.CriticalHitEvent.class, priority, CriticalHitEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.player.CriticalHitEvent f;

	CriticalHitEvent(net.minecraftforge.event.entity.player.CriticalHitEvent f) {
		super(f);
		this.f = f;
	}

	public Player getEntity() {
		return f.getEntity();
	}

	public Entity getTarget() {
		return f.getTarget();
	}

	public boolean isVanillaCritical() {
		return f.isVanillaCritical();
	}

	/** Whether the blow is critical as things stand: what an earlier listener said, else vanilla's verdict. */
	public boolean isCriticalHit() {
		return switch (f.getResult()) {
			case ALLOW -> true;
			case DENY -> false;
			case DEFAULT -> f.isVanillaCritical();
		};
	}
}
