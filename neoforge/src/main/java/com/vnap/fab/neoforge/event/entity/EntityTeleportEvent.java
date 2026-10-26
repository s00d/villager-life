package com.vnap.fab.neoforge.event.entity;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** A teleport by a thrown pearl or by eating (com.vnap.fab.mixin.TeleportMixin); a cancel keeps the entity where it is. */
public final class EntityTeleportEvent {
	private EntityTeleportEvent() {
	}

	public static final class EnderPearl extends Event implements ICancellableEvent {
		private final ServerPlayer player;

		public EnderPearl(ServerPlayer player) {
			this.player = player;
		}

		public ServerPlayer getPlayer() {
			return player;
		}

		public Entity getEntity() {
			return player;
		}
	}

	/** A teleport by eating (NeoForge's ItemConsumption from 1.21.2, its ChorusFruit before). */
	//? if >=1.21.2 {
	/*public static final class ItemConsumption extends Event implements ICancellableEvent {
	*///?} else
	public static final class ChorusFruit extends Event implements ICancellableEvent {
		private final LivingEntity entity;

		//? if >=1.21.2 {
		/*public ItemConsumption(LivingEntity entity) {
		*///?} else
		public ChorusFruit(LivingEntity entity) {
			this.entity = entity;
		}

		public LivingEntity getEntityLiving() {
			return entity;
		}

		public Entity getEntity() {
			return entity;
		}
	}
}
