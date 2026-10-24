package com.vnap.dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What changed about a villager since the last look (grew up, got a job, picked something up) and what it is doing. */
final class VillagerMoments {
	/** Each trade's work block and its "at work" line. */
	private static final Map<String, String[]> TRADES = Map.ofEntries(
			Map.entry("armorer", new String[]{"blast_furnace", "djpksc"}), Map.entry("butcher", new String[]{"smoker", "ueczyh"}),
			Map.entry("cartographer", new String[]{"cartography_table", "wuoloh"}),
			Map.entry("cleric", new String[]{"brewing_stand", "hkowex"}), Map.entry("farmer", new String[]{"composter", "umdvtb"}),
			Map.entry("fisherman", new String[]{"barrel", "tgggoh"}), Map.entry("fletcher", new String[]{"fletching_table", "fzjope"}),
			Map.entry("leatherworker", new String[]{"cauldron", "ljewqf"}), Map.entry("librarian", new String[]{"lectern", "fezzjw"}),
			Map.entry("mason", new String[]{"stonecutter", "yldlzt"}), Map.entry("shepherd", new String[]{"loom", "opxfuo"}),
			Map.entry("toolsmith", new String[]{"smithing_table", "ivktls"}), Map.entry("weaponsmith", new String[]{"grindstone", "ccpvqj"}));
	private static final Map<UUID, Look> LAST_LOOK = new HashMap<>();
	private static final Map<UUID, Boolean> ASLEEP = new HashMap<>();
	private static final Map<UUID, Map<String, Integer>> POCKETS = new HashMap<>();
	private static final Map<UUID, Long> NO_WORK_BLOCK_SINCE = new HashMap<>();
	private static final Map<UUID, Long> NO_BELL_SINCE = new HashMap<>();

	private record Look(boolean baby, String trade, int rank, boolean working, String name, boolean walled) {
	}

	private VillagerMoments() {
	}

	static String profession(Villager villager) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(com.vnap.Mc.profession(villager.getVillagerData())).getPath();
	}

	private static boolean jobless(String trade) {
		return trade.equals("none") || trade.equals("nitwit");
	}

	static boolean wasWalled(UUID id) {
		Look look = LAST_LOOK.get(id);
		return look != null && look.walled();
	}

	static void awake(UUID id) {
		ASLEEP.put(id, false);
	}

	/** A villager came into the world: remember it as it is, so the first look says nothing. */
	static void met(Villager villager) {
		LAST_LOOK.put(villager.getUUID(), look(villager, false));
		POCKETS.put(villager.getUUID(), pockets(villager));
	}

	private static Look look(Villager villager, boolean working) {
		Component custom = villager.hasCustomName() ? villager.getCustomName() : null;
		return new Look(villager.isBaby(), profession(villager), com.vnap.Mc.villagerLevel(villager.getVillagerData()), working,
				custom == null ? "" : custom.getString(), villager.isInWall());
	}

	private static Map<String, Integer> pockets(Villager villager) {
		Map<String, Integer> counts = new HashMap<>();
		for (ItemStack stack : villager.getInventory().getItems()) {
			if (!stack.isEmpty()) counts.merge(Near.path(stack), stack.getCount(), Integer::sum);
		}
		return counts;
	}

	private static @Nullable BlockPos workBlock(Villager villager) {
		String[] trade = TRADES.get(profession(villager));
		if (trade == null) return null;
		for (BlockPos pos : BlockPos.betweenClosed(villager.blockPosition().offset(-3, -2, -3), villager.blockPosition().offset(3, 2, 3))) {
			if (Near.path(villager.level().getBlockState(pos)).equals(trade[0])) return pos.immutable();
		}
		return null;
	}

	/** Every 100 ticks for a villager near a player. */
	static void check(Villager villager) {
		Trades.mayorOffers(villager);
		UUID id = villager.getUUID();
		Look before = LAST_LOOK.get(id);
		BlockPos work = workBlock(villager);
		Look now = look(villager, work != null);
		Map<String, Integer> pocketsBefore = POCKETS.get(id);
		boolean sleeping = villager.isSleeping();
		boolean wasAsleep = ASLEEP.getOrDefault(id, sleeping);
		ASLEEP.put(id, sleeping);
		if (sleeping) {
			if (wasAsleep) Speech.title(villager, "Sleeping", "sleeping:" + id, Speech.SELDOM, null);
			POCKETS.put(id, pockets(villager));
			LAST_LOOK.put(id, now);
			return;
		}
		if (wasAsleep) Speech.titleAsAdult(villager, "Wake Up Naturally", "wake:" + id, Speech.SELDOM, null);
		if (pocketsBefore != null) pickedUp(villager, pocketsBefore);
		POCKETS.put(id, pockets(villager));
		if (before != null) changed(villager, before, now, work);
		Conditions.check(villager);
		missing(villager, now.trade(), work);
		if (now.trade().equals("farmer") && Near.anyBlock(villager.level(), villager.blockPosition(), 4, 2, state -> Near.crop(Near.path(state)))) {
			Speech.say(villager, "aobqjt", "farming:" + id, Speech.SELDOM, null);
		}
		if (Near.moving(villager) && villager.level() instanceof ServerLevel level) wandering(level, villager);
		long sinceFright = Harm.sinceFright(id);
		if (sinceFright >= 100L && sinceFright <= 200L) {
			Speech.say(villager, villager.isBaby() ? "wsxfok" : "wbbxpo", "calm:" + id, Speech.SELDOM, null);
		}
		if (villager.isBaby() && villager.getDeltaMovement().horizontalDistanceSqr() > 0.02) {
			DayOfWeek day = LocalDate.now(java.time.ZoneId.systemDefault()).getDayOfWeek();
			boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
			Speech.say(villager, weekend ? "vbclem" : "vhwksn", "baby_sprint:" + id, Speech.SELDOM, null);
		}
		LAST_LOOK.put(id, now);
	}

	private static void changed(Villager villager, Look before, Look now, @Nullable BlockPos work) {
		UUID id = villager.getUUID();
		if (before.baby() && !now.baby()) {
			Speech.say(villager, "smvnbj", "grow:" + id, 1L, null);
		} else if (jobless(before.trade()) && !jobless(now.trade())) {
			Speech.say(villager, "zndzjx", "job:" + id, 1L, null);
		} else if (now.rank() > before.rank()) {
			Speech.say(villager, now.rank() >= 5 ? "pnvkfy" : "fltegg", "level:" + id + ":" + now.rank(), 1L, null);
		} else if (!now.name().isEmpty() && !now.name().equals(before.name())) {
			String name = now.name().toLowerCase(Locale.ROOT);
			String line = now.baby() ? (name.equals("dragon") ? "cmrqhw" : "gzsztp")
					: switch (name) {
						case "dinnerbone" -> "qmpcxi";
						case "jeb", "jeb_" -> "armupg";
						default -> "spfsrr";
					};
			Speech.say(villager, line, "name:" + id + ":" + now.name(), 1L, null);
		}
		Vec3 at = work == null ? null : Vec3.atCenterOf(work);
		if (!before.working() && now.working()) {
			Speech.say(villager, "qawras", "work_start:" + id, Speech.SELDOM, at);
		} else if (now.working()) {
			String[] trade = TRADES.get(now.trade());
			String line = Speech.now() / Speech.SELDOM % 3L == 0L ? "sdhkke" : trade == null ? null : trade[1];
			if (line != null) Speech.say(villager, line, "work:" + id + ":" + line, Speech.SELDOM, at);
		}
	}

	/** No work block half a minute in, no bell a minute in: it says so (and again every while). */
	private static void missing(Villager villager, String trade, @Nullable BlockPos work) {
		UUID id = villager.getUUID();
		if (!jobless(trade) && work == null) {
			if (Speech.now() - NO_WORK_BLOCK_SINCE.computeIfAbsent(id, ignored -> Speech.now()) >= 600L) {
				Speech.say(villager, "ywzhwz", "missing_workstation:" + id, Speech.SELDOM, null);
				NO_WORK_BLOCK_SINCE.put(id, Speech.now());
			}
		} else {
			NO_WORK_BLOCK_SINCE.remove(id);
		}
		if (!villager.isBaby() && !villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT)) {
			if (Speech.now() - NO_BELL_SINCE.computeIfAbsent(id, ignored -> Speech.now()) >= 1200L) {
				Speech.say(villager, "trphsn", "missing_bell:" + id, Speech.SELDOM, null);
				NO_BELL_SINCE.put(id, Speech.now());
			}
		} else {
			NO_BELL_SINCE.remove(id);
		}
	}

	private static void wandering(ServerLevel level, Villager villager) {
		UUID id = villager.getUUID();
		float day = Family.dayFraction(level);
		boolean evening = day >= 0.5F && day < 0.85F;
		Villager hero = Heroes.near(level, villager, 8.0);
		if (villager.isBaby()) {
			// a hero walking by is better than any game
			if (!evening && hero != null && Speech.title(villager, "Kid Admires the Hero", "kid_hero:" + id, Speech.SELDOM * 2L, hero)) return;
			String title = evening ? "Kid Bedtime" : day < 0.45F ? "Kid Plays" : null;
			if (title != null) Speech.title(villager, title, "kid:" + id + ":" + title, Speech.SELDOM * 2L, null);
			return;
		}
		if (!evening) {
			// by day a hero keeps watch, and a neighbour stops for a word with it, which it answers
			if (com.vnap.spire.Heroes.armed(villager)) {
				Speech.title(villager, "Hero Keeps Watch", "hero_watch:" + id, Speech.SELDOM * 3L, null);
			} else if (hero != null && hero.distanceTo(villager) < 6.0
					&& Speech.title(villager, "Talks With the Hero", "hero_talk:" + id, Speech.SELDOM * 2L, hero)) {
				Speech.queue(hero, "Hero Answers", villager, 60L);
			}
			return;
		}
		String line = level.dimension().equals(Level.END) ? "iubjul" : level.dimension().equals(Level.NETHER) ? "bvtmmz"
				: !level.dimension().equals(Level.OVERWORLD) ? "uhbigm"
				: villager.getBrain().hasMemoryValue(MemoryModuleType.HOME) ? "wkfbuv" : "uqguqj";
		Speech.say(villager, line, "return_home:" + id + ":" + line, Speech.SELDOM, null);
	}

	/** Something new in its pockets: a child thanks the one who fed it, a grown-up says what it found. */
	private static void pickedUp(Villager villager, Map<String, Integer> before) {
		ItemStack found = null;
		Map<String, Integer> now = pockets(villager);
		for (ItemStack stack : villager.getInventory().getItems()) {
			if (!stack.isEmpty() && now.getOrDefault(Near.path(stack), 0) > before.getOrDefault(Near.path(stack), 0)) {
				found = stack;
				break;
			}
		}
		if (found == null || !(villager.level() instanceof ServerLevel level)) return;
		String path = Near.path(found);
		UUID id = villager.getUUID();
		boolean armour = path.endsWith("_helmet") || path.endsWith("_chestplate") || path.endsWith("_leggings") || path.endsWith("_boots");
		String own = armour ? found.isEnchanted() ? "habfnx" : "zjwpzi" : "dxmmiu";
		boolean food = switch (path) {
			case "beetroot", "bread", "carrot", "potato" -> true;
			default -> false;
		};
		Villager giver = Near.villager(level, villager.position(), 4.0, other -> !other.equals(villager));
		ServerPlayer player = Near.player(level, villager.position(), 6.0);
		if (villager.isBaby() && giver != null && food
				&& Speech.say(giver, "locuih", "share_food:" + giver.getUUID(), Speech.SOON, villager)) {
			SpeechQueue.add(level, villager, "saxuwk", giver, Speech.now() + DialogueCatalog.durationTicks("locuih") + 2L);
		} else if (giver != null && !armour) {
			Speech.say(villager, "ujyxfg", "pickup_villager:" + id + ":" + path, Speech.SOON, giver);
		} else if (player != null) {
			Speech.say(villager, armour ? own : "vkhrme", "pickup_player:" + id + ":" + path, Speech.SOON, player);
		} else {
			Speech.say(villager, own, "pickup:" + id + ":" + path, Speech.SOON, null);
		}
	}

	static void forget(UUID id) {
		LAST_LOOK.remove(id);
		ASLEEP.remove(id);
		POCKETS.remove(id);
		NO_WORK_BLOCK_SINCE.remove(id);
		NO_BELL_SINCE.remove(id);
	}

	static void clear() {
		LAST_LOOK.clear();
		ASLEEP.clear();
		POCKETS.clear();
		NO_WORK_BLOCK_SINCE.clear();
		NO_BELL_SINCE.clear();
	}
}
