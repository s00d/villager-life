package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Builders' door in a doorway between two rooms: three panels wide, four high, a bronze-banded leaf with the
 * Builders' rune that lights when it is touched. A touch opens it for good (it is a door, not a lock): the rune flares,
 * then the leaf parts from the middle outwards, a column at a time, in a fall of its own dust. {@code ROW}/{@code COL}
 * say which panel of the leaf a block is (its piece of the picture); {@code OPENING} is set while it parts.
 */
public final class SpireDoorBlock extends Block {
	public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 3);
	public static final IntegerProperty COL = IntegerProperty.create("col", 0, 2);
	public static final BooleanProperty OPENING = BlockStateProperties.OPEN;
	private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);
	/** Ticks from the touch to the first column parting, and between the middle and the sides. */
	private static final int FLARE = 14;
	private static final int STEP = 7;

	public SpireDoorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROW, 0).setValue(COL, 0).setValue(OPENING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ROW, COL, OPENING);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_X : ALONG_Z;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && !state.getValue(OPENING)) open(server, pos);
		return com.vnap.Mc.success(level.isClientSide());
	}

	/** The whole leaf this panel belongs to (the panels touching it, of the same facing). */
	static List<BlockPos> leaf(BlockGetter level, BlockPos pos) {
		BlockState first = level.getBlockState(pos);
		if (!(first.getBlock() instanceof SpireDoorBlock)) return List.of();
		Direction facing = first.getValue(FACING);
		List<BlockPos> leaf = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		queue.add(pos);
		seen.add(pos);
		while (!queue.isEmpty() && leaf.size() < 24) {
			BlockPos at = queue.poll();
			leaf.add(at);
			for (Direction side : Direction.values()) {
				BlockPos next = at.relative(side);
				BlockState there = level.getBlockState(next);
				if (there.getBlock() instanceof SpireDoorBlock && there.getValue(FACING) == facing && seen.add(next)) queue.add(next);
			}
		}
		return leaf;
	}

	/** Opens the leaf: the rune flares on every panel now, the columns part later (the middle first). */
	public static void open(ServerLevel level, BlockPos pos) {
		List<BlockPos> leaf = leaf(level, pos);
		if (leaf.isEmpty()) return;
		for (BlockPos at : leaf) {
			BlockState state = level.getBlockState(at);
			level.setBlock(at, state.setValue(OPENING, true), 2);
			int col = state.getValue(COL);
			int delay = FLARE + (col == 1 ? 0 : STEP) + state.getValue(ROW) * 2;
			level.scheduleTick(at, state.getBlock(), delay);
		}
		BlockPos middle = leaf.get(0);
		level.playSound(null, middle, com.vnap.sound.SpireSounds.DOOR_UNLOCK, SoundSource.BLOCKS, 1.2F, 1.0F);
		for (BlockPos at : leaf) {
			level.sendParticles(ParticleTypes.ENCHANT, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.6);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(OPENING)) return;
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.05);
		level.sendParticles(com.vnap.Mc.dust(0xE6B85A, 1.0F), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.4, 0.3, 0.0);
		// one grind a column, from its foot
		if (state.getValue(ROW) == 0) level.playSound(null, pos, com.vnap.sound.SpireSounds.DOOR_GRIND, SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// the rune on the middle panels breathes; when it is touched it flares
		if (state.getValue(COL) != 1 || (state.getValue(ROW) != 1 && state.getValue(ROW) != 2)) return;
		if (state.getValue(OPENING) || random.nextInt(4) == 0) {
			Direction out = state.getValue(FACING);
			double x = pos.getX() + 0.5 + out.getStepX() * 0.2;
			double z = pos.getZ() + 0.5 + out.getStepZ() * 0.2;
			level.addParticle(state.getValue(OPENING) ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT, x, pos.getY() + 0.5, z, 0.0, 0.02, 0.0);
		}
	}
}
