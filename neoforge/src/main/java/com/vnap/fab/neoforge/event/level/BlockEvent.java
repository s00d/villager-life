package com.vnap.fab.neoforge.event.level;

import com.vnap.fab.bus.api.Event;
import com.vnap.fab.bus.api.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A block broken by a player (Fabric API PlayerBlockBreakEvents.BEFORE) or set down by an entity (com.vnap.fab.mixin.BlockPlaceMixin). */
public final class BlockEvent {
	private BlockEvent() {
	}

	public static final class BreakEvent extends Event implements ICancellableEvent {
		private final Level level;
		private final BlockPos pos;
		private final BlockState state;
		private final Player player;

		public BreakEvent(Level level, BlockPos pos, BlockState state, Player player) {
			this.level = level;
			this.pos = pos;
			this.state = state;
			this.player = player;
		}

		public LevelAccessor getLevel() {
			return level;
		}

		public BlockPos getPos() {
			return pos;
		}

		public BlockState getState() {
			return state;
		}

		public Player getPlayer() {
			return player;
		}
	}

	public static final class EntityPlaceEvent extends Event implements ICancellableEvent {
		private final com.vnap.fab.neoforge.common.util.BlockSnapshot snapshot;
		private final BlockState placedAgainst;
		private final @Nullable Entity entity;

		public EntityPlaceEvent(com.vnap.fab.neoforge.common.util.BlockSnapshot snapshot, BlockState placedAgainst, @Nullable Entity entity) {
			this.snapshot = snapshot;
			this.placedAgainst = placedAgainst;
			this.entity = entity;
		}

		public LevelAccessor getLevel() {
			return snapshot.getLevel();
		}

		public BlockPos getPos() {
			return snapshot.getPos();
		}

		public BlockState getState() {
			return getPlacedBlock();
		}

		public BlockState getPlacedBlock() {
			return snapshot.getLevel().getBlockState(snapshot.getPos());
		}

		public BlockState getPlacedAgainst() {
			return placedAgainst;
		}

		public @Nullable Entity getEntity() {
			return entity;
		}
	}
}
