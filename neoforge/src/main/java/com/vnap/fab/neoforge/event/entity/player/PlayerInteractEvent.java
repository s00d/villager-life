package com.vnap.fab.neoforge.event.entity.player;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** A player's use of a block, an item or an entity (Fabric API's Use*Callback): a cancel's result is the interaction's. */
public final class PlayerInteractEvent {
	private PlayerInteractEvent() {
	}

	/** What every interaction has: who, with which hand and item, where; a cancel's result. */
	public abstract static class Interaction extends Event implements ICancellableEvent {
		private final Player player;
		private final InteractionHand hand;
		private final BlockPos pos;
		private InteractionResult cancellationResult = InteractionResult.PASS;

		Interaction(Player player, InteractionHand hand, BlockPos pos) {
			this.player = player;
			this.hand = hand;
			this.pos = pos;
		}

		public Player getEntity() {
			return player;
		}

		public InteractionHand getHand() {
			return hand;
		}

		public ItemStack getItemStack() {
			return player.getItemInHand(hand);
		}

		public BlockPos getPos() {
			return pos;
		}

		public Level getLevel() {
			return com.vnap.Mc.levelOf(player);
		}

		public InteractionResult getCancellationResult() {
			return cancellationResult;
		}

		public void setCancellationResult(InteractionResult result) {
			cancellationResult = result;
		}
	}

	public static final class EntityInteract extends Interaction {
		private final Entity target;

		public EntityInteract(Player player, InteractionHand hand, Entity target) {
			super(player, hand, target.blockPosition());
			this.target = target;
		}

		public Entity getTarget() {
			return target;
		}
	}

	public static final class RightClickBlock extends Interaction {
		private final BlockHitResult hit;

		public RightClickBlock(Player player, InteractionHand hand, BlockHitResult hit) {
			super(player, hand, hit.getBlockPos());
			this.hit = hit;
		}

		public BlockHitResult getHitVec() {
			return hit;
		}
	}

	public static final class RightClickItem extends Interaction {
		public RightClickItem(Player player, InteractionHand hand) {
			super(player, hand, player.blockPosition());
		}
	}
}
