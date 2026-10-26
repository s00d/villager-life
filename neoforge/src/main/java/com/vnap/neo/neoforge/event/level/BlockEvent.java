package com.vnap.neo.neoforge.event.level;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import com.vnap.neo.bus.api.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class BlockEvent {
	private BlockEvent() {
	}

	public static final class BreakEvent extends Event implements ICancellableEvent {
		static {
			Bridge.bind(BreakEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.level.BlockEvent.BreakEvent.class, priority, BreakEvent::new, sink));
		}

		private final net.minecraftforge.event.level.BlockEvent.BreakEvent f;

		BreakEvent(net.minecraftforge.event.level.BlockEvent.BreakEvent f) {
			super(f);
			this.f = f;
		}

		public BreakEvent(Level level, BlockPos pos, BlockState state, Player player) {
			//? if >=1.21.5 {
			/*this(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, player, net.minecraftforge.common.util.Result.DEFAULT));
			*///?} else
			this(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, state, player));
		}

		public LevelAccessor getLevel() {
			return f.getLevel();
		}

		public BlockPos getPos() {
			return f.getPos();
		}

		public BlockState getState() {
			return f.getState();
		}

		public Player getPlayer() {
			return f.getPlayer();
		}
	}

	public static final class EntityPlaceEvent extends Event implements ICancellableEvent {
		static {
			Bridge.bind(EntityPlaceEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent.class, priority,
					EntityPlaceEvent::new, sink));
		}

		private final net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent f;

		EntityPlaceEvent(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent f) {
			super(f);
			this.f = f;
		}

		public EntityPlaceEvent(net.minecraftforge.common.util.BlockSnapshot snapshot, BlockState placedAgainst, @Nullable Entity entity) {
			this(new net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent(snapshot, placedAgainst, entity));
		}

		public LevelAccessor getLevel() {
			return f.getLevel();
		}

		public BlockPos getPos() {
			return f.getPos();
		}

		public BlockState getState() {
			return f.getState();
		}

		public BlockState getPlacedBlock() {
			return f.getPlacedBlock();
		}

		public BlockState getPlacedAgainst() {
			return f.getPlacedAgainst();
		}

		public @Nullable Entity getEntity() {
			return f.getEntity();
		}
	}
}
