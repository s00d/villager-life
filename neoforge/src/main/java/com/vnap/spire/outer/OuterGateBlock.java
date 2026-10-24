package com.vnap.spire.outer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An Outer Gate's portal in the hall at a spire's foot (the Colosseum's in the west wall, the Sky Path's in the east,
 * the Nameless's in the south), each its colour; or a run's way out. Walked into, it takes a player in (Outer.enter)
 * or out (Outer.leave).
 */
public final class OuterGateBlock extends Block {
	public enum Kind implements StringRepresentable {
		ARENA, ISLES, RUSH, EXIT;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10);
	private static final VoxelShape ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);

	public OuterGateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(KIND, Kind.EXIT).setValue(AXIS, Direction.Axis.X));
	}

	public static BlockState of(Kind kind, Direction.Axis axis) {
		return OuterBlocks.GATE.defaultBlockState().setValue(KIND, kind).setValue(AXIS, axis);
	}

	static BlockState exit(Direction.Axis axis) {
		return of(Kind.EXIT, axis);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(KIND, AXIS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	//? if >=1.21.11 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean precise) {
	*///?} else if >=1.21.5 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects) {
	*///?} else
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player)) return;
		switch (state.getValue(KIND)) {
			case ARENA -> Outer.enter(player, Outer.Kind.ARENA, pos);
			case ISLES -> Outer.enter(player, Outer.Kind.ISLES, pos);
			case RUSH -> Outer.enter(player, Outer.Kind.RUSH, pos);
			case EXIT -> Outer.leave(player);
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
		int colour = switch (state.getValue(KIND)) {
			case ARENA -> 0xE0503A;
			case ISLES -> 0x8AD0F4;
			case RUSH -> 0xB884EC;
			case EXIT -> 0xF0E6C8;
		};
		for (int i = 0; i < 2; i++) {
			level.addParticle(com.vnap.Mc.dust(colour, 1.0F), pos.getX() + (alongX ? random.nextDouble() : 0.5), pos.getY() + random.nextDouble(),
					pos.getZ() + (alongX ? 0.5 : random.nextDouble()), 0.0, 0.02, 0.0);
		}
		if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
	}
}
