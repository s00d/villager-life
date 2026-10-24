package com.vnap.spire;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Every spire there is: the town it grew by (its bell), the seed its floors come from, where its gate stands and the
 * box nobody may break (docs/tower/DESIGN.md). One a town, never grown twice.
 */
public final class Spires extends SavedData {
	private static final String NAME = "villager_life_spires";

	public static final class Spire {
		public final int id;
		public final ResourceKey<Level> dimension;
		public final BlockPos bell;
		public final long seed;
		/** The gate's portal block, and where one who comes out of the spire stands. */
		public BlockPos gate;
		public BlockPos front;
		public BoundingBox box;
		/** The middle of its plaza at ground level, its turn toward the town, and how far it is grown in the world (0: not yet). */
		public BlockPos centre = BlockPos.ZERO;
		public net.minecraft.world.level.block.Rotation rotation = net.minecraft.world.level.block.Rotation.NONE;
		public int shown;
		/** What the town talks about the next morning (a dialogue title: the spire rose, grew, a chapter fell, the beacon), or "". */
		public String news = "";
		/** How high the town's expeditions have got (Expeditions): never above the best its players reached, or ten. */
		public int expeditionFloor;
		/** The highest floor any player of it has opened: the spire outside is as tall as that (SpireExterior). */
		public int best = 1;

		Spire(int id, ResourceKey<Level> dimension, BlockPos bell, long seed, BlockPos gate, BlockPos front, BoundingBox box) {
			this.id = id;
			this.dimension = dimension;
			this.bell = bell;
			this.seed = seed;
			this.gate = gate;
			this.front = front;
			this.box = box;
		}
	}

	private final List<Spire> all = new ArrayList<>();
	private int nextId = 1;

	public static Spires get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Spires::new, Spires::load, Spires::save);
	}

	public List<Spire> all() {
		return all;
	}

	public Spire add(ResourceKey<Level> dimension, BlockPos bell, long seed, BlockPos gate, BlockPos front, BoundingBox box) {
		Spire spire = new Spire(nextId++, dimension, bell.immutable(), seed, gate.immutable(), front.immutable(), box);
		all.add(spire);
		setDirty();
		return spire;
	}

	public @Nullable Spire byId(int id) {
		for (Spire spire : all) if (spire.id == id) return spire;
		return null;
	}

	/** The spire of a town's bell (any bell of the town: within a town's span). */
	public @Nullable Spire ofTown(ResourceKey<Level> dimension, BlockPos bell) {
		for (Spire spire : all) {
			if (spire.dimension.equals(dimension) && spire.bell.closerThan(bell, com.vnap.village.town.TownRules.SPAN)) return spire;
		}
		return null;
	}

	/** The spire whose gate is by this spot. */
	public @Nullable Spire byGate(ResourceKey<Level> dimension, BlockPos pos) {
		Spire best = null;
		for (Spire spire : all) {
			if (spire.dimension.equals(dimension) && spire.gate.closerThan(pos, 8)
					&& (best == null || spire.gate.distSqr(pos) < best.gate.distSqr(pos))) best = spire;
		}
		return best;
	}

	/** The spire this block is part of (its hall's Outer Gates, its plaza), or null. */
	public @Nullable Spire around(ResourceKey<Level> dimension, BlockPos pos) {
		for (Spire spire : all) {
			if (spire.dimension.equals(dimension) && spire.box.isInside(pos)) return spire;
		}
		return null;
	}

	/** Whether a block here is part of a spire (nobody breaks it). */
	public boolean guards(ResourceKey<Level> dimension, BlockPos pos) {
		for (Spire spire : all) {
			if (spire.dimension.equals(dimension) && spire.box.isInside(pos)) return true;
		}
		return false;
	}

	private static net.minecraft.world.level.block.Rotation rotation(String name) {
		for (net.minecraft.world.level.block.Rotation rotation : net.minecraft.world.level.block.Rotation.values()) {
			if (rotation.name().equals(name)) return rotation;
		}
		return net.minecraft.world.level.block.Rotation.NONE;
	}

	private static Spires load(CompoundTag tag, HolderLookup.Provider registries) {
		Spires spires = new Spires();
		spires.nextId = Math.max(1, Nbt.getInt(tag, "Next"));
		for (Tag entry : Nbt.getList(tag, "Spires", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			ResourceLocation dimension = ResourceLocation.tryParse(Nbt.getString(one, "Dim"));
			if (dimension == null) continue;
			int[] box = Nbt.getIntArray(one, "Box");
			if (box.length != 6) continue;
			Spire spire = new Spire(Nbt.getInt(one, "Id"), ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Nbt.getLong(one, "Bell")),
					Nbt.getLong(one, "Seed"), BlockPos.of(Nbt.getLong(one, "Gate")), BlockPos.of(Nbt.getLong(one, "Front")),
					new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]));
			spire.expeditionFloor = Nbt.getInt(one, "Expedition");
			spire.best = Math.max(1, Nbt.getInt(one, "Best"));
			spire.centre = BlockPos.of(Nbt.getLong(one, "Centre"));
			spire.rotation = rotation(Nbt.getString(one, "Turn"));
			spire.shown = Nbt.getInt(one, "Shown");
			spire.news = Nbt.getString(one, "News");
			spires.all.add(spire);
			spires.nextId = Math.max(spires.nextId, spire.id + 1);
		}
		return spires;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putInt("Next", nextId);
		ListTag list = new ListTag();
		for (Spire spire : all) {
			CompoundTag one = new CompoundTag();
			one.putInt("Id", spire.id);
			one.putString("Dim", com.vnap.Mc.id(spire.dimension).toString());
			one.putLong("Bell", spire.bell.asLong());
			one.putLong("Seed", spire.seed);
			one.putLong("Gate", spire.gate.asLong());
			one.putLong("Front", spire.front.asLong());
			BoundingBox box = spire.box;
			one.putIntArray("Box", new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
			one.putInt("Expedition", spire.expeditionFloor);
			one.putInt("Best", spire.best);
			one.putLong("Centre", spire.centre.asLong());
			one.putString("Turn", spire.rotation.name());
			one.putInt("Shown", spire.shown);
			one.putString("News", spire.news);
			list.add(one);
		}
		tag.put("Spires", list);
		return tag;
	}
}
