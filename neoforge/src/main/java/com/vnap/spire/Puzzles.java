package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A side room's small riddle, never in the way (a dead end; the floor is beaten without it), its cache rising out of
 * the floor when it is done (SpireRewards: a share once a climber). Three kinds, after the dungeons that did them well:
 * <ul>
 * <li>{@code LEVERS} — lights out on a row of four (Zelda's switch rooms): each lever flips its glyph and its
 * neighbours'; all four lit opens it. Any start of four on a row can be put right.</li>
 * <li>{@code CHIMES} — the conductor glyph plays a tune of the four crystal chimes; play it back (Simon).</li>
 * <li>{@code PLATES} — rune plates in the floor light underfoot; light them all before the first fades.</li>
 * </ul>
 */
public final class Puzzles {
	public enum Kind {
		LEVERS, CHIMES, PLATES
	}

	/** Ticks the plates stay lit once the first is stepped on. */
	static final int PLATE_TICKS = 300;
	private static final int TUNE_GAP = 14;

	/** A floor's riddle as far as it has got. */
	static final class State {
		boolean solved;
		/** The chimes played back so far; -1 before the tune was heard. */
		int heard = -1;
		/** When the first plate of a run lit. */
		long platesFrom = -1;
		final java.util.Set<java.util.UUID> toldOf = new java.util.HashSet<>();
	}

	/** What is to happen a few ticks on (a tune's notes, a cache rising), by game time; dropped with the server. */
	private static final List<Later> LATER = new java.util.ArrayList<>();

	private record Later(ServerLevel level, long at, Runnable then) {
	}

	private Puzzles() {
	}

	static void later(ServerLevel level, int ticks, Runnable then) {
		LATER.add(new Later(level, level.getGameTime() + ticks, then));
	}

	static void tick() {
		if (LATER.isEmpty()) return;
		for (Later due : List.copyOf(LATER)) {
			if (due.level().getGameTime() < due.at()) continue;
			LATER.remove(due);
			due.then().run();
		}
	}

	static void clear() {
		LATER.clear();
	}

	private static SpireSlots.@Nullable Slot slot(BlockPos pos) {
		SpireSlots.Slot slot = SpireSlots.at(pos);
		return slot == null || slot.plan.puzzle == null ? null : slot;
	}

	private static int index(SpireSlots.Slot slot, List<BlockPos> cells, BlockPos pos) {
		for (int i = 0; i < cells.size(); i++) if (slot.world(cells.get(i)).equals(pos)) return i;
		return -1;
	}

	static boolean solvedAt(ServerLevel level, BlockPos pos) {
		SpireSlots.Slot slot = slot(pos);
		return slot != null && slot.riddle.solved;
	}

	static void lever(ServerLevel level, BlockPos pos, Player player) {
		SpireSlots.Slot slot = slot(pos);
		FloorPlan.Puzzle puzzle = slot == null ? null : slot.plan.puzzle;
		if (slot == null || puzzle == null || puzzle.kind() != Kind.LEVERS || slot.riddle.solved) return;
		int i = index(slot, puzzle.parts(), pos);
		if (i < 0) return;
		for (int j = Math.max(0, i - 1); j <= Math.min(puzzle.glyphs().size() - 1, i + 1); j++) {
			BlockPos glyph = slot.world(puzzle.glyphs().get(j));
			BlockState state = level.getBlockState(glyph);
			if (state.getBlock() instanceof SpirePuzzleBlock) level.setBlock(glyph, state.setValue(SpirePuzzleBlock.LIT, !state.getValue(SpirePuzzleBlock.LIT)), 3);
		}
		level.playSound(null, slot.world(puzzle.glyphs().get(i)), com.vnap.sound.SpireSounds.GLYPH, SoundSource.BLOCKS, 0.8F, 1.0F + i * 0.1F);
		boolean all = true;
		for (BlockPos glyph : puzzle.glyphs()) {
			BlockState state = level.getBlockState(slot.world(glyph));
			all &= state.getBlock() instanceof SpirePuzzleBlock && state.getValue(SpirePuzzleBlock.LIT);
		}
		if (all) solve(level, slot, puzzle, player);
		else tell(slot, player, "levers");
	}

	/** The conductor touched: it plays the tune on the chimes, a note a while. */
	static void conductor(ServerLevel level, BlockPos pos, Player player) {
		SpireSlots.Slot slot = slot(pos);
		FloorPlan.Puzzle puzzle = slot == null ? null : slot.plan.puzzle;
		if (slot == null || puzzle == null || puzzle.kind() != Kind.CHIMES || slot.riddle.solved) return;
		slot.riddle.heard = 0;
		tell(slot, player, "chimes");
		level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.6);
		List<Integer> tune = puzzle.tune();
		for (int n = 0; n < tune.size(); n++) {
			BlockPos chime = slot.world(puzzle.parts().get(tune.get(n)));
			int delay = 10 + n * TUNE_GAP;
			later(level, delay, () -> {
				BlockState state = level.getBlockState(chime);
				if (state.getBlock() instanceof SpirePuzzleBlock) SpirePuzzleBlock.ring(level, chime, state);
			});
		}
	}

	/** A chime struck by a climber: the next of the tune, or a wrong note that starts it over. */
	static void chime(ServerLevel level, BlockPos pos, Player player) {
		SpireSlots.Slot slot = slot(pos);
		FloorPlan.Puzzle puzzle = slot == null ? null : slot.plan.puzzle;
		if (slot == null || puzzle == null || puzzle.kind() != Kind.CHIMES || slot.riddle.solved || slot.riddle.heard < 0) return;
		int i = index(slot, puzzle.parts(), pos);
		if (i < 0) return;
		if (puzzle.tune().get(slot.riddle.heard) == i) {
			if (++slot.riddle.heard == puzzle.tune().size()) solve(level, slot, puzzle, player);
			return;
		}
		slot.riddle.heard = 0;
		level.playSound(null, pos, com.vnap.sound.SpireSounds.PUZZLE_WRONG, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	static void plate(ServerLevel level, BlockPos pos, Player player) {
		SpireSlots.Slot slot = slot(pos);
		FloorPlan.Puzzle puzzle = slot == null ? null : slot.plan.puzzle;
		if (slot == null || puzzle == null || puzzle.kind() != Kind.PLATES || slot.riddle.solved) return;
		if (index(slot, puzzle.parts(), pos) < 0) return;
		long now = level.getGameTime();
		if (slot.riddle.platesFrom < 0 || now - slot.riddle.platesFrom > PLATE_TICKS) {
			slot.riddle.platesFrom = now;
			tell(slot, player, "plates");
		}
		BlockState state = level.getBlockState(pos);
		level.setBlock(pos, state.setValue(SpirePuzzleBlock.LIT, true), 3);
		// the plates go dark together when the first one's time is up
		level.scheduleTick(pos, state.getBlock(), (int) Math.max(1, PLATE_TICKS - (now - slot.riddle.platesFrom)));
		int lit = 0;
		for (BlockPos part : puzzle.parts()) {
			BlockState there = level.getBlockState(slot.world(part));
			if (there.getBlock() instanceof SpirePuzzleBlock && there.getValue(SpirePuzzleBlock.LIT)) lit++;
		}
		level.playSound(null, pos, com.vnap.sound.SpireSounds.PLATE, SoundSource.BLOCKS, 1.0F, 0.8F + lit * 0.12F);
		if (lit == puzzle.parts().size()) solve(level, slot, puzzle, player);
	}

	/** Done: a chord, light over its pieces, and its cache rises out of the floor in the middle of the room. */
	private static void solve(ServerLevel level, SpireSlots.Slot slot, FloorPlan.Puzzle puzzle, Player player) {
		slot.riddle.solved = true;
		BlockPos cache = slot.world(puzzle.cache());
		level.playSound(null, cache, com.vnap.sound.SpireSounds.PUZZLE_SOLVED, SoundSource.BLOCKS, 1.4F, 1.0F);
		for (BlockPos part : puzzle.parts()) {
			BlockPos at = slot.world(part);
			level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 10, 0.2, 0.4, 0.2, 0.05);
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, cache.getX() + 0.5, cache.getY() + 0.3, cache.getZ() + 0.5, 60, 0.4, 0.2, 0.4, 0.08);
		level.sendParticles(com.vnap.Mc.dust(0xE6B85A, 1.6F), cache.getX() + 0.5, cache.getY() + 0.5, cache.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.0);
		later(level, 16, () -> {
			level.setBlock(cache, SpireBlocks.CHEST.defaultBlockState(), 3);
			level.playSound(null, cache, com.vnap.sound.SpireSounds.CACHE, SoundSource.BLOCKS, 1.2F, 1.0F);
		});
		// everyone on the floor: each has a share of the cache to take
		for (net.minecraft.server.level.ServerPlayer on : SpireSlots.players(level, slot)) com.vnap.Mc.say(on, Component.translatable("spire.villager_life.puzzle.solved"), true);
		if (!(player instanceof net.minecraft.server.level.ServerPlayer)) com.vnap.Mc.say(player, Component.translatable("spire.villager_life.puzzle.solved"), true);
	}

	/** The first touch of a riddle says what it wants, once a climber a floor. */
	private static void tell(SpireSlots.Slot slot, Player player, String kind) {
		if (!slot.riddle.toldOf.add(player.getUUID())) return;
		com.vnap.Mc.say(player, Component.translatable("spire.villager_life.puzzle." + kind), true);
	}

	/** Whether a block of the floor is its riddle's cache (SpireRewards opens it as one). */
	static boolean cache(SpireSlots.Slot slot, BlockPos pos) {
		FloorPlan.Puzzle puzzle = slot.plan.puzzle;
		return puzzle != null && slot.world(puzzle.cache()).equals(pos);
	}
}
