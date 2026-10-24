package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The wall laid across a secret room's doorway (FloorGenerator.secret): the one thing in the spire a climber may
 * break. It shows itself to the watchful — a darker course, a crack with a glint in it, a little dust now and then —
 * and the whole of it comes down at the first blow.
 */
public final class SpireCrackedWallBlock extends Block {
	private static final int MOST = 32;

	public SpireCrackedWallBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(24) != 0) return;
		level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
				pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) crumble(server, pos, state);
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** The rest of the wall comes down with the block broken. */
	static void crumble(ServerLevel level, BlockPos from, BlockState state) {
		Deque<BlockPos> open = new ArrayDeque<>();
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		open.add(from);
		seen.add(from);
		while (!open.isEmpty() && seen.size() < MOST) {
			BlockPos at = open.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = at.relative(direction);
				if (seen.add(next) && level.getBlockState(next).is(state.getBlock())) open.add(next);
			}
			if (!at.equals(from)) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(at)), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5,
						12, 0.3, 0.3, 0.3, 0.05);
				level.removeBlock(at, false);
			}
		}
		level.playSound(null, from, com.vnap.sound.SpireSounds.SECRET_BREAK, SoundSource.BLOCKS, 1.4F, 1.0F);
	}
}
