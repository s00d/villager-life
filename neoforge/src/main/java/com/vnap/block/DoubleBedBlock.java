package com.vnap.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A family's double bed: two beds side by side under one frame, each a real bed (a villager's home, a player's
 * spawn point), so husband and wife sleep next to each other and a player may lie down in either half. It is placed
 * whole (the right half goes in beside the left); a half broken gives a bed back and leaves the other half a single bed.
 * A plain block model instead of the vanilla bed renderer.
 */
public final class DoubleBedBlock extends BedBlock {
	public static final EnumProperty<Side> SIDE = EnumProperty.create("side", Side.class);

	public enum Side implements StringRepresentable {
		LEFT, RIGHT;

		@Override
		public String getSerializedName() {
			return this == LEFT ? "left" : "right";
		}
	}

	public DoubleBedBlock(Properties properties) {
		super(DyeColor.RED, properties);
		registerDefaultState(defaultBlockState().setValue(SIDE, Side.LEFT));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(SIDE);
	}

	/** Where the other half lies: to the right of a bed's left half as one lies in it, head first. */
	public static Direction toOther(BlockState state) {
		Direction facing = state.getValue(FACING);
		return state.getValue(SIDE) == Side.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		if (state == null) return null;
		Level level = context.getLevel();
		BlockPos foot = context.getClickedPos();
		Direction facing = state.getValue(FACING);
		BlockPos side = foot.relative(facing.getClockWise());
		boolean room = level.getBlockState(side).canBeReplaced(context) && level.getBlockState(side.relative(facing)).canBeReplaced(context)
				&& level.getWorldBorder().isWithinBounds(side) && level.getWorldBorder().isWithinBounds(side.relative(facing));
		return room ? state.setValue(SIDE, Side.LEFT) : null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) return;
		Direction facing = state.getValue(FACING);
		BlockPos foot = pos.relative(facing.getClockWise());
		BlockState right = state.setValue(SIDE, Side.RIGHT).setValue(PART, BedPart.FOOT);
		level.setBlock(foot, right, Block.UPDATE_ALL);
		level.setBlock(foot.relative(facing), right.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
		level.updateNeighborsAt(pos, Blocks.AIR);
		state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
	}

	/**
	 * A half taken away (broken, blown up) leaves the other half a single bed, still its sleeper's (the same home to
	 * villagers). Only on removal: while a double bed is put together (a build sets one half after the other) a half
	 * without its other half yet stays as it is.
	 */
	//? if >=1.21.5 {
	/*@Override
	protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!level.getBlockState(pos).is(this)) {
	*///?} else {
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!level.isClientSide() && !newState.is(this)) {
	//?}
			BlockPos other = pos.relative(toOther(state));
			Direction facing = state.getValue(FACING);
			for (BlockPos cell : new BlockPos[]{other, other.relative(state.getValue(PART) == BedPart.FOOT ? facing : facing.getOpposite())}) {
				BlockState there = level.getBlockState(cell);
				if (!there.is(this) || there.getValue(SIDE) == state.getValue(SIDE)) continue;
				// both ends at once, without shape updates: a single bed's end next to a double bed's would break
				level.setBlock(cell, Blocks.RED_BED.defaultBlockState().setValue(FACING, there.getValue(FACING))
						.setValue(PART, there.getValue(PART)).setValue(OCCUPIED, there.getValue(OCCUPIED)), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		//? if >=1.21.5 {
		/*super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		*///?} else
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	// a bed is no block entity from 26.2
	//? if <26.2
	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return null;
	}

	/**
	 * Both halves' heads are homes to villagers, as any bed's head is (vanilla lists only its own beds): put in the
	 * point-of-interest map once the registries stand.
	 */
	public static void registerHome(Block block) {
		Holder<PoiType> home = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.HOME);
		Map<BlockState, Holder<PoiType>> map = net.neoforged.neoforge.registries.GameData.getBlockStatePointOfInterestTypeMap();
		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			if (state.getValue(PART) == BedPart.HEAD) map.putIfAbsent(state, home);
		}
	}

	/** Makes the two single beds side by side a double bed in place: the same cells, the same sleepers (ServerLevel only). */
	public static boolean join(ServerLevel level, Block block, BlockPos leftHead, BlockPos rightHead, Direction facing) {
		BlockPos leftFoot = leftHead.relative(facing.getOpposite());
		BlockPos rightFoot = rightHead.relative(facing.getOpposite());
		BlockState left = block.defaultBlockState().setValue(FACING, facing).setValue(SIDE, Side.LEFT);
		BlockState right = left.setValue(SIDE, Side.RIGHT);
		// no shape updates while half of it is set: a single bed's half would break next to a double bed's
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		level.setBlock(leftFoot, left.setValue(PART, BedPart.FOOT), flags);
		level.setBlock(leftHead, left.setValue(PART, BedPart.HEAD), flags);
		level.setBlock(rightFoot, right.setValue(PART, BedPart.FOOT), flags);
		level.setBlock(rightHead, right.setValue(PART, BedPart.HEAD), flags);
		return true;
	}
}
