package com.vnap.neo.neoforge.event.entity;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class EntityTeleportEvent {
	private EntityTeleportEvent() {
	}

	public static final class EnderPearl extends Event implements ICancellableEvent {
		static {
			Bridge.bind(EnderPearl.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.EntityTeleportEvent.EnderPearl.class, priority, EnderPearl::new, sink));
		}

		private final net.minecraftforge.event.entity.EntityTeleportEvent.EnderPearl f;

		EnderPearl(net.minecraftforge.event.entity.EntityTeleportEvent.EnderPearl f) {
			super(f);
			this.f = f;
		}

		public ServerPlayer getPlayer() {
			return f.getPlayer();
		}

		public Entity getEntity() {
			return f.getEntity();
		}
	}

	/** A teleport by eating (NeoForge's ItemConsumption from 1.21.2, its ChorusFruit before; Forge's ChorusFruit). */
	//? if >=1.21.2 {
	/*public static final class ItemConsumption extends Event implements ICancellableEvent {
	*///?} else
	public static final class ChorusFruit extends Event implements ICancellableEvent {
		static {
			Bridge.bind(EntityTeleportEvent.ChorusFruit.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.EntityTeleportEvent.ChorusFruit.class, priority,
					EntityTeleportEvent.ChorusFruit::new, sink));
		}

		private final net.minecraftforge.event.entity.EntityTeleportEvent.ChorusFruit f;

		//? if >=1.21.2 {
		/*ItemConsumption(net.minecraftforge.event.entity.EntityTeleportEvent.ChorusFruit f) {
		*///?} else
		ChorusFruit(net.minecraftforge.event.entity.EntityTeleportEvent.ChorusFruit f) {
			super(f);
			this.f = f;
		}

		public LivingEntity getEntityLiving() {
			return f.getEntityLiving();
		}

		public Entity getEntity() {
			return f.getEntity();
		}
	}
}
