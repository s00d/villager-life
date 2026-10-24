package com.vnap.spire;

import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Towns;
import com.vnap.village.town.Ground;
import com.vnap.village.town.Rings;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A finished town's spire sprouting and growing (DESIGN §2): its place is chosen the day the town is done (wall laid,
 * chapel, bakehouse and tavern up, no house going up), it rises on the next night, and every floor its players open
 * adds to it. Growing sets only what differs between the old shape and the new (the old break is taken down), a few
 * thousand blocks a tick, and only while all of it is loaded.
 */
public final class SpireGrowth {
	private static final int CHECK_EVERY = 40;
	/** Night: from dusk till the small hours, when a spire rises. */
	private static final long DUSK = 13_000L;
	private static final long DAWN = 22_000L;

	/** A growth in hand: its blocks, how many go in a tick, and where it is in its show (prelude, rise, finale). */
	private static final class Job {
		final int spire;
		final Deque<Map.Entry<BlockPos, BlockState>> todo;
		final int to;
		final boolean first;
		final int rate;
		int prelude;
		int tick;

		Job(int spire, Deque<Map.Entry<BlockPos, BlockState>> todo, int to, boolean first) {
			this.spire = spire;
			this.todo = todo;
			this.to = to;
			this.first = first;
			// a first rising takes about forty seconds, a floor's growth a few: slow enough to be watched
			this.rate = Math.max(first ? 12 : 6, todo.size() / (first ? 800 : 120));
			this.prelude = first ? PRELUDE : 40;
		}
	}

	/** Ticks the ground shakes and the light gathers before a spire comes up. */
	private static final int PRELUDE = 200;
	/** Players this near feel the ground shake and get the show's sounds full. */
	private static final double WITNESS = 96.0;

	private static final List<Job> JOBS = new ArrayList<>();

	private SpireGrowth() {
	}

	/** Whether this town is done: its wall laid, the chapel, the bakehouse and the tavern standing, no house going up. */
	public static boolean due(ServerLevel level, Roads.Network network) {
		if (network.wall <= 0) return false;
		for (Roads.Segment segment : network.segments) {
			if ("wall".equals(segment.label) || "ditch".equals(segment.label)) return false;
		}
		for (TownPlots.Kind kind : new TownPlots.Kind[] {TownPlots.Kind.CHAPEL, TownPlots.Kind.BAKEHOUSE, TownPlots.Kind.TAVERN}) {
			if (!TownPlots.built(network, kind)) return false;
		}
		for (HouseProject house : com.vnap.mood.VillageMoodSavedData.get(level).houses()) {
			if (!house.done && Towns.ours(level, network.bell, house.origin)) return false;
		}
		return true;
	}

	/** Daily with the town's plan: a done town without a spire gets its place (it rises at night: tick). */
	public static void plan(ServerLevel level, Roads.Network network) {
		if (claimed(level, network.bell) || !due(level, network)) return;
		place(level, network.bell);
	}

	/**
	 * The town has its spire: its own, or one a writ called up nearby (out in the wild by the town, or before it grew),
	 * which is the town's spire too.
	 */
	public static boolean claimed(ServerLevel level, BlockPos bell) {
		Spires spires = Spires.get(level.getServer());
		if (spires.ofTown(level.dimension(), bell) != null) return true;
		for (Spires.Spire other : spires.all()) {
			if (other.dimension.equals(level.dimension()) && other.gate.closerThan(bell, SpireWritItem.APART)) return true;
		}
		return false;
	}

	/** Finds and records the place of a town's spire, outside its wall; null when none fits (tried again tomorrow). */
	public static Spires.@Nullable Spire place(ServerLevel level, BlockPos bell) {
		int reach = (int) Towns.reach(level, bell);
		return place(level, bell, bell, Rings.around(bell, reach + 20, reach + 56, 4, 32));
	}

	/**
	 * The first of {@code spots} where a spire's plaza fits, recorded for {@code bell} (its town, or for a spire called
	 * up by a writ away from any town, the spot it was read at), its gate turned to {@code facing}.
	 */
	public static Spires.@Nullable Spire place(ServerLevel level, BlockPos bell, BlockPos facing, Iterable<BlockPos> spots) {
		for (BlockPos spot : spots) {
			Integer ground = flat(level, spot);
			if (ground == null) continue;
			BlockPos centre = new BlockPos(spot.getX(), ground, spot.getZ());
			if (com.vnap.village.town.Land.around(level, centre).blocked(centre, SpireExterior.PLAZA + 2)) continue;
			Rotation rotation = toward(centre, facing);
			long seed = FloorGenerator.mix(level.getSeed(), bell.asLong());
			BlockPos gate = centre.offset(SpireExterior.gateOffset().rotate(rotation));
			BlockPos front = centre.offset(SpireExterior.frontOffset().rotate(rotation));
			int r = SpireExterior.REACH;
			BoundingBox box = new BoundingBox(centre.getX() - r, centre.getY() - 2, centre.getZ() - r, centre.getX() + r,
					Math.min((level.getMinBuildHeight() + level.getHeight()) - 1, centre.getY() + SpireExterior.height(SpireExterior.FULL) + SpireExterior.CROWN), centre.getZ() + r);
			Spires.Spire spire = Spires.get(level.getServer()).add(level.dimension(), bell, seed, gate, front, box);
			spire.centre = centre;
			spire.rotation = rotation;
			Spires.get(level.getServer()).setDirty();
			return spire;
		}
		return null;
	}

	/** Ground under the plaza within three blocks up or down, all of it loaded, dry and natural; its middle height. */
	private static @Nullable Integer flat(ServerLevel level, BlockPos spot) {
		List<Integer> heights = new ArrayList<>();
		int r = SpireExterior.PLAZA;
		for (int x = -r; x <= r; x += 2) {
			for (int z = -r; z <= r; z += 2) {
				int px = spot.getX() + x;
				int pz = spot.getZ() + z;
				if (!level.isLoaded(new BlockPos(px, 0, pz))) return null;
				int y = Ground.surface(level, px, pz);
				if (!level.getBlockState(new BlockPos(px, y, pz)).getFluidState().isEmpty()) return null;
				heights.add(y);
			}
		}
		heights.sort(Integer::compare);
		int low = heights.get(0);
		int high = heights.get(heights.size() - 1);
		if (high - low > 6) return null;
		int mid = heights.get(heights.size() / 2);
		return mid + SpireExterior.height(SpireExterior.FULL) + SpireExterior.CROWN < (level.getMinBuildHeight() + level.getHeight()) ? mid : null;
	}

	/** The turn that sets the gate (north before turning) facing the bell. */
	static Rotation toward(BlockPos centre, BlockPos bell) {
		int dx = bell.getX() - centre.getX();
		int dz = bell.getZ() - centre.getZ();
		if (Math.abs(dz) >= Math.abs(dx)) return dz < 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
		return dx > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
	}

	public static void tick(MinecraftServer server) {
		if (!JOBS.isEmpty()) {
			work(server);
			return;
		}
		if (server.getTickCount() % CHECK_EVERY != 0) return;
		for (Spires.Spire spire : Spires.get(server).all()) {
			ServerLevel level = server.getLevel(spire.dimension);
			if (level == null || !loaded(level, spire.box)) continue;
			int target = Math.min(spire.best, SpireExterior.FULL);
			if (spire.shown == 0) {
				long time = com.vnap.dialogue.Family.timeOfDay(level);
				if (time < DUSK || time > DAWN) continue;
				grow(level, spire, 0, target);
				return;
			}
			if (spire.shown < target) {
				grow(level, spire, spire.shown, target);
				return;
			}
		}
	}

	private static boolean loaded(ServerLevel level, BoundingBox box) {
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				if (!level.isPositionEntityTicking(new BlockPos(cx << 4, box.minY(), cz << 4))) return false;
			}
		}
		return true;
	}

	/** Queues what differs between the spire grown to {@code from} (nothing for 0) and to {@code to}. */
	static void grow(ServerLevel level, Spires.Spire spire, int from, int to) {
		Map<BlockPos, BlockState> before = from <= 0 ? Map.of() : SpireExterior.shape(spire.seed, from);
		Map<BlockPos, BlockState> after = new HashMap<>(SpireExterior.shape(spire.seed, to));
		if (from <= 0) ground(level, spire, after);
		Map<BlockPos, BlockState> change = new HashMap<>();
		for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
			if (!after.containsKey(entry.getKey())) change.put(entry.getKey(), Blocks.AIR.defaultBlockState());
		}
		for (Map.Entry<BlockPos, BlockState> entry : after.entrySet()) {
			if (!entry.getValue().equals(before.get(entry.getKey()))) change.put(entry.getKey(), entry.getValue());
		}
		Deque<Map.Entry<BlockPos, BlockState>> todo = new ArrayDeque<>();
		// from the ground up: what goes on top stands on what is under it
		change.entrySet().stream().sorted(java.util.Comparator.comparingInt(entry -> entry.getKey().getY()))
				.forEach(entry -> {
					BlockPos at = spire.centre.offset(entry.getKey().rotate(spire.rotation));
					todo.add(Map.entry(at, entry.getValue().rotate(level, at, spire.rotation)));
				});
		JOBS.add(new Job(spire.id, todo, to, from <= 0));
		if (from <= 0) com.vnap.village.town.Chronicle.note(level, spire.bell, "spire");
	}

	/**
	 * Its first night: the plaza stands on stone down to the ground on a slope, and trees and plants over the plaza are
	 * cleared away (the Core pushes them aside as the spire comes up).
	 */
	private static void ground(ServerLevel level, Spires.Spire spire, Map<BlockPos, BlockState> shape) {
		BlockState stone = shape.getOrDefault(new BlockPos(0, -1, 0), Blocks.STONE_BRICKS.defaultBlockState());
		int r = SpireExterior.PLAZA + 1;
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z > (r + 0.5) * (r + 0.5)) continue;
				BlockPos column = spire.centre.offset(new BlockPos(x, 0, z).rotate(spire.rotation));
				int ground = Ground.surface(level, column.getX(), column.getZ());
				for (int y = ground + 1; y < spire.centre.getY() - 1 && y > spire.centre.getY() - 16; y++) {
					shape.putIfAbsent(new BlockPos(x, y - spire.centre.getY(), z), stone);
				}
				for (int up = 1; up <= 24; up++) {
					BlockPos at = column.above(up);
					BlockState there = level.getBlockState(at);
					if (there.is(net.minecraft.tags.BlockTags.LOGS) || there.is(net.minecraft.tags.BlockTags.LEAVES) || there.canBeReplaced()) {
						if (!there.isAir()) shape.putIfAbsent(new BlockPos(x, up, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static void work(MinecraftServer server) {
		Job job = JOBS.get(0);
		Spires.Spire spire = Spires.get(server).byId(job.spire);
		ServerLevel level = spire == null ? null : server.getLevel(spire.dimension);
		if (spire == null || level == null) {
			JOBS.remove(0);
			return;
		}
		job.tick++;
		if (job.prelude > 0) {
			prelude(level, spire, job);
			job.prelude--;
			return;
		}
		int top = Integer.MIN_VALUE;
		for (int i = 0; i < job.rate && !job.todo.isEmpty(); i++) {
			Map.Entry<BlockPos, BlockState> next = job.todo.poll();
			level.setBlock(next.getKey(), next.getValue(), 2 | 16 | 32);
			top = Math.max(top, next.getKey().getY());
			// sparks where the stone comes up: one block in eight of the layer
			if (!next.getValue().isAir() && (next.getKey().hashCode() & 7) == 0) {
				level.sendParticles(ParticleTypes.END_ROD, next.getKey().getX() + 0.5, next.getKey().getY() + 1.0, next.getKey().getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.02);
			}
		}
		if (top != Integer.MIN_VALUE && job.tick % 8 == 0) {
			level.playSound(null, new BlockPos(spire.centre.getX(), top, spire.centre.getZ()), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F,
					0.6F + Math.min(1.2F, (top - spire.centre.getY()) / 120.0F));
			if (job.first) shake(level, spire, 0.15F);
		}
		if (!job.todo.isEmpty()) return;
		JOBS.remove(0);
		finale(level, spire, job);
		spire.news = spire.shown <= 0 ? "The Spire Has Risen" : job.to >= SpireExterior.FULL ? "The Beacon Is Lit" : "The Spire Grew";
		spire.shown = job.to;
		Spires.get(server).setDirty();
	}

	/** Before it rises: the ground trembles harder and harder, a column of light gathers over the spot, the town's bell rings by itself. */
	private static void prelude(ServerLevel level, Spires.Spire spire, Job job) {
		BlockPos centre = spire.centre;
		int total = job.first ? PRELUDE : 40;
		float done = 1.0F - job.prelude / (float) total;
		if (job.tick % 10 == 0) shake(level, spire, job.first ? 0.2F + done * 0.8F : 0.25F);
		if (job.tick % 60 == 0) level.playSound(null, centre, com.vnap.sound.SpireSounds.GROW, SoundSource.BLOCKS, 3.0F, 0.4F + done * 0.4F);
		if (job.first && job.tick == 20) {
			BlockPos bell = spire.bell;
			if (level.isLoaded(bell) && level.getBlockState(bell).getBlock() instanceof net.minecraft.world.level.block.BellBlock ringing) {
				ringing.attemptToRing(level, bell, net.minecraft.core.Direction.NORTH);
			}
		}
		if (job.first && job.tick == total - 20) level.playSound(null, centre, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 6.0F, 0.5F);
		// the column of light, as high as the show has gone
		int height = (int) (8 + done * (job.first ? 60 : 10));
		int base = job.first ? 0 : SpireExterior.height(spire.shown);
		for (int i = 0; i < 6; i++) {
			double y = centre.getY() + base + level.getRandom().nextDouble() * height;
			level.sendParticles(ParticleTypes.END_ROD, centre.getX() + 0.5, y, centre.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.01);
		}
		if (job.first) {
			// dust rising off the ground round the spot
			for (int i = 0; i < 4; i++) {
				double angle = level.getRandom().nextDouble() * Math.PI * 2;
				double r = level.getRandom().nextDouble() * SpireExterior.PLAZA;
				level.sendParticles(ParticleTypes.POOF, centre.getX() + 0.5 + Math.cos(angle) * r, centre.getY() + 1.0, centre.getZ() + 0.5 + Math.sin(angle) * r,
						1, 0.1, 0.0, 0.1, 0.02);
			}
		}
	}

	/** It stands: a burst of light at the top, one last shake, the beacon's chord. */
	private static void finale(ServerLevel level, Spires.Spire spire, Job job) {
		BlockPos top = spire.centre.above(SpireExterior.height(job.to) + 2);
		level.sendParticles(ParticleTypes.END_ROD, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, job.first ? 120 : 30, 2.0, 2.0, 2.0, 0.15);
		level.playSound(null, top, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 6.0F, job.first ? 0.6F : 1.0F);
		shake(level, spire, job.first ? 1.0F : 0.3F);
	}

	private static void shake(ServerLevel level, Spires.Spire spire, float intensity) {
		for (net.minecraft.server.level.ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(spire.centre, WITNESS)) {
				net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.vnap.network.ScriptShakePayload(intensity));
			}
		}
	}

	static void clear() {
		JOBS.clear();
	}
}
