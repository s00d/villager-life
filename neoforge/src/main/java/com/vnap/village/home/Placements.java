package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Things the village sets down for an occasion (a birthday cake, a gravestone and its name sign): bought when it
 * happens (Market.order), carried there and set down by hand (PlaceBehavior), by whom the occasion names if it can,
 * else by any grown-up of the village. Nobody came in two days (the place out of everyone's way): it is set down then.
 */
public final class Placements extends SavedData {
	private static final String NAME = "villager_life_placements";
	private final List<Job> jobs = new ArrayList<>();

	/** One thing to set down; {@code lines} are a sign's text. */
	public record Job(BlockPos pos, BlockState state, List<String> lines, @Nullable UUID by, long day) {
	}

	public static Placements get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Placements::new, Placements::load, Placements::save);
	}

	public List<Job> jobs() {
		return jobs;
	}

	public static void add(ServerLevel level, BlockPos pos, BlockState state, @Nullable UUID by, String... lines) {
		Placements all = get(level.getServer());
		all.jobs.removeIf(job -> job.pos.equals(pos));
		all.jobs.add(new Job(pos.immutable(), state, List.of(lines), by, Family.day(level)));
		all.setDirty();
	}

	/** Whether something waits to be set down on this cell. */
	public static boolean pending(ServerLevel level, BlockPos pos) {
		for (Job job : get(level.getServer()).jobs) {
			if (job.pos.equals(pos)) return true;
			// a bed and a door go in whole: the cell of their other half is taken too
			if (job.state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
					&& job.pos.relative(job.state.getValue(net.minecraft.world.level.block.BedBlock.FACING)).equals(pos)) return true;
			if (job.state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock && job.pos.above().equals(pos)) return true;
		}
		return false;
	}

	/** Beds waiting for hands within {@code reach} of the bell. */
	public static int pendingBeds(ServerLevel level, BlockPos bell, double reach) {
		return (int) get(level.getServer()).jobs.stream()
				.filter(job -> job.state.getBlock() instanceof net.minecraft.world.level.block.BedBlock && job.pos.closerThan(bell, reach)).count();
	}

	public static void cancel(ServerLevel level, BlockPos pos) {
		Placements all = get(level.getServer());
		if (all.jobs.removeIf(job -> job.pos.equals(pos))) all.setDirty();
	}

	/** The nearest job this villager may take within {@code range}: its own first, anyone's once it waited half a day. */
	public static @Nullable Job forHands(ServerLevel level, Villager villager, double range, java.util.function.Predicate<BlockPos> claimed) {
		// the jobs carry no dimension: they are the overworld's (their tick runs there), a villager elsewhere takes none
		if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return null;
		Job best = null;
		double bestDistance = Double.MAX_VALUE;
		long now = Family.day(level);
		for (Job job : get(level.getServer()).jobs) {
			if (claimed.test(job.pos) || !level.isPositionEntityTicking(job.pos)) continue;
			boolean own = villager.getUUID().equals(job.by);
			boolean anyone = job.by == null || now > job.day || !(level.getEntity(job.by) instanceof Villager by && by.isAlive());
			// the mayor and the keeper set only their own things down: anyone's job took the keeper from its counter
			if (!own && (!anyone || com.vnap.village.life.Life.official(villager))) continue;
			double distance = villager.distanceToSqr(job.pos.getX() + 0.5, job.pos.getY(), job.pos.getZ() + 0.5) - (own ? 1.0E6 : 0.0);
			if (distance > range * range * (own ? 4 : 1) - (own ? 1.0E6 : 0.0)) continue;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = job;
			}
		}
		return best;
	}

	/** Sets the job's thing down (when the cell is still free) and forgets it. */
	public static boolean place(ServerLevel level, Job job) {
		Placements all = get(level.getServer());
		all.jobs.remove(job);
		all.setDirty();
		if (!level.getBlockState(job.pos).canBeReplaced()) return false;
		// a door goes in whole: its upper half with it (alone, the lower half would drop at the next update)
		if (job.state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
				&& job.state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
			if (!level.getBlockState(job.pos.above()).canBeReplaced()) return false;
			level.setBlock(job.pos.above(), job.state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
					net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
		}
		// a bed too: its head with its foot (alone, the foot would drop at the next update)
		if (job.state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
				&& job.state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
			BlockPos head = job.pos.relative(job.state.getValue(net.minecraft.world.level.block.BedBlock.FACING));
			if (!level.getBlockState(head).canBeReplaced()) return false;
			level.setBlock(head, job.state.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), Block.UPDATE_CLIENTS);
		}
		level.setBlock(job.pos, job.state, Block.UPDATE_ALL);
		if (job.state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
			com.vnap.dialogue.FamilyHome.register(level, com.vnap.dialogue.FamilyHome.bedHead(level, job.pos));
		}
		if (job.lines.equals(List.of(com.vnap.village.town.BellMove.MARK))) {
			com.vnap.village.town.BellMove.hung(level, job.pos);
			return true;
		}
		if (job.lines.equals(List.of(com.vnap.village.town.Guards.MARK))) {
			com.vnap.village.town.Guards.raised(level, job.pos);
			return true;
		}
		if (job.lines.equals(List.of(com.vnap.village.town.TownFlag.MARK))) {
			com.vnap.village.town.TownFlag.hung(level, job.pos);
			return true;
		}
		if (!job.lines.isEmpty() && level.getBlockEntity(job.pos) instanceof SignBlockEntity sign) {
			com.vnap.Mc.writeSign(sign, true, job.lines.stream().limit(4).map(Component::literal).toArray(Component[]::new));
			sign.setChanged();
		}
		return true;
	}

	/** Every 100 ticks: what nobody came for in TownRules.PLACE_WAIT_DAYS is set down now. */
	public static void tick(ServerLevel level) {
		Placements all = get(level.getServer());
		long now = Family.day(level);
		for (Job job : new ArrayList<>(all.jobs)) {
			if (now - job.day >= TownRules.PLACE_WAIT_DAYS && level.isPositionEntityTicking(job.pos)) place(level, job);
		}
	}

	private static Placements load(CompoundTag tag, HolderLookup.Provider registries) {
		Placements all = new Placements();
		for (Tag entry : Nbt.getList(tag, "Jobs", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			List<String> lines = new ArrayList<>();
			for (Tag line : Nbt.getList(one, "Lines", Tag.TAG_STRING)) lines.add(com.vnap.Mc.text(line));
			all.jobs.add(new Job(BlockPos.of(Nbt.getLong(one, "Pos")), NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), Nbt.getCompound(one, "State")),
					List.copyOf(lines), Nbt.hasUUID(one, "By") ? Nbt.getUUID(one, "By") : null, Nbt.getLong(one, "Day")));
		}
		return all;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Job job : jobs) {
			CompoundTag one = new CompoundTag();
			one.putLong("Pos", job.pos.asLong());
			one.put("State", NbtUtils.writeBlockState(job.state));
			ListTag lines = new ListTag();
			for (String line : job.lines) lines.add(StringTag.valueOf(line));
			one.put("Lines", lines);
			if (job.by != null) Nbt.putUUID(one, "By", job.by);
			one.putLong("Day", job.day);
			list.add(one);
		}
		tag.put("Jobs", list);
		return tag;
	}
}
