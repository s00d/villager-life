package com.vnap.village.life;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A villager's agenda: the places it means to be at (a feast on the square, the tavern of an evening, a pew, its
 * lost child's hiding place), each with a priority and as long as its reason holds: the feast runs, it is evening,
 * the villager is ill. The town's life only puts things on it (TownLife, TownStory, the family's scenes); one brain
 * behaviour (AgendaBehavior) goes to the top one, says its word on arrival and stays while the reason holds (a thing
 * with no reason is done on arrival), and the vanilla strolls and work walks step aside meanwhile (the life gate).
 * A more pressing thing comes first; the same reason again is the same thing. Kept in memory only: a restart
 * forgets a tavern evening, nothing worse.
 */
public final class Agenda {
	/** A visit of its own (the bakehouse, a pew on an ordinary day, a lunch at the tavern). */
	public static final int VISIT = 1;
	/** The town's routine gatherings (the service, the tavern evening) and the family's (a father home, a lesson). */
	public static final int ROUTINE = 2;
	/** The day's event (a feast, a dispute, a lost child, an illness). */
	public static final int EVENT = 3;

	/**
	 * Be at {@code where} (within {@code near} blocks), for {@code why}; stay while {@code lasts} holds (null: just get
	 * there); say {@code say} on arrival (null: nothing) to {@code to}. A job to do there: {@code carry} in hand on
	 * the way, {@code does} done on arrival (a table set down, a cake put on it).
	 */
	public record Item(String why, BlockPos where, int priority, int near, float speed, @Nullable Predicate<ServerLevel> lasts,
					   @Nullable String say, @Nullable UUID to, ItemStack carry, @Nullable BiConsumer<ServerLevel, Villager> does) {
		public Item {
			where = where.immutable();
		}

		public Item(String why, BlockPos where, int priority, int near, float speed, @Nullable Predicate<ServerLevel> lasts,
					@Nullable String say, @Nullable UUID to) {
			this(why, where, priority, near, speed, lasts, say, to, ItemStack.EMPTY, null);
		}

		boolean holds(ServerLevel level) {
			Predicate<ServerLevel> reason = lasts;
			return reason == null || reason.test(level);
		}
	}

	private static final Map<UUID, List<Item>> AGENDAS = new HashMap<>();

	private Agenda() {
	}

	/** Puts {@code item} on the villager's agenda; the same reason to the same place is already there and stays. */
	public static void add(Villager villager, Item item) {
		// in market hours the keeper stays at its counter: visits, the tavern, a hand at the feast wait (an event does not)
		if (item.priority() < EVENT && com.vnap.village.market.Shops.isKeeper(villager) && com.vnap.village.market.Shops.marketHours(villager.level())) return;
		List<Item> agenda = AGENDAS.computeIfAbsent(villager.getUUID(), id -> new ArrayList<>());
		for (Item old : agenda) if (old.why().equals(item.why()) && old.where().equals(item.where())) return;
		agenda.removeIf(old -> old.why().equals(item.why()));
		agenda.add(item);
		// the most pressing first; of equal ones, the earlier put
		agenda.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
	}

	/** Shorthand: go to {@code where} for {@code why} and stay while {@code lasts} (null: just get there). */
	public static void go(Villager villager, String why, BlockPos where, int priority, @Nullable Predicate<ServerLevel> lasts, @Nullable String say) {
		add(villager, new Item(why, where, priority, 2, 0.55F, lasts, say, null));
	}

	public static void cancel(Villager villager, String why) {
		List<Item> agenda = AGENDAS.get(villager.getUUID());
		if (agenda != null) agenda.removeIf(item -> item.why().equals(why));
	}

	/** Whether the villager has anything on its agenda (it is about the town's business, not idling). */
	public static boolean busy(Villager villager) {
		List<Item> agenda = AGENDAS.get(villager.getUUID());
		return agenda != null && !agenda.isEmpty();
	}

	public static boolean has(Villager villager, String why) {
		List<Item> agenda = AGENDAS.get(villager.getUUID());
		return agenda != null && agenda.stream().anyMatch(item -> item.why().equals(why));
	}

	/** Where the villager's entry for {@code why} leads, or null. */
	public static @Nullable BlockPos where(Villager villager, String why) {
		List<Item> agenda = AGENDAS.get(villager.getUUID());
		if (agenda == null) return null;
		return agenda.stream().filter(item -> item.why().equals(why)).map(Item::where).findFirst().orElse(null);
	}

	/** Its entry for {@code why} (one whose reason is over dropped), or null; the night's package carries out only the shelter, wherever it stands. */
	static @Nullable Item of(ServerLevel level, Villager villager, String why) {
		top(level, villager);
		List<Item> agenda = AGENDAS.get(villager.getUUID());
		return agenda == null ? null : agenda.stream().filter(item -> item.why().equals(why)).findFirst().orElse(null);
	}

	/** The thing the villager should be doing now (those whose reason is over dropped), or null. */
	public static @Nullable Item top(ServerLevel level, Villager villager) {
		List<Item> agenda = AGENDAS.get(villager.getUUID());
		if (agenda == null) return null;
		agenda.removeIf(item -> item.lasts() != null && !item.holds(level));
		if (agenda.isEmpty()) {
			AGENDAS.remove(villager.getUUID());
			return null;
		}
		return agenda.get(0);
	}

	static void done(Villager villager, Item item) {
		List<Item> agenda = AGENDAS.get(villager.getUUID());
		if (agenda != null) agenda.remove(item);
	}

	/** A job: carry {@code carry} to {@code where} and do {@code does} there (in the order such jobs were put). */
	public static void job(Villager villager, String why, BlockPos where, int priority, ItemStack carry, BiConsumer<ServerLevel, Villager> does) {
		add(villager, new Item(why, where, priority, 2, 0.6F, null, null, null, carry, does));
	}

	/** Until the day's time reaches {@code end} (a fraction of the day: the evening's, the lesson's end). */
	public static Predicate<ServerLevel> until(float end) {
		return level -> com.vnap.dialogue.Family.dayFraction(level) < end;
	}

	/** A villager gone for good (dead, left): its queue, and the places and reasons it holds, go with it. */
	public static void forget(java.util.UUID villager) {
		AGENDAS.remove(villager);
		ErrandRunner.forget(villager);
	}

	/** A new world: the last one's agendas mean nothing. */
	public static void clear() {
		AGENDAS.clear();
		ErrandRunner.clear();
	}
}
