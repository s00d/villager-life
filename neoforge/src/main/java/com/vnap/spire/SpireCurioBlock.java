package com.vnap.spire;

import com.vnap.sound.SpireSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Things the Builders left in a floor's rooms to be touched and watched — nothing hangs on them, they only answer: a
 * gong in the roots, a lectern of whispers in the archive, a crank that turns the workshop's cogs, buds in the
 * gardens, a valve in the galleries, puffballs in the spores, a honey tap in the hive, an orrery in the observatory, a
 * resonating stone at the summit. {@code FRAME} is the picture it shows (a disk swung, a page turned, a cog a quarter
 * round); a touch runs its frames on scheduled ticks with its particles and sound.
 */
public final class SpireCurioBlock extends Block {
	public enum Kind {
		GONG(24, false), LECTERN(30, false), CRANK(60, false), COG(0, true), BUD(600, false), VALVE(80, true), PUFFBALL(400, false),
		HONEY_TAP(60, true), ORRERY(100, false), RESONATOR(60, false);

		/** Ticks a touch keeps it going (a bud stays open this long, a puffball takes this long to grow back). */
		final int runs;
		final boolean onWall;

		Kind(int runs, boolean onWall) {
			this.runs = runs;
			this.onWall = onWall;
		}

		SoundEvent sound() {
			return switch (this) {
				case GONG -> SpireSounds.GONG;
				case LECTERN -> SpireSounds.PAGES;
				case CRANK, COG -> SpireSounds.GEARS;
				case BUD -> SpireSounds.BLOOM;
				case VALVE -> SpireSounds.VALVE;
				case PUFFBALL -> SpireSounds.PUFF;
				case HONEY_TAP -> SpireSounds.HONEY_DRIP;
				case ORRERY -> SpireSounds.ORRERY;
				case RESONATOR -> SpireSounds.RESONATOR;
			};
		}
	}

	public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty FRAME = IntegerProperty.create("frame", 0, 3);
	/** When each running curio stops (server side, by place; nothing to keep over a restart: a floor is drawn anew). */
	private static final Map<BlockPos, Long> UNTIL = new HashMap<>();
	private static final int LECTERN_LINES = 12;
	private final Kind kind;

	public SpireCurioBlock(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FRAME, 0));
	}

	public Kind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, FRAME);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, kind.onWall ? context.getClickedFace() : context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (kind) {
			case GONG -> Block.box(0, 0, 5, 16, 16, 11);
			case LECTERN -> Block.box(2, 0, 3, 14, 14, 13);
			case CRANK -> Block.box(2, 0, 2, 14, 16, 14);
			case BUD -> Block.box(1, 0, 1, 15, 16, 15);
			case PUFFBALL -> Block.box(2, 0, 2, 14, 12, 14);
			case ORRERY -> Block.box(0, 0, 0, 16, 15, 16);
			case RESONATOR -> Block.box(1, 0, 1, 15, 16, 15);
			case COG, VALVE, HONEY_TAP -> wall(state.getValue(FACING));
		};
	}

	private static VoxelShape wall(Direction facing) {
		return switch (facing) {
			case NORTH -> Block.box(1, 0, 5, 15, 16, 16);
			case SOUTH -> Block.box(1, 0, 0, 15, 16, 11);
			case WEST -> Block.box(5, 0, 1, 16, 16, 15);
			default -> Block.box(0, 0, 1, 11, 16, 15);
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && kind != Kind.COG) touch(server, pos, player);
		return com.vnap.Mc.success(level.isClientSide());
	}

	private void touch(ServerLevel level, BlockPos pos, Player player) {
		Long running = UNTIL.get(pos);
		// a bud open or a puffball spent answers nothing till it is back; the rest can be touched again
		if (running != null && running > level.getGameTime() && (kind == Kind.BUD || kind == Kind.PUFFBALL)) return;
		UNTIL.put(pos.immutable(), level.getGameTime() + kind.runs);
		level.playSound(null, pos, kind.sound(), SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		switch (kind) {
			case LECTERN -> com.vnap.Mc.say(player, Component.translatable("spire.villager_life.curio.lectern." + level.getRandom().nextInt(LECTERN_LINES)), true);
			case CRANK -> {
				// the cogs on the walls round it turn while it does
				for (BlockPos at : BlockPos.betweenClosed(pos.offset(-7, -1, -7), pos.offset(7, 5, 7))) {
					BlockState there = level.getBlockState(at);
					if (there.getBlock() instanceof SpireCurioBlock cog && cog.kind == Kind.COG) {
						UNTIL.put(at.immutable(), level.getGameTime() + kind.runs);
						level.scheduleTick(at.immutable(), cog, 2);
					}
				}
			}
			case PUFFBALL -> {
				level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 60, 0.6, 0.5, 0.6, 0.02);
				level.sendParticles(com.vnap.Mc.dust(0xC9B8E8, 1.4F), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 40, 0.5, 0.6, 0.5, 0.0);
			}
			case GONG -> ring(level, pos, ParticleTypes.WAX_OFF, 1.5, 24);
			case RESONATOR -> ring(level, pos, ParticleTypes.END_ROD, 2.5, 40);
			default -> {
			}
		}
		level.scheduleTick(pos, this, 2);
	}

	/** A ring of motes round it, widening as they fly (a gong's boom, the summit stone's hum). */
	private static void ring(ServerLevel level, BlockPos pos, ParticleOptions mote, double radius, int count) {
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count;
			level.sendParticles(mote, pos.getX() + 0.5 + Math.cos(a) * 0.5, pos.getY() + 0.9, pos.getZ() + 0.5 + Math.sin(a) * 0.5, 0,
					Math.cos(a) * radius * 0.1, 0.01, Math.sin(a) * radius * 0.1, 1.0);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		long now = level.getGameTime();
		Long until = UNTIL.get(pos);
		long left = until == null ? 0 : until - now;
		boolean going = left > 0;
		int frame = state.getValue(FRAME);
		int next = switch (kind) {
			// a bud opens over three frames and stays so; a puffball shows its deflated frame, then grows back
			case BUD -> going ? Math.min(3, frame + 1) : Math.max(0, frame - 1);
			case PUFFBALL -> going ? (left > kind.runs - 10 ? 1 : left > kind.runs / 2 ? 2 : 3) : 0;
			case HONEY_TAP -> going ? 1 : 0;
			case GONG, LECTERN, RESONATOR -> going ? 1 + (frame % 3) : 0;
			default -> going ? (frame + 1) % 4 : frame;
		};
		if (next != frame) level.setBlock(pos, state.setValue(FRAME, next), 2);
		if (going) {
			effects(level, pos, state, random);
			level.scheduleTick(pos, this, kind == Kind.BUD ? 4 : kind == Kind.LECTERN ? 6 : 2);
		} else {
			UNTIL.remove(pos);
			if (next != 0 && kind == Kind.BUD) level.scheduleTick(pos, this, 4);
		}
	}

	private void effects(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		Direction out = state.getValue(FACING);
		switch (kind) {
			case LECTERN -> level.sendParticles(ParticleTypes.ENCHANT, x, y + 1.2, z, 6, 1.2, 0.6, 1.2, 0.8);
			case CRANK, COG -> {
				if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 2, 0.25, 0.25, 0.25, 0.05);
			}
			case BUD -> {
				if (random.nextInt(3) == 0) level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y + 0.6, z, 2, 0.3, 0.2, 0.3, 0.0);
			}
			case VALVE -> {
				// water bursts out of the pipe's mouth under the wheel
				level.sendParticles(ParticleTypes.SPLASH, x + out.getStepX() * 0.4, y - 0.2, z + out.getStepZ() * 0.4, 8, 0.1, 0.05, 0.1, 0.2);
				level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x + out.getStepX() * 0.5, y - 0.3, z + out.getStepZ() * 0.5, 2, 0.1, 0.0, 0.1, 0.05);
			}
			case HONEY_TAP -> level.sendParticles(ParticleTypes.DRIPPING_HONEY, x + out.getStepX() * 0.3, y - 0.2, z + out.getStepZ() * 0.3, 1, 0.02, 0.0, 0.02, 0.0);
			case ORRERY -> {
				double a = level.getGameTime() * 0.25;
				for (int i = 0; i < 3; i++) {
					double r = 0.6 + i * 0.35;
					double b = a * (1.0 - i * 0.25) + i * 2.1;
					level.sendParticles(i == 0 ? ParticleTypes.END_ROD : com.vnap.Mc.dust(i == 1 ? 0x8FB8FF : 0xE6B85A, 1.0F),
							x + Math.cos(b) * r, y + 0.7 + i * 0.1, z + Math.sin(b) * r, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
			case RESONATOR -> {
				if (random.nextInt(2) == 0) level.sendParticles(ParticleTypes.END_ROD, x, y + 0.8, z, 1, 0.2, 0.4, 0.2, 0.01);
			}
			default -> {
			}
		}
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// at rest: a little life of its own, so it is seen to be something
		if (random.nextInt(kind == Kind.RESONATOR ? 4 : 10) != 0) return;
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		switch (kind) {
			case RESONATOR -> level.addParticle(ParticleTypes.END_ROD, x, pos.getY() + 1.05, z, 0.0, 0.01, 0.0);
			case LECTERN -> level.addParticle(ParticleTypes.ENCHANT, x, pos.getY() + 1.4, z, 0.0, -0.3, 0.0);
			case PUFFBALL -> {
				if (state.getValue(FRAME) == 0) level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, pos.getY() + 0.8, z, 0.0, 0.0, 0.0);
			}
			case HONEY_TAP -> level.addParticle(ParticleTypes.DRIPPING_HONEY, x, pos.getY() + 0.3, z, 0.0, 0.0, 0.0);
			default -> {
			}
		}
	}
}
