package com.vnap.dialogue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** What a villager remarks on around it: the weather and the place, animals and monsters, the hour, the date. */
final class Surroundings {
	private static final double SIGHT = 8.0;
	/** Mob type → the line about it; "baby" lines are for its young. */
	private static final Map<String, String> MOBS = table("""
			allay rnlher, armor_stand ckniqq, bat ozmthf, bee rbkjsr, cave_spider gtmfpl, bogged nsosix, camel turlrl,
			cat ynxhfb, chicken hggexx, cow lvzfcv, creaking nwlcij, creeper odwhzm, dolphin aqtshb, drowned atwycp,
			enderman yeqxvm, frog pguaqp, horse yazvzs, husk gcoysc, llama ysbfqu, trader_llama ysbfqu, panda swewsr,
			parrot vapupl, phantom nwzvkb, pig jqdeef, rabbit spfefr, sheep vxycol, skeleton lqzdqk, slime rzvitn,
			sniffer tqishj, spider gtmfpl, stray bxbibd, turtle neoxpu, warden jicosq, witch lwcrnt, wither satsrf,
			wolf vvntcf, zombie dortcb, zombie_villager xtooxu, zombified_piglin wboncy, copper_golem ktdshy,
			snow_golem kxjegd, iron_golem cuchwi, ender_dragon xxjkmo, happy_ghast lxvofx, polar_bear toolzx,
			sulfur_cube dmcjmd, cod trkugw, salmon trkugw, pufferfish trkugw, tropical_fish trkugw""");
	private static final Map<String, String> YOUNG = table("""
			bee qqtnlm, cat knjdbi, chicken pjcwec, cow hzahog, drowned vakwgb, horse ualabt, husk hwltxk, panda gggzar,
			pig htibul, sheep eccdga, wolf hyzwpr, zombie zvwapr, zombified_piglin qltnkz, zombie_villager nstwos""");
	/** Mobs newer than this game cannot be here: a mob given one of their names stands in for them. */
	private static final Set<String> STAND_INS = Set.of("creaking", "copper_golem", "happy_ghast", "sulfur_cube");
	private static final Map<String, Long> LAST_TIME = new HashMap<>();
	private static final Map<String, Difficulty> LAST_DIFFICULTY = new HashMap<>();

	private Surroundings() {
	}

	private static Map<String, String> table(String pairs) {
		Map<String, String> out = new HashMap<>();
		for (String pair : pairs.replace("\n", " ").split(",", -1)) {
			String[] parts = pair.strip().split(" ", -1);
			out.put(parts[0], parts[1]);
		}
		return Map.copyOf(out);
	}

	/** Where the villager is: a ride, another world, the weather, the ground, a fire nearby; null for nothing special. */
	static @Nullable String place(ServerLevel level, Villager villager) {
		Entity ride = villager.getVehicle();
		if (ride != null) {
			String what = Near.path(ride);
			if (what.contains("minecart")) return Near.moving(ride) ? "Ride in a Moving Minecart" : "Sit in a Minecart";
			if (what.contains("boat")) {
				if (Speech.now() / Speech.SELDOM % 3L == 0L) return "Sit in a Boat";
				return ride.isInWater() ? "Boat on Water" : "Boat on Land";
			}
		}
		if (level.dimension().equals(Level.NETHER)) return "Wander in the Nether";
		if (level.dimension().equals(Level.END)) return "Wander in the End";
		if (!level.dimension().equals(Level.OVERWORLD)) return "Wander in Another Dimension";
		BlockPos feet = villager.blockPosition();
		if (level.isRainingAt(feet)) return "Caught in the Rain";
		if (villager.isInWater()) return "Stand in Shallow Water";
		String biome = level.getBiome(feet).unwrapKey().map(key -> com.vnap.Mc.id(key).getPath()).orElse("");
		if (Stream.of("desert", "badlands", "savanna").anyMatch(biome::contains)) return "Wander Somewhere Hot";
		if (Stream.of("snow", "frozen", "ice", "cold").anyMatch(biome::contains)) return "Wander Somewhere Cold";
		BlockState ground = level.getBlockState(feet.below());
		if (ground.is(Blocks.ICE) || ground.is(Blocks.PACKED_ICE) || ground.is(Blocks.BLUE_ICE)) return "Stand on Ice";
		if (ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.POWDER_SNOW)) return "Stand on Snow";
		if (ground.is(Blocks.MAGMA_BLOCK)) return "Stand on Magma";
		String nearby = nearbyBlock(level, villager);
		if (nearby != null) return nearby;
		if (boxedIn(level, feet)) return "Trap a Villager with Blocks";
		int sea = level.getChunkSource().getGenerator().getSeaLevel();
		if (villager.getY() < sea - 30) return "Wander Deep Underground";
		if (villager.getY() > sea + 75) return "Wander High Above the Ground";
		return null;
	}

	/** The first notable block in a 7x5x7 box around the villager, in x, y, z order. */
	private static @Nullable String nearbyBlock(ServerLevel level, Villager villager) {
		BlockPos feet = villager.blockPosition();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos pos = feet.offset(dx, dy, dz);
					ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
					String block = id.getPath();
					boolean fire = block.equals("fire") || block.equals("soul_fire");
					if (fire && (Near.homeWithin(villager, pos, 10) || Near.block(level, pos, "bed", 6))) return "Village House on Fire";
					String modded = ModFlavorMatchers.nearBlockTitle(id.getNamespace(), block);
					if (modded != null) return modded;
					if (block.contains("campfire")) return "See a Campfire";
					if (fire) return "Stand Near Fire";
					if (block.equals("bookshelf") && !Near.moving(villager)) return "Inspect Bookshelves";
				}
			}
		}
		return null;
	}

	private static boolean boxedIn(ServerLevel level, BlockPos feet) {
		for (BlockPos side : new BlockPos[]{feet.north(), feet.south(), feet.east(), feet.west(), feet.above()}) {
			if (level.getBlockState(side).getCollisionShape(level, side).isEmpty()) return false;
		}
		return true;
	}

	static String partOfDay(ServerLevel level) {
		float day = Family.dayFraction(level);
		if (day < 0.125F || day >= 0.875F) return "Morning";
		if (day < 0.45F) return "Afternoon";
		return day < 0.625F ? "Evening" : "Night";
	}

	/** What a plain villager mutters walking about: its mood, its trade, else the calendar. */
	static String idle(Villager villager) {
		String mood = VillagerHome.moodGroup(villager);
		if (mood != null) return mood;
		String trade = VillagerMoments.profession(villager);
		if (trade.equals("nitwit")) return "uookqp";
		if (trade.equals("none")) return "gbxzxv";
		LocalDate date = LocalDate.now(java.time.ZoneId.systemDefault());
		int month = date.getMonthValue();
		int day = date.getDayOfMonth();
		List<String> lines = new ArrayList<>();
		if (month == 10) lines.add("mltyge");
		if (month == 12) lines.add("tkkegl");
		String holiday = switch (month * 100 + day) {
			case 101 -> "uyqiwv";
			case 214 -> "fabiyx";
			case 401 -> "obitls";
			case 517 -> "iriuqa";
			case 1031 -> "adhxce";
			case 1224 -> "zoqxvy";
			case 1225 -> "rclyrl";
			case 1231 -> "xljknt";
			default -> null;
		};
		if (holiday != null) lines.add(holiday);
		if (day == 13 && date.getDayOfWeek() == java.time.DayOfWeek.FRIDAY) lines.add("qfcwvz");
		if (LocalTime.now(java.time.ZoneId.systemDefault()).getMinute() == 0) lines.add("jqgkhy");
		boolean odd = Speech.now() / Speech.SELDOM % 2L != 0L;
		switch (date.getDayOfWeek()) {
			case MONDAY -> lines.add(odd ? "jpucos" : "gkvlqc");
			case TUESDAY -> lines.add(odd ? "lgeeem" : "dkpihl");
			case WEDNESDAY -> lines.add(odd ? "qiqiez" : "gwakiz");
			case THURSDAY -> lines.add(odd ? "caiyte" : "zglkgp");
			case FRIDAY -> lines.add(odd ? "cxtvsx" : "ypyumu");
			case SATURDAY -> {
				lines.add("bkyidl");
				lines.add("ckngck");
				lines.add(odd ? "lfhnxz" : "ildosa");
			}
			case SUNDAY -> {
				lines.add("bkyidl");
				lines.add("ckngck");
				lines.add(odd ? "zckxrc" : "uzvatl");
			}
		}
		if (lines.size() == 1) lines.add(Cast.VILLAGER.idles());
		return lines.get(Math.floorMod((int) (Speech.now() / Speech.SELDOM), lines.size()));
	}

	/** The villager remarks on something it sees close by. True when it said something. */
	static boolean remark(ServerLevel level, Villager speaker) {
		List<Entity> seen = level.getEntities(speaker, speaker.getBoundingBox().inflate(SIGHT, 6.0, SIGHT), Entity::isAlive).stream()
				// the town's statue of its heroes is no clothes rack to talk about (Plaza.unveil)
				.filter(e -> speaker.distanceToSqr(e) <= SIGHT * SIGHT && speaker.hasLineOfSight(e) && !com.vnap.Mc.tags(e).contains("vnap_statue"))
				.sorted(Comparator.comparingDouble(speaker::distanceToSqr)).toList();
		String who = speaker.getUUID().toString();
		for (Entity thing : seen) {
			String path = Near.path(thing);
			String key = who + ":" + thing.getUUID();
			if (path.equals("tnt") && (Near.homeWithin(speaker, thing.blockPosition(), 16) || Near.block(level, thing.blockPosition(), "bed", 16))
					&& Speech.title(speaker, "Set Off TNT Near Homes", "tnt_home:" + key, Speech.SOON, thing)) return true;
			String line = switch (path) {
				case "falling_block" -> "bodvsv";
				case "experience_orb" -> "cvltyw";
				case "tnt" -> "pmaqgq";
				case "firework_rocket" -> speaker.isBaby() ? "zeykfp" : null;
				default -> null;
			};
			if (line != null && Speech.sayAsAdult(speaker, line, "nearby_misc:" + key + ":" + line, Speech.SELDOM, thing)) return true;
		}
		List<ItemEntity> loot = level.getEntitiesOfClass(ItemEntity.class, AABB.ofSize(speaker.position(), 10, 10, 10), Entity::isAlive)
				.stream().filter(speaker::hasLineOfSight).sorted(Comparator.comparingDouble(speaker::distanceToSqr)).toList();
		if (loot.size() >= 5 && Speech.sayAsAdult(speaker, "zywcju", "item_pile:" + who, Speech.SELDOM, loot.getFirst())) return true;
		for (Entity thing : seen) {
			if (thing instanceof LivingEntity animal && !(thing instanceof Player) && !(thing instanceof Villager) && aboutMob(speaker, animal)) {
				return true;
			}
		}
		return false;
	}

	private static boolean aboutMob(Villager speaker, LivingEntity mob) {
		String path = Near.path(mob);
		Entity look = mob;
		String line = null;
		if (speaker.isBaby() && path.equals("iron_golem")) line = "mqnapy";
		else if (mob instanceof Sheep sheep && sheep.isSheared()) line = "afxbav";
		else if (mob instanceof TamableAnimal pet && pet.isTame()) line = mob.isBaby() ? "sxikgq" : "aqxgxh";
		if (line == null && mob instanceof Mob hunter && hunter.getTarget() != null) {
			if (path.equals("bee")) line = "bmimxe";
			else if (path.equals("iron_golem") && hunter.getTarget() instanceof Player victim) {
				line = "qffeco";
				look = victim;
			}
		}
		if (line == null && (mob.isPassenger() || !mob.getPassengers().isEmpty())) line = "dxeaal";
		if (line == null && mob.isBaby()) line = YOUNG.get(path);
		if (line == null) line = MOBS.get(standIn(mob, path));
		String key = speaker.getUUID() + ":" + mob.getUUID();
		String modded = ModFlavorMatchers.nearbyEntityTitle(mob);
		if (modded != null) return speaker.hasLineOfSight(look) && Speech.title(speaker, modded, "nearby_mod:" + key + ":" + modded, Speech.SELDOM, look);
		if (line == null && !(mob instanceof WanderingTrader)) line = "gjtuqd";
		return line != null && speaker.hasLineOfSight(look) && Speech.sayAsAdult(speaker, line, "nearby:" + key + ":" + line, Speech.SELDOM, look);
	}

	private static String standIn(Entity mob, String path) {
		if (!mob.hasCustomName() || mob.getCustomName() == null) return path;
		String name = mob.getCustomName().getString().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
		return STAND_INS.contains(name) ? name : path;
	}

	/** /time set or a slept-away night: the nearest villager to each player says so. */
	static void clock(ServerLevel level) {
		String dimension = com.vnap.Mc.id(level.dimension()).toString();
		long now = com.vnap.Mc.dayTime(level);
		Long before = LAST_TIME.put(dimension, now);
		if (before == null || Math.abs(now - before) <= 40L) return;
		boolean wasDay = Family.timeOfDay(before) < 12000L;
		boolean isDay = Family.timeOfDay(now) < 12000L;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			Villager speaker = Near.villager(level, player.position(), 32.0, v -> !v.isSleeping());
			if (speaker == null) continue;
			boolean child = speaker.isBaby();
			String line = wasDay == isDay ? child ? "durjjd" : "uqwdqn" : isDay ? child ? "wkwcrf" : "mgmzeh" : child ? "msemoe" : "ohdwnz";
			Speech.sayAsAdult(speaker, line, "time_skip:" + dimension + ":" + speaker.getUUID(), Speech.SOON, player);
		}
	}

	static void difficulty(ServerLevel level) {
		String dimension = com.vnap.Mc.id(level.dimension()).toString();
		Difficulty now = level.getDifficulty();
		Difficulty before = LAST_DIFFICULTY.put(dimension, now);
		if (before == null || before == now) return;
		String line = now == Difficulty.HARD ? "arzojk" : now == Difficulty.PEACEFUL ? "xuyypm" : "ibcrvx";
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			Villager speaker = Near.villager(level, player.position(), 32.0, v -> !v.isBaby() && !v.isSleeping());
			if (speaker != null) {
				Speech.sayAsAdult(speaker, line, "difficulty:" + dimension + ":" + now.name() + ":" + speaker.getUUID(), Speech.SOON, player);
			}
		}
	}

	static void clear() {
		LAST_TIME.clear();
		LAST_DIFFICULTY.clear();
	}
}
