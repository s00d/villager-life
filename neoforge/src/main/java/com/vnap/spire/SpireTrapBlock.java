package com.vnap.spire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Builders' traps on a floor's way (FloorGenerator.traps), seen by who looks and never without a warning:
 * <ul>
 * <li>{@code SPIKES} — a plate in the floor pocked with holes: stepped on, it clicks, and half a second later spikes
 * come up through the holes for a moment (whoever still stands on it is hurt) and sink again.</li>
 * <li>{@code DART} — a slit stone in a wall across a passage: someone in front of it, it clicks, puffs a little
 * dust and looses a dart along its line; then a while to wind again.</li>
 * </ul>
 * Their blows grow with the floor as the guards' do (SpireGuards.damage). {@code STAGE} 0 waits, 1 is the click, 2 is
 * the spikes out (or the dart winding).
 */
public final class SpireTrapBlock extends Block {
	public enum Kind {
		SPIKES, DART
	}

	public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
	/** How far down its line a dart wall sees and shoots. */
	static final int DART_RANGE = 9;
	private static final int SPIKES_DELAY = 10;
	private static final int SPIKES_OUT = 24;
	private static final int DART_DELAY = 8;
	private static final int DART_WIND = 50;
	private static final int DART_LOOK = 6;
	private final Kind kind;

	public SpireTrapBlock(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STAGE, 0));
	}

	public Kind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STAGE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (kind == Kind.DART) return Shapes.block();
		return state.getValue(STAGE) == 2 ? Block.box(0, 0, 0, 16, 11, 16) : Block.box(0, 0, 0, 16, 1, 16);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return kind == Kind.DART ? Shapes.block() : Shapes.empty();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		// a floor rebuilt over itself sets the same block anew (a dart left winding, spikes left out): its tick is set
		// whenever none waits, or it stood so for good
		if (!level.isClientSide() && (kind == Kind.DART || state.getValue(STAGE) != 0) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, kind == Kind.DART ? DART_LOOK : SPIKES_OUT);
		}
	}

	@Override
	//? if >=1.21.11 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean precise) {
	*///?} else if >=1.21.5 {
	/*protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects) {
	*///?} else
	public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (kind != Kind.SPIKES || state.getValue(STAGE) != 0 || !(level instanceof ServerLevel server) || !(entity instanceof Player player)
				|| player.isCreative() || player.isSpectator()) return;
		server.setBlock(pos, state.setValue(STAGE, 1), 3);
		server.playSound(null, pos, com.vnap.sound.SpireSounds.TRAP_CLICK, SoundSource.BLOCKS, 1.0F, 1.0F);
		server.scheduleTick(pos, this, SPIKES_DELAY);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int stage = state.getValue(STAGE);
		if (kind == Kind.SPIKES) {
			if (stage == 1) {
				level.setBlock(pos, state.setValue(STAGE, 2), 3);
				level.playSound(null, pos, com.vnap.sound.SpireSounds.SPIKES, SoundSource.BLOCKS, 1.2F, 0.9F + random.nextFloat() * 0.2F);
				level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.35, 0.3, 0.35, 0.05);
				for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).expandTowards(0, 1, 0), com.vnap.spire.guard.SpireGuard::fair)) {
					com.vnap.Mc.hurt(player, source(level), damage(pos, 3.0F));
					// pinned by the feet a moment: the spire's own hobbling, not a potion's
					player.addEffect(Afflictions.of(Afflictions.ENTANGLED, 30, 0));
				}
				level.scheduleTick(pos, this, SPIKES_OUT);
			} else if (stage == 2) {
				level.setBlock(pos, state.setValue(STAGE, 0), 3);
			}
			return;
		}
		Direction facing = state.getValue(FACING);
		switch (stage) {
			case 0 -> {
				if (sees(level, pos, facing) != null) {
					level.setBlock(pos, state.setValue(STAGE, 1), 3);
					level.playSound(null, pos, com.vnap.sound.SpireSounds.TRAP_CLICK, SoundSource.BLOCKS, 1.0F, 1.3F);
					Vec3 mouth = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.55, 0, facing.getStepZ() * 0.55);
					level.sendParticles(ParticleTypes.SMOKE, mouth.x, mouth.y, mouth.z, 4, 0.05, 0.05, 0.05, 0.01);
					level.scheduleTick(pos, this, DART_DELAY);
				} else {
					level.scheduleTick(pos, this, DART_LOOK);
				}
			}
			case 1 -> {
				shoot(level, pos, facing);
				level.setBlock(pos, state.setValue(STAGE, 2), 3);
				level.scheduleTick(pos, this, DART_WIND);
			}
			default -> {
				level.setBlock(pos, state.setValue(STAGE, 0), 3);
				level.scheduleTick(pos, this, DART_LOOK);
			}
		}
	}

	/** The first climber on the dart's line in front of it, in sight of it. */
	private static @org.jspecify.annotations.Nullable Player sees(ServerLevel level, BlockPos pos, Direction facing) {
		BlockPos far = pos.relative(facing, DART_RANGE);
		AABB line = new AABB(pos.relative(facing)).minmax(new AABB(far)).inflate(0.3, 0.6, 0.3);
		for (Player player : level.getEntitiesOfClass(Player.class, line, com.vnap.spire.guard.SpireGuard::fair)) {
			if (!player.isCreative()) return player;
		}
		return null;
	}

	private void shoot(ServerLevel level, BlockPos pos, Direction facing) {
		Vec3 mouth = Vec3.atCenterOf(pos).add(facing.getStepX() * 0.7, 0.0, facing.getStepZ() * 0.7);
		com.vnap.spire.guard.SpireBolt dart = com.vnap.spire.guard.SpireBolt.dart(level, damage(pos, 1.5F), source(level));
		dart.setPos(mouth.x, mouth.y, mouth.z);
		dart.shoot(facing.getStepX(), 0.0, facing.getStepZ(), 2.2F, 0.4F);
		level.addFreshEntity(dart);
		level.playSound(null, pos, com.vnap.sound.SpireSounds.DART, SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, 3, 0.05, 0.05, 0.05, 0.01);
	}

	/** The Builders' trap as a cause of death ({@code damage_type/spire_trap}: its own words, not "died"). */
	public static net.minecraft.world.damagesource.DamageSource source(ServerLevel level) {
		return new net.minecraft.world.damagesource.DamageSource(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
				.getHolderOrThrow(TRAP));
	}

	private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> TRAP =
			net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, com.vnap.VillagerLife.id("spire_trap"));

	/** A trap's blow on its floor: grows as the guards' do. */
	private static float damage(BlockPos pos, float base) {
		SpireSlots.Slot slot = SpireSlots.at(pos);
		int floor = slot == null ? 1 : slot.key.floor();
		return (float) (base * SpireGuards.damage(floor));
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// a dart wall breathes a little dust from its slits now and then: there to be noticed by who looks
		if (kind == Kind.DART && random.nextInt(24) == 0) {
			Direction facing = state.getValue(FACING);
			level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + 0.5 + facing.getStepX() * 0.55, pos.getY() + 0.3 + random.nextDouble() * 0.4,
					pos.getZ() + 0.5 + facing.getStepZ() * 0.55, 0.0, -0.01, 0.0);
		}
	}
}
