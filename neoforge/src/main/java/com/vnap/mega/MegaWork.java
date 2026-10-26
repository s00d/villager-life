package com.vnap.mega;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The work of the megastructures past the Quarry and the Waygate: the Smelter's furnace, the Beacon of Calm's peace,
 * the Dome's growth, the Well's lore, the Drain's pull. What they keep (fuel, stored lore, the Drain's reach) is the
 * controller's ({@code store}, {@code fuel}).
 */
public final class MegaWork {
	/** The Beacon of Calm's reach, and the Dome's, the Well's, the Drain's. */
	public static final int CALM_REACH = 64;
	public static final int DOME_REACH = 16;
	public static final int WELL_REACH = 16;
	public static final int DRAIN_REACH = 24;
	public static final int SAW_REACH = 12;
	/** A tree's blocks at most (a giant one is felled in parts, the rest next time). */
	private static final int TREE = 400;
	/** Ticks a smelting takes (a furnace's is 200). */
	static final int SMELT_EVERY = 5;
	/** Lore a Well holds at most. */
	public static final int WELL_HOLDS = 100_000;
	/** A craftsman's lesson at a Well: the lore it takes, the trade's experience it gives. */
	static final int TEACH_COST = 10;
	static final int TEACH_XP = 5;
	/** The beacons of calm standing whole now (they count themselves in at their look, out when they go dark). */
	// read by FinalizeSpawn, which comes from the world generation threads too
	private static final Set<GlobalPos> CALM = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private MegaWork() {
	}

	public static void listen() {
		NeoForge.EVENT_BUS.addListener(FinalizeSpawnEvent.class, (FinalizeSpawnEvent event) -> {
			if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy) || CALM.isEmpty()) return;
			String why = event.getSpawnType().name();
			// the world's own births only: a spawner, an egg, a command, a raid, a run's guards are not the beacon's to stop
			if (!why.equals("NATURAL") && !why.equals("CHUNK_GENERATION") && !why.equals("PATROL")) return;
			if (calm(event.getLevel().getLevel(), BlockPos.containing(event.getX(), event.getY(), event.getZ()))) event.setSpawnCancelled(true);
		});
	}

	/** Whether a Beacon of Calm stands whole within its reach of this place. */
	public static boolean calm(net.minecraft.world.level.Level level, BlockPos pos) {
		for (GlobalPos beacon : CALM) {
			if (beacon.dimension().equals(level.dimension()) && beacon.pos().closerThan(pos, CALM_REACH)) return true;
		}
		return false;
	}

	static void calm(MegaControllerBlockEntity beacon, boolean whole) {
		if (whole) CALM.add(beacon.where());
		else CALM.remove(beacon.where());
	}

	public static void forgetAll() {
		CALM.clear();
	}

	static void tick(ServerLevel level, MegaControllerBlockEntity controller) {
		switch (controller.kind()) {
			case SMELTER -> smelt(level, controller);
			case CALM -> {
				if (level.getGameTime() % 40 == 0) calm(controller, true);
				if (level.getGameTime() % 10 == 0) glow(level, controller.getBlockPos().above(3), ParticleTypes.END_ROD);
			}
			case DOME -> grow(level, controller);
			case WELL -> {
				gather(level, controller);
				teach(level, controller);
			}
			case DRAIN -> drain(level, controller);
			case SAWMILL -> saw(level, controller);
			default -> {
			}
		}
	}

	// ---- the Smelter ----

	/** One item a few ticks from the chest by its front's left into the one by its right; ores and raw metals come out twice. */
	private static void smelt(ServerLevel level, MegaControllerBlockEntity smelter) {
		if (level.getGameTime() % SMELT_EVERY != 0) return;
		Direction left = smelter.facing().getClockWise();
		// beside its front, past the frame: the chest on its left in, the one on its right out
		Container from = container(level, smelter.getBlockPos().relative(left, 2));
		Container to = container(level, smelter.getBlockPos().relative(left.getOpposite(), 2));
		if (from == null || to == null) return;
		for (int slot = 0; slot < from.getContainerSize(); slot++) {
			ItemStack stack = from.getItem(slot);
			if (stack.isEmpty()) continue;
			SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
			ItemStack made = com.vnap.Mc.recipes(level).getRecipeFor(RecipeType.SMELTING, input, level)
					.map(holder -> com.vnap.Mc.assemble(holder.value(), input, level.registryAccess())).orElse(ItemStack.EMPTY);
			if (made.isEmpty()) continue;
			if (smelter.fuel <= 0 && !fuel(from, smelter)) return;
			if (stack.is(net.neoforged.neoforge.common.Tags.Items.RAW_MATERIALS) || stack.is(net.neoforged.neoforge.common.Tags.Items.ORES)) made.grow(made.getCount());
			ItemStack left2 = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, to, made, null);
			// the out-chest full: the ingots to the town's stall (its smiths' iron), or it waits
			if (!left2.isEmpty() && !TownWork.deliver(level, smelter, left2).isEmpty()) return;
			stack.shrink(1);
			from.setChanged();
			smelter.fuel--;
			smelter.setChanged();
			if (level.getGameTime() % 40 == 0) {
				level.playSound(null, smelter.getBlockPos(), com.vnap.sound.SpireSounds.SMELTER_WORK, SoundSource.BLOCKS, 0.8F, 1.0F);
				glow(level, smelter.getBlockPos().relative(smelter.facing().getOpposite()).above(), ParticleTypes.FLAME);
			}
			return;
		}
	}

	/** A furnace's fuel out of the input chest: as many smeltings as it burns in a furnace, twice. */
	private static boolean fuel(Container from, MegaControllerBlockEntity smelter) {
		for (int slot = 0; slot < from.getContainerSize(); slot++) {
			ItemStack stack = from.getItem(slot);
			int burn = com.vnap.Mc.burnTime(stack);
			if (burn <= 0) continue;
			smelter.fuel += Math.max(1, burn / 100);
			ItemStack remains = stack.is(net.minecraft.world.item.Items.LAVA_BUCKET) ? new ItemStack(net.minecraft.world.item.Items.BUCKET) : ItemStack.EMPTY;
			stack.shrink(1);
			if (stack.isEmpty() && !remains.isEmpty()) from.setItem(slot, remains);
			from.setChanged();
			return true;
		}
		return false;
	}

	// ---- the Dome of Growth ----

	/** Growth ticks over its reach: the plants in it grow eight times as fast as the world's own. */
	private static void grow(ServerLevel level, MegaControllerBlockEntity dome) {
		BlockPos middle = dome.getBlockPos().relative(dome.facing().getOpposite(), 3);
		var random = level.getRandom();
		// seven more random ticks a block for the world's three in a section's 4096 a tick, over five layers of its reach: eight times the world's
		int tries = (2 * DOME_REACH + 1) * (2 * DOME_REACH + 1) * 5 * 21 / 4096 + 1;
		for (int i = 0; i < tries; i++) {
			BlockPos at = middle.offset(random.nextInt(2 * DOME_REACH + 1) - DOME_REACH, random.nextInt(5) - 2, random.nextInt(2 * DOME_REACH + 1) - DOME_REACH);
			BlockState state = level.getBlockState(at);
			if ((state.is(net.minecraft.tags.BlockTags.CROPS) || state.is(net.minecraft.tags.BlockTags.SAPLINGS) || state.getBlock() instanceof BonemealableBlock)
					&& state.isRandomlyTicking()
					&& !state.is(Blocks.GRASS_BLOCK)) {
				state.randomTick(level, at, random);
				if (random.nextInt(8) == 0) glow(level, at, ParticleTypes.HAPPY_VILLAGER);
			}
		}
	}

	// ---- the Well of Lore ----

	/** The experience lying within its reach drawn in and kept. */
	private static void gather(ServerLevel level, MegaControllerBlockEntity well) {
		if (level.getGameTime() % 10 != 0 || well.store >= WELL_HOLDS) return;
		AABB reach = new AABB(well.getBlockPos()).inflate(WELL_REACH);
		List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, reach);
		for (ExperienceOrb orb : orbs) {
			well.store = Math.min(WELL_HOLDS, well.store + orb.getValue() * ((com.vnap.mixin.ExperienceOrbAccessor) orb).vnap$count());
			level.sendParticles(ParticleTypes.ENCHANT, orb.getX(), orb.getY(), orb.getZ(), 6, 0.2, 0.2, 0.2, 0.3);
			orb.discard();
		}
		if (!orbs.isEmpty()) {
			well.setChanged();
			level.playSound(null, well.getBlockPos(), com.vnap.sound.SpireSounds.WELL_GATHER, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
	}

	/**
	 * A Well in a town teaches its craftsmen: each with a trade within its reach takes some of the lore it holds and
	 * learns from it — a few points of its trade's experience a while, a level when they are enough (new trades for
	 * the player, as trading with it would bring).
	 */
	private static void teach(ServerLevel level, MegaControllerBlockEntity well) {
		if (level.getGameTime() % 200 != 0 || well.store < TEACH_COST || TownWork.stall(level, well) == null) return;
		for (net.minecraft.world.entity.npc.Villager villager : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
				new AABB(well.getBlockPos()).inflate(WELL_REACH), v -> !v.isBaby() && com.vnap.Mc.villagerLevel(v.getVillagerData()) < 5
						&& !com.vnap.Mc.is(v.getVillagerData(), net.minecraft.world.entity.npc.VillagerProfession.NONE)
						&& !com.vnap.Mc.is(v.getVillagerData(), net.minecraft.world.entity.npc.VillagerProfession.NITWIT))) {
			if (well.store < TEACH_COST) break;
			well.store -= TEACH_COST;
			villager.setVillagerXp(villager.getVillagerXp() + TEACH_XP);
			com.vnap.mixin.VillagerAccessor learner = (com.vnap.mixin.VillagerAccessor) villager;
			//? if >=1.21.11 {
			/*if (learner.vnap$shouldIncreaseLevel()) learner.vnap$increaseMerchantCareer(level);
			*///?} else
			if (learner.vnap$shouldIncreaseLevel()) learner.vnap$increaseMerchantCareer();
			level.sendParticles(ParticleTypes.ENCHANT, villager.getX(), villager.getY() + 2.0, villager.getZ(), 12, 0.3, 0.3, 0.3, 0.5);
		}
		well.setChanged();
	}

	/** A click on a Well: all it holds to the player. */
	static void giveLore(ServerLevel level, MegaControllerBlockEntity well, Player player) {
		if (well.store <= 0) {
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.well.empty"), true);
			return;
		}
		player.giveExperiencePoints(well.store);
		com.vnap.Mc.say(player, Component.translatable("mega.villager_life.well.given", well.store), true);
		well.store = 0;
		well.setChanged();
		level.playSound(null, well.getBlockPos(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
	}

	// ---- the Drain ----

	/**
	 * A ring of its reach a tick, out from it: every water and lava in its columns, from a little over the pump down
	 * as deep as its reach, taken away (waterlogged blocks dried). Passes again while the last one found any (two
	 * sources make a third between them: one pass is never enough for a pool), and once in a while after.
	 */
	private static void drain(ServerLevel level, MegaControllerBlockEntity drain) {
		int r = drain.store;
		if (r > DRAIN_REACH) {
			// a pass done: again at once if it found water, else now and then (rain, a spring)
			if (drain.fuel > 0 || level.getGameTime() % 400 == 0) {
				// the town's land it dried is surveyed anew: more of it may be built on
				if (drain.fuel > 0) com.vnap.village.town.TownSurvey.again(level, drain.getBlockPos(), DRAIN_REACH);
				drain.fuel = 0;
				drain.store = 0;
				drain.setChanged();
			}
			return;
		}
		BlockPos at = drain.getBlockPos();
		int found = 0;
		for (int i = 0; i < (r == 0 ? 1 : 8 * r); i++) {
			int[] column = column(r == 0 ? 0 : (2 * r - 1) * (2 * r - 1) + i);
			if (column == null) break;
			for (int y = 2; y >= -DRAIN_REACH; y--) {
				BlockPos pos = at.offset(column[0], y, column[1]);
				if (!level.isLoaded(pos)) continue;
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof LiquidBlock) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				} else if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
					level.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), 2);
				} else {
					continue;
				}
				if (found++ % 12 == 0) glow(level, pos, state.getFluidState().is(FluidTags.LAVA) ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SPLASH);
			}
		}
		drain.fuel += found;
		drain.store++;
		drain.setChanged();
		if (found > 0 && level.getGameTime() % 50 < 2) level.playSound(null, at, com.vnap.sound.SpireSounds.DRAIN_WORK, SoundSource.BLOCKS, 0.8F, 1.0F);
	}

	// ---- the Sawmill ----

	/**
	 * A tree every few seconds within its reach: found by its foot (a log on earth), felled whole — its logs and the
	 * leaves that are a tree's (not one laid by a hand) — all it drops into the chest beside the controller, and a
	 * sapling of it set back on its foot. It waits while the chest is full.
	 */
	private static void saw(ServerLevel level, MegaControllerBlockEntity mill) {
		if (level.getGameTime() % 60 != 0) return;
		BlockPos middle = mill.getBlockPos().relative(mill.facing().getOpposite(), 2);
		BlockPos foot = null;
		for (BlockPos at : BlockPos.betweenClosed(middle.offset(-SAW_REACH, -2, -SAW_REACH), middle.offset(SAW_REACH, 10, SAW_REACH))) {
			if (level.getBlockState(at).is(net.minecraft.tags.BlockTags.LOGS) && level.getBlockState(at.below()).is(net.minecraft.tags.BlockTags.DIRT)
					&& !mill.notTrees.contains(at)) {
				foot = at.immutable();
				break;
			}
		}
		if (foot == null) return;
		java.util.List<Container> out = new java.util.ArrayList<>();
		for (Direction side : Direction.values()) {
			Container chest = container(level, mill.getBlockPos().relative(side));
			if (chest != null) out.add(chest);
		}
		if (out.isEmpty() && TownWork.stall(level, mill) == null) return;
		java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>(List.of(foot));
		Set<BlockPos> seen = new HashSet<>(List.of(foot));
		java.util.List<BlockPos> tree = new java.util.ArrayList<>();
		ItemStack axe = new ItemStack(net.minecraft.world.item.Items.NETHERITE_AXE);
		while (!open.isEmpty() && tree.size() < TREE) {
			BlockPos at = open.poll();
			BlockState state = level.getBlockState(at);
			boolean log = state.is(net.minecraft.tags.BlockTags.LOGS);
			boolean leaf = state.is(net.minecraft.tags.BlockTags.LEAVES) && !(state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT));
			if (!log && !leaf) continue;
			tree.add(at);
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				BlockPos next = at.offset(dx, dy, dz);
				if (next.getY() >= foot.getY() && seen.add(next) && next.closerThan(foot, 24)) open.add(next);
			}
		}
		// a tree has leaves of its own (grown, not laid by a hand): logs without them are someone's building, left be
		int leaves = 0;
		for (BlockPos at : tree) if (level.getBlockState(at).is(net.minecraft.tags.BlockTags.LEAVES)) leaves++;
		if (leaves < 4) {
			mill.notTrees.add(foot);
			return;
		}
		ItemStack sapling = ItemStack.EMPTY;
		for (BlockPos at : tree) {
			BlockState state = level.getBlockState(at);
			for (ItemStack drop : net.minecraft.world.level.block.Block.getDrops(state, level, at, null, null, axe)) {
				if (sapling.isEmpty() && drop.is(net.minecraft.tags.ItemTags.SAPLINGS)) {
					sapling = drop.split(1);
				}
				ItemStack left = drop;
				for (Container chest : out) {
					left = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, chest, left, null);
					if (left.isEmpty()) break;
				}
				// the town's stall takes the logs its builders want
				if (!left.isEmpty()) TownWork.deliver(level, mill, left);
			}
			level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
		}
		level.levelEvent(2001, foot, net.minecraft.world.level.block.Block.getId(Blocks.OAK_LOG.defaultBlockState()));
		if (!sapling.isEmpty() && sapling.getItem() instanceof net.minecraft.world.item.BlockItem item) {
			BlockState plant = item.getBlock().defaultBlockState();
			if (plant.canSurvive(level, foot)) level.setBlock(foot, plant, 3);
		}
		mill.store += 1;
		mill.setChanged();
		level.playSound(null, foot, com.vnap.sound.SpireSounds.SAWMILL_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	/** The {@code n}th column round a pump, ring by ring (0 under it), or null past its reach. */
	static int @org.jspecify.annotations.Nullable [] column(int n) {
		int left = n;
		for (int r = 0; r <= DRAIN_REACH; r++) {
			int ring = r == 0 ? 1 : 8 * r;
			if (left < ring) {
				if (r == 0) return new int[] {0, 0};
				int side = left / (2 * r);
				int along = left % (2 * r);
				return switch (side) {
					case 0 -> new int[] {-r + along, -r};
					case 1 -> new int[] {r, -r + along};
					case 2 -> new int[] {r - along, r};
					default -> new int[] {-r, r - along};
				};
			}
			left -= ring;
		}
		return null;
	}

	static void tell(ServerLevel level, MegaControllerBlockEntity controller, Player player) {
		Component said = switch (controller.kind()) {
			case SMELTER -> Component.translatable("mega.villager_life.smelter.working", controller.fuel);
			case CALM -> Component.translatable("mega.villager_life.calm.working", CALM_REACH);
			case DOME -> Component.translatable("mega.villager_life.dome.working", DOME_REACH);
			case WELL -> null;
			case SAWMILL -> Component.translatable("mega.villager_life.sawmill.working", SAW_REACH, controller.store);
			case DRAIN -> Component.translatable("mega.villager_life.drain.working", Math.min(controller.store, DRAIN_REACH), DRAIN_REACH);
			default -> Component.empty();
		};
		if (controller.kind() == MegaKind.WELL) giveLore(level, controller, player);
		else if (said != null) com.vnap.Mc.say(player, said, false);
	}

	private static @org.jspecify.annotations.Nullable Container container(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof Container container ? container : null;
	}

	private static void glow(ServerLevel level, BlockPos pos, net.minecraft.core.particles.ParticleOptions particle) {
		level.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
	}
}
