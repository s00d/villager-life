package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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

/**
 * The pieces of a floor's small riddles (Puzzles): a brass lever on the wall, a glyph stone that lights, a crystal
 * chime on its stand (four notes, {@code NOTE}), a rune plate in the floor that lights underfoot. Each answers a touch
 * (or a step) with its own sound and light and tells the floor's riddle, if it belongs to one; a chime outside a
 * riddle simply rings.
 */
public final class SpirePuzzleBlock extends Block {
	public enum Kind {
		LEVER, GLYPH, CHIME, PLATE
	}

	public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, 3);
	/** The four notes of the chimes (pitches of one struck crystal): a minor chord and its octave. */
	static final float[] PITCH = {0.75F, 0.89F, 1.12F, 1.5F};
	private final Kind kind;

	public SpirePuzzleBlock(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(NOTE, 0));
	}

	public Kind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, NOTE);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, kind == Kind.LEVER || kind == Kind.GLYPH ? context.getClickedFace() : context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (kind) {
			case CHIME -> Block.box(2, 0, 2, 14, 16, 14);
			case PLATE -> Block.box(1, 0, 1, 15, 1, 15);
			default -> switch (state.getValue(FACING)) {
				case NORTH -> Block.box(3, 2, 13, 13, 14, 16);
				case SOUTH -> Block.box(3, 2, 0, 13, 14, 3);
				case WEST -> Block.box(13, 2, 3, 16, 14, 13);
				default -> Block.box(0, 2, 3, 3, 14, 13);
			};
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return kind == Kind.PLATE ? net.minecraft.world.phys.shapes.Shapes.empty() : getShape(state, level, pos, context);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) return com.vnap.Mc.success(true);
		switch (kind) {
			case LEVER -> {
				server.setBlock(pos, state.setValue(LIT, !state.getValue(LIT)), 3);
				server.playSound(null, pos, com.vnap.sound.SpireSounds.LEVER, SoundSource.BLOCKS, 1.0F, state.getValue(LIT) ? 0.8F : 1.1F);
				Puzzles.lever(server, pos, player);
			}
			case CHIME -> {
				ring(server, pos, state);
				Puzzles.chime(server, pos, player);
			}
			case GLYPH -> Puzzles.conductor(server, pos, player);
			case PLATE -> {
			}
		}
		return com.vnap.Mc.success(false);
	}

	/** A chime struck: its note, its light for a moment, a puff of its colour. */
	static void ring(ServerLevel level, BlockPos pos, BlockState state) {
		int note = state.getValue(NOTE);
		level.playSound(null, pos, com.vnap.sound.SpireSounds.CHIME, SoundSource.BLOCKS, 1.2F, PITCH[note]);
		level.setBlock(pos, state.setValue(LIT, true), 2);
		level.scheduleTick(pos, state.getBlock(), 12);
		level.sendParticles(com.vnap.Mc.dust(colour(note), 1.2F), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.2, 0.3, 0.2, 0.0);
		level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 0, note / 4.0, 0.0, 0.0, 1.0);
	}

	static int colour(int note) {
		return switch (note) {
			case 0 -> 0x7FB8FF;
			case 1 -> 0x9BE08F;
			case 2 -> 0xE6B85A;
			default -> 0xE07FC0;
		};
	}

	@Override
	//? if >=1.21.11 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean precise) {
	*///?} else if >=1.21.5 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects) {
	*///?} else
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		// a plate is walked over (no collision): stepping into its cell is the step
		if (kind == Kind.PLATE && entity instanceof Player player && level instanceof ServerLevel server && !state.getValue(LIT)) {
			Puzzles.plate(server, pos, player);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// a chime's light dies down after its note; a plate's when its riddle was not done in time
		if (kind == Kind.CHIME || (kind == Kind.PLATE && !Puzzles.solvedAt(level, pos))) level.setBlock(pos, state.setValue(LIT, false), 2);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT) || random.nextInt(3) != 0) return;
		double y = kind == Kind.PLATE ? pos.getY() + 0.1 : pos.getY() + 0.6;
		level.addParticle(kind == Kind.CHIME ? ParticleTypes.END_ROD : ParticleTypes.ENCHANT, pos.getX() + 0.5, y, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
	}
}
