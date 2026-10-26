package com.vnap.neo.neoforge.event.entity.living;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

public final class FinalizeSpawnEvent extends Event implements ICancellableEvent {
	static {
		Bridge.bind(FinalizeSpawnEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn.class, priority,
				FinalizeSpawnEvent::new, sink));
	}

	private final net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn f;

	FinalizeSpawnEvent(net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn f) {
		super(f);
		this.f = f;
	}

	public Mob getEntity() {
		return f.getEntity();
	}

	public ServerLevelAccessor getLevel() {
		return f.getLevel();
	}

	public double getX() {
		return f.getX();
	}

	public double getY() {
		return f.getY();
	}

	public double getZ() {
		return f.getZ();
	}

	public MobSpawnType getSpawnType() {
		//? if >=1.21.2 {
		/*return f.getSpawnReason();
		*///?} else
		return f.getSpawnType();
	}

	public void setSpawnCancelled(boolean cancel) {
		f.setSpawnCancelled(cancel);
	}
}
