package com.vnap.fab.neoforge.event.tick;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.world.entity.Entity;

/** Around an entity's tick in a level's loop and a passenger's (com.vnap.fab.mixin.EntityTickMixin and the levels'). */
public final class EntityTickEvent {
	private EntityTickEvent() {
	}

	/** Whether the entity ticks: false when a listener cancelled it. */
	public static boolean around(Entity entity, Runnable tick) {
		if (Bridge.fire(new Pre(entity)).vnap$canceled()) return false;
		tick.run();
		Bridge.fire(new Post(entity));
		return true;
	}

	public static final class Pre extends Event implements ICancellableEvent {

		private final Entity entity;

		Pre(Entity entity) {
			this.entity = entity;
		}

		public Entity getEntity() {
			return entity;
		}
	}

	public static final class Post extends Event {

		private final Entity entity;

		Post(Entity entity) {
			this.entity = entity;
		}

		public Entity getEntity() {
			return entity;
		}
	}
}
