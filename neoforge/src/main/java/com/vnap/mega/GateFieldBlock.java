package com.vnap.mega;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The field of a whole, keyed, linked Waygate: a thin sheet in the frame's plane in its keystone's colour, with motes
 * of it drifting; a player who walks in comes out in front of the other gate (Waygates.cross). It is the gate's own:
 * a field without its gate (the frame broken, the key taken) goes at its next look.
 */
public final class GateFieldBlock extends Block {
	public static final IntegerProperty COLOUR = IntegerProperty.create("colour", 0, 15);
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);

	public GateFieldBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(COLOUR, 0).setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(COLOUR, AXIS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
	}

	@Override
	//? if >=1.21.11 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean precise) {
	*///?} else if >=1.21.5 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects) {
	*///?} else
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (level instanceof ServerLevel server && entity instanceof ServerPlayer player && !player.isPassenger()) Waygates.cross(server, player, pos);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		if (!level.isClientSide()) level.scheduleTick(pos, this, MegaControllerBlockEntity.LOOK_EVERY * 2);
	}

	/** Still its gate's? Else it goes (a frame broken while nobody looked keeps no field of its own). */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		MegaControllerBlockEntity gate = Waygates.gateOf(level, pos);
		if (gate == null || !gate.formed() || gate.key() == null) {
			level.removeBlock(pos, false);
			return;
		}
		level.scheduleTick(pos, this, MegaControllerBlockEntity.LOOK_EVERY * 2);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int rgb = DyeColor.byId(state.getValue(COLOUR)).getTextureDiffuseColor();
		for (int i = 0; i < 2; i++) {
			boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
			double x = pos.getX() + (alongX ? random.nextDouble() : 0.5);
			double z = pos.getZ() + (alongX ? 0.5 : random.nextDouble());
			level.addParticle(com.vnap.Mc.dust(rgb & 0xFFFFFF, 1.0F), x, pos.getY() + random.nextDouble(), z, 0.0, 0.02, 0.0);
		}
	}
}
