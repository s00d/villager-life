package com.vnap.block.clock;

import com.vnap.Nbt;
import com.vnap.block.LifeBlocks;
import com.vnap.sound.GolemSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A tower clock's spring: how long it runs on, the time it stopped at, the strokes of the hour still to strike. */
public final class TowerClockBlockEntity extends BlockEntity {
	/** One winding runs it this long; it holds at most THREE windings. */
	public static final long WIND = 2 * 24000L;
	private static final long MOST = 3 * 24000L;
	/** Ticks between two strokes of the hour. */
	private static final int STROKE_GAP = 50;
	/** The hours it strikes (by day only): the first and the last. */
	static final int FIRST_HOUR = 7;
	static final int LAST_HOUR = 21;

	private long woundUntil;
	/** The day time its hands stopped at, -1 while it runs. */
	private long stoppedAt = -1L;
	private int lastHour = -1;
	private int strokes;
	private long nextStroke;

	public TowerClockBlockEntity(BlockPos pos, BlockState state) {
		super(LifeBlocks.CLOCK_BE, pos, state);
	}

	public boolean running() {
		return level != null && level.getGameTime() < woundUntil;
	}

	/** Game ticks it runs on before it needs winding again (0 when stopped). */
	public long runsFor() {
		return level == null ? 0L : Math.max(0L, woundUntil - level.getGameTime());
	}

	/** The time of day the hands show: the world's while it runs, where they stopped when it ran down. */
	public long shownTime(long dayTime) {
		return stoppedAt >= 0 ? stoppedAt : dayTime;
	}

	public void wind() {
		if (!(level instanceof ServerLevel server)) return;
		long now = server.getGameTime();
		woundUntil = Math.min(Math.max(woundUntil, now) + WIND, now + MOST);
		if (stoppedAt >= 0) {
			// started again: it shows the time again (a keeper sets the hands right while winding)
			stoppedAt = -1L;
			lastHour = hour(com.vnap.Mc.dayTime(server));
		}
		server.playSound(null, worldPosition, GolemSounds.CLOCK_WIND, SoundSource.BLOCKS, 1.0F, 1.0F);
		changed();
	}

	/** The hour of the day (0-23) at this day time: day time 0 is six in the morning. */
	public static int hour(long dayTime) {
		return (int) ((com.vnap.dialogue.Family.timeOfDay(dayTime) / 1000L + 6L) % 24L);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TowerClockBlockEntity clock) {
		if (!(level instanceof ServerLevel server)) return;
		if (!clock.running()) {
			if (clock.stoppedAt < 0) {
				clock.stoppedAt = com.vnap.dialogue.Family.timeOfDay(server);
				clock.strokes = 0;
				clock.changed();
			}
			return;
		}
		int hour = hour(com.vnap.Mc.dayTime(server));
		if (hour != clock.lastHour) {
			boolean first = clock.lastHour < 0;
			clock.lastHour = hour;
			// a tower has a dial on each side: the one with the lowest place strikes for all of them
			if (!first && hour >= FIRST_HOUR && hour <= LAST_HOUR && clock.leads(server)) {
				clock.strokes = hour % 12 == 0 ? 12 : hour % 12;
				clock.nextStroke = server.getGameTime();
			}
			clock.setChanged();
		}
		if (clock.strokes > 0 && server.getGameTime() >= clock.nextStroke) {
			clock.strokes--;
			clock.nextStroke = server.getGameTime() + STROKE_GAP;
			server.playSound(null, pos, GolemSounds.CLOCK_STRIKE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	private boolean leads(ServerLevel server) {
		for (BlockPos other : BlockPos.betweenClosed(worldPosition.offset(-6, -2, -6), worldPosition.offset(6, 2, 6))) {
			if (other.asLong() < worldPosition.asLong() && server.getBlockEntity(other) instanceof TowerClockBlockEntity clock && clock.running()) return false;
		}
		return true;
	}

	private void changed() {
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	@Override
	//? if >=1.21.6 {
	/*protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		CompoundTag tag = new CompoundTag();
	*///?} else {
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
	//?}
		tag.putLong("WoundUntil", woundUntil);
		tag.putLong("StoppedAt", stoppedAt);
		//? if >=1.21.6
		/*com.vnap.Mc.put(output, tag);*/
	}

	@Override
	//? if >=1.21.6 {
	/*protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input);
		CompoundTag tag = com.vnap.Mc.tagOf(input);
	*///?} else {
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
	//?}
		woundUntil = Nbt.getLong(tag, "WoundUntil");
		stoppedAt = tag.contains("StoppedAt") ? Nbt.getLong(tag, "StoppedAt") : -1L;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** What its renderer draws: the dial over the wall around it. */
	public net.minecraft.world.phys.AABB renderBox() {
		return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1.5);
	}
	//? if forge {

	/*@Override
	public net.minecraft.world.phys.AABB getRenderBoundingBox() {
		return renderBox();
	}
	*///?}
}
