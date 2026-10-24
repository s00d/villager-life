package com.vnap.village.town;

import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Infants;
import com.vnap.dialogue.Temperament;
import com.vnap.dialogue.VillagerHome;
import com.vnap.village.home.HousePalette;
import com.vnap.village.home.Mayors;
import com.vnap.village.life.Agenda;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.script.WorkerControl;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.List;

/**
 * The harvest feast on the square (TownStory), done the way a village does one: a few grown-ups carry the tables and
 * benches out and set them (slabs on the square, stairs either side), put the cakes, candles and flowers on, the mayor
 * opens it, the town sits down and eats, the young ones and the merry dance round to the music, fireworks at dusk,
 * then the tables are carried off again. Every block is set and taken by hand (Agenda jobs, the thing in hand on the
 * way); the town's treasury pays for the cakes. What was set is kept (TownStory.State.placed): a feast cut short (the
 * world left) is cleared all the same.
 */
final class Feast {
	/** Ticks for setting the tables, for the feast itself, for the fireworks; after that the clearing. */
	static final long SETUP = 1600;
	static final long MAIN = 2400;
	static final long FIREWORKS = 400;
	/** The clearing is given this long before what is left goes at once. */
	static final long CLEARING = 2400;
	/** Hands that set the tables. */
	static final int HELPERS = 6;
	static final int DANCERS = 5;

	static final int SETTING = 0;
	static final int FEASTING = 1;
	static final int FIRES = 2;
	static final int CLEARING_UP = 3;

	private static final List<FireworkExplosion.Shape> SHAPES = List.of(FireworkExplosion.Shape.SMALL_BALL, FireworkExplosion.Shape.LARGE_BALL,
			FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.BURST);
	private static final ItemStack[] FOOD = {new ItemStack(Items.BREAD), new ItemStack(Items.PUMPKIN_PIE), new ItemStack(Items.COOKIE), new ItemStack(Items.BAKED_POTATO)};

	private Feast() {
	}

	/** One column of a table: its top, its two benches, what stands on it; set in that order, cleared the other way. */
	record Column(List<SchematicSpec.PlacedBlock> blocks) {
	}

	// ---- the tables -----------------------------------------------------------------------------

	/** Up to two tables on the square round the bell: free, level cells three out, a bench either side. */
	static List<Column> layout(ServerLevel level, Roads.Network network) {
		BlockPos bell = network.bell;
		String palette = HousePalette.forBiome(level.getBiome(bell));
		BlockState top = Roads.palette(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), palette);
		BlockState[] food = {Blocks.CAKE.defaultBlockState(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true),
				Blocks.CAKE.defaultBlockState(), Blocks.POTTED_POPPY.defaultBlockState()};
		List<Column> out = new ArrayList<>();
		int tables = 0;
		// round the bell, near first and anywhere on the square (it was only three out at the bell's height, and a village
		// of the world's well there had no feast at all), a side of the bell for each table
		for (int away = 3; away <= TownRules.SQUARE_LARGE + 2 && tables < 2; away++) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (tables >= 2) break;
				if (taken(out, bell, d)) continue;
				Direction along = d.getClockWise();
				for (int dy : new int[]{0, -1, 1}) {
					List<Column> table = new ArrayList<>();
					for (int c = -1; c <= 2; c++) {
						BlockPos cell = bell.relative(d, away).relative(along, c).above(dy);
						BlockPos near = bell.relative(d, away - 1).relative(along, c).above(dy);
						BlockPos far = bell.relative(d, away + 1).relative(along, c).above(dy);
						if (!free(level, network, cell) || !free(level, network, near) || !free(level, network, far) || !level.getBlockState(cell.above()).isAir()) {
							table.clear();
							break;
						}
						BlockState nearBench = Roads.palette(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d.getOpposite()), palette);
						BlockState farBench = Roads.palette(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d), palette);
						table.add(new Column(List.of(new SchematicSpec.PlacedBlock(cell, top), new SchematicSpec.PlacedBlock(near, nearBench),
								new SchematicSpec.PlacedBlock(far, farBench), new SchematicSpec.PlacedBlock(cell.above(), food[c + 1]))));
					}
					if (table.size() == 4) {
						out.addAll(table);
						tables++;
						break;
					}
				}
			}
		}
		return out;
	}

	/** A table stands on this side of the bell already. */
	private static boolean taken(List<Column> out, BlockPos bell, Direction d) {
		return out.stream().anyMatch(column -> {
			BlockPos at = column.blocks().get(0).rel();
			int along = (at.getX() - bell.getX()) * d.getStepX() + (at.getZ() - bell.getZ()) * d.getStepZ();
			return along >= 2;
		});
	}

	/** A cell of the square a table may stand on: open, on firm ground, nothing of the town's there. */
	private static boolean free(ServerLevel level, Roads.Network network, BlockPos pos) {
		return level.isLoaded(pos) && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
				&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) && !network.features.contains(pos)
				&& !network.lamps.contains(pos.below()) && !pos.equals(network.bell);
	}

	// ---- the feast ------------------------------------------------------------------------------

	/** The day's feast begins: the tables are carried out by a few hands. False when there is no room or nobody to hold it. */
	static boolean start(ServerLevel level, Roads.Network network, TownStory.State story, List<Villager> people) {
		// what a feast cut short left on the square (the world closed mid-feast) goes first
		if (!story.placed.isEmpty()) clearNow(level, story);
		Villager mayor = Mayors.mayorNear(level, network.bell);
		if (mayor == null || people.size() < TownRules.FEAST_PEOPLE) {
			TownStory.refused = mayor == null ? "feast: no mayor about" : "feast: " + people.size() + " people about";
			return false;
		}
		List<Column> columns = layout(level, network);
		if (columns.isEmpty()) {
			TownStory.refused = "feast: no room on the square for a table";
			return false;
		}
		List<Villager> hands = people.stream().filter(v -> !v.isBaby() && !com.vnap.village.life.Life.official(v) && !Infants.minding(level, v))
				.sorted(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(network.bell.getX(), network.bell.getY(), network.bell.getZ())))
				.limit(HELPERS).toList();
		if (hands.isEmpty()) return false;
		story.who = mayor.getUUID();
		story.phase = SETTING;
		story.placed.clear();
		for (int i = 0; i < columns.size(); i++) {
			Villager hand = hands.get(i % hands.size());
			List<SchematicSpec.PlacedBlock> blocks = columns.get(i).blocks();
			for (int k = 0; k < blocks.size(); k++) {
				SchematicSpec.PlacedBlock block = blocks.get(k);
				if (block.state().is(Blocks.CAKE)) com.vnap.village.market.Market.order(level, null, block.rel(), Items.CAKE);
				Agenda.job(hand, "feast_set_" + i + "_" + k, block.rel(), Agenda.EVENT, carried(block.state()),
						(l, v) -> set(l, story, block));
			}
			if (i % hands.size() == 0) Speech.queue(hand, "Sets the Feast Table", null, 40L);
		}
		Chronicle.note(level, network.bell, "story.feast", VillagerHome.nameOf(mayor));
		return true;
	}

	private static ItemStack carried(BlockState state) {
		if (state.is(Blocks.POTTED_POPPY)) return new ItemStack(Items.FLOWER_POT);
		ItemStack stack = new ItemStack(state.getBlock().asItem());
		return stack.isEmpty() ? new ItemStack(Items.OAK_PLANKS) : stack;
	}

	/** A block of the feast set down by hand (on its table, if it goes on one). */
	private static void set(ServerLevel level, TownStory.State story, SchematicSpec.PlacedBlock block) {
		BlockPos pos = block.rel();
		if (story.phase >= CLEARING_UP || !level.getBlockState(pos).canBeReplaced() || !block.state().canSurvive(level, pos)) return;
		level.setBlock(pos, block.state(), Block.UPDATE_ALL);
		level.playSound(null, pos, block.state().getSoundType(level, pos, null).getPlaceSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
		story.placed.add(pos.immutable());
	}

	/** One look (every 100 ticks) at the feast running {@code ticks}; true when it is over and cleared. */
	static boolean run(ServerLevel level, Roads.Network network, TownStory.State story, long ticks) {
		if (story.phase == SETTING && ticks >= SETUP) {
			story.phase = FEASTING;
			sitDown(level, network, story);
		}
		if (story.phase == FEASTING) {
			feasting(level, network, story, ticks);
			if (ticks >= SETUP + MAIN) story.phase = FIRES;
		}
		if (story.phase == FIRES) {
			fireworks(level, network, ticks);
			if (ticks >= SETUP + MAIN + FIREWORKS) {
				story.phase = CLEARING_UP;
				clearUp(level, network, story);
			}
		}
		if (story.phase == CLEARING_UP) {
			if (story.placed.isEmpty()) return true;
			if (ticks >= SETUP + MAIN + FIREWORKS + CLEARING) {
				clearNow(level, story);
				return true;
			}
		}
		return false;
	}

	private static java.util.function.Predicate<ServerLevel> feasting(TownStory.State story) {
		return ignored -> story.kind == TownStory.Kind.FEAST && story.phase < CLEARING_UP;
	}

	/** The mayor opens it; everyone takes a bench (the rest stand round the tables). */
	private static void sitDown(ServerLevel level, Roads.Network network, TownStory.State story) {
		if (story.who != null && level.getEntity(story.who) instanceof Villager mayor && mayor.isAlive()) {
			Speech.queue(mayor, "Mayor Opens the Feast", null, 20L);
		}
		// the music: a jukebox in the middle of the dance floor, the jig on it
		Music.play(level, network, danceFloor(network, story), com.vnap.item.LifeItems.DISC_FEAST, feasting(story));
		List<BlockPos> seats = story.placed.stream().filter(pos -> level.getBlockState(pos).getBlock() instanceof StairBlock).toList();
		int n = 0;
		for (Villager villager : TownStory.people(level, network)) {
			if (villager.isBaby() || Infants.minding(level, villager)) continue;
			BlockPos at = n < seats.size() ? seats.get(n) : Rings.at(network.bell, n * 0.8, 5.5);
			n++;
			Agenda.add(villager, new Agenda.Item("feast", at, Agenda.EVENT, n <= seats.size() ? 0 : 1, 0.55F, feasting(story), null, null));
		}
	}

	/** The feast itself: those at table eat (bread in hand, the sound, the crumbs), the young and merry dance, music. */
	private static void feasting(ServerLevel level, Roads.Network network, TownStory.State story, long ticks) {
		List<Villager> people = TownStory.people(level, network);
		List<Villager> dancers = new ArrayList<>();
		boolean spoke = false;
		for (Villager villager : people) {
			boolean seated = Agenda.has(villager, "feast") && villager.blockPosition().closerThan(network.bell, 6.0);
			if (seated && level.getRandom().nextFloat() < 0.3F) eat(level, villager);
			if (!spoke && seated && level.getRandom().nextFloat() < 0.04F) {
				Speech.queue(villager, "Harvest Feast", null, 1L);
				spoke = true;
			}
			if (dancers.size() < DANCERS && (villager.isBaby() || Temperament.is(villager, Temperament.Trait.SOCIABLE)) && !Infants.infant(villager)) dancers.add(villager);
		}
		dance(level, network, story, dancers, ticks);
	}

	private static void eat(ServerLevel level, Villager villager) {
		ItemStack food = FOOD[level.getRandom().nextInt(FOOD.length)];
		WorkerControl.hold(villager, food.copy());
		com.vnap.Mc.swing(villager, net.minecraft.world.InteractionHand.MAIN_HAND);
		level.playSound(null, villager, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.3F);
		level.sendParticles(com.vnap.Mc.itemParticle(food), villager.getX(), villager.getEyeY() - 0.15, villager.getZ(), 6, 0.1, 0.05, 0.1, 0.05);
	}

	/** A ring dance away from the tables: each look every dancer steps on round the ring and hops, notes rise. */
	private static void dance(ServerLevel level, Roads.Network network, TownStory.State story, List<Villager> dancers, long ticks) {
		if (dancers.size() < 2) return;
		BlockPos centre = danceFloor(network, story);
		long step = ticks / 100;
		for (int i = 0; i < dancers.size(); i++) {
			Villager dancer = dancers.get(i);
			BlockPos at = Rings.at(centre, (i + step) * Math.PI * 2 / 6.0, 2.0).atY(centre.getY());
			Agenda.add(dancer, new Agenda.Item("dance", at, Agenda.EVENT, 1, 0.7F, feasting(story), i == 0 && step % 6 == 0 ? "Dances at the Feast" : null, null));
			if (dancer.onGround() && level.getRandom().nextBoolean()) {
				dancer.setDeltaMovement(dancer.getDeltaMovement().x, 0.42, dancer.getDeltaMovement().z);
				com.vnap.Mc.moved(dancer);
			}
			level.sendParticles(ParticleTypes.NOTE, dancer.getX(), dancer.getEyeY() + 0.5, dancer.getZ(), 1, 0.2, 0.1, 0.2, 1.0);
		}
	}

	/** The open side of the square: opposite the first table, or the bell's own side when there was room all round. */
	private static BlockPos danceFloor(Roads.Network network, TownStory.State story) {
		BlockPos bell = network.bell;
		for (BlockPos placed : story.placed) {
			int dx = Integer.signum(placed.getX() - bell.getX());
			int dz = Integer.signum(placed.getZ() - bell.getZ());
			if (Math.abs(placed.getX() - bell.getX()) == 3 || Math.abs(placed.getZ() - bell.getZ()) == 3) {
				Direction table = Math.abs(placed.getX() - bell.getX()) == 3 ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
				return bell.relative(table.getOpposite(), 4);
			}
		}
		return bell.relative(Direction.SOUTH, 4);
	}

	/** Dusk: rockets of every colour go up from round the square. */
	private static void fireworks(ServerLevel level, Roads.Network network, long ticks) {
		if (ticks - SETUP - MAIN < 100) {
			for (Villager villager : TownStory.people(level, network)) {
				if (villager.blockPosition().closerThan(network.bell, 8.0) && level.getRandom().nextFloat() < 0.3F) {
					Speech.queue(villager, "Feast Fireworks", null, 20L + level.getRandom().nextInt(60));
					break;
				}
			}
		}
		for (int i = 0; i < 2; i++) {
			BlockPos from = Rings.at(network.bell, level.getRandom().nextDouble() * Math.PI * 2, 6.0).atY(network.bell.getY());
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			IntList colours = IntList.of(DyeColor.byId(level.getRandom().nextInt(16)).getFireworkColor(), DyeColor.byId(level.getRandom().nextInt(16)).getFireworkColor());
			FireworkExplosion.Shape shape = SHAPES.get(level.getRandom().nextInt(SHAPES.size()));
			rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + level.getRandom().nextInt(2),
					List.of(new FireworkExplosion(shape, colours, IntList.of(), level.getRandom().nextBoolean(), level.getRandom().nextBoolean()))));
			level.addFreshEntity(new FireworkRocketEntity(level, from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5, rocket));
		}
	}

	/** The tables go back as they came, by the same few hands: what is on them first, then the benches, then the tops. */
	private static void clearUp(ServerLevel level, Roads.Network network, TownStory.State story) {
		List<BlockPos> placed = new ArrayList<>(story.placed);
		// the highest first: what stands on the tables before the tables
		placed.sort(java.util.Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
		List<Villager> hands = TownStory.people(level, network).stream().filter(v -> !v.isBaby() && !com.vnap.village.life.Life.official(v) && !Infants.minding(level, v))
				.sorted(java.util.Comparator.comparingDouble(v -> v.distanceToSqr(network.bell.getX(), network.bell.getY(), network.bell.getZ())))
				.limit(HELPERS).toList();
		if (hands.isEmpty()) {
			clearNow(level, story);
			return;
		}
		for (int i = 0; i < placed.size(); i++) {
			BlockPos pos = placed.get(i);
			Villager hand = hands.get(i % hands.size());
			Agenda.job(hand, "feast_clear_" + i, pos, Agenda.EVENT, ItemStack.EMPTY, (l, v) -> take(l, story, pos));
		}
		Speech.queue(hands.get(0), "Clears the Feast Table", null, 20L);
	}

	/** A block of the feast taken up by hand (gone with it: carried off, nothing dropped). */
	private static void take(ServerLevel level, TownStory.State story, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.isAir()) {
			level.playSound(null, pos, state.getSoundType(level, pos, null).getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		story.placed.remove(pos);
	}

	/** What the hands did not get to (or a feast the world left): gone at once. */
	static void clearNow(ServerLevel level, TownStory.State story) {
		List<BlockPos> placed = new ArrayList<>(story.placed);
		placed.sort(java.util.Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
		for (BlockPos pos : placed) if (level.isLoaded(pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		story.placed.clear();
	}

}
