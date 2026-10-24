package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyDeaths;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * One mayor a village, chosen among its own people: when the office of a bell is empty, the eldest adult without a
 * trade takes it. The office is kept here, not on the villager, so a mayor in an unloaded chunk still holds it and a
 * second one is never elected meanwhile. The mayor builds a finer house, speaks at the bell in the morning, looks
 * round the village after work and leads weddings and funerals. A mayor who died, left for a hamlet, went missing or
 * stayed homeless for days is replaced.
 */
public final class Mayors extends SavedData {
	private static final String NAME = "villager_life_mayors";
	/** The mayor's halls (structure/village/house/): the big one, and a smaller one for hilly and crowded villages. */
	public static final List<String> HALLS = List.of("mayor_house", "mayor_lodge");
	private static final String SPEECH = "vnap_mayor_speech";
	private static final String PRAISED = "vnap_mayor_praised";
	private static final String ROUND = "vnap_mayor_round";
	private static final String PAYDAY = "vnap_mayor_payday";

	private final List<Office> offices = new ArrayList<>();

	/** A village's office: its bell and who holds it. */
	public static final class Office {
		public final ResourceKey<Level> dimension;
		public BlockPos bell;
		public @Nullable UUID mayor;
		/** The late mayor's young child the office waits for: no election while it grows up. */
		public @Nullable UUID heir;
		public long heirSeenDay;
		public String name = "";
		public long electedDay;
		public long seenDay;
		public long lastElection = Long.MIN_VALUE;
		public long homelessSince = Long.MIN_VALUE;
		/** The village's money in copper (village/market/Market): wages out, the stall's takings in; -1 until opened. */
		public int treasury = -1;
		/** Copper the village made up when the treasury ran short (it sold its produce): only for the record. */
		public long minted;
		/** Who lives in this village and the last day each was seen in a ticking chunk (Towns). */
		public final java.util.Map<UUID, Long> residents = new java.util.HashMap<>();
		public long activeDay = Long.MIN_VALUE;
		/** How far from the bell the village the world made stands (WorldHouses): the town's land takes it all in. */
		public int worldExtent;
		/** How far from the bell a town building had to go when the town had no room left inside (TownPlots): its land reaches it. */
		public int outskirts;
		/** The village day the town first saw its people: who it knew then are its founders (Housing). */
		public long foundedDay = Long.MIN_VALUE;
		/** The last day its founders were made households (Family.founders): once a day over the founding days. */
		public long householdsDay = Long.MIN_VALUE;

		public Office(ResourceKey<Level> dimension, BlockPos bell) {
			this.dimension = dimension;
			this.bell = bell.immutable();
		}
	}

	public static Mayors get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Mayors::new, Mayors::load, Mayors::save);
	}

	public List<Office> offices() {
		return offices;
	}

	public static boolean isMayor(Villager villager) {
		return villager instanceof VillagerLifeData data && data.vnap$mayor();
	}

	public @Nullable Office officeNear(ResourceKey<Level> dimension, BlockPos bell) {
		Office best = null;
		for (Office office : offices) {
			if (office.dimension.equals(dimension) && office.bell.closerThan(bell, TownRules.SPAN)
					&& (best == null || office.bell.distSqr(bell) < best.bell.distSqr(bell))) best = office;
		}
		return best;
	}

	/** The office of the village of this bell, opened (empty) when the village has none yet. */
	public Office officeFor(ResourceKey<Level> dimension, BlockPos bell) {
		Office office = officeNear(dimension, bell);
		if (office == null) {
			office = new Office(dimension, bell);
			offices.add(office);
			setDirty();
		}
		return office;
	}

	public @Nullable Office officeOf(UUID mayor) {
		for (Office office : offices) if (mayor.equals(office.mayor)) return office;
		return null;
	}

	/** The mayor of the village around {@code pos}, if loaded here. */
	public static @Nullable Villager mayorNear(ServerLevel level, BlockPos pos) {
		Office office = get(level.getServer()).officeNear(level.dimension(), pos);
		return office != null && office.mayor != null && level.getEntity(office.mayor) instanceof Villager mayor && mayor.isAlive()
				&& isMayor(mayor) ? mayor : null;
	}

	// ---- elections ---------------------------------------------------------------------

	/** Once a day for every awake adult in a ticking chunk (Family.daily). */
	public static void daily(ServerLevel level, Villager villager, long day) {
		if (com.vnap.Mc.tags(villager).contains(Speech.TEST_TAG)) return;
		Mayors mayors = get(level.getServer());
		if (villager.isBaby()) {
			Office waiting = mayors.waitingFor(villager.getUUID());
			if (waiting != null) {
				waiting.heirSeenDay = day;
				mayors.setDirty();
			}
			return;
		}
		Office waiting = mayors.waitingFor(villager.getUUID());
		if (waiting != null && waiting.mayor == null) {
			// the young heir came of age: the office it waited for is its own
			mayors.take(level, waiting, villager, day, true);
			return;
		}
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		if (bell != null) mayors.merge(level.dimension(), bell);
		if (isMayor(villager)) {
			mayors.keep(level, villager, bell, day);
			return;
		}
		if (bell == null) return;
		Office office = mayors.officeFor(level.dimension(), bell);
		mayors.vacateIfGone(level, office, day);
		if (office.foundedDay != Long.MIN_VALUE && day - office.foundedDay <= TownRules.FOUNDING_DAYS + 2 && office.householdsDay != day) {
			office.householdsDay = day;
			com.vnap.dialogue.Family.founders(level, office.bell, day);
		}
		com.vnap.village.town.Roads.daily(level, office, day);
		if (office.mayor == null && office.heir == null && office.lastElection != day) mayors.elect(level, office, day, null);
	}

	/**
	 * Offices of one village (saves from when villages were counted by 32 blocks): the one whose mayor has served
	 * longest stays, the others are closed; their mayors find no office and step down (keep).
	 */
	private void merge(ResourceKey<Level> dimension, BlockPos bell) {
		List<Office> here = offices.stream().filter(office -> office.dimension.equals(dimension) && office.bell.closerThan(bell, TownRules.SPAN)).toList();
		if (here.size() < 2) return;
		Office stays = here.stream().min(Comparator.comparing((Office office) -> office.mayor == null && office.heir == null)
				.thenComparingLong(office -> office.electedDay)).orElse(here.get(0));
		for (Office office : here) {
			if (office != stays && office.treasury > 0) stays.treasury = Math.max(0, stays.treasury) + office.treasury;
			if (office != stays) office.residents.forEach((who, seen) -> stays.residents.merge(who, seen, Math::max));
		}
		offices.removeIf(office -> here.contains(office) && office != stays);
		setDirty();
	}

	private @Nullable Office waitingFor(UUID heir) {
		for (Office office : offices) if (heir.equals(office.heir)) return office;
		return null;
	}

	/** The mayor's own daily check: still the one in office, still in its village, not homeless for long. */
	private void keep(ServerLevel level, Villager mayor, @Nullable BlockPos bell, long day) {
		Office office = officeOf(mayor.getUUID());
		if (office == null) {
			// the record was lost (or the flag set by hand): the office is taken again unless someone else holds it
			Office near = bell == null ? null : officeNear(level.dimension(), bell);
			if (near != null) vacateIfGone(level, near, day);
			if (near == null && bell != null) {
				near = new Office(level.dimension(), bell);
				offices.add(near);
			}
			if (near == null || near.mayor != null) {
				resign(level, mayor, day, false);
				return;
			}
			take(level, near, mayor, day);
			office = near;
		}
		if (bell != null && (!office.dimension.equals(level.dimension()) || !office.bell.closerThan(bell, TownRules.SPAN))) {
			// moved to another village: the old one elects a new mayor
			resign(level, mayor, day, true);
			return;
		}
		office.seenDay = day;
		setDirty();
		closeDeadOwners(level, office.bell);
		house(level, mayor, office.bell, day);
		// a homeless mayor whose hall is going up is not homeless for long; one with no hall and no bed steps down
		if (Family.home(mayor) != null || building(level, mayor)) {
			office.homelessSince = Long.MIN_VALUE;
			return;
		}
		if (office.homelessSince == Long.MIN_VALUE) office.homelessSince = day;
		if (day - office.homelessSince >= TownRules.MAYOR_HOMELESS_DAYS) resign(level, mayor, day, true);
	}

	/**
	 * The mayor's daily round of the register: a house the villagers built whose owner died, with nobody of its
	 * household living there, still carried its home sign (deaths before 18.10.2026 freed nothing) and stood empty
	 * for good. Its sign comes down and the bed is free for someone without a home.
	 */
	private static void closeDeadOwners(ServerLevel level, BlockPos bell) {
		FamilyDeaths deaths = FamilyDeaths.get(level.getServer());
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done || !house.bell.closerThan(bell, TownRules.SPAN) || !deaths.contains(house.owner)) continue;
			BlockPos bed = house.bed(level);
			if (bed == null || !level.isLoaded(bed)) continue;
			var box = house.footprint(level);
			boolean lived = !level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(TownRules.NEAR_BELL), villager -> {
				BlockPos own = villager instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
				BlockPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
				return villager.isAlive() && ((own != null && box.isInside(own)) || (home != null && box.isInside(home)));
			}).isEmpty();
			if (lived) continue;
			BlockPos sign = com.vnap.dialogue.FamilyHome.findSign(level, bed, null, "");
			if (sign == null) continue;
			level.removeBlock(sign, false);
			com.vnap.dialogue.FamilyHome.release(level, bed);
			VillagerHome.noteStructureChange();
		}
	}

	private static boolean building(ServerLevel level, Villager mayor) {
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done && house.owner.equals(mayor.getUUID())) return true;
		}
		return false;
	}

	/** A dead mayor (or one long missing, or one whose flag is gone) leaves the office empty. */
	private void vacateIfGone(ServerLevel level, Office office, long day) {
		UUID heir = office.heir;
		if (heir != null && (FamilyDeaths.get(level.getServer()).contains(heir)
				|| (level.getEntity(heir) == null && day - office.heirSeenDay > TownRules.GONE_DAYS))) {
			// the young heir died or was carried off: the village elects after all
			office.heir = null;
			office.lastElection = Long.MIN_VALUE;
			setDirty();
		}
		UUID id = office.mayor;
		if (id == null) return;
		boolean dead = FamilyDeaths.get(level.getServer()).contains(id);
		boolean unflagged = level.getEntity(id) instanceof Villager loaded && (!loaded.isAlive() || !isMayor(loaded));
		boolean missing = level.getEntity(id) == null && day - office.seenDay > TownRules.GONE_DAYS;
		if (!dead && !unflagged && !missing) return;
		office.mayor = null;
		office.name = "";
		office.lastElection = Long.MIN_VALUE;
		setDirty();
	}

	/** Holds the election now among the loaded people of the village; returns the new mayor or null. */
	public @Nullable Villager elect(ServerLevel level, Office office, long day, @Nullable UUID except) {
		office.lastElection = day;
		setDirty();
		if (!office.dimension.equals(level.dimension())) return null;
		List<Villager> adults = Towns.members(level, office.bell, villager -> !villager.isBaby()
				&& !com.vnap.Mc.tags(villager).contains(Speech.TEST_TAG));
		if (adults.size() < TownRules.MAYOR_MIN_ADULTS) return null;
		// the one stepping down still counts as one of the village, it just cannot be chosen again
		Comparator<Villager> eldest = Comparator.comparingLong((Villager villager) -> Family.state(villager).bornDay).thenComparing(Villager::getUUID);
		Villager chosen = adults.stream().filter(villager -> !villager.getUUID().equals(except) && candidate(villager)).min(eldest).orElse(null);
		// a village where everyone has a trade (a snowy one of five workers stayed without a mayor for good): the eldest
		// leaves its trade for the office, as an heir does, and takes it back on stepping down
		if (chosen == null) {
			chosen = adults.stream().filter(villager -> !villager.getUUID().equals(except) && electable(villager)
					&& !com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NITWIT)).min(eldest).orElse(null);
		}
		if (chosen != null) take(level, office, chosen, day);
		return chosen;
	}

	/** A grown-up without a trade (no job site, not a nitwit), who is not leaving and not already someone's mayor. */
	public static boolean candidate(Villager villager) {
		return electable(villager) && com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE)
				&& !villager.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE);
	}

	private static boolean electable(Villager villager) {
		return !villager.isBaby() && villager.isAlive() && !isMayor(villager) && !com.vnap.village.market.Shops.isKeeper(villager)
				&& !Hamlets.leaving(villager);
	}

	/**
	 * The office passes down: to the eldest grown child of the late mayor living in the village, else to its spouse,
	 * else to its eldest young child (who takes it on coming of age). Null when nobody of the family is left (here
	 * and loaded): then the village elects.
	 */
	public @Nullable Villager heir(ServerLevel level, Office office, Villager late) {
		if (!office.dimension.equals(level.dimension())) return null;
		UUID spouse = Family.state(late).spouse;
		List<Villager> family = Towns.kin(level, office.bell, late, villager -> !isMayor(villager)
				&& !com.vnap.village.market.Shops.isKeeper(villager) && !Hamlets.leaving(villager));
		// grown children first, then the spouse, then the young; the eldest of each
		return family.stream()
				.min(Comparator.comparingInt((Villager villager) -> villager.isBaby() ? 2 : villager.getUUID().equals(spouse) ? 1 : 0)
						.thenComparingLong(villager -> Family.state(villager).bornDay)
						.thenComparing(Villager::getUUID))
				.orElse(null);
	}

	/** The late mayor's heir takes the office, or the village elects one. */
	public static @Nullable Villager succeed(ServerLevel level, Office office, Villager late, long day) {
		Mayors mayors = get(level.getServer());
		Villager heir = mayors.heir(level, office, late);
		if (heir == null) return mayors.elect(level, office, day, late.getUUID());
		if (heir.isBaby()) {
			// the office waits for the child: it grows up in the hall and takes the office the day it is grown
			office.heir = heir.getUUID();
			office.heirSeenDay = day;
			mayors.setDirty();
			return null;
		}
		mayors.take(level, office, heir, day, true);
		return heir;
	}

	private void take(ServerLevel level, Office office, Villager mayor, long day) {
		take(level, office, mayor, day, false);
	}

	private void take(ServerLevel level, Office office, Villager mayor, long day, boolean inherited) {
		office.heir = null;
		office.mayor = mayor.getUUID();
		office.name = VillagerHome.nameOf(mayor);
		office.electedDay = day;
		com.vnap.village.town.Chronicle.note(level, office.bell, inherited ? "mayor_heir" : "mayor", office.name);
		office.seenDay = day;
		office.homelessSince = Long.MIN_VALUE;
		setDirty();
		if (mayor instanceof VillagerLifeData data) data.vnap$setMayor(true);
		leaveJobSites(mayor);
		com.vnap.dialogue.Trades.mayorOffers(mayor);
		// an heir who grew up in the hall of its late parent heads that house now
		if (inherited) Family.takeOver(level, mayor);
		Speech.queue(mayor, inherited ? "Mayor Takes Up the Office" : "Mayor Elected", null, 2L);
		level.getEntitiesOfClass(Villager.class, mayor.getBoundingBox().inflate(16.0),
						other -> !other.equals(mayor) && other.isAlive() && !other.isBaby() && !other.isSleeping())
				.stream().min(Comparator.comparingDouble(other -> other.distanceToSqr(mayor)))
				.ifPresent(cheer -> Speech.queue(cheer, "Villager Cheers the New Mayor", mayor, 6L));
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, mayor.getBoundingBox().inflate(TownRules.NEAR_BELL))) {
			com.vnap.Mc.say(player, Component.translatable(inherited ? "vnap.mayor.inherited" : "vnap.mayor.elected", office.name), true);
		}
	}

	/**
	 * Leaves the office alive (homeless, moved away, by command): the village elects someone else at once if it can.
	 * The office passes down in the family only when the mayor dies or leaves the village. {@code say}: a farewell.
	 */
	public static void resign(ServerLevel level, Villager mayor, long day, boolean say) {
		Office office = step(level, mayor);
		if (say) Speech.queue(mayor, "Mayor Resigns", null, 2L);
		if (office != null) get(level.getServer()).elect(level, office, day, mayor.getUUID());
	}

	/** Out of office without an election: the office it left, or null. */
	private static @Nullable Office step(ServerLevel level, Villager mayor) {
		Mayors mayors = get(level.getServer());
		if (mayor instanceof VillagerLifeData data) data.vnap$setMayor(false);
		com.vnap.dialogue.Trades.mayorOffers(mayor);
		Office office = mayors.officeOf(mayor.getUUID());
		if (office == null) return null;
		office.mayor = null;
		office.name = "";
		mayors.setDirty();
		return office;
	}

	public static void onDeath(ServerLevel level, Villager villager) {
		if (!isMayor(villager)) return;
		Mayors mayors = get(level.getServer());
		Office office = mayors.officeOf(villager.getUUID());
		if (office == null) return;
		office.mayor = null;
		office.name = "";
		mayors.setDirty();
		succeed(level, office, villager, Family.day(level));
	}

	/**
	 * The mayor leaves for a hamlet: it arrives there an ordinary settler. The office it left is returned to be passed
	 * on ({@link #succeed}) once all the settlers are gone (one of them must not be chosen on its way out).
	 */
	public static @Nullable Office onDepart(ServerLevel level, Villager villager) {
		return isMayor(villager) ? step(level, villager) : null;
	}

	// ---- the mayor's house -------------------------------------------------------------

	/** The furnishing sets of a hall, in its own frame: the mayor's office upstairs, the family's home downstairs. */
	public static final String OFFICE = "office";
	public static final String HOME = "home";

	/** The hall {@code pos} lies in, or null. */
	public static @Nullable HouseProject hallAt(ServerLevel level, BlockPos pos) {
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (house.done && HALLS.contains(house.plan) && house.footprint(level).isInside(pos)) return house;
		}
		return null;
	}

	/**
	 * The pieces of a furnishing set still to go in, in build order (what stands on or hangs from another goes
	 * after it); its first is the next to place ({@code placeNext}). Null once the set is all in: it is marked done
	 * and never redone (a family may move things about, a player may take a chair).
	 */
	public static com.vnap.village.build.@Nullable BuildJob furnishing(ServerLevel level, HouseProject hall, String part) {
		int bit = part.equals(OFFICE) ? 1 : 2;
		if ((hall.furnished & bit) != 0) return null;
		String name = HousePalette.schematicName(hall.plan + "_" + part, hall.palette);
		var spec = com.vnap.village.workshop.Workshops.schematic(level, name);
		var job = com.vnap.village.build.BuildJob.missing(level, name, spec, hall.origin, hall.rotation, -1);
		if (!spec.blocks.isEmpty() && !job.done()) return job;
		hall.furnished |= bit;
		VillageMoodSavedData.get(level).markDirty();
		return null;
	}

	/** Whether {@code pos} lies in a mayor's hall. */
	public static boolean inHall(ServerLevel level, BlockPos pos) {
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (house.done && HALLS.contains(house.plan) && house.footprint(level).isInside(pos)) return true;
		}
		return false;
	}

	/** The mayor's finished house it lives in, or null. */
	public static @Nullable HouseProject hall(ServerLevel level, Villager mayor) {
		BlockPos own = mayor instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
		BlockPos family = Family.home(mayor);
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!house.done || !HALLS.contains(house.plan)) continue;
			// its own bed or the house it lives in stands in the hall: a widow or a child who took the office stays there
			var box = house.footprint(level);
			if ((own != null && box.isInside(own)) || (family != null && box.isInside(family))) return house;
		}
		return null;
	}

	/** A mayor without a hall of its own has one built near the bell, by its own hands. */
	private static void house(ServerLevel level, Villager mayor, BlockPos bell, long day) {
		if (hall(level, mayor) != null) return;
		for (HouseProject other : VillageMoodSavedData.get(level).houses()) {
			// one at a time; a hall it finished but could not move into is not built again
			if (other.owner.equals(mayor.getUUID()) && (!other.done || HALLS.contains(other.plan))) return;
		}
		// one hall a village: an empty one the mayor moves into; one still lived in (a late mayor's widow, a mayor
		// who stepped down) or still going up is not built a second time
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			if (!HALLS.contains(house.plan) || !house.bell.closerThan(bell, TownRules.SPAN)) continue;
			// a hall a late or former mayor began is the village's: this mayor builds on
			if (!house.done && !house.owner.equals(mayor.getUUID())) {
				house.owner = mayor.getUUID();
				VillageMoodSavedData.get(level).markDirty();
			}
			BlockPos bed = house.done ? house.bed(level) : null;
			if (bed != null && level.isLoaded(bed) && VillagerHome.claimable(level, bed)) HomeProjects.moveIn(level, mayor, house);
			return;
		}
		HouseProject started = planHall(level, mayor, bell, day);
		if (started != null) {
			Speech.queue(mayor, "Mayor Lays the First Stone", null, 6L);
			return;
		}
		// tried again every day until ground frees up; it says so now and then
		if (day % 3 == 0) Speech.queue(mayor, "Mayor Has No Room for the Hall", null, 2L);
	}

	/** The hall's project: on free ground, else where a house is to be taken down first (clearGround); null when neither. */
	public static @Nullable HouseProject planHall(ServerLevel level, Villager mayor, BlockPos bell, long day) {
		HouseProject started = HomeProjects.start(level, mayor, day, HALLS);
		return started != null ? started : clearGround(level, mayor, bell, day);
	}

	/**
	 * No free ground for a hall: a house is taken down to make room, by hand (HouseBehavior: the mayor and the
	 * villagers without a trade take it apart before the hall goes up). First an empty one the villagers built (its
	 * blocks are known), then an empty house of the world, last the mayor's own house (the family sleeps where it can
	 * while the hall goes up). The hall's project, or null when none of them will do.
	 */
	private static @Nullable HouseProject clearGround(ServerLevel level, Villager mayor, BlockPos bell, long day) {
		for (HouseProject empty : HomeProjects.emptyHouses(level, bell)) {
			var box = empty.footprint(level);
			HouseProject hall = HomeProjects.planAt(level, mayor, day, HALLS, centreOf(box, empty.origin.getY()), empty.rotation, box);
			if (hall != null) {
				HomeProjects.clearForHall(level, hall, empty);
				return cleared(level, mayor, hall, "Mayor Clears an Empty House");
			}
		}
		for (var world : HomeProjects.worldHouses(level, bell, null)) {
			int floor = floorOf(level, world);
			HouseProject hall = HomeProjects.planAt(level, mayor, day, HALLS, centreOf(world, floor), null, world);
			if (hall != null) {
				HomeProjects.clearForHall(level, hall, world, floor);
				return cleared(level, mayor, hall, "Mayor Clears an Empty House");
			}
		}
		if (!Family.isHead(mayor)) return null;
		HouseProject own = FamilyRooms.houseOf(level, mayor);
		if (own != null && !HALLS.contains(own.plan) && HomeProjects.onlyItsOwn(level, own)) {
			var box = own.footprint(level);
			HouseProject hall = HomeProjects.planAt(level, mayor, day, HALLS, centreOf(box, own.origin.getY()), own.rotation, box);
			if (hall != null) {
				Family.leaveForNewHouse(level, mayor);
				HomeProjects.clearForHall(level, hall, own);
				return cleared(level, mayor, hall, "Mayor Rebuilds on the Old Ground");
			}
			return null;
		}
		BlockPos bed = mayor instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
		var ownWorld = own == null && bed != null ? HomeProjects.piece(level, bed) : null;
		if (ownWorld != null && HomeProjects.canClear(level, ownWorld, mayor)) {
			int floor = floorOf(level, ownWorld);
			HouseProject hall = HomeProjects.planAt(level, mayor, day, HALLS, centreOf(ownWorld, floor), null, ownWorld);
			if (hall != null) {
				Family.leaveForNewHouse(level, mayor);
				HomeProjects.clearForHall(level, hall, ownWorld, floor);
				return cleared(level, mayor, hall, "Mayor Rebuilds on the Old Ground");
			}
		}
		return null;
	}

	private static HouseProject cleared(ServerLevel level, Villager mayor, HouseProject hall, String title) {
		HomeProjects.register(level, hall);
		Speech.queue(mayor, title, null, 2L);
		return hall;
	}

	static BlockPos centreOf(net.minecraft.world.level.levelgen.structure.BoundingBox box, int floor) {
		return new BlockPos((box.minX() + box.maxX()) / 2, floor, (box.minZ() + box.maxZ()) / 2);
	}

	/** A world house's floor: one below its lowest bed. */
	static int floorOf(ServerLevel level, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
		int floor = Integer.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BedBlock) floor = Math.min(floor, pos.getY() - 1);
		}
		return floor == Integer.MAX_VALUE ? box.minY() : floor;
	}

	// ---- duties ------------------------------------------------------------------------

	/** Every family tick for a loaded, awake villager (Family.tick). */
	public static void tick(ServerLevel level, Villager villager) {
		if (!isMayor(villager)) return;
		leaveJobSites(villager);
		tether(level, villager);
	}

	/**
	 * A villager without a trade keeps booking job sites and walking to them (vanilla); a mayor's trade is fixed
	 * (VillagerDataMixin), so each booking only kept a lectern or a composter from someone who wants it.
	 */
	public static void leaveJobSites(Villager mayor) {
		for (MemoryModuleType<GlobalPos> site : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE)) {
			if (!mayor.getBrain().hasMemoryValue(site)) continue;
			mayor.releasePoi(site);
			mayor.getBrain().eraseMemory(site);
		}
	}

	/**
	 * A mayor keeps to its village. Without a trade vanilla lets it stroll all day, and it ended up far out in the
	 * fields; one gone past the village's edge with nowhere to go walks back to the bell.
	 */
	public static void tether(ServerLevel level, Villager mayor) {
		// a mayor minding her newborn stays by its cradle
		if (!isMayor(mayor) || com.vnap.dialogue.Infants.cradleOf(level, mayor) != null) return;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(mayor);
		if (bell == null || mayor.blockPosition().closerThan(bell, Towns.reach(level, bell) + 8.0)) return;
		// a walk somewhere nearer the bell is fine; one leading further out is not (the old mayor strolled off for good)
		BlockPos goal = mayor.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(walk -> walk.getTarget().currentBlockPosition()).orElse(null);
		if (goal != null && goal.distSqr(bell) < mayor.blockPosition().distSqr(bell)) return;
		for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
			// at work on its own hall, which may stand at the village's very edge
			if (!house.done && house.owner.equals(mayor.getUUID()) && house.origin.closerThan(mayor.blockPosition(), 24.0)) return;
		}
		com.vnap.village.life.Agenda.go(mayor, "tether", bell, com.vnap.village.life.Agenda.ROUTINE, null, null);
	}

	/**
	 * With a player around (Family.ambient): mornings the mayor speaks at the bell and a villager answers; after work
	 * it pays the wages at the bell (a village with a stall) and then looks in on a house going up or on a villager at
	 * work and has a word for them.
	 */
	public static void ambient(ServerLevel level, Villager mayor, float time, long day) {
		if (!isMayor(mayor)) return;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(mayor);
		if (bell == null) return;
		if (time >= 0.02F && time < 0.08F) {
			if (doneToday(mayor, SPEECH, day)) return;
			// not every morning: a speech every day is a clock, not a mayor
			if (!com.vnap.dialogue.Temperament.scene(mayor, "speech", day, 0.5F)) {
				mayor.getPersistentData().putLong(SPEECH, day);
				return;
			}
			if (!mayor.blockPosition().closerThan(bell, 4.0)) {
				com.vnap.village.life.Agenda.go(mayor, "speech", bell, com.vnap.village.life.Agenda.ROUTINE, null, null);
				return;
			}
			mayor.getPersistentData().putLong(SPEECH, day);
			Speech.queue(mayor, "Mayor Morning Speech", null, 1L);
			level.getEntitiesOfClass(Villager.class, mayor.getBoundingBox().inflate(12.0),
							other -> !other.equals(mayor) && other.isAlive() && !other.isBaby() && !other.isSleeping())
					.stream().min(Comparator.comparingDouble(other -> other.distanceToSqr(mayor)))
					.ifPresent(listener -> Speech.queue(listener, "Villager Answers the Mayor", mayor, 5L));
			return;
		}
		if (time >= 0.375F && time < 0.42F && com.vnap.village.market.Shops.near(level, bell) != null) {
			// payday: the mayor waits at the bell and the workers come for their wages (Market.payWage)
			if (!com.vnap.village.life.Agenda.has(mayor, "payday") && !doneToday(mayor, PAYDAY, day)) {
				// waits at the bell through the payday's hour
				com.vnap.village.life.Agenda.go(mayor, "payday", bell, com.vnap.village.life.Agenda.ROUTINE, com.vnap.village.life.Agenda.until(0.42F), null);
			}
			if (!mayor.blockPosition().closerThan(bell, 3.0)) {
				return;
			} else if (!doneToday(mayor, PAYDAY, day)) {
				mayor.getPersistentData().putLong(PAYDAY, day);
				Speech.queue(mayor, "Mayor Pays Wages", null, 1L);
			}
			return;
		}
		if (time < 0.42F || time >= 0.5F || doneToday(mayor, ROUND, day)) return;
		HouseProject building = HomeProjects.active(level, bell);
		if (building != null && building.owner.equals(mayor.getUUID())) building = null;
		BlockPos goal;
		String title;
		Villager worker = null;
		if (building != null) {
			goal = building.origin;
			title = "Mayor Inspects the Building";
		} else {
			List<Villager> workers = level.getEntitiesOfClass(Villager.class, new AABB(bell).inflate(TownRules.NEAR_BELL),
					other -> !other.equals(mayor) && other.isAlive() && !other.isBaby() && !other.isSleeping()
							&& !com.vnap.Mc.is(other.getVillagerData(), VillagerProfession.NONE)
							&& !com.vnap.Mc.is(other.getVillagerData(), VillagerProfession.NITWIT));
			if (workers.isEmpty()) return;
			// a worker the mayor picks for the day (the same all day), not the one it praised last time
			workers.sort(Comparator.comparing(Villager::getUUID));
			int pick = (int) Math.floorMod(java.util.Objects.hash(mayor.getUUID(), day), (long) workers.size());
			String last = Nbt.getString(mayor.getPersistentData(), PRAISED);
			if (workers.size() > 1 && workers.get(pick).getUUID().toString().equals(last)) pick = (pick + 1) % workers.size();
			worker = workers.get(pick);
			goal = worker.blockPosition();
			title = "Mayor Praises a Worker";
		}
		if (!mayor.blockPosition().closerThan(goal, 4.0)) {
			com.vnap.village.life.Agenda.go(mayor, "round", goal, com.vnap.village.life.Agenda.ROUTINE, null, null);
			return;
		}
		mayor.getPersistentData().putLong(ROUND, day);
		if (worker != null) mayor.getPersistentData().putString(PRAISED, worker.getUUID().toString());
		Speech.queue(mayor, title, worker, 1L);
	}

	private static boolean doneToday(Villager mayor, String key, long day) {
		return mayor.getPersistentData().contains(key) && Nbt.getLong(mayor.getPersistentData(), key) == day;
	}

	/** At a wedding by the bell the mayor speaks first; returns how many ticks the vows wait. */
	public static long officiate(ServerLevel level, Villager bride, Villager groom, BlockPos bell) {
		Villager mayor = mayorNear(level, bell);
		if (mayor == null || mayor.equals(bride) || mayor.equals(groom) || mayor.isSleeping() || !mayor.closerThan(bride, 24.0)) return 0L;
		return Speech.queue(mayor, "Mayor Weds the Couple", bride, 1L) ? 4L : 0L;
	}

	private static Mayors load(CompoundTag tag, HolderLookup.Provider registries) {
		Mayors mayors = new Mayors();
		for (Tag entry : Nbt.getList(tag, "Offices", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			ResourceLocation dimension = ResourceLocation.tryParse(Nbt.getString(one, "Dim"));
			if (dimension == null) continue;
			Office office = new Office(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Nbt.getLong(one, "Bell")));
			office.mayor = Nbt.hasUUID(one, "Mayor") ? Nbt.getUUID(one, "Mayor") : null;
			office.heir = Nbt.hasUUID(one, "Heir") ? Nbt.getUUID(one, "Heir") : null;
			office.heirSeenDay = Nbt.getLong(one, "HeirSeen");
			office.name = Nbt.getString(one, "Name");
			office.electedDay = Nbt.getLong(one, "Elected");
			office.seenDay = Nbt.getLong(one, "Seen");
			office.lastElection = one.contains("LastElection") ? Nbt.getLong(one, "LastElection") : Long.MIN_VALUE;
			office.homelessSince = one.contains("Homeless") ? Nbt.getLong(one, "Homeless") : Long.MIN_VALUE;
			office.treasury = one.contains("Treasury") ? Nbt.getInt(one, "Treasury") : -1;
			office.worldExtent = Nbt.getInt(one, "WorldExtent");
			office.outskirts = Nbt.getInt(one, "Outskirts");
			office.minted = Nbt.getLong(one, "Minted");
			office.foundedDay = one.contains("Founded") ? Nbt.getLong(one, "Founded") : Long.MIN_VALUE;
			Towns.load(office, one);
			mayors.offices.add(office);
		}
		return mayors;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Office office : offices) {
			CompoundTag one = new CompoundTag();
			one.putString("Dim", com.vnap.Mc.id(office.dimension).toString());
			one.putLong("Bell", office.bell.asLong());
			if (office.mayor != null) Nbt.putUUID(one, "Mayor", office.mayor);
			if (office.heir != null) Nbt.putUUID(one, "Heir", office.heir);
			one.putLong("HeirSeen", office.heirSeenDay);
			one.putString("Name", office.name);
			one.putLong("Elected", office.electedDay);
			one.putLong("Seen", office.seenDay);
			one.putLong("LastElection", office.lastElection);
			one.putLong("Homeless", office.homelessSince);
			one.putInt("Treasury", office.treasury);
			one.putInt("WorldExtent", office.worldExtent);
			one.putInt("Outskirts", office.outskirts);
			one.putLong("Minted", office.minted);
			one.putLong("Founded", office.foundedDay);
			Towns.save(office, one);
			list.add(one);
		}
		tag.put("Offices", list);
		return tag;
	}
}
