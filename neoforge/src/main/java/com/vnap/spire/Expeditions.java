package com.vnap.spire;

import com.vnap.Nbt;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.market.Market;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The town's people going up its spire (DESIGN §8): nothing of the climb is played out, it is worked out. One villager
 * a spire at a time walks to the gate and steps in; it is kept here as it was (its entity's NBT) and stays one of the
 * town all the while: counted, its bed, work and household its own, nobody born or moved in for it. It comes back the
 * same villager a day or three later, with what it earned and sometimes a find; tired ones come back early with less
 * and rest a while. Nobody dies up there.
 */
public final class Expeditions extends SavedData {
	private static final String NAME = "villager_life_expeditions";
	/** A villager back from the spire stays down this many days (twice that when it came back tired). */
	static final int REST_DAYS = 3;
	/** The share of trips the spire keeps (the villager dies up there): a rare grief, not a toll. */
	static final double LOST = 0.005;
	/** The chance a day that a town with a spire and nobody up it sends someone. */
	static final double DAILY_CHANCE = 0.5;
	/** What a trip may earn, in copper: never less than a day's wage, never more than two silver. */
	static final int MIN_PAY = 8;
	static final int MAX_PAY = 2 * com.vnap.village.market.Coins.SILVER;
	/** Comes back between these hours of the day (it walks out of the gate in daylight). */
	private static final long RETURN_FROM = 1_000L;
	private static final long RETURN_TO = 10_000L;
	public static final String WHY = "spire";

	public static final class Trip {
		public final int spire;
		public final UUID id;
		public final String name;
		final CompoundTag villager;
		public final long back;
		public final int days;
		final @Nullable BlockPos bed;
		public final BlockPos bell;

		Trip(int spire, UUID id, String name, CompoundTag villager, long back, int days, @Nullable BlockPos bed, BlockPos bell) {
			this.spire = spire;
			this.id = id;
			this.name = name;
			this.villager = villager;
			this.back = back;
			this.days = days;
			this.bed = bed;
			this.bell = bell;
		}
	}

	private final List<Trip> trips = new ArrayList<>();
	/** Villagers at the gate, about to step in (not saved: one stopped by a restart simply goes another day). */
	private static final Map<UUID, Integer> STEPPING = new HashMap<>();
	/** Who rests from a trip, until which day. */
	private final Map<UUID, Long> resting = new HashMap<>();

	public static Expeditions get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Expeditions::new, Expeditions::load, Expeditions::save);
	}

	public List<Trip> trips() {
		return trips;
	}

	/** Whether this villager is up a spire now (it is no less one of its town for it). */
	public static boolean away(MinecraftServer server, UUID id) {
		for (Trip trip : get(server).trips) if (trip.id.equals(id)) return true;
		return false;
	}

	/** Whether the bed is the home of one up a spire now: nobody takes it meanwhile. */
	public static boolean holds(MinecraftServer server, BlockPos bed) {
		for (Trip trip : get(server).trips) if (trip.bed != null && trip.bed.closerThan(bed, 2.0)) return true;
		return false;
	}

	// ---- going --------------------------------------------------------------------------------

	/** Daily with the town's plan: a town whose spire stands and nobody is up it may send someone. */
	public static void daily(ServerLevel level, Roads.Network network) {
		Spires.Spire spire = Spires.get(level.getServer()).ofTown(level.dimension(), network.bell);
		if (spire == null || spire.shown <= 0) return;
		talk(level, network, spire);
		if (!level.isLoaded(spire.front)) return;
		Expeditions all = get(level.getServer());
		for (Trip trip : all.trips) if (trip.spire == spire.id) return;
		if (level.getRandom().nextDouble() >= DAILY_CHANCE) return;
		long day = Family.day(level);
		Villager chosen = null;
		int best = Integer.MIN_VALUE;
		for (Villager villager : Towns.members(level, network.bell, villager -> fit(level, villager, day, all))) {
			// the out-of-work first, then the bold (by temperament), then any
			int score = (com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE) ? 10 : 0) + Math.floorMod(villager.getUUID().hashCode() + (int) day, 7);
			if (score > best) {
				best = score;
				chosen = villager;
			}
		}
		if (chosen == null) return;
		Speech.queue(chosen, "Going Up the Spire", null, 10L);
		com.vnap.village.life.Agenda.job(chosen, WHY, spire.front, com.vnap.village.life.Agenda.ROUTINE, ItemStack.EMPTY, (l, v) -> {
			// a word at the gate, and in (Expeditions.tick takes it through a few seconds later)
			Speech.queue(v, "Into the Spire", null, 0L);
			STEPPING.put(v.getUUID(), spire.id);
		});
	}

	/**
	 * The town talks of its spire in the morning: the news of the night (it rose, grew, a chapter's keeper fell, the
	 * beacon lit) by two or three, otherwise now and then one muses; the household of one up it says where it is.
	 */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded by the trip or the day: the same trip comes to the same")
	static void talk(ServerLevel level, Roads.Network network, Spires.Spire spire) {
		java.util.List<Villager> people = Towns.people(level, network.bell, villager -> !villager.isSleeping());
		if (people.isEmpty()) return;
		Random random = new Random(level.getGameTime() ^ spire.seed ^ 0x51L);
		java.util.Collections.shuffle(people, random);
		if (!spire.news.isEmpty()) {
			for (int i = 0; i < Math.min(3, people.size()); i++) Speech.queue(people.get(i), spire.news, null, 40L + i * 120L);
			spire.news = "";
			Spires.get(level.getServer()).setDirty();
		} else if (random.nextInt(4) == 0) {
			Speech.queue(people.get(0), "Talks About the Spire", null, 60L);
		}
		// one with coins to spare dresses like those back from the spire (the stall's tailor)
		for (Villager villager : people) {
			if (SpireOutfits.buy(level, villager, random)) {
				Speech.queue(villager, "New Clothes From the Spire", null, 400L);
				break;
			}
		}
		for (Trip trip : get(level.getServer()).trips) {
			if (trip.spire != spire.id) continue;
			for (Villager villager : people) {
				FamilyState family = Family.state(villager);
				if (trip.id.equals(family.spouse) || trip.id.equals(family.head)) {
					Speech.queue(villager, "Family Member in the Spire", null, 200L);
					break;
				}
			}
		}
	}

	/** Who may go: a grown-up of the town, not the mayor or the keeper, not minding a baby, building or leaving, not deep in debt, rested. */
	static boolean fit(ServerLevel level, Villager villager, long day, Expeditions all) {
		if (villager.isBaby() || !villager.isAlive() || com.vnap.village.life.Life.official(villager)) return false;
		if (com.vnap.village.home.Hamlets.leaving(villager) || com.vnap.dialogue.Infants.minding(level, villager)) return false;
		if (Market.debt(level, villager) > TownRules.LOAN_CAP / 2) return false;
		Long rested = all.resting.get(villager.getUUID());
		if (rested != null && day < rested) return false;
		for (com.vnap.village.home.HouseProject house : com.vnap.mood.VillageMoodSavedData.get(level).houses()) {
			if (!house.done && house.owner.equals(villager.getUUID())) return false;
		}
		return com.vnap.village.home.HomeProjects.hasHome(villager);
	}

	/** At the gate: it steps in and is kept, as it is, until it comes back. */
	public static void depart(ServerLevel level, Villager villager, Spires.Spire spire) {
		Expeditions all = get(level.getServer());
		for (Trip trip : all.trips) if (trip.spire == spire.id) return;
		if (villager.isSleeping()) villager.stopSleeping();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		CompoundTag tag = new CompoundTag();
		if (!com.vnap.Mc.saveEntity(villager, tag)) return;
		int days = 1 + level.getRandom().nextInt(3);
		BlockPos bed = villager instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
		long back = level.getGameTime() + days * 24_000L;
		BlockPos bell = Towns.bellOf(villager, spire.bell);
		all.trips.add(new Trip(spire.id, villager.getUUID(), VillagerHome.nameOf(villager), tag, back, days, bed, bell));
		all.setDirty();
		level.playSound(null, spire.gate, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.8F);
		com.vnap.VillagerLife.trace("vnap expedition: {} went up spire {} for {} days", VillagerHome.nameOf(villager), spire.id, days);
		villager.discard();
	}

	// ---- away and back -------------------------------------------------------------------------

	/** Every 100 ticks: those up a spire are counted with their town; those due come out of the gate. */
	public static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, Integer> step : List.copyOf(STEPPING.entrySet())) {
			STEPPING.remove(step.getKey());
			Spires.Spire spire = Spires.get(server).byId(step.getValue());
			ServerLevel level = spire == null ? null : server.getLevel(spire.dimension);
			if (spire != null && level != null && level.getEntity(step.getKey()) instanceof Villager villager && villager.isAlive()) depart(level, villager, spire);
		}
		Expeditions all = get(server);
		if (all.trips.isEmpty()) return;
		for (Trip trip : List.copyOf(all.trips)) {
			Spires.Spire spire = Spires.get(server).byId(trip.spire);
			ServerLevel level = spire == null ? null : server.getLevel(spire.dimension);
			if (spire == null || level == null) continue;
			Towns.seen(level, trip.id, trip.bell, Family.day(level));
			long time = com.vnap.dialogue.Family.timeOfDay(level);
			if (level.getGameTime() < trip.back || time < RETURN_FROM || time > RETURN_TO || !level.isPositionEntityTicking(spire.front)) continue;
			comeBack(level, spire, trip, all);
		}
	}

	/** Debug and tests: this villager comes out of its spire now. False when it is up none (or its spire is gone). */
	public static boolean bringBack(MinecraftServer server, UUID id) {
		Expeditions all = get(server);
		for (Trip trip : List.copyOf(all.trips)) {
			if (!trip.id.equals(id)) continue;
			Spires.Spire spire = Spires.get(server).byId(trip.spire);
			ServerLevel level = spire == null ? null : server.getLevel(spire.dimension);
			if (spire == null || level == null) return false;
			comeBack(level, spire, trip, all);
			return true;
		}
		return false;
	}

	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "DMI_RANDOM_USED_ONLY_ONCE", justification = "seeded by the trip or the day: the same trip comes to the same")
	private static void comeBack(ServerLevel level, Spires.Spire spire, Trip trip, Expeditions all) {
		BlockPos at = spire.front;
		//? if >=1.21.2 {
		/*Entity entity = EntityType.loadEntityRecursive(trip.villager, level, net.minecraft.world.entity.EntitySpawnReason.LOAD, loaded -> {
		*///?} else
		Entity entity = EntityType.loadEntityRecursive(trip.villager, level, loaded -> {
			com.vnap.Mc.snap(loaded, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, loaded.getYRot(), 0.0F);
			return loaded;
		});
		all.trips.remove(trip);
		all.setDirty();
		if (!(entity instanceof Villager villager) || !level.addFreshEntity(villager)) return;
		villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), trip.bell));
		Random random = new Random(trip.back ^ trip.id.getLeastSignificantBits());
		// very rarely the spire keeps one: carried out by the gate, gone (its family, grave and gear as any death)
		if (random.nextDouble() < LOST) {
			com.vnap.village.town.Chronicle.note(level, trip.bell, "spire_lost", trip.name);
			com.vnap.VillagerLife.trace("vnap expedition: {} did not come back alive", trip.name);
			for (Villager near : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(48.0), other -> !other.getUUID().equals(villager.getUUID()) && !other.isBaby())) {
				Speech.queue(near, "Lost to the Spire", null, 40L);
				break;
			}
			com.vnap.Mc.kill(villager);
			return;
		}
		Result result = result(spire, trip.days, random);
		spire.expeditionFloor = result.floor();
		Spires.get(level.getServer()).setDirty();
		Market.earn(level, villager, result.coins());
		long day = Family.day(level);
		all.resting.put(villager.getUUID(), day + (result.tired() ? REST_DAYS * 2L : REST_DAYS));
		// a find: a thing for the house, which its household sets up as it would one it bought (VillagerHome decor)
		ItemStack find = !result.tired() && random.nextInt(3) == 0 ? find(random) : ItemStack.EMPTY;
		if (!find.isEmpty()) villager.getInventory().addItem(find);
		// or clothes it found up there, of the chapter it reached
		boolean clothes = find.isEmpty() && !result.tired() && random.nextInt(4) == 0;
		if (clothes) SpireOutfits.wear(villager, SpireOutfits.ofFloor(result.floor(), random));
		// the Builders' gear, bought with the household's coins, or a good trip's shards in the set it has
		if (Heroes.afterTrip(level, villager, result.floor(), !result.tired(), random) != null) {
			com.vnap.village.town.Chronicle.note(level, trip.bell, "hero", trip.name, Integer.toString(result.floor()));
		}
		Speech.queue(villager, result.tired() ? "Back From the Spire Tired" : clothes ? "New Clothes From the Spire"
				: find.isEmpty() ? "Back From the Spire" : "Back With a Find", null, 20L);
		com.vnap.VillagerLife.trace("vnap expedition: {} came back from floor {} with {}c{}", trip.name, result.floor(), result.coins(),
				result.tired() ? " (tired)" : "");
		com.vnap.village.town.Chronicle.note(level, trip.bell, "expedition", trip.name, Integer.toString(result.floor()));
	}

	private static final net.minecraft.world.item.Item[] FINDS = {net.minecraft.world.item.Items.LANTERN, net.minecraft.world.item.Items.FLOWER_POT,
			net.minecraft.world.item.Items.CANDLE, net.minecraft.world.item.Items.PAINTING, net.minecraft.world.item.Items.SOUL_LANTERN};

	static ItemStack find(Random random) {
		return new ItemStack(FINDS[random.nextInt(FINDS.length)]);
	}

	/** What a trip comes to, worked out: how high it got, what it earned, whether it came back worn out. */
	public record Result(int floor, int coins, boolean tired) {
	}

	/**
	 * Floors climbed: one to three past the town's mark, never above what its players opened (ten while nobody has).
	 * Pay: a day's worth for each day up, a little more the higher, between MIN_PAY and MAX_PAY; tired, two fifths.
	 */
	public static Result result(Spires.Spire spire, int days, Random random) {
		int ceiling = Math.max(10, spire.best);
		int floor = Math.min(ceiling, Math.max(1, spire.expeditionFloor) + 1 + random.nextInt(3));
		boolean tired = random.nextDouble() < 0.15;
		double perDay = 6.0 + 0.6 * Math.pow(floor, 0.7);
		int coins = (int) Math.round(days * perDay * (0.8 + random.nextDouble() * 0.4));
		if (tired) coins = coins * 2 / 5;
		return new Result(floor, Math.max(MIN_PAY, Math.min(MAX_PAY, coins)), tired);
	}

	// ---- saving -----------------------------------------------------------------------------

	private static Expeditions load(CompoundTag tag, HolderLookup.Provider registries) {
		Expeditions all = new Expeditions();
		for (Tag entry : Nbt.getList(tag, "Trips", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			if (!Nbt.hasUUID(one, "Id")) continue;
			BlockPos bed = one.contains("Bed") ? BlockPos.of(Nbt.getLong(one, "Bed")) : null;
			all.trips.add(new Trip(Nbt.getInt(one, "Spire"), Nbt.getUUID(one, "Id"), Nbt.getString(one, "Name"), Nbt.getCompound(one, "Villager"),
					Nbt.getLong(one, "Back"), Nbt.getInt(one, "Days"), bed, BlockPos.of(Nbt.getLong(one, "Bell"))));
		}
		for (Tag entry : Nbt.getList(tag, "Resting", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			if (Nbt.hasUUID(one, "Id")) all.resting.put(Nbt.getUUID(one, "Id"), Nbt.getLong(one, "Until"));
		}
		return all;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Trip trip : trips) {
			CompoundTag one = new CompoundTag();
			one.putInt("Spire", trip.spire);
			Nbt.putUUID(one, "Id", trip.id);
			one.putString("Name", trip.name);
			one.put("Villager", trip.villager);
			one.putLong("Back", trip.back);
			one.putInt("Days", trip.days);
			if (trip.bed != null) one.putLong("Bed", trip.bed.asLong());
			one.putLong("Bell", trip.bell.asLong());
			list.add(one);
		}
		tag.put("Trips", list);
		ListTag rest = new ListTag();
		resting.forEach((id, until) -> {
			CompoundTag one = new CompoundTag();
			Nbt.putUUID(one, "Id", id);
			one.putLong("Until", until);
			rest.add(one);
		});
		tag.put("Resting", rest);
		return tag;
	}
}
