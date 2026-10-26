package com.vnap.neo.neoforge.event.entity.player;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

public final class PlayerInteractEvent {
	private PlayerInteractEvent() {
	}

	/** What every interaction has: who, with which hand and item, where; a cancel's result. */
	public abstract static class Interaction extends Event implements ICancellableEvent {
		private final net.minecraftforge.event.entity.player.PlayerInteractEvent f;

		Interaction(net.minecraftforge.event.entity.player.PlayerInteractEvent f) {
			super(f);
			this.f = f;
		}

		public Player getEntity() {
			return f.getEntity();
		}

		public InteractionHand getHand() {
			return f.getHand();
		}

		public ItemStack getItemStack() {
			return f.getItemStack();
		}

		public BlockPos getPos() {
			return f.getPos();
		}

		public Level getLevel() {
			return f.getLevel();
		}

		public InteractionResult getCancellationResult() {
			return f.getCancellationResult();
		}

		public void setCancellationResult(InteractionResult result) {
			f.setCancellationResult(result);
		}
	}

	//? if >=26.1 {
	/*/^* From 26.1 the one interaction with an entity (vanilla's at-a-point one merged into it): Forge's EntityInteractSpecific. ^/
	public static final class EntityInteract extends Interaction {
		static {
			Bridge.bind(EntityInteract.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific.class, priority,
					EntityInteract::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific f;

		EntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific f) {
			super(f);
			this.f = f;
		}

		public Entity getTarget() {
			return f.getTarget();
		}
	}
	*///?} else {
	public static final class EntityInteract extends Interaction {
		static {
			Bridge.bind(EntityInteract.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, priority,
					EntityInteract::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract f;

		EntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract f) {
			super(f);
			this.f = f;
		}

		public Entity getTarget() {
			return f.getTarget();
		}
	}
	//?}

	public static final class RightClickBlock extends Interaction {
		static {
			Bridge.bind(RightClickBlock.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, priority,
					RightClickBlock::new, sink));
		}

		private final net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock f;

		RightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock f) {
			super(f);
			this.f = f;
		}

		public BlockHitResult getHitVec() {
			return f.getHitVec();
		}
	}

	public static final class RightClickItem extends Interaction {
		static {
			Bridge.bind(RightClickItem.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem.class, priority,
					RightClickItem::new, sink));
		}

		RightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem f) {
			super(f);
		}
	}
}
