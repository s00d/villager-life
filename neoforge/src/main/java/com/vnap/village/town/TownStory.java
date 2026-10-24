package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.Infants;
import com.vnap.dialogue.VillagerHome;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Towns;
import com.vnap.village.life.Agenda;
import com.vnap.village.script.SchematicSpec;
import com.vnap.sound.GolemSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The town's storyteller (RimWorld's, a village's kind): most days one thing happens that is not the routine, or
 * nothing at all, never the same thing twice in TownRules.STORY_COOLDOWN days and a quiet day after each. What may
 * happen depends on the town (children to get lost, houses for a storm to unroof, a mayor for a feast or a dispute)
 * and the weather. Each resolves on its own within hours; a player may step in (find the child, bring food to the
 * feast, a remedy to the sick) and is thanked for it, never needed. Each goes into the Chronicle.
 */
public final class TownStory {
	public enum Kind { LOST_CHILD, STORM, FEAST, SICK, DISPUTE }

	/** A town's story: the day's event (its kind, when it starts, its people and place) and when each kind last ran. */
	public static final class State {
		@Nullable Kind kind;
		long day = Long.MIN_VALUE / 2;
		float at;
		long started = -1;
		@Nullable UUID who;
		@Nullable UUID other;
		@Nullable BlockPos where;
		long quietUntil = Long.MIN_VALUE / 2;
		final Map<Kind, Long> last = new HashMap<>();
		/** A feast's part (Feast: setting, feasting, fireworks, clearing) and the blocks it set down. */
		int phase;
		final List<BlockPos> placed = new ArrayList<>();

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			if (kind != null) tag.putString("Kind", kind.name());
			tag.putLong("Day", day);
			tag.putFloat("At", at);
			tag.putLong("Started", started);
			if (who != null) Nbt.putUUID(tag, "Who", who);
			if (other != null) Nbt.putUUID(tag, "Other", other);
			if (where != null) tag.putLong("Where", where.asLong());
			tag.putLong("Quiet", quietUntil);
			tag.putInt("Phase", phase);
			tag.putLongArray("Placed", placed.stream().mapToLong(BlockPos::asLong).toArray());
			CompoundTag last = new CompoundTag();
			this.last.forEach((k, d) -> last.putLong(k.name(), d));
			tag.put("Last", last);
			return tag;
		}

		void load(CompoundTag tag) {
			kind = tag.contains("Kind") ? Kind.valueOf(Nbt.getString(tag, "Kind")) : null;
			day = tag.contains("Day") ? Nbt.getLong(tag, "Day") : Long.MIN_VALUE / 2;
			at = Nbt.getFloat(tag, "At");
			started = tag.contains("Started") ? Nbt.getLong(tag, "Started") : -1;
			who = Nbt.hasUUID(tag, "Who") ? Nbt.getUUID(tag, "Who") : null;
			other = Nbt.hasUUID(tag, "Other") ? Nbt.getUUID(tag, "Other") : null;
			where = tag.contains("Where") ? BlockPos.of(Nbt.getLong(tag, "Where")) : null;
			quietUntil = tag.contains("Quiet") ? Nbt.getLong(tag, "Quiet") : Long.MIN_VALUE / 2;
			phase = Nbt.getInt(tag, "Phase");
			for (long pos : Nbt.getLongArray(tag, "Placed")) placed.add(BlockPos.of(pos));
			CompoundTag last = Nbt.getCompound(tag, "Last");
			for (String key : com.vnap.Mc.keys(last)) this.last.put(Kind.valueOf(key), Nbt.getLong(last, key));
		}

		void end(long day) {
			if (kind != null) last.put(kind, day);
			kind = null;
			started = -1;
			who = null;
			other = null;
			where = null;
			quietUntil = day + 1;
		}
	}

	private static final String SICK = "vnap_sick_until";
	/** Why the last event did not start (debug, /vnap story). */
	static String refused = "";

	private TownStory() {
	}

	// ---- choosing ------------------------------------------------------------------------------

	/** Every 100 ticks for a ticking town (TownLife): a new day may bring an event; today's starts, runs and ends. */
	public static void tick(ServerLevel level, Roads.Network network) {
		State story = network.story;
		long day = Family.day(level);
		float time = Family.dayFraction(level);
		// a feast ended some other way (cut short for another event, a debug command): its tables go
		if (story.kind != Kind.FEAST && !story.placed.isEmpty()) Feast.clearNow(level, story);
		// an event that never got going on its day (nobody to get lost, no house loaded) is let go
		if (story.kind != null && story.started < 0 && story.day != day) story.end(story.day);
		if (story.kind == null && story.day != day && time < 0.1F) choose(level, network, day);
		if (story.kind == null) return;
		if (story.started < 0) {
			if (time < story.at) return;
			if (!start(level, network, story)) {
				story.end(day);
				return;
			}
			story.started = level.getGameTime();
			Roads.get(level.getServer()).setDirty();
		}
		if (run(level, network, story, level.getGameTime() - story.started)) {
			story.end(day);
			Roads.get(level.getServer()).setDirty();
		}
	}

	private static void choose(ServerLevel level, Roads.Network network, long day) {
		State story = network.story;
		story.day = day;
		Roads.get(level.getServer()).setDirty();
		if (day < story.quietUntil || level.getRandom().nextFloat() >= TownRules.STORY_CHANCE) return;
		List<Kind> can = new ArrayList<>();
		for (Kind kind : Kind.values()) {
			if (day - story.last.getOrDefault(kind, Long.MIN_VALUE / 2) < TownRules.STORY_COOLDOWN) continue;
			if (kind == Kind.STORM && !level.isRaining()) continue;
			can.add(kind);
		}
		if (can.isEmpty()) return;
		story.kind = can.get(level.getRandom().nextInt(can.size()));
		// a feast and a dispute are evening and afternoon things; the rest come any time of the day
		story.at = switch (story.kind) {
			case FEAST -> 0.30F;
			case DISPUTE -> 0.3F;
			default -> 0.1F + level.getRandom().nextFloat() * 0.25F;
		};
	}

	// ---- starting ------------------------------------------------------------------------------

	static List<Villager> people(ServerLevel level, Roads.Network network) {
		return Towns.people(level, network.bell, villager -> !villager.isNoAi() && !villager.isSleeping() && !Travellers.passing(villager));
	}

	private static <T> @Nullable T any(ServerLevel level, List<T> list) {
		return list.isEmpty() ? null : list.get(level.getRandom().nextInt(list.size()));
	}

	private static boolean start(ServerLevel level, Roads.Network network, State story) {
		Kind kind = story.kind;
		if (kind == null) return false;
		List<Villager> people = people(level, network);
		switch (kind) {
			case LOST_CHILD -> {
				Villager child = any(level, people.stream().filter(v -> v.isBaby() && !Infants.infant(v)).toList());
				if (child == null) return false;
				double reach = Towns.reach(level, network.bell);
				BlockPos edge = Rings.at(network.bell, level.getRandom().nextDouble() * Math.PI * 2, Math.max(16.0, reach - 6.0));
				BlockPos spot = Ground.top(level, edge.getX(), edge.getZ());
				if (spot == null || !level.isLoaded(spot)) return false;
				story.who = child.getUUID();
				story.where = spot.above();
				Agenda.add(child, new Agenda.Item("lost", spot.above(), Agenda.EVENT, 1, 0.6F, running(network, Kind.LOST_CHILD), null, null));
				Villager mother = Family.state(child).mother != null && level.getEntity(Family.state(child).mother) instanceof Villager m ? m : null;
				if (mother != null) Speech.queue(mother, "Where Is My Child", child, 200L);
				Chronicle.note(level, network.bell, "story.lost", VillagerHome.nameOf(child));
			}
			case STORM -> {
				HouseProject house = any(level, VillageMoodSavedData.get(level).houses().stream().filter(h -> h.done && h.bell.closerThan(network.bell, TownRules.SPAN)
						&& level.isLoaded(h.origin) && level.getEntity(h.owner) instanceof Villager).toList());
				if (house == null || unroof(level, house) == 0) return false;
				if (level.getEntity(house.owner) instanceof Villager owner) {
					story.who = owner.getUUID();
					Speech.queue(owner, "Storm Took the Roof", null, 20L);
					Chronicle.note(level, network.bell, "story.storm", VillagerHome.nameOf(owner));
				}
			}
			case FEAST -> {
				if (!Feast.start(level, network, story, people)) return false;
			}
			case SICK -> {
				Villager sick = any(level, people.stream().filter(v -> !v.isBaby() && !Mayors.isMayor(v) && !Infants.minding(level, v)).toList());
				if (sick == null) return false;
				story.who = sick.getUUID();
				sick.getPersistentData().putLong(SICK, level.getGameTime() + TownRules.SICK_TICKS);
				Speech.queue(sick, "Feels Unwell", null, 20L);
				Chronicle.note(level, network.bell, "story.sick", VillagerHome.nameOf(sick));
			}
			case DISPUTE -> {
				List<Villager> heads = people.stream().filter(v -> !v.isBaby() && Family.isHead(v) && !Mayors.isMayor(v)).toList();
				Villager a = any(level, heads);
				Villager b = a == null ? null : any(level, heads.stream().filter(v -> !v.equals(a)).toList());
				if (a == null || b == null) return false;
				story.who = a.getUUID();
				story.other = b.getUUID();
				for (Villager v : new Villager[]{a, b}) Agenda.add(v, new Agenda.Item("dispute", network.bell, Agenda.EVENT, 3, 0.6F, running(network, Kind.DISPUTE), null, null));
				Chronicle.note(level, network.bell, "story.dispute", VillagerHome.nameOf(a), VillagerHome.nameOf(b));
			}
		}
		return true;
	}

	/** A storm takes a few blocks off a house's roof (no drops); its owners mend it in the days after (HomeProjects.inspect). */
	static int unroof(ServerLevel level, HouseProject house) {
		List<BlockPos> roof = new ArrayList<>();
		for (SchematicSpec.PlacedBlock block : house.schematic(level).blocks) {
			if (block.rel().getY() < 4) continue;
			BlockPos pos = BuildJob.at(house.origin, house.rotation, block.rel());
			if (level.getBlockState(pos).isAir() || level.getBlockState(pos).hasBlockEntity() || !level.canSeeSky(pos.above())) continue;
			roof.add(pos);
		}
		java.util.Collections.shuffle(roof, new java.util.Random(level.getGameTime()));
		int taken = 0;
		for (BlockPos pos : roof.subList(0, Math.min(TownRules.STORM_BLOCKS, roof.size()))) {
			if (level.destroyBlock(pos, false)) taken++;
		}
		return taken;
	}

	// ---- running -------------------------------------------------------------------------------

	/** One look at the running event (every 100 ticks); true when it is over. */
	private static boolean run(ServerLevel level, Roads.Network network, State story, long ticks) {
		Kind kind = story.kind;
		if (kind == null) return true;
		Villager who = story.who != null && level.getEntity(story.who) instanceof Villager v && v.isAlive() ? v : null;
		return switch (kind) {
			case LOST_CHILD -> lost(level, network, story, ticks, who);
			case STORM -> true;
			case FEAST -> Feast.run(level, network, story, ticks);
			case SICK -> sick(level, network, ticks, who);
			case DISPUTE -> dispute(level, network, story, ticks, who);
		};
	}

	private static boolean lost(ServerLevel level, Roads.Network network, State story, long ticks, @Nullable Villager who) {
			BlockPos spot = story.where;
			if (who == null || spot == null) return true;
			Player finder = level.getNearestPlayer(who, 4.0);
			if (finder != null && !finder.isSpectator()) {
				home(who);
				Speech.queue(who, "Kid Is Found", finder, 1L);
				thankMother(level, who, finder);
				VillageMoodApi.applyAt(level, network.bell, 3.0F, 2.0F, "found_child");
				Chronicle.note(level, network.bell, "story.found_by", VillagerHome.nameOf(who), com.vnap.Mc.name(finder));
				return true;
			}
			if (ticks >= TownRules.LOST_TICKS) {
				home(who);
				Speech.queue(who, "Kid Is Found", null, 1L);
				Chronicle.note(level, network.bell, "story.found", VillagerHome.nameOf(who));
				return true;
			}
			return false;
	}

	private static boolean sick(ServerLevel level, Roads.Network network, long ticks, @Nullable Villager who) {
			if (who == null || !who.getPersistentData().contains(SICK)) return true;
			if (level.getGameTime() >= Nbt.getLong(who.getPersistentData(), SICK)) {
				well(level, network, who, null);
				return true;
			}
			who.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 220, 1, false, false));
			BlockPos home = Family.home(who);
			if (home != null && !Agenda.has(who, "sick")) {
				Agenda.add(who, new Agenda.Item("sick", home, Agenda.EVENT, 3, 0.4F, ignored -> who.getPersistentData().contains(SICK), null, null));
			}
			// someone of the house brings soup once
			if (between(ticks, 1200)) {
				UUID spouse = Family.state(who).spouse;
				if (spouse != null && level.getEntity(spouse) instanceof Villager carer && carer.isAlive()) {
					Agenda.add(carer, new Agenda.Item("soup", who.blockPosition(), Agenda.EVENT, 2, 0.6F, null, "Brings Soup", who.getUUID()));
				}
			}
			if (between(ticks % 2400, 1200) && level.getRandom().nextBoolean()) Speech.queue(who, "Feels Unwell", null, 1L);
			return false;
	}

	private static boolean dispute(ServerLevel level, Roads.Network network, State story, long ticks, @Nullable Villager who) {
			Villager other = story.other != null && level.getEntity(story.other) instanceof Villager v && v.isAlive() ? v : null;
			if (who == null || other == null) return true;
			if (between(ticks, 400)) {
				Speech.queue(who, "Neighbours Quarrel", other, 1L);
				Speech.queue(other, "Neighbours Quarrel", who, 120L);
			}
			if (between(ticks, 800)) {
				Villager mayor = Mayors.mayorNear(level, network.bell);
				if (mayor != null && mayor.closerThan(who, 24.0)) Speech.queue(mayor, "Mayor Settles a Dispute", who, 1L);
			}
			return ticks >= 1000;
	}

	/** An agenda's reason: the town's event of this kind goes on. */
	private static java.util.function.Predicate<ServerLevel> running(Roads.Network network, Kind kind) {
		return ignored -> network.story.kind == kind && network.story.started >= 0;
	}

	/** The look (every 100 ticks) that falls on {@code mark} ticks into the event. */
	private static boolean between(long ticks, long mark) {
		return ticks >= mark && ticks < mark + 100;
	}

	private static void home(Villager child) {
		Agenda.cancel(child, "lost");
		BlockPos home = Family.home(child);
		if (home != null) Agenda.add(child, new Agenda.Item("home", home, Agenda.EVENT, 2, 0.6F, null, null, null));
	}

	private static void thankMother(ServerLevel level, Villager child, Player finder) {
		UUID mother = Family.state(child).mother;
		if (mother != null && level.getEntity(mother) instanceof Villager m && m.isAlive()) Speech.queue(m, "Thanks for Finding the Child", finder, 40L);
	}

	private static void well(ServerLevel level, Roads.Network network, Villager sick, @Nullable Player healer) {
		sick.getPersistentData().remove(SICK);
		Agenda.cancel(sick, "sick");
		sick.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		Speech.queue(sick, "Feels Better", healer, 1L);
		if (healer != null) Chronicle.note(level, network.bell, "story.cured_by", VillagerHome.nameOf(sick), com.vnap.Mc.name(healer));
		else Chronicle.note(level, network.bell, "story.well", VillagerHome.nameOf(sick));
	}

	// ---- the player stepping in ----------------------------------------------------------------

	/**
	 * A player's gift to a villager while the town's event runs (Family.give): a remedy to the sick (honey, a stew,
	 * soup), food for the feast; false when it has nothing to do with today's event.
	 */
	public static boolean give(ServerLevel level, Player player, Villager villager, ItemStack stack) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), villager.blockPosition());
		if (network == null || network.story.kind == null || network.story.started < 0) return false;
		State story = network.story;
		if (story.kind == Kind.SICK && villager.getUUID().equals(story.who) && remedy(stack)) {
			if (!player.getAbilities().instabuild) stack.shrink(1);
			well(level, network, villager, player);
			VillageMoodApi.applyAt(level, network.bell, 2.0F, 1.0F, "cured");
			story.end(Family.day(level));
			Roads.get(level.getServer()).setDirty();
			return true;
		}
		if (story.kind == Kind.FEAST && stack.has(net.minecraft.core.component.DataComponents.FOOD) && villager.blockPosition().closerThan(network.bell, 12.0)) {
			int given = Math.min(8, stack.getCount());
			if (!player.getAbilities().instabuild) stack.shrink(given);
			Speech.queue(villager, "Thanks for the Treat", player, 1L);
			VillageMoodApi.applyAt(level, network.bell, Math.min(3.0F, given * 0.5F), 1.0F, "feast");
			return true;
		}
		return false;
	}

	static boolean remedy(ItemStack stack) {
		return stack.is(Items.HONEY_BOTTLE) || stack.is(Items.MUSHROOM_STEW) || stack.is(Items.RABBIT_STEW) || stack.is(Items.BEETROOT_SOUP)
				|| stack.is(Items.SUSPICIOUS_STEW);
	}

	/** Debug (/vnap story): today's event of the nearest town, or start {@code kind} now. */
	public static String describe(ServerLevel level, BlockPos pos, @Nullable Kind force) {
		Roads.Network network = Roads.get(level.getServer()).near(level.dimension(), pos);
		if (network == null) return "no town";
		State story = network.story;
		if (force != null) {
			story.end(Family.day(level) - 1);
			story.quietUntil = Long.MIN_VALUE / 2;
			story.kind = force;
			story.day = Family.day(level);
			story.at = 0.0F;
			story.started = -1;
		}
		return "story: " + story.kind + " day " + story.day + " at " + story.at + (story.started >= 0 ? " running " + (level.getGameTime() - story.started) + "t" : "")
				+ ", last " + story.last + ", quiet until " + story.quietUntil + (refused.isEmpty() ? "" : ", last refusal: " + refused);
	}
}
