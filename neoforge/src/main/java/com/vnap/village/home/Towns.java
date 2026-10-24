package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import com.vnap.mood.MoodConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import java.util.List;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * A village as a town: its office (Mayors, one per bell cluster) keeps who lives there, loaded or not, so the
 * population limit counts the villagers out of loaded chunks too and never a neighbouring village's. The town reaches
 * further as it grows, and never past half way to the next town's bell: two towns side by side split the land
 * between them, whatever chunks are loaded.
 */
public final class Towns {

	private Towns() {
	}

	/** Every 100 ticks for each villager in a ticking chunk (Family.tickAll): it lives in its bell's town today. */
	public static void seen(ServerLevel level, Villager villager, long day) {
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager, BlockPos.ZERO);
		if (bell.equals(BlockPos.ZERO)) {
			BlockPos home = Family.home(villager);
			if (home == null) return;
			bell = home;
		}
		Mayors mayors = Mayors.get(level.getServer());
		Mayors.Office office = bellOf(villager) != null
				? mayors.officeFor(level.dimension(), bell) : mayors.officeNear(level.dimension(), bell);
		if (office == null) return;
		// vanilla forgets the bell of one that could not walk to it for a minute: the town forgot it in turn (no
		// shelter at night, out of the census), and one of them stood by its walled-up door till dawn
		if (bellOf(villager) == null) villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT,
				net.minecraft.core.GlobalPos.of(level.dimension(), office.bell));
		if (record(mayors, office, villager.getUUID(), day)) com.vnap.village.market.Housing.firstSeen(villager, office.foundedDay, day);
	}

	/** One of the town not here to be seen (up its spire, Expeditions): still counted, still one of it. */
	public static void seen(ServerLevel level, UUID id, BlockPos bell, long day) {
		Mayors mayors = Mayors.get(level.getServer());
		Mayors.Office office = mayors.officeNear(level.dimension(), bell);
		if (office != null) record(mayors, office, id, day);
	}

	/** Marks the resident seen today; true when the town did not know it before. */
	private static boolean record(Mayors mayors, Mayors.Office office, UUID id, long day) {
		Long last = office.residents.put(id, day);
		if (last != null && last == day) return false;
		if (last == null) {
			for (Mayors.Office other : mayors.offices()) {
				if (other != office) other.residents.remove(id);
			}
			if (office.foundedDay == Long.MIN_VALUE) office.foundedDay = day;
		}
		if (office.activeDay != day) {
			office.activeDay = day;
			office.residents.values().removeIf(seenDay -> seenDay < day - TownRules.GONE_DAYS);
		}
		mayors.setDirty();
		return last == null;
	}

	/** The villager is gone from every town: it died, or left for a hamlet. */
	public static void forget(ServerLevel level, UUID villager) {
		Mayors mayors = Mayors.get(level.getServer());
		for (Mayors.Office office : mayors.offices()) {
			if (office.residents.remove(villager) != null) mayors.setDirty();
		}
	}

	/** A newborn counts at once, before its first tick. */
	public static void born(ServerLevel level, Villager baby, BlockPos bell) {
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		if (office == null) return;
		office.residents.put(baby.getUUID(), Family.day(level));
		Mayors.get(level.getServer()).setDirty();
	}

	public static Mayors.@Nullable Office of(ServerLevel level, BlockPos bell) {
		return Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
	}

	/**
	 * The town's people, children and newborns too: its register, or the living villagers of this town loaded right
	 * now if there are more (the register fills in over the first day).
	 */
	public static int population(ServerLevel level, BlockPos bell) {
		Mayors.Office office = of(level, bell);
		double reach = reach(level, bell);
		int loaded = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(reach, 32.0, reach), villager -> villager.isAlive()
				&& (office == null || office.equals(of(level, com.vnap.village.home.Towns.bellOf(villager, villager.blockPosition()))))).size();
		return office == null ? loaded : Math.max(loaded, office.residents.size());
	}

	/** How far from its bell the town reaches: farther as it grows, never past half way to the next town. */
	public static double reach(ServerLevel level, BlockPos bell) {
		Mayors.Office office = of(level, bell);
		int people = office == null ? 0 : office.residents.size();
		// the village the world made is all the town's, and a lot's width round it: its houses stood on the young town's
		// land up to its edge, the plan's streets reached that edge at once, and a town of ten had nowhere to build
		double grown = TownRules.BASE_REACH + Math.max(0, people - 24) * 0.5;
		double world = office == null || office.worldExtent == 0 ? 0 : office.worldExtent + TownRules.LOT;
		double reach = Math.min(TownRules.MAX_REACH, Math.max(grown, world));
		if (office != null) reach = Math.max(reach, office.outskirts);
		BlockPos centre = office == null ? bell : office.bell;
		for (Mayors.Office other : Mayors.get(level.getServer()).offices()) {
			if (other == office || !other.dimension.equals(level.dimension())) continue;
			reach = Math.min(reach, Math.sqrt(other.bell.distSqr(centre)) / 2.0 - 2.0);
		}
		return Math.max(16.0, reach);
	}

	/**
	 * The town's living villagers on its land that {@code filter} takes: the one look round a town (loaded only; the
	 * reach's box, 32 up and down). There were ten copies with their own heights and checks.
	 */
	public static List<Villager> people(ServerLevel level, BlockPos bell, java.util.function.Predicate<Villager> filter) {
		double reach = reach(level, bell);
		Territory land = territory(level, bell);
		return level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(reach, 32.0, reach),
				villager -> villager.isAlive() && land.contains(villager.blockPosition()) && filter.test(villager));
	}

	/**
	 * The loaded villagers who belong to this town (their bell is its), wherever they are about it, that {@code filter}
	 * takes: who may be elected or inherit.
	 */
	public static List<Villager> members(ServerLevel level, BlockPos bell, java.util.function.Predicate<Villager> filter) {
		return level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(TownRules.SPAN + TownRules.NEAR_BELL), villager -> {
			if (!villager.isAlive()) return false;
			BlockPos own = bellOf(villager);
			return own != null && bell.closerThan(own, TownRules.SPAN) && filter.test(villager);
		});
	}

	/** The late villager's spouse and children among the town's members that {@code eligible} takes (an heir's family). */
	public static List<Villager> kin(ServerLevel level, BlockPos bell, Villager late, java.util.function.Predicate<Villager> eligible) {
		UUID id = late.getUUID();
		UUID spouse = com.vnap.dialogue.Family.state(late).spouse;
		return members(level, bell, villager -> !villager.equals(late) && eligible.test(villager)
				&& (villager.getUUID().equals(spouse) || id.equals(com.vnap.dialogue.Family.state(villager).father)
				|| id.equals(com.vnap.dialogue.Family.state(villager).mother)));
	}

	/** This spot is the town's: within its reach and nearer its bell than any other town's. */
	public static boolean ours(ServerLevel level, BlockPos bell, BlockPos pos) {
		return territory(level, bell).contains(pos);
	}

	/**
	 * The town's land as it is now, worked out once ({@link #ours} looks the office up and works out the reach on every
	 * call; a road's A* asked it for every cell it tried, and a town's daily plan took seconds): for a loop over many
	 * points.
	 */
	public static Territory territory(ServerLevel level, BlockPos bell) {
		Mayors.Office office = of(level, bell);
		Mayors mayors = Mayors.get(level.getServer());
		// the same within a tick while nothing it depends on changed: ours() is asked point by point all over the plan
		TerritoryKey key = new TerritoryKey(level.dimension(), bell, level.getGameTime(), mayors.offices().size(),
				office == null ? -1 : office.residents.size(), office == null ? 0 : office.worldExtent + 1000 * office.outskirts);
		Territory cached = TERRITORIES.get(key);
		if (cached != null) return cached;
		if (TERRITORIES.size() > 64) TERRITORIES.clear();
		Territory made = territory(level, bell, office, mayors);
		TERRITORIES.put(key, made);
		return made;
	}

	private record TerritoryKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos bell, long tick,
								int offices, int residents, int worldExtent) {
	}

	private static final java.util.Map<TerritoryKey, Territory> TERRITORIES = new java.util.HashMap<>();

	private static Territory territory(ServerLevel level, BlockPos bell, Mayors.@Nullable Office office, Mayors mayors) {
		BlockPos centre = office == null ? bell : office.bell;
		double reach = reach(level, bell);
		List<BlockPos> others = new java.util.ArrayList<>();
		for (Mayors.Office other : mayors.offices()) {
			if (other != office && other.dimension.equals(level.dimension())) others.add(other.bell);
		}
		return new Territory(centre, reach * reach, List.copyOf(others));
	}

	public record Territory(BlockPos centre, double reachSqr, List<BlockPos> others) {
		public boolean contains(BlockPos pos) {
			double own = horizontal(centre, pos);
			if (own > reachSqr) return false;
			for (BlockPos other : others) if (horizontal(other, pos) < own) return false;
			return true;
		}
	}

	/** The town is full: no more children, no house for the homeless (who may set off for a hamlet). */
	public static boolean full(ServerLevel level, BlockPos bell) {
		return population(level, bell) >= limit(level, bell);
	}

	/** The most people this town takes: what its land holds (TownSurvey), never more than the config's maxVillagers. */
	public static int limit(ServerLevel level, BlockPos bell) {
		return com.vnap.village.town.TownSurvey.limit(com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), bell));
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	static void save(Mayors.Office office, net.minecraft.nbt.CompoundTag tag) {
		net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
		for (Map.Entry<UUID, Long> resident : office.residents.entrySet()) {
			net.minecraft.nbt.CompoundTag one = new net.minecraft.nbt.CompoundTag();
			Nbt.putUUID(one, "U", resident.getKey());
			one.putLong("S", resident.getValue());
			list.add(one);
		}
		tag.put("Residents", list);
		tag.putLong("Active", office.activeDay);
	}

	static void load(Mayors.Office office, net.minecraft.nbt.CompoundTag tag) {
		for (net.minecraft.nbt.Tag entry : Nbt.getList(tag, "Residents", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			net.minecraft.nbt.CompoundTag one = (net.minecraft.nbt.CompoundTag) entry;
			office.residents.put(Nbt.getUUID(one, "U"), Nbt.getLong(one, "S"));
		}
		office.activeDay = Nbt.getLong(tag, "Active");
	}

	/** The bell a villager belongs to (its meeting point, in its own dimension), or null when it knows none. */
	public static @org.jspecify.annotations.Nullable BlockPos bellOf(net.minecraft.world.entity.npc.Villager villager) {
		return villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.MEETING_POINT)
				.filter(point -> point.dimension().equals(villager.level().dimension())).map(net.minecraft.core.GlobalPos::pos).orElse(null);
	}

	/** {@link #bellOf(net.minecraft.world.entity.npc.Villager)}, or {@code fallback}. */
	public static BlockPos bellOf(net.minecraft.world.entity.npc.Villager villager, BlockPos fallback) {
		BlockPos bell = bellOf(villager);
		return bell == null ? fallback : bell;
	}
}
