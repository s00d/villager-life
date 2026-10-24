package com.vnap.dialogue;

import com.vnap.village.town.Roads;
import com.vnap.village.town.TownPlots;
import com.vnap.village.town.TownRules;
import com.vnap.entity.VillagerLifeData;
import com.vnap.item.LifeItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodApi;
import com.vnap.progress.VillageAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.village.home.Graveyard;
import com.vnap.village.home.Hamlets;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Pets;
import net.minecraft.world.entity.TamableAnimal;
import com.vnap.village.life.Pantry;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Women, couples and their children. A household is the house of its head (the villager who claimed or built it):
 * the spouse and the children live in beds the head's daily chore places next to its own bed, and the home sign
 * lists them. Only a married couple with room for a child bed has children (VillagerMakeLoveMixin).
 */
public final class Family {
	public static final String KIDS_SIGN_KEY = "sign.villager_life.family.kids";
	private static final double ALTAR_RANGE = 6.0;
	private static final long SLOW_TICK_NANOS = 20_000_000L;
	private static long tickStarted;
	private static final long WEDDING_WALK = 1200L;
	private static final Map<UUID, Long> WEDDING_STARTED = new HashMap<>();
	private static long slowLogged = Long.MIN_VALUE / 2;
	private static final Map<String, Long> SAID = new HashMap<>();
	/** Server tick each SAID key was last written: keys of villagers long gone are dropped (see tickAll). */
	private static final Map<String, Long> SAID_AT = new HashMap<>();
	private static final int FORGET_AFTER = 48000;
	/** Bed VillagerMakeLove took for the coming baby, by parent; read back when the baby is born a moment later. */
	private static final Map<UUID, BlockPos> CHILD_BEDS = new HashMap<>();
	private static long ticker;

	static final String[] MALE_NAMES = {
			"Aldren", "Bram", "Cedric", "Ellis", "Gideon", "Ivo", "Kai", "Nils", "Quinn", "Soren", "Yorick", "Boris",
			"Dmitri", "Felix", "Hugo", "Jonas", "Leo", "Nestor", "Petr", "Sten", "Tomas", "Viktor", "Ansel", "Casper",
			"Einar", "Gustav", "Jens", "Knut", "Lars", "Oskar", "Pavel", "Torsten", "Ulrik", "Viggo", "Werner", "Anton",
			"Conrad", "Emil", "Gleb", "Ilia", "Josef", "Lev", "Nikita", "Oleg", "Roman", "Taras", "Vadim", "Arkady",
			"Bogdan", "Denis", "Fedor", "Igor", "Maxim", "Osip", "Semyon", "Vasil", "Borisav", "Danilo", "Filip", "Henrik"
	};
	static final String[] FEMALE_NAMES = {
			"Dora", "Freya", "Hilda", "Jora", "Lina", "Mara", "Oda", "Pia", "Rosa", "Tilda", "Una", "Vera", "Willa",
			"Agnes", "Clara", "Edna", "Greta", "Iris", "Klara", "Mina", "Olga", "Rita", "Ulla", "Wanda", "Ylva", "Zora",
			"Britta", "Dagny", "Frida", "Hedda", "Ingrid", "Magda", "Nora", "Runa", "Sigrid", "Yrsa", "Zlata", "Berta",
			"Daria", "Faina", "Hanna", "Katya", "Mila", "Polina", "Sonya", "Ulyana", "Yana", "Zina", "Celia", "Elena",
			"Galina", "Lada", "Nadia", "Raisa", "Tamara", "Zoya", "Alina", "Ewa", "Gosia", "Irena"
	};

	/** A name a villager could bear, by a number (a spire note's signature). */
	public static String someName(long n) {
		String[] names = (n & 1L) == 0 ? MALE_NAMES : FEMALE_NAMES;
		return names[(int) Math.floorMod(n >>> 8, (long) names.length)];
	}

	/** Adult line title -> what a woman says instead; a title not listed leaves her quiet (I_women.yaml). */
	public static final Map<String, String> WOMAN_LINES = womanLines();
	/** A married villager is attacked: the spouse nearby answers. */
	private static final Set<String> ATTACKS = Set.of("Attacked by the Player", "Attacked with a Sword",
			"Attacked with an Axe", "Attacked with an Arrow", "Attacked with a Hoe", "Attacked with a Shovel",
			"Attacked with a Firework", "Attacked with a Splash Potion", "Attacked with a Gun", "Attacked Inside Their Home",
			"Woman Is Hit by the Player");
	/** Lines of a hurt or frightened child that bring its mother over. */
	private static final Set<String> CHILD_HURT = Set.of("Player Hurts a Baby Villager");
	private static final Set<String> CHILD_SCARED = Set.of("Baby Villager Gets Hurt", "Kid Scared of a Monster",
			"Kid Sees a Fire", "Kid Hears a Boom", "Kid Sees Someone Die");

	private Family() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
			if (!event.getLevel().isClientSide() && event.getEntity() instanceof Villager villager) ensureGender(villager);
			// a pet adopted in this session got its goal then; one that comes back with its chunk gets it here
			if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof TamableAnimal pet) {
				Pets.attachGoal(pet);
				// sorted out next tick: the chunk is still loading its entities now
				if (event.getLevel() instanceof ServerLevel level) {
					com.vnap.Mc.later(level.getServer(), new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
						if (pet.isAlive()) Pets.orphanCheck(level, pet);
					}));
				}
			}
		});
		NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
			if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
				// one line a death: how a village loses its people (when, where, doing what) is read off the log
				net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
				com.vnap.VillagerLife.LOGGER.info("vnap death: {} by {} {} at {} time {} {} sky={} asleep={} act={} bed={}",
						VillagerHome.nameOf(villager), event.getSource().getMsgId(),
						killer == null ? "-" : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()) + "@" + killer.blockPosition().toShortString()
								+ " light=" + level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, killer.blockPosition()) + " roofed=" + FamilyHome.roofed(level, killer.blockPosition()),
						villager.blockPosition().toShortString(), timeOfDay(level),
						home(villager) == null ? "homeless" : "housed", level.canSeeSky(villager.blockPosition().above()), villager.isSleeping(),
						villager.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"),
						villager.getBrain().getMemory(MemoryModuleType.HOME).map(home -> (int) Math.sqrt(home.pos().distSqr(villager.blockPosition()))).orElse(-1));
				onDeath(level, villager);
			}
			if (event.getEntity() instanceof TamableAnimal pet && pet.level() instanceof ServerLevel level) Pets.onDeath(level, pet);
		});
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) -> {
			// where a town's monsters come from: a siege (EVENT), the dark (NATURAL), a spawner
			if (event.getEntity() instanceof net.minecraft.world.entity.monster.Enemy && event.getLevel() instanceof ServerLevel level) {
				BlockPos at = BlockPos.containing(event.getX(), event.getY(), event.getZ());
				com.vnap.village.town.Roads.Network network = com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), at);
				if (network != null && network.bell.closerThan(at, com.vnap.village.home.Towns.reach(level, network.bell) + 16)) {
					com.vnap.VillagerLife.trace("vnap monster: {} {} at {} light={} sky={} roofed={} time {} bell {}",
							net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()), event.getSpawnType(),
							at.toShortString(), level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at), level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at),
							FamilyHome.roofed(level, at), timeOfDay(level), network.bell.toShortString());
				}
			}
		});
		// a villager whose one tick takes more than SLOW_TICK_NANOS: what its brain was doing (/neoforge track only
		// says which villager and how long, over seconds)
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) -> {
			if (event.getEntity() instanceof Villager && !event.getEntity().level().isClientSide()) tickStarted = System.nanoTime();
		});
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) -> {
			if (!(event.getEntity() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
			long took = System.nanoTime() - tickStarted;
			// one line a few seconds at most (it was a time stamp in every slow villager's save)
			if (took < SLOW_TICK_NANOS || Math.abs(level.getGameTime() - slowLogged) < 50) return;
			slowLogged = level.getGameTime();
			String running = com.vnap.village.life.LifeBrain.running(villager).stream().map(net.minecraft.world.entity.ai.behavior.BehaviorControl::debugString)
					.collect(java.util.stream.Collectors.joining(","));
			com.vnap.VillagerLife.trace("vnap slow: {} {} ms at {} act={} run=[{}] walk={} path={} time {}", VillagerHome.nameOf(villager),
					took / 1_000_000, villager.blockPosition().toShortString(),
					villager.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"), running,
					villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(target -> target.getTarget().currentBlockPosition().toShortString()).orElse("-"),
					villager.getNavigation().getPath() == null ? "-" : villager.getNavigation().getPath().getNodeCount() + "n", timeOfDay(level));
		});
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.village.VillageSiegeEvent event) ->
				com.vnap.VillagerLife.trace("vnap siege: 20 zombies at {} time {}", BlockPos.containing(event.getAttemptedSpawnPos()).toShortString(), timeOfDay(com.vnap.Mc.dayTime(event.getLevel()))));
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post event) -> {
			if (event.getEntity() instanceof Villager villager && villager.level() instanceof ServerLevel level) {
				com.vnap.VillagerLife.LOGGER.info("vnap gone: {} turned into a {} at {} time {}", VillagerHome.nameOf(villager),
						net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(event.getOutcome().getType()), villager.blockPosition().toShortString(), timeOfDay(level));
			}
		});
		NeoForge.EVENT_BUS.addListener(Family::onInteract);
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tickAll(event.getServer()));
		NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
			if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player player) {
				com.vnap.village.home.FamilyRooms.onPlayerPlaced(level, event.getPos(), player);
			}
		});
		// a villager gone for good leaves nothing behind in the maps kept by villager (an unloaded one keeps its place)
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) -> {
			if (!(event.getEntity() instanceof Villager villager) || event.getLevel().isClientSide()) return;
			net.minecraft.world.entity.Entity.RemovalReason reason = villager.getRemovalReason();
			if (reason == null || !reason.shouldDestroy()) return;
			java.util.UUID id = villager.getUUID();
			com.vnap.village.life.Agenda.forget(id);
			com.vnap.village.town.Shelter.forget(id);
			VillagerHome.forget(id);
			CHILD_BEDS.remove(id);
			WEDDING_STARTED.remove(id);
		});
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
			com.vnap.village.town.Shelter.clear();
			NURSERY.clear();
			WEDDING_STARTED.clear();
			SAID.clear();
			SAID_AT.clear();
			VillagerHome.forgetStill();
			CHILD_BEDS.clear();
			FamilyEvents.clear();
			com.vnap.village.life.Agenda.clear();
			com.vnap.village.life.HouseBehavior.clear();
			com.vnap.village.town.Music.clear();
			Pets.clear();
		});
		NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player) FamilyEvents.wokeUp(player);
		});
	}

	// ---- gender and names --------------------------------------------------------------

	public static boolean female(Villager villager) {
		return villager instanceof VillagerLifeData data && data.vnap$female();
	}

	public static FamilyState state(Villager villager) {
		return data(villager).vnap$family();
	}

	@SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST", justification = "VillagerDataMixin makes every Villager a VillagerLifeData.")
	private static VillagerLifeData data(Villager villager) {
		return (VillagerLifeData) villager;
	}

	/** A name tag with a woman's or a man's name decides it; everyone else is a coin flip on the UUID. */
	public static void ensureGender(Villager villager) {
		if (!(villager instanceof VillagerLifeData data) || data.vnap$family().genderKnown) return;
		boolean female;
		Component custom = villager.getCustomName();
		String name = custom == null ? "" : custom.getString().trim();
		if (contains(FEMALE_NAMES, name)) {
			female = true;
		} else if (contains(MALE_NAMES, name)) {
			female = false;
		} else {
			female = (villager.getUUID().getLeastSignificantBits() & 1L) == 1L;
		}
		data.vnap$setFemale(female);
		data.vnap$family().genderKnown = true;
	}

	public static String givenName(Villager villager) {
		String[] names = female(villager) ? FEMALE_NAMES : MALE_NAMES;
		int start = Math.floorMod(villager.getUUID().hashCode(), names.length);
		// two of one name in a village read as one person on signs, letters and the board: the next free one
		java.util.Set<String> taken = new java.util.HashSet<>();
		for (Villager other : villager.level().getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(TownRules.SPAN),
				other -> !other.equals(villager) && other.hasCustomName())) {
			Component custom = other.getCustomName();
			if (custom != null) taken.add(custom.getString());
		}
		for (int i = 0; i < names.length; i++) {
			String name = names[(start + i) % names.length];
			if (!taken.contains(name)) return name;
		}
		return names[start];
	}

	private static boolean contains(String[] names, String name) {
		for (String candidate : names) if (candidate.equals(name)) return true;
		return false;
	}

	// ---- household ---------------------------------------------------------------------

	/** The village day (Calendar): it turns with the world's calendar, sleeping the night away too, and never goes back. */
	public static long day(ServerLevel level) {
		return com.vnap.village.town.Calendar.day(level);
	}

	/** The clock: ticks into the day, 0 to 23999 (0 sunrise, 6000 noon, 12000 dusk). Days are counted by {@link #day}. */
	public static long timeOfDay(Level level) {
		return timeOfDay(com.vnap.Mc.dayTime(level));
	}

	public static long timeOfDay(long dayTime) {
		return Math.floorMod(dayTime, 24000L);
	}

	/** {@link #timeOfDay} as a fraction of the day (0.5 = dusk). */
	public static float dayFraction(Level level) {
		return timeOfDay(level) / 24000.0F;
	}

	/** The head's bed: where this villager's family lives. */
	public static @Nullable BlockPos home(Villager villager) {
		FamilyState family = state(villager);
		return family.home != null ? family.home : data(villager).vnap$homeBed();
	}

	/** The home is loaded and within reach of a walk: home chores and checks run only then. */
	public static boolean homeNear(ServerLevel level, Villager villager, @Nullable BlockPos home) {
		return home != null && level.isLoaded(home) && home.closerThan(villager.blockPosition(), TownRules.NEAR_BELL);
	}

	public static boolean isHead(Villager villager) {
		BlockPos bed = data(villager).vnap$homeBed();
		FamilyState family = state(villager);
		return bed != null && (family.home == null || family.home.equals(bed));
	}

	/** Lives in someone else's house: a spouse who moved in, or a child. */
	public static boolean member(Villager villager) {
		FamilyState family = state(villager);
		return family.home != null && !family.home.equals(data(villager).vnap$homeBed());
	}

	/** Loaded villagers who live under this head's roof, the head included. */
	public static List<Villager> household(ServerLevel level, Villager head) {
		BlockPos anchor = home(head);
		if (anchor == null) return List.of(head);
		UUID id = head.getUUID();
		return level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(TownRules.NEAR_BELL),
				villager -> villager.isAlive() && (villager.equals(head) || id.equals(state(villager).head)));
	}

	/** Everyone under the same roof as this villager, whether it heads the house or lives in it. */
	public static List<Villager> householdOf(ServerLevel level, Villager villager) {
		FamilyState family = state(villager);
		if (family.head != null && !family.head.equals(villager.getUUID())
				&& level.getEntity(family.head) instanceof Villager head && head.isAlive()) {
			return household(level, head);
		}
		return household(level, villager);
	}

	/** VillagerHome claimed a house for this villager: it heads that house from now on. */
	static void onClaim(Villager villager, BlockPos bed, @Nullable BlockPos sign) {
		FamilyState family = state(villager);
		family.home = bed.immutable();
		family.head = villager.getUUID();
		family.signPos = sign == null ? null : sign.immutable();
	}

	/** The bed of this villager is gone. A head loses the house; a member waits for the head to give it a new bed. */
	static void onLostBed(Villager villager, BlockPos bed) {
		FamilyState family = state(villager);
		if (bed.equals(family.home)) {
			family.home = null;
			family.signPos = null;
		}
	}

	/**
	 * The head's daily chore: a bed for the first member of the house who has none (the spouse right beside the
	 * head's bed). True when something was placed.
	 */
	static boolean furnish(ServerLevel level, Villager head) {
		BlockPos anchor = data(head).vnap$homeBed();
		if (anchor == null || !isHead(head)) return false;
		// a bed already standing free in the house (the mayor's hall has its family's) is taken at once
		for (Villager member : household(level, head)) {
			if (member.equals(head) || hasBedHere(level, member, anchor)) continue;
			BlockPos free = bedFor(level, head, anchor);
			if (free == null) break;
			moveIn(level, member, anchor, free);
		}
		// A head with AI builds the bed by hand (FamilyBuildBehavior); this instant bed is for the AI-less.
		if (!head.isNoAi()) return false;
		for (Villager member : household(level, head)) {
			if (member.equals(head) || hasBedHere(level, member, anchor)) continue;
			boolean spouse = member.getUUID().equals(state(head).spouse);
			BlockPos[] spot = FamilyHome.bedSpot(level, anchor, spouse);
			if (spot == null) return false;
			moveIn(level, member, anchor, FamilyHome.placeBed(level, anchor, spot[0], spot[1]));
			return true;
		}
		return false;
	}

	/** The first member of this head's house without a bed there (the spouse first), or null. */
	public static @Nullable Villager needsBed(ServerLevel level, Villager head) {
		BlockPos anchor = data(head).vnap$homeBed();
		if (anchor == null || !isHead(head)) return null;
		Villager waiting = null;
		for (Villager member : household(level, head)) {
			if (member.equals(head) || hasBedHere(level, member, anchor)) continue;
			if (member.getUUID().equals(state(head).spouse)) return member;
			if (waiting == null) waiting = member;
		}
		return waiting;
	}

	/** The head finished a bed by hand: the member sleeps there from now on. False when the spot was taken meanwhile. */
	public static boolean buildBed(ServerLevel level, Villager head, Villager member, BlockPos foot, BlockPos headCell) {
		BlockPos anchor = data(head).vnap$homeBed();
		if (anchor == null || !level.getBlockState(foot).isAir() || !level.getBlockState(headCell).isAir()) return false;
		moveIn(level, member, anchor, FamilyHome.placeBed(level, anchor, foot, headCell));
		return true;
	}

	/** Moves a member into another bed of the same house (a child into its new room). */
	public static void giveBed(ServerLevel level, Villager member, BlockPos bed) {
		BlockPos anchor = state(member).home;
		leaveBed(level, member);
		moveIn(level, member, anchor == null ? bed : anchor, bed);
	}

	/**
	 * The villager moves to a new house: a head gives up the old one (members follow on their next day), a member
	 * (a mayor who lived in his wife's house) gives up its bed there.
	 */
	public static void leaveForNewHouse(ServerLevel level, Villager villager) {
		if (isHead(villager)) leaveHouse(level, villager);
		else if (member(villager)) leaveBed(level, villager);
	}

	private static boolean hasBedHere(ServerLevel level, Villager villager, BlockPos anchor) {
		BlockPos own = data(villager).vnap$homeBed();
		return own != null && VillagerHome.stillBed(level, own) && own.closerThan(anchor, 9.0);
	}

	/** The member sleeps in {@code bed} from now on; the bed it kept while it waited for this one is given up. */
	private static void moveIn(ServerLevel level, Villager villager, BlockPos anchor, BlockPos bed) {
		BlockPos head = FamilyHome.bedHead(level, bed);
		BlockPos old = data(villager).vnap$homeBed();
		if (old != null && !old.equals(head)) {
			if (state(villager).signPos != null) leaveHouse(level, villager);
			else leaveBed(level, villager);
		}
		data(villager).vnap$setHomeBed(head);
		state(villager).home = anchor.immutable();
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
		FamilyHome.take(level, head);
	}

	/** A bed for the coming baby: a free one in the house, else a new one where the room allows. Its ticket is taken. */
	public static Optional<BlockPos> childBed(ServerLevel level, Villager parent) {
		BlockPos anchor = home(parent);
		if (anchor == null) return Optional.empty();
		Villager head = headOf(level, parent);
		BlockPos ready = state(head).nursery;
		state(head).nursery = null;
		if (ready != null && VillagerHome.stillBed(level, ready)) {
			// made ready by hand, its ticket already held
			CHILD_BEDS.put(parent.getUUID(), FamilyHome.bedHead(level, ready));
			return Optional.of(FamilyHome.bedHead(level, ready));
		}
		BlockPos bed = bedFor(level, head, anchor);
		if (bed == null) {
			BlockPos[] spot = FamilyHome.bedSpot(level, anchor, false);
			if (spot == null) return Optional.empty();
			// the baby cannot wait for the stall: the bed is bought at once (the treasury helps a poor family)
			com.vnap.village.market.Market.order(level, head, spot[1], net.minecraft.world.item.Items.WHITE_BED);
			bed = FamilyHome.placeBed(level, anchor, spot[0], spot[1]);
		}
		if (!FamilyHome.take(level, bed)) return Optional.empty();
		CHILD_BEDS.put(parent.getUUID(), bed);
		return Optional.of(bed);
	}

	/** Rewrites the head's home sign: its name, the spouse, how many children live here. */
	public static void rewriteSign(ServerLevel level, Villager head) {
		BlockPos anchor = home(head);
		if (anchor == null || !isHead(head)) return;
		FamilyState family = state(head);
		String name = VillagerHome.nameOf(head);
		BlockPos sign = FamilyHome.findSign(level, anchor, family.signPos, name);
		if (sign == null) return;
		family.signPos = sign;
		int kids = (int) household(level, head).stream()
				.filter(villager -> !villager.equals(head) && !villager.getUUID().equals(family.spouse)).count();
		FamilyHome.rewriteSign(level, sign, name, family.spouse == null ? "" : family.spouseName, kids);
	}

	private static void rewriteSignOf(ServerLevel level, @Nullable UUID headId) {
		if (headId != null && level.getEntity(headId) instanceof Villager head && head.isAlive()) rewriteSign(level, head);
	}

	// ---- daily life --------------------------------------------------------------------

	/** Every 100 ticks for each loaded, awake villager, whatever the dialogue settings. Cheap until a new day. */
	private static void tickAll(MinecraftServer server) {
		if (++ticker % 5 == 0) Pets.tickTraining(server);
		if (ticker % 100 != 0) return;
		if (ticker % 24000 == 0) forgetOld(day(server.overworld()));
		for (ServerLevel level : server.getAllLevels()) {
			long day = day(level);
			// Only where vanilla itself runs villagers: chunks a player (simulation distance) or a chunk loader keeps
			// entity-ticking. Border chunks hold frozen villagers that could not walk to a wedding anyway.
			for (Villager villager : level.getEntities(EntityType.VILLAGER, villager -> villager.isAlive()
					&& level.isPositionEntityTicking(villager.blockPosition()))) {
				if (villager.isInPowderSnow || level.getBlockState(villager.blockPosition()).is(Blocks.POWDER_SNOW)) outOfSnow(level, villager);
				outOfWater(level, villager);
				com.vnap.village.town.Shelter.night(level, villager);
				// a traveller at the tavern is nobody's yet: no family life, not one of the town's people
				if (com.vnap.village.town.Travellers.passing(villager)) {
					com.vnap.village.town.Travellers.tick(level, villager, day);
					continue;
				}
				com.vnap.village.home.Towns.seen(level, villager, day);
				if (Infants.tick(level, villager, day)) continue;
				if (villager.isSleeping()) {
					Generations.night(level, villager, day);
					continue;
				}
				VillagerHome.homeward(level, villager);
				VillagerHome.tick(level, villager, day);
				tick(level, villager, day);
			}
			if (level.dimension().equals(Level.OVERWORLD)) {
				Graveyard.tick(level);
				com.vnap.village.home.Placements.tick(level);
				com.vnap.village.town.TownLife.tick(level);
			}
		}
		Hamlets.tick(server);
	}

	/**
	 * Mountain villages have powder snow right by their doors: a villager pushed into it cannot climb out and freezes
	 * to death within seconds, taking a family with it. It is set on the nearest firm ground instead.
	 */
	private static void outOfSnow(ServerLevel level, Villager villager) {
		BlockPos from = villager.blockPosition();
		BlockPos best = null;
		// sunk up to two blocks deep: firm ground may be up to the snow's top; a snowy field of it is wide (two froze
		// to death in a snowy village with no firm ground within four blocks)
		for (BlockPos pos : BlockPos.betweenClosed(from.offset(-8, -1, -8), from.offset(8, 3, 8))) {
			if (level.getBlockState(pos).is(Blocks.POWDER_SNOW) || level.getBlockState(pos.above()).is(Blocks.POWDER_SNOW)
					|| !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					|| !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
					|| !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
			if (best == null || pos.distSqr(from) < best.distSqr(from)) best = pos.immutable();
		}
		if (best == null) return;
		villager.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
		villager.setTicksFrozen(0);
	}

	/**
	 * A villager in water three family ticks running (a pond two deep by the bell, a ditch) climbs out onto the
	 * nearest dry ground: villagers cannot jump out of a pool whose rim stands over the water.
	 */
	static final int WET_TICKS = 3;

	private static void outOfWater(ServerLevel level, Villager villager) {
		FamilyState family = state(villager);
		if (!villager.isInWater() || villager.isPassenger() || villager.isNoAi()) {
			family.wetTicks = 0;
			return;
		}
		if (++family.wetTicks < WET_TICKS) return;
		BlockPos from = villager.blockPosition();
		BlockPos best = null;
		for (BlockPos pos : BlockPos.betweenClosed(from.offset(-4, -1, -4), from.offset(4, 3, 4))) {
			if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()
					|| !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
					|| !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
					|| !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) continue;
			if (best == null || pos.distSqr(from) < best.distSqr(from)) best = pos.immutable();
		}
		if (best == null) return;
		family.wetTicks = 0;
		villager.getNavigation().stop();
		villager.teleportTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5);
	}

	public static void tick(ServerLevel level, Villager villager, long day) {
		ensureGender(villager);
		if (Hamlets.leaving(villager)) {
			Hamlets.tickLeaving(level, villager);
			return;
		}
		FamilyState family = state(villager);
		// a hero mends between fights
		if (com.vnap.spire.Heroes.armed(villager) && villager.getHealth() < villager.getMaxHealth()) villager.heal(1.0F);
		Infants.parentTick(level, villager, family, day);
		Generations.ensureAge(villager, family, day);
		// the village's day begins one by one over the morning, not for everyone on one tick (one without a brain: a
		// test, a command, at once)
		if (family.tickedDay != day && (villager.isNoAi() || Math.floorMod(level.getGameTime(), 24000L) >= Temperament.dawn(villager))) {
			family.tickedDay = day;
			daily(level, villager, family, day);
		}
		if (family.weddingDay >= 0 && day >= family.weddingDay && female(villager)) tickWedding(level, villager, family);
		// Small talk and its walks are for someone to see; a chunk-loaded village with nobody around skips them.
		if (isHead(villager)) Pets.tick(level, villager, family, day);
		Mayors.tick(level, villager);
		if (com.vnap.village.market.Shops.isKeeper(villager)) Mayors.leaveJobSites(villager);
		com.vnap.village.market.Market.tick(level, villager, day);
		if (level.hasNearbyAlivePlayer(villager.getX(), villager.getY(), villager.getZ(), TownRules.NEAR_BELL)) ambient(level, villager, family, day);
	}

	private static void daily(ServerLevel level, Villager villager, FamilyState family, long day) {
		FamilyDeaths deaths = FamilyDeaths.get(level.getServer());
		if (family.spouse != null && deaths.contains(family.spouse)) widowed(level, villager, day);
		if (family.courting != null && deaths.contains(family.courting)) cancelCourting(family);
		if (member(villager)) follow(level, family, deaths);
		Generations.daily(level, villager, family, day);
		// a couple that may have a child is given, free, the bread vanilla wants of both before they breed (12 food
		// points each): the town grows whatever its stall and pantries hold
		if (family.spouse != null && level.getEntity(family.spouse) instanceof Villager spouse && spouse.isAlive()
				&& !villager.isSleeping() && !villager.canBreed() && mayHaveChild(level, villager, spouse)) {
			villager.getInventory().addItem(new ItemStack(Items.BREAD, 3));
		}
		// a young heir the office waits for is counted as seen every day
		Mayors.daily(level, villager, day);
		if (villager.isBaby()) return;
		com.vnap.village.market.Shops.daily(level, villager, day);
		FamilyEvents.maybeQuarrel(level, villager, family, day);
		// a grown child moves out the day after the coming-of-age party
		if (family.home != null && !family.married() && (family.mother != null || family.father != null) && member(villager)
				&& day > family.grownDay && !Mayors.isMayor(villager)) {
			moveOut(level, villager, family);
		}
		if (family.courting != null) {
			Villager partner = level.getEntity(family.courting) instanceof Villager found && found.isAlive() ? found : null;
			if (partner != null && !courtsEachOther(villager, partner)) {
				// The partner gave up while one of them was out of loaded chunks (and may have married since).
				cancelCourting(family);
			} else if (partner == null && day - family.courtStartDay > MoodConfig.courtshipDays() + 3L) {
				cancelCourting(family);
			} else if (partner != null && family.weddingDay < 0 && day - family.courtStartDay >= MoodConfig.courtshipDays()) {
				family.weddingDay = day;
				state(partner).weddingDay = day;
			}
			return;
		}
		if (isHead(villager)) Pets.daily(level, villager, family, day);
		Hamlets.consider(level, villager, day);
		if (single(villager, family, day)) court(level, villager, day);
	}

	/** A member whose head moved (a new bed, a new house) or died follows: it sleeps in its old bed until the head gives it one there. */
	private static void follow(ServerLevel level, FamilyState family, FamilyDeaths deaths) {
		if (family.head != null && deaths.contains(family.head)) {
			UUID parent = family.mother != null && !deaths.contains(family.mother) ? family.mother
					: family.father != null && !deaths.contains(family.father) ? family.father : null;
			if (parent != null && level.getEntity(parent) instanceof Villager living) family.head = state(living).head;
		}
		if (family.head == null || !(level.getEntity(family.head) instanceof Villager head) || !head.isAlive()) return;
		BlockPos headHome = home(head);
		if (headHome == null || headHome.equals(family.home)) return;
		// the old bed is kept until the head sets one down in the new house: nobody sleeps standing meanwhile
		family.home = headHome.immutable();
	}

	private static boolean courtsEachOther(Villager one, Villager other) {
		return other.getUUID().equals(state(one).courting) && one.getUUID().equals(state(other).courting) && !state(other).married();
	}

	private static void cancelCourting(FamilyState family) {
		family.courting = null;
		family.weddingDay = -1L;
	}

	public static boolean single(Villager villager, FamilyState family, long day) {
		return !villager.isBaby() && !family.married() && family.courting == null && !Hamlets.leaving(villager)
				&& day - family.widowedDay >= MoodConfig.remarryDays()
				&& villager.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT);
	}

	private static void court(ServerLevel level, Villager villager, long day) {
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
		if (bell == null) return;
		Villager match = match(level, villager, bell, day);
		if (match != null) startCourting(villager, match, day);
	}

	private static boolean sameVillage(Villager other, BlockPos bell) {
		BlockPos theirs = com.vnap.village.home.Towns.bellOf(other);
		return theirs != null && theirs.closerThan(bell, TownRules.SPAN);
	}

	/** The nearest single of the other sex in the same village, not kin; one of the two must have a house. */
	public static @Nullable Villager match(ServerLevel level, Villager villager, BlockPos bell, long day) {
		boolean female = female(villager);
		FamilyState family = state(villager);
		return level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(TownRules.COURT_RANGE),
						other -> !other.equals(villager) && other.isAlive() && female(other) != female && single(other, state(other), day)
								&& sameVillage(other, bell)
								&& !family.related(other.getUUID(), state(other), villager.getUUID())
								&& (isHead(villager) || isHead(other)))
				// near ones first, but not just the nearest: some take to each other more (the same for a pair always)
				.stream().min(Comparator.comparingDouble(other -> other.distanceToSqr(villager)
						* (0.3 + Temperament.roll(other.getUUID(), villager.getUUID().getLeastSignificantBits())))).orElse(null);
	}

	public static void startCourting(Villager a, Villager b, long day) {
		for (Villager villager : new Villager[]{a, b}) {
			FamilyState family = state(villager);
			family.courting = (villager.equals(a) ? b : a).getUUID();
			family.courtStartDay = day;
			family.weddingDay = -1L;
			WEDDING_STARTED.remove(villager.getUUID());
		}
	}

	/** Bride's side runs it: both walk to the bell, then the vows, the cheers and the move into one house. */
	private static void tickWedding(ServerLevel level, Villager bride, FamilyState family) {
		if (family.courting == null || !(level.getEntity(family.courting) instanceof Villager groom) || !groom.isAlive()) return;
		if (!courtsEachOther(bride, groom)) {
			WEDDING_STARTED.remove(bride.getUUID());
			cancelCourting(family);
			return;
		}
		if (!isHead(groom) && !isHead(bride)) {
			// Neither has a house any more: court again until one of them has.
			startCourting(bride, groom, day(level));
			return;
		}
		BlockPos bell = com.vnap.village.home.Towns.bellOf(bride, bride.blockPosition());
		// before the chapel's altar once the town has its chapel
		BlockPos altar = com.vnap.village.town.TownLife.weddingPlace(level, bell);
		long now = level.getGameTime();
		if (!WEDDING_STARTED.containsKey(bride.getUUID())) FamilyEvents.inviteNeighbours(level, bride, altar);
		long started = WEDDING_STARTED.computeIfAbsent(bride.getUUID(), ignored -> now);
		boolean there = bride.blockPosition().closerThan(altar, ALTAR_RANGE) && groom.blockPosition().closerThan(altar, ALTAR_RANGE);
		if (!there && now - started < WEDDING_WALK) {
			for (Villager one : new Villager[]{bride, groom}) {
				// to the altar, and there while the wedding lasts
				com.vnap.village.life.Agenda.add(one, new com.vnap.village.life.Agenda.Item("wedding", altar, com.vnap.village.life.Agenda.EVENT, 1, 0.6F,
						ignored -> WEDDING_STARTED.containsKey(bride.getUUID()), null, null));
			}
			return;
		}
		WEDDING_STARTED.remove(bride.getUUID());
		if (!marry(level, groom, bride)) {
			startCourting(bride, groom, day(level));
			return;
		}
		long words = Mayors.officiate(level, bride, groom, bell);
		Speech.queue(bride, "Woman Wedding Vow", groom, 2L + words);
		Speech.queue(groom, "Man Wedding Vow", bride, 4L + words);
		level.getEntitiesOfClass(Villager.class, bride.getBoundingBox().inflate(16.0),
						guest -> !guest.equals(bride) && !guest.equals(groom) && guest.isAlive() && !guest.isBaby() && !female(guest))
				.stream().findFirst()
				.ifPresent(guest -> Speech.queue(guest, "Wedding Cheers", bride, 6L + words));
		level.sendParticles(ParticleTypes.HEART, bride.getX(), bride.getY() + 2.0, bride.getZ(), 12, 0.8, 0.4, 0.8, 0.02);
		level.sendParticles(ParticleTypes.NOTE, groom.getX(), groom.getY() + 2.2, groom.getZ(), 8, 0.8, 0.4, 0.8, 1.0);
		FamilyEvents.weddingFireworks(level, groom, bride, bell);
		level.playSound(null, altar, com.vnap.sound.GolemSounds.WEDDING, net.minecraft.sounds.SoundSource.NEUTRAL, 2.0F, 1.0F);
		com.vnap.village.town.TownLife.ringChapel(level, bell, com.vnap.sound.GolemSounds.CHAPEL_BELLS);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, bride.getBoundingBox().inflate(32.0))) {
			VillageAdvancements.award(player, "family/wedding");
		}
	}

	/**
	 * Spouses for life; whoever has no house of their own moves into the other's (the husband's when both have one),
	 * still sleeping in its own bed until the head sets one down there. A widow who gives up her house brings her
	 * children: they follow her to the new one on their next day.
	 */
	public static boolean marry(ServerLevel level, Villager husband, Villager wife) {
		Villager head = isHead(husband) ? husband : isHead(wife) ? wife : null;
		if (head == null) return false;
		Villager other = head.equals(husband) ? wife : husband;
		BlockPos anchor = data(head).vnap$homeBed();
		if (anchor == null) return false;
		FamilyState headState = state(head);
		FamilyState otherState = state(other);
		UUID oldHead = otherState.head;
		headState.spouse = other.getUUID();
		headState.spouseName = VillagerHome.nameOf(other);
		com.vnap.village.town.Chronicle.note(level, head.blockPosition(), "wedding", VillagerHome.nameOf(husband), VillagerHome.nameOf(wife));
		otherState.spouse = head.getUUID();
		otherState.spouseName = VillagerHome.nameOf(head);
		for (FamilyState family : new FamilyState[]{headState, otherState}) {
			cancelCourting(family);
			family.childrenBorn = 0;
		}
		WEDDING_STARTED.remove(husband.getUUID());
		WEDDING_STARTED.remove(wife.getUUID());
		headState.home = anchor.immutable();
		headState.head = head.getUUID();
		takeIn(level, head, other, anchor);
		furnish(level, head);
		rewriteSign(level, head);
		if (oldHead != null && !oldHead.equals(other.getUUID())) rewriteSignOf(level, oldHead);
		return true;
	}

	/** {@code other} and the children of its house live under {@code head}'s roof from now on (their old beds kept meanwhile). */
	private static void takeIn(ServerLevel level, Villager head, Villager other, BlockPos anchor) {
		state(other).home = anchor.immutable();
		state(other).head = head.getUUID();
		for (Villager kid : level.getEntitiesOfClass(Villager.class, other.getBoundingBox().inflate(TownRules.NEAR_BELL * 2),
				kid -> kid.isAlive() && other.getUUID().equals(state(kid).head))) {
			state(kid).head = head.getUUID();
		}
	}

	/**
	 * The head moved into a house of its own (the mayor's hall): the spouse (who may head the house the head married
	 * into) and the children come along, straight into the free beds there; each house left behind is free for
	 * someone else. Without a free bed one keeps its old bed until the head sets one down.
	 */
	public static void gather(ServerLevel level, Villager head, @Nullable BlockPos oldHome) {
		BlockPos anchor = data(head).vnap$homeBed();
		if (anchor == null) return;
		FamilyState family = state(head);
		List<BlockPos> left = new java.util.ArrayList<>();
		if (oldHome != null && !oldHome.equals(anchor)) left.add(oldHome);
		Villager spouse = family.spouse != null && level.getEntity(family.spouse) instanceof Villager found && found.isAlive() ? found : null;
		if (spouse != null && !head.getUUID().equals(state(spouse).head)) {
			BlockPos hers = data(spouse).vnap$homeBed();
			if (hers != null && !left.contains(hers)) left.add(hers);
			takeIn(level, head, spouse, anchor);
		}
		List<Villager> members = level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(TownRules.NEAR_BELL * 2),
				member -> member.isAlive() && !member.equals(head) && head.getUUID().equals(state(member).head));
		// the spouse first: the bed by the head's
		members.sort(Comparator.comparing((Villager member) -> !member.getUUID().equals(family.spouse)));
		for (Villager member : members) {
			state(member).home = anchor.immutable();
			if (hasBedHere(level, member, anchor)) continue;
			BlockPos bed = bedFor(level, head, anchor);
			if (bed == null) break;
			moveIn(level, member, anchor, bed);
		}
		Pets.moveHouse(level, head, spouse, left);
		furnish(level, head);
		rewriteSign(level, head);
	}

	/**
	 * A bed for a member of the house: a free one in it; in the mayor's hall also a spare one a villager of another
	 * house has taken for itself meanwhile (vanilla lets the homeless grab any free bed): the guest gives it up.
	 */
	private static @Nullable BlockPos bedFor(ServerLevel level, Villager head, BlockPos anchor) {
		BlockPos free = FamilyHome.freeBed(level, anchor);
		if (free != null || !Mayors.inHall(level, anchor)) return free;
		UUID id = head.getUUID();
		for (Villager guest : level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(TownRules.NEAR_BELL),
				villager -> villager.isAlive() && !villager.equals(head) && !id.equals(state(villager).head))) {
			BlockPos bed = guest.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
			if (bed == null || bed.equals(anchor) || !Mayors.inHall(level, bed) || !VillagerHome.stillBed(level, bed)) continue;
			if (data(guest).vnap$homeBed() != null && bed.equals(data(guest).vnap$homeBed())) data(guest).vnap$setHomeBed(null);
			guest.getBrain().eraseMemory(MemoryModuleType.HOME);
			FamilyHome.release(level, bed);
			return bed;
		}
		return null;
	}

	/**
	 * A grown heir who took the mayor's office in the hall it grew up in, its head dead: it heads the house now
	 * (from its own bed), and its brothers and sisters live under its roof.
	 */
	public static void takeOver(ServerLevel level, Villager heir) {
		FamilyState family = state(heir);
		UUID late = family.head;
		BlockPos anchor = family.home;
		BlockPos own = data(heir).vnap$homeBed();
		if (late == null || late.equals(heir.getUUID()) || anchor == null || own == null
				|| !FamilyDeaths.get(level.getServer()).contains(late)) return;
		family.home = own.immutable();
		family.head = heir.getUUID();
		family.signPos = FamilyHome.findSign(level, anchor, null, "");
		for (Villager kid : level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(TownRules.NEAR_BELL),
				kid -> kid.isAlive() && !kid.equals(heir) && late.equals(state(kid).head))) {
			state(kid).head = heir.getUUID();
			state(kid).home = own.immutable();
		}
		rewriteSign(level, heir);
	}

	/** Gives up an own house: its sign comes down and the bed is free for a homeless neighbour. */
	private static void leaveHouse(ServerLevel level, Villager villager) {
		BlockPos bed = data(villager).vnap$homeBed();
		if (bed == null) return;
		FamilyState family = state(villager);
		BlockPos sign = FamilyHome.findSign(level, bed, family.signPos, VillagerHome.nameOf(villager));
		if (sign != null) level.removeBlock(sign, false);
		family.signPos = null;
		leaveBed(level, villager);
		VillagerHome.noteStructureChange();
	}

	private static void leaveBed(ServerLevel level, Villager villager) {
		BlockPos bed = data(villager).vnap$homeBed();
		if (bed != null) FamilyHome.release(level, bed);
		data(villager).vnap$setHomeBed(null);
		villager.getBrain().eraseMemory(MemoryModuleType.HOME);
	}

	/** A grown child leaves its bed for the next one; HomeProjects builds it a house after a couple of days. */
	private static void moveOut(ServerLevel level, Villager villager, FamilyState family) {
		UUID headId = family.head;
		leaveBed(level, villager);
		family.home = null;
		family.head = null;
		if (family.mother != null && level.getEntity(family.mother) instanceof Villager mother && mother.isAlive()) {
			Speech.queue(mother, "Woman Child Grew Up", villager, 2L);
		}
		rewriteSignOf(level, headId);
	}

	// ---- children ----------------------------------------------------------------------

	/** Gate for VillagerMakeLove: spouses in one house, under the child cap and cooldown, in a village not too sour. */
	public static boolean mayHaveChild(ServerLevel level, Villager a, Villager b) {
		return whyNoChild(level, a, b) == null;
	}

	/** Why this couple cannot have a child now, or null when it can. */
	public static @Nullable String whyNoChild(ServerLevel level, Villager a, Villager b) {
		FamilyState first = state(a);
		FamilyState second = state(b);
		if (!b.getUUID().equals(first.spouse) || !a.getUUID().equals(second.spouse)) return "not married to each other";
		BlockPos anchor = home(a);
		if (anchor == null || !anchor.equals(home(b))) return "not living in one house";
		if (Math.max(first.childrenBorn, second.childrenBorn) >= MoodConfig.maxChildren()) return "enough children";
		if (day(level) - Math.max(first.lastBirthDay, second.lastBirthDay) < MoodConfig.birthCooldownDays()) return "a baby was born lately";
		MoodState mood = VillagerHome.moodAt(level, anchor);
		if (mood != null && mood.mood < MoodConfig.minMoodForBirth()) return "the village is too sour";
		if (first.quarrelling(day(level)) || second.quarrelling(day(level))) return "quarrelled";
		if (!FamilyHome.roomForChild(level, anchor)) return "no room for a child's bed";
		if (com.vnap.village.home.Towns.full(level, com.vnap.village.home.Towns.bellOf(a, anchor))) return "the village is full";
		// the nursery is made ready by hand beforehand (FamilyBuildBehavior, HomeChoreBehavior); a pair without a
		// brain (a test, a command) gets it set down at the birth
		if (!a.isNoAi() && !b.isNoAi()) return nurseryLacks(level, female(a) ? a : b, a, anchor);
		return null;
	}

	/** What the nursery lacks, looked at once every NURSERY_TTL ticks a house: VillagerMakeLove asks every tick. */
	private static final Map<net.minecraft.core.GlobalPos, Map.Entry<Long, String>> NURSERY = new HashMap<>();
	private static final long NURSERY_TTL = 100L;

	private static @Nullable String nurseryLacks(ServerLevel level, Villager mother, Villager parent, BlockPos anchor) {
		long now = level.getGameTime();
		Map.Entry<Long, String> seen = NURSERY.get(net.minecraft.core.GlobalPos.of(level.dimension(), anchor));
		if (seen != null && now - seen.getKey() < NURSERY_TTL) return seen.getValue().isEmpty() ? null : seen.getValue();
		String lacks = !babyBedReady(level, parent, anchor) ? NO_BABY_BED
				: MoodConfig.infantDays() > 0 && Infants.freeCradle(level, mother, anchor) == null ? NO_CRADLE : "";
		if (NURSERY.size() > 512) NURSERY.clear();
		NURSERY.put(net.minecraft.core.GlobalPos.of(level.dimension(), anchor.immutable()), Map.entry(now, lacks));
		return lacks.isEmpty() ? null : lacks;
	}

	/** The nursery changed (a bed or a cradle set down): looked at again at once. */
	public static void nurseryChanged(net.minecraft.world.level.Level level, @Nullable BlockPos anchor) {
		if (anchor != null) NURSERY.remove(net.minecraft.core.GlobalPos.of(level.dimension(), anchor));
	}

	public static final String NO_BABY_BED = "no bed made for the baby yet";
	public static final String NO_CRADLE = "no cradle for the baby yet";

	/** A bed waits for the baby: the one the head made ready, or a free one in the house. */
	private static boolean babyBedReady(ServerLevel level, Villager parent, BlockPos anchor) {
		Villager head = headOf(level, parent);
		BlockPos nursery = state(head).nursery;
		return (nursery != null && VillagerHome.stillBed(level, nursery)) || FamilyHome.freeBed(level, anchor) != null;
	}

	private static Villager headOf(ServerLevel level, Villager member) {
		UUID id = state(member).head;
		return id != null && level.getEntity(id) instanceof Villager found ? found : member;
	}

	/** The head set a bed down for the coming baby by hand: kept for it (its ticket taken). False when the spot was taken. */
	public static boolean makeNursery(ServerLevel level, Villager head, BlockPos foot, BlockPos headCell) {
		BlockPos anchor = data(head).vnap$homeBed();
		if (anchor == null || !level.getBlockState(foot).isAir() || !level.getBlockState(headCell).isAir()) return false;
		BlockPos bed = FamilyHome.placeBed(level, anchor, foot, headCell);
		FamilyHome.take(level, bed);
		state(head).nursery = bed.immutable();
		nurseryChanged(level, anchor);
		return true;
	}

	/**
	 * A new town starts with households, not with singles waiting days to court: among its founders a third of the
	 * adults are wed at once (a head with a house and the nearest single of the other sex) — the rest court later, as
	 * ever — and half the couples (one at least) came with children, one or two each, never past the family's limit
	 * or the town's. Called each of the founding days: what stands already counts.
	 */
	public static String founders(ServerLevel level, BlockPos bell, long day) {
		List<Villager> adults = com.vnap.village.home.Towns.members(level, bell,
				villager -> !villager.isBaby() && com.vnap.village.market.Housing.foundedIt(villager));
		for (Villager adult : adults) ensureGender(adult);
		int wed = (int) adults.stream().filter(adult -> state(adult).spouse != null).count() / 2;
		int quota = adults.size() / 3;
		for (Villager head : adults) {
			if (wed >= quota) break;
			if (state(head).spouse != null || !isHead(head) || Generations.elder(head)) continue;
			// sweethearts of the first day are wed first; the rest pair with the nearest single
			UUID sweetheart = state(head).courting;
			Villager mate = adults.stream().filter(other -> sweetheart != null ? other.getUUID().equals(sweetheart)
							: !other.equals(head) && female(other) != female(head) && state(other).spouse == null
							&& state(other).courting == null && !Generations.elder(other))
					.min(Comparator.comparingDouble(other -> other.distanceToSqr(head))).orElse(null);
			if (mate == null) continue;
			if (female(head) ? marry(level, mate, head) : marry(level, head, mate)) wed++;
		}
		String why = adults.size() + " founders, " + wed + "/" + quota + " couples";
		List<Villager> mothers = adults.stream().filter(adult -> female(adult) && state(adult).spouse != null && home(adult) != null).toList();
		int families = (int) mothers.stream().filter(mother -> state(mother).childrenBorn > 0).count();
		int room = com.vnap.village.home.Towns.limit(level, bell) - com.vnap.village.home.Towns.population(level, bell);
		for (Villager mother : mothers) {
			if (families >= (mothers.size() + 1) / 2 || room <= 0) break;
			if (state(mother).childrenBorn > 0 || !(level.getEntity(state(mother).spouse) instanceof Villager father) || !father.isAlive()) continue;
			int kids = Math.min(MoodConfig.maxChildren(), 1 + (int) (mother.getUUID().getLeastSignificantBits() >>> 63));
			for (int i = 0; i < kids && room > 0; i++, room--) foundersChild(level, bell, mother, father, day);
			families++;
		}
		why += ", " + families + " with children, room " + room;
		com.vnap.VillagerLife.trace("vnap founders: {} at {}", why, bell.toShortString());
		return why;
	}

	/** A child the founding household came with: halfway through its childhood, in the family's house. */
	private static void foundersChild(ServerLevel level, BlockPos bell, Villager mother, Villager father, long day) {
		Villager child = mother.getBreedOffspring(level, father);
		if (child == null) return;
		com.vnap.Mc.snap(child, mother.getX(), mother.getY(), mother.getZ(), 0.0F, 0.0F);
		level.addFreshEntityWithPassengers(child);
		if (!child.isAddedToLevel()) return;
		ensureGender(child);
		int childhood = MoodConfig.childhoodDays();
		FamilyState family = state(child);
		family.bornDay = day - childhood / 2;
		family.mother = mother.getUUID();
		family.father = father.getUUID();
		BlockPos anchor = home(mother);
		family.home = anchor == null ? null : anchor.immutable();
		family.head = state(mother).head != null ? state(mother).head : mother.getUUID();
		child.setAge(-24000 * Math.max(1, childhood - childhood / 2));
		child.setCustomName(Component.literal(givenName(child)));
		child.setCustomNameVisible(true);
		com.vnap.village.home.Towns.born(level, child, bell);
		for (Villager parent : new Villager[]{mother, father}) {
			state(parent).childrenBorn++;
			state(parent).lastBirthDay = day;
		}
		rewriteSignOf(level, family.head);
	}

	/** The same birth VillagerMakeLove gives, without the walk and the wait: for commands and tests. */
	public static @Nullable Villager birth(ServerLevel level, Villager parent, Villager partner) {
		if (!mayHaveChild(level, parent, partner)) return null;
		Optional<BlockPos> bed = childBed(level, parent);
		if (bed.isEmpty()) return null;
		Villager child = parent.getBreedOffspring(level, partner);
		if (child != null) {
			parent.setAge(6000);
			partner.setAge(6000);
			com.vnap.Mc.snap(child, parent.getX(), parent.getY(), parent.getZ(), 0.0F, 0.0F);
			level.addFreshEntityWithPassengers(child);
		}
		if (child == null || !child.isAddedToLevel()) {
			FamilyHome.release(level, bed.get());
			CHILD_BEDS.remove(parent.getUUID());
			return null;
		}
		onBorn(level, parent, partner, child);
		child.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed.get()));
		return child;
	}

	public static void onBorn(ServerLevel level, Villager parent, Villager partner, Villager child) {
		Villager mother = female(parent) ? parent : partner;
		Villager father = mother.equals(parent) ? partner : parent;
		long day = day(level);
		ensureGender(child);
		state(child).bornDay = day;
		com.vnap.village.home.Towns.born(level, child, com.vnap.village.home.Towns.bellOf(mother, mother.blockPosition()));
		com.vnap.village.town.Chronicle.note(level, mother.blockPosition(), "birth", VillagerHome.nameOf(mother), VillagerHome.nameOf(father), VillagerHome.nameOf(child));
		FamilyState family = state(child);
		family.mother = mother.getUUID();
		family.father = father.getUUID();
		BlockPos anchor = home(mother);
		family.home = anchor == null ? null : anchor.immutable();
		family.head = state(mother).head != null ? state(mother).head : mother.getUUID();
		BlockPos bed = CHILD_BEDS.remove(parent.getUUID());
		if (bed == null) bed = CHILD_BEDS.remove(partner.getUUID());
		if (bed != null) data(child).vnap$setHomeBed(bed);
		// a vanilla baby grows up in one day: too short for school, games and a coming of age
		child.setAge(-24000 * MoodConfig.childhoodDays());
		Infants.onBorn(level, mother, child, day);
		// the cradle day comes before the childhood days
		if (Infants.infant(child)) child.setAge(child.getAge() - 24000 * MoodConfig.infantDays());
		child.setCustomName(Component.literal(givenName(child)));
		child.setCustomNameVisible(true);
		for (Villager adult : new Villager[]{mother, father}) {
			state(adult).childrenBorn++;
			state(adult).lastBirthDay = day;
		}
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, child.getBoundingBox().inflate(32.0))) {
			VillageAdvancements.award(player, "family/baby");
		}
		Speech.queue(mother, "Woman New Baby", child, 30L);
		com.vnap.village.post.Letters.toNeighbours(level, com.vnap.village.home.Towns.bellOf(mother, mother.blockPosition()), VillagerHome.nameOf(mother), "birth",
				VillagerHome.nameOf(child), VillagerHome.nameOf(father));
		Speech.queue(father, "Man New Baby", child, 34L);
		level.getEntitiesOfClass(Villager.class, child.getBoundingBox().inflate(TownRules.NEAR_BELL),
						sibling -> sibling.isBaby() && !sibling.equals(child) && mother.getUUID().equals(state(sibling).mother))
				.stream().findFirst()
				.ifPresent(sibling -> Speech.queue(sibling, "Kid Gets a Sibling", child, 38L));
		rewriteSignOf(level, family.head);
	}

	// ---- death -------------------------------------------------------------------------

	static void onDeath(ServerLevel level, Villager villager) {
		FamilyDeaths.get(level.getServer()).add(villager.getUUID());
		if (com.vnap.spire.Heroes.armed(villager)) Heroes.mourn(level, villager);
		com.vnap.spire.Heroes.bequeath(level, villager);
		com.vnap.village.town.Chronicle.note(level, villager.blockPosition(), "death", VillagerHome.nameOf(villager));
		com.vnap.village.home.Towns.forget(level, villager.getUUID());
		Graveyard.bury(level, villager);
		Mayors.onDeath(level, villager);
		com.vnap.village.market.Shops.onGone(level, villager);
		FamilyState family = state(villager);
		if (family.courting != null && level.getEntity(family.courting) instanceof Villager partner) cancelCourting(state(partner));
		Villager heir = family.spouse != null && level.getEntity(family.spouse) instanceof Villager spouse && spouse.isAlive() ? spouse : null;
		// no baby is coming now: the bed kept for it is anybody's again
		for (FamilyState couple : heir == null ? List.of(family) : List.of(family, state(heir))) {
			if (couple.nursery == null) continue;
			FamilyHome.release(level, couple.nursery);
			couple.nursery = null;
		}
		com.vnap.village.market.Market.bequeath(level, villager, heir);
		// the spouse by name, loaded or not: one in an unloaded chunk is the heir all the same
		com.vnap.village.home.HomeProjects.handOver(level, villager.getUUID(), family.spouse);
		if (family.spouse != null && level.getEntity(family.spouse) instanceof Villager spouse && spouse.isAlive()) {
			widowed(level, spouse, day(level));
			Pets.ownerGone(level, villager, spouse);
		} else {
			Pets.ownerGone(level, villager, null);
			UUID headId = family.head;
			if (!villager.getUUID().equals(headId)) {
				family.head = null;
				family.home = null;
				data(villager).vnap$setHomeBed(null);
				rewriteSignOf(level, headId);
			} else if (isHead(villager)) {
				// the eldest left under the roof heads the house, so the sign names the living; nobody left: the
				// sign would keep the bed from everyone for good
				household(level, villager).stream()
						.filter(member -> !member.equals(villager) && data(member).vnap$homeBed() != null)
						.min(Comparator.comparing(Villager::isBaby).thenComparingLong(member -> state(member).bornDay))
						.ifPresentOrElse(eldest -> takeOver(level, eldest), () -> leaveHouse(level, villager));
			}
		}
	}

	/**
	 * The spouse died. A widow living in the late head's house takes it over: its own bed if it has one there,
	 * else the head's bed. The children's head becomes the widow.
	 */
	private static void widowed(ServerLevel level, Villager survivor, long day) {
		FamilyState family = state(survivor);
		UUID late = family.spouse;
		family.lateSpouse = late;
		family.spouse = null;
		family.spouseName = "";
		family.widowedDay = day;
		if (member(survivor) && family.home != null) {
			BlockPos anchor = family.home;
			BlockPos own = data(survivor).vnap$homeBed();
			if (own == null || !VillagerHome.stillBed(level, own)) {
				own = VillagerHome.stillBed(level, anchor) ? anchor : null;
				if (own != null) {
					data(survivor).vnap$setHomeBed(own);
					survivor.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), own));
					FamilyHome.take(level, own);
				}
			}
			if (own == null) {
				family.home = null;
				family.head = null;
			} else {
				family.home = own.immutable();
				family.head = survivor.getUUID();
				family.signPos = FamilyHome.findSign(level, anchor, null, "");
				for (Villager kid : level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(TownRules.NEAR_BELL * 2),
						kid -> kid.isAlive() && late != null && late.equals(state(kid).head))) {
					state(kid).head = survivor.getUUID();
					state(kid).home = own.immutable();
				}
			}
		}
		rewriteSign(level, survivor);
		Speech.queue(survivor, female(survivor) ? "Woman Widowed" : "Man Widowed", null, 60L);
	}

	// ---- talk --------------------------------------------------------------------------

	/** Morning greeting between spouses; a courting pair's daily chat; a mother calling her children home in the evening. */
	private static void ambient(ServerLevel level, Villager villager, FamilyState family, long day) {
		if (villager.isSleeping()) return;
		float sun = dayFraction(level);
		// the family's scenes go by the villager's own clock (an early riser greets earlier); the town's by the sun's
		float time = Temperament.clock(level, villager);
		if (villager.isBaby()) {
			school(level, villager, time, day);
			FamilyEvents.greetGodparent(level, villager, family, day);
			FamilyEvents.visitNeighbour(level, villager, time, day);
			FamilyEvents.hideAndSeek(level, villager, time, day);
			Pets.kidAmbient(level, villager, time, day);
			return;
		}
		Generations.visitGrave(level, villager, family, time, day);
		Mayors.ambient(level, villager, sun, day);
		if (!female(villager)) {
			FamilyEvents.askForWish(level, villager, family, day);
			comeHome(level, villager, family, time, day);
			return;
		}
		inviteToDinner(level, villager, family, time, day);
		if (householdOf(level, villager).stream().noneMatch(Villager::isBaby)) Pets.kidAmbient(level, villager, time, day);
		if (time < 0.08F && family.spouse != null
				&& level.getEntity(family.spouse) instanceof Villager husband && husband.closerThan(villager, 10.0)
				&& Temperament.scene(villager, "morning", day, 0.5F) && once("morning:" + villager.getUUID(), day)) {
			Speech.queue(villager, "Woman Good Morning Dear", husband, 2L);
			Speech.queue(husband, "Man Good Morning Dear", villager, 4L);
		}
		if (time >= 0.1F && time < 0.45F && family.courting != null && family.weddingDay < 0
				&& level.getEntity(family.courting) instanceof Villager suitor && suitor.closerThan(villager, 12.0)
				&& Temperament.scene(villager, "court", day, 0.7F) && once("court:" + villager.getUUID(), day)) {
			Speech.queue(villager, "Woman Courting", suitor, 2L);
			Speech.queue(suitor, "Man Courting", villager, 4L);
		}
		if (time >= 0.52F && time < 0.58F && family.home != null) {
			level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(TownRules.NEAR_BELL),
							kid -> kid.isBaby() && villager.getUUID().equals(state(kid).mother) && !kid.isSleeping() && !Infants.infant(kid))
					.stream().findFirst()
					.filter(kid -> Temperament.scene(villager, "kids", day, 0.6F) && once("kids:" + villager.getUUID(), day))
					.ifPresent(kid -> Speech.queue(villager, "Woman Calls the Kids", kid, 2L));
		}
	}

	/** In the school's plan (town/school): the middle row of benches, and the teacher's place behind the desk. */
	private static final BlockPos SCHOOL_BENCH = new BlockPos(5, 1, 5);
	private static final BlockPos SCHOOL_DESK = new BlockPos(5, 1, 10);
	/** A morning at school: a quarter day of childhood. */
	private static final int SCHOOL_SECONDS = 300;

	/** Mornings: children walk to the village librarian, who gives them a little lesson (to the school, once the town has one). */
	private static void school(ServerLevel level, Villager kid, float time, long day) {
		if (time < 0.06F || time >= 0.2F) return;
		Villager elder = Generations.storyteller(level, kid);
		Villager teacher = elder != null ? elder : level.getEntitiesOfClass(Villager.class, kid.getBoundingBox().inflate(48.0),
						adult -> adult.isAlive() && !adult.isBaby() && !adult.isSleeping()
								&& com.vnap.Mc.is(adult.getVillagerData(), VillagerProfession.LIBRARIAN))
				.stream().min(Comparator.comparingDouble(adult -> Temperament.roll(adult.getUUID(), day))).orElse(null);
		BlockPos bell = com.vnap.village.home.Towns.bellOf(kid);
		Roads.Network network = bell == null ? null : Roads.get(level.getServer()).near(level.dimension(), bell);
		BlockPos desk = network == null ? null : TownPlots.at(network, TownPlots.Kind.SCHOOL, SCHOOL_BENCH);
		if (network != null && desk != null && TownPlots.built(network, TownPlots.Kind.SCHOOL)) {
			// the school: the children to its benches, the teacher (if there is one) to its desk; a morning there grows them up a quarter day sooner
			if (!kid.blockPosition().closerThan(desk, 3.0)) {
				com.vnap.village.life.Agenda.add(kid, new com.vnap.village.life.Agenda.Item("lesson", desk, com.vnap.village.life.Agenda.ROUTINE, 2, 0.6F,
						com.vnap.village.life.Agenda.until(0.2F), null, null));
				return;
			}
			if (!once(kid, "school", day)) return;
			kid.ageUp(SCHOOL_SECONDS);
			if (teacher != null && once(teacher, "lesson", day)) {
				BlockPos front = TownPlots.at(network, TownPlots.Kind.SCHOOL, SCHOOL_DESK);
				if (front != null) com.vnap.village.life.Agenda.add(teacher, new com.vnap.village.life.Agenda.Item("lesson", front, com.vnap.village.life.Agenda.ROUTINE, 2, 0.6F,
						com.vnap.village.life.Agenda.until(0.2F), null, null));
				Speech.queue(teacher, elder != null ? "Elder Tells a Story" : "Librarian Teaches the Kids", kid, 2L);
			}
			return;
		}
		if (teacher == null) return;
		if (!kid.closerThan(teacher, 5.0)) {
			com.vnap.village.life.Agenda.add(kid, new com.vnap.village.life.Agenda.Item("lesson", teacher.blockPosition(), com.vnap.village.life.Agenda.ROUTINE, 3, 0.6F,
					com.vnap.village.life.Agenda.until(0.2F), null, null));
			return;
		}
		if (once(teacher, "lesson", day)) {
			Speech.queue(teacher, elder != null ? "Elder Tells a Story" : "Librarian Teaches the Kids", kid, 2L);
		}
	}

	/** Evenings: a father walks home, and his children run to meet him. */
	private static void comeHome(ServerLevel level, Villager father, FamilyState family, float time, long day) {
		BlockPos anchor = home(father);
		if (time < 0.46F || time >= 0.52F || anchor == null || !family.married()) return;
		if (!father.blockPosition().closerThan(anchor, 6.0)) {
			com.vnap.village.life.Agenda.go(father, "home", anchor, com.vnap.village.life.Agenda.ROUTINE, null, null);
			return;
		}
		level.getEntitiesOfClass(Villager.class, father.getBoundingBox().inflate(10.0),
						kid -> kid.isBaby() && kid.isAlive() && father.getUUID().equals(state(kid).father) && !Infants.infant(kid))
				.stream().findFirst()
				.filter(kid -> Temperament.scene(father, "home", day, 0.6F) && once(father, "home", day))
				.ifPresent(kid -> {
					Speech.queue(kid, "Kid Welcomes Dad", father, 1L);
					Speech.queue(father, "Man Comes Home", kid, 3L);
				});
	}

	/** A wife who likes the player asks them in for dinner once a day and hands them a home meal. */
	private static void inviteToDinner(ServerLevel level, Villager wife, FamilyState family, float time, long day) {
		if (time < 0.44F || time >= 0.55F || !family.married() || family.quarrelling(day)) return;
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, wife.getBoundingBox().inflate(8.0))) {
			BlockPos anchor = home(wife);
			if (player.isSpectator() || wife.getPlayerReputation(player) < 25 || anchor == null) continue;
			// the meal is made of the family's own food: an empty pantry invites nobody
			DecorBlockEntity pantry = Pantry.of(level, anchor);
			if (Pantry.food(pantry) < 2 || !dinnerToday(wife, player.getUUID(), day)) continue;
			Pantry.take(pantry, 2);
			ItemEntity meal = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), new ItemStack(LifeItems.HOME_MEAL));
			meal.setPickUpDelay(0);
			level.addFreshEntity(meal);
			Speech.queue(wife, "Woman Invites the Player", player, 1L);
			VillageAdvancements.award(player, "family/guest");
			return;
		}
	}

	/** True for the first wife of a village to invite this player today: ten wives must not be ten free meals. */
	public static boolean dinnerToday(Villager wife, UUID player, long day) {
		return once("dinner:" + player + ":" + com.vnap.village.home.Towns.bellOf(wife, BlockPos.ZERO).asLong(), day);
	}

	/** Two villagers who stand together and one of them is a woman: spouses chat, two women gossip. */
	public static boolean pairChat(ServerLevel level, Villager first, Villager second, String key, long cooldown) {
		boolean spouses = first.getUUID().equals(state(second).spouse);
		boolean women = female(first) && female(second);
		if ((!spouses && !women) || (spouses && state(first).quarrelling(day(level))) || !once(key, level.getGameTime() / cooldown)) return false;
		Villager wife = female(first) ? first : second;
		Villager other = wife.equals(first) ? second : first;
		if (spouses) {
			return Speech.queue(wife, "Woman Chats With Husband", other, 1L)
					&& Speech.queue(other, "Man Chats With Wife", wife, 3L);
		}
		return Speech.queue(first, "Women Gossip", second, 1L)
				&& Speech.queue(second, "Women Gossip Reply", first, 3L);
	}

	/** After any line: the spouse answers an attack, the mother comes to a hurt or frightened child. */
	static void afterLine(ServerLevel level, Villager speaker, DialogueCatalog.DialogueGroup group, long duration) {
		FamilyState family = state(speaker);
		if (!speaker.isBaby() && family.spouse != null && ATTACKS.contains(group.title())
				&& level.getEntity(family.spouse) instanceof Villager spouse && spouse.closerThan(speaker, 16.0)) {
			Speech.queue(spouse, female(spouse) ? "Woman Defends Her Husband" : "Man Defends His Wife",
					null, duration + 4L);
		}
		if (speaker.isBaby() && family.mother != null && (CHILD_HURT.contains(group.title()) || CHILD_SCARED.contains(group.title()))
				&& level.getEntity(family.mother) instanceof Villager mother && mother.closerThan(speaker, 16.0)
				&& once("mother:" + speaker.getUUID(), level.getGameTime() / 600L)) {
			Speech.queue(mother,
					CHILD_HURT.contains(group.title()) ? "Woman Protects Her Child" : "Woman Comforts Her Child", speaker, duration + 4L);
			mother.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(speaker, 0.7F, 1));
		}
	}

	/** Drops what was said more than two days ago: every villager and pair that ever lived would stay forever. */
	private static void forgetOld(long today) {
		SAID_AT.entrySet().removeIf(entry -> {
			if (ticker - entry.getValue() < FORGET_AFTER) return false;
			SAID.remove(entry.getKey());
			return true;
		});
		Pets.forgetOld(today);
		VillagerHome.forgetStill();
	}

	/** True the first time this villager asks for {@code key} in this period (a day, a meal). Not saved. */
	public static boolean once(Villager villager, String key, long period) {
		return once(key + ":" + villager.getUUID(), period);
	}

	static boolean once(String key, long period) {
		Long last = SAID.get(key);
		if (last != null && last == period) return false;
		SAID.put(key, period);
		SAID_AT.put(key, ticker);
		return true;
	}

	// ---- the player helps --------------------------------------------------------------

	/**
	 * The player takes part: a flower or a cake helps a courting pair along (a cake or a ring sets the wedding for
	 * today), a flower or a ring for a single villager finds them a match, a toy delights a child, a blanket is a
	 * present for a mother of a newborn.
	 */
	private static void onInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getTarget() instanceof TamableAnimal animal && animal.level() instanceof ServerLevel petLevel && animal.isAlive()
				&& Pets.give(petLevel, event.getEntity(), animal, event.getItemStack())) {
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
			return;
		}
		if (!(event.getTarget() instanceof Villager villager) || !(villager.level() instanceof ServerLevel level) || !villager.isAlive()) return;
		FamilyEvents.nameTag(level, event.getEntity(), villager, event.getItemStack());
		if (!give(level, event.getEntity(), villager, event.getItemStack())) return;
		event.setCancellationResult(InteractionResult.SUCCESS);
		event.setCanceled(true);
	}

	/** Hands {@code stack} to {@code villager} the family way; false when the villager has no use for it here. */
	public static boolean give(ServerLevel level, Player player, Villager villager, ItemStack stack) {
		// the errand the player took at the notice board comes first: it is what this villager waits for
		if (com.vnap.village.town.Errands.deliver(level, player, villager, stack)) return true;
		if (com.vnap.village.town.Travellers.give(level, player, villager, stack)) return true;
		if (com.vnap.village.town.TownStory.give(level, player, villager, stack)) return true;
		FamilyState family = state(villager);
		long day = day(level);
		boolean flower = stack.is(ItemTags.SMALL_FLOWERS);
		boolean ring = stack.is(LifeItems.WEDDING_RING);
		if (FamilyEvents.grantWish(villager, family, player, stack) || FamilyEvents.makeUp(level, villager, family, player, stack, day)
				|| comingOfAgeGift(villager, family, player, stack, day)) {
			// accepted: a wish granted, a quarrel ended, a present for a young one's coming of age
		} else if (stack.is(LifeItems.WOODEN_TOY) && villager.isBaby()) {
			// one toy a child: a second one would be a free mood boost for a stack of planks
			if ((family.gifts & FamilyState.TOY) != 0 || villager.getMainHandItem().is(LifeItems.WOODEN_TOY)) return false;
			family.gifts |= FamilyState.TOY;
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(LifeItems.WOODEN_TOY));
			Speech.queue(villager, "Kid Gets a Toy", player, 1L);
			Villager parent = family.mother != null && level.getEntity(family.mother) instanceof Villager mother ? mother : villager;
			com.vnap.village.post.Letters.to(level, player.getUUID(), VillagerHome.nameOf(parent), "thanks_toy", VillagerHome.nameOf(villager));
			VillageMoodApi.applyAt(level, villager.blockPosition(), 2.0F, 0.0F, "toy");
			if (player instanceof ServerPlayer server) VillageAdvancements.award(server, "family/kids_friend");
		} else if (stack.is(LifeItems.BABY_BLANKET) && female(villager) && !villager.isBaby()) {
			Villager baby = newborn(level, villager);
			if (baby == null || (state(baby).gifts & FamilyState.BLANKET) != 0) return false;
			state(baby).gifts |= FamilyState.BLANKET;
			Speech.queue(villager, "Woman Thanks for the Blanket", player, 1L);
			com.vnap.village.post.Letters.to(level, player.getUUID(), VillagerHome.nameOf(villager), "thanks_blanket", VillagerHome.nameOf(baby));
			VillageMoodApi.applyAt(level, villager.blockPosition(), 2.0F, 0.0F, "blanket");
		} else if ((flower || ring || stack.is(Items.CAKE)) && family.courting != null
				&& level.getEntity(family.courting) instanceof Villager partner) {
			FamilyState other = state(partner);
			if (flower) {
				if (family.flowerDay == day) return false;
				family.flowerDay = day;
				other.flowerDay = day;
				family.courtStartDay--;
				other.courtStartDay--;
			} else {
				family.courtStartDay = day - MoodConfig.courtshipDays();
				other.courtStartDay = family.courtStartDay;
				family.weddingDay = day;
				other.weddingDay = day;
			}
			thank(villager, player);
		} else if ((flower || ring) && single(villager, family, day)) {
			BlockPos bell = com.vnap.village.home.Towns.bellOf(villager, villager.blockPosition());
			Villager match = match(level, villager, bell, day);
			if (match == null) {
				// said, not swallowed: the flower stays in hand, and no trade window opens over the answer
				com.vnap.Mc.say(player, Component.translatable("vnap.family.no_match", VillagerHome.nameOf(villager)), true);
				return true;
			}
			startCourting(villager, match, day);
			if (ring) {
				family.weddingDay = day;
				state(match).weddingDay = day;
			}
			thank(villager, player);
			com.vnap.village.post.Letters.to(level, player.getUUID(), VillagerHome.nameOf(villager), "thanks_match", VillagerHome.nameOf(match));
			if (player instanceof ServerPlayer server) VillageAdvancements.award(server, "family/matchmaker");
		} else if (stack.has(net.minecraft.core.component.DataComponents.FOOD) && !villager.isBaby() && family.foodDay != day && (family.wish.isEmpty() || family.wishGranted)) {
			// food for a grown-up, once a day each: it goes to the household's table
			family.foodDay = day;
			villager.getInventory().addItem(stack.copyWithCount(1));
			com.vnap.mood.MoodHooks.onGiftFood(level, villager.blockPosition());
			thank(villager, player);
		} else {
			return false;
		}
		if (!player.getAbilities().instabuild) stack.shrink(1);
		level.sendParticles(ParticleTypes.HEART, villager.getX(), villager.getY() + 2.0, villager.getZ(), 4, 0.3, 0.2, 0.3, 0.02);
		return true;
	}

	/** A present for a young villager who came of age today: they remember the player kindly. */
	private static boolean comingOfAgeGift(Villager villager, FamilyState family, Player player, ItemStack stack, long day) {
		if (villager.isBaby() || family.grownDay != day || (family.mother == null && family.father == null)) return false;
		if (!(stack.is(ItemTags.SMALL_FLOWERS) || stack.is(Items.CAKE) || stack.is(Items.EMERALD) || stack.is(Items.COOKIE)
				|| stack.is(Items.BOOK)) || !once(villager, "coming_of_age:" + player.getUUID(), day)) return false;
		villager.getGossips().add(player.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MINOR_POSITIVE, 20);
		thank(villager, player);
		if (player instanceof ServerPlayer server) VillageAdvancements.award(server, "family/coming_of_age");
		return true;
	}

	private static void thank(Villager villager, Player player) {
		Speech.queue(villager, female(villager) ? "Woman Thanks for the Flower" : "Man Thanks for the Flower",
				player, 1L);
	}

	/** Her baby born within the last day, or null. */
	private static @Nullable Villager newborn(ServerLevel level, Villager mother) {
		long day = day(level);
		return level.getEntitiesOfClass(Villager.class, mother.getBoundingBox().inflate(TownRules.NEAR_BELL),
				kid -> kid.isBaby() && mother.getUUID().equals(state(kid).mother) && day - state(mother).lastBirthDay <= 1L)
				.stream().min(Comparator.comparingInt(Villager::getAge)).orElse(null);
	}

	private static Map<String, String> womanLines() {
		Map<String, String> lines = new HashMap<>();
		put(lines, "Woman Greets the Player", "Approach a Villager", "Stare at a Villager", "Nudge a Villager");
		put(lines, "Woman Starts Trading", "Start Trading with Neutral Reputation", "Start Trading with High Reputation",
				"Start Trading with Extremely High Reputation");
		put(lines, "Woman Trades Warily", "Start Trading with Low Reputation", "Start Trading with Extremely Low Reputation");
		put(lines, "Woman Completes a Trade", "Complete a Trade", "Close Trading After Buying");
		put(lines, "Woman Closes Without a Trade", "Close Trading Without Buying", "Close the Trading Window");
		put(lines, "Woman Gets a Gift", "Receive Food", "Receive Bread", "Receive Carrot", "Receive Potatoes", "Receive Beetroot",
				"Pick Up an Item Dropped by a Player", "Give a Villager a Cosmetic", "Give a Villager a Sign");
		put(lines, "Woman Is Hit by the Player", "Attacked by the Player", "Attacked with a Sword", "Attacked with an Axe",
				"Attacked with an Arrow", "Attacked with a Hoe", "Attacked with a Shovel", "Attacked with a Firework",
				"Attacked with a Splash Potion", "Attacked with a Gun", "Attacked Inside Their Home");
		put(lines, "Woman Praises the Hero", "Hero of the Village", "High Reputation", "Extremely High Reputation");
		put(lines, "Woman Scolds a Bad Player", "Low Reputation", "Extremely Low Reputation", "Extremely Low Reputation with a Sword");
		put(lines, "Woman Finds the Player in Her House", "Open a Chest in a Villager's House", "Stand on a Villager's Bed",
				"Wake a Sleeping Villager", "Close a Door in a Villager's Face");
		put(lines, "Woman Sees Her Home Damaged", "Break Blocks Inside a Villager's Home", "House damaged",
				"Set Fire Near a Villager's Home", "Village House on Fire", "Break a Bed", "Break a Door");
		put(lines, "Woman Sees the Player Die", "Player Dies", "Player Dies Again", "Player Dies in Hardcore");
		put(lines, "Woman Chats With the Player", "Wear Armor", "Wear Full Iron Armor", "Wear High-Level Armor", "Crouch-Walk",
				"Stand Completely Still", "Fly in Creative Mode", "Glide with Elytra", "Catch a Fish", "Mine Diamond Ore");
		put(lines, "Woman Worries About the Player", "Low Health", "Hunger", "Player on Fire", "Darkness", "Nausea");
		put(lines, "Woman Evening at Home", "Return Home", "Go to Bed");
		put(lines, "Woman Sees a Monster", "Villager Panics", "Zombie", "Skeleton", "Creeper", "Spider", "Witch", "Enderman",
				"Phantom", "Pillager", "Ravager", "Vindicator", "Evoker", "Vex", "Zoglin", "Warden", "Drowned", "Husk", "Stray",
				"Bogged", "Zombie Villager", "Spawn Hostile Mobs in the Village");
		put(lines, "Woman in the Weather", "Caught in the Rain", "Thunderstorm", "See Lightning", "Lightning Hits Nearby",
				"Stand on Snow", "Wander Somewhere Cold");
		put(lines, "Woman in a Happy Village", "Village Mood Rising", "Warm village", "Kind village", "Festoon Hung",
				"Festival Begins", "Pavilion Raised");
		put(lines, "Woman in a Grumpy Village", "Village Mood Falling", "Sour village", "Harsh village", "Vigil Lit",
				"Mourning Laid", "Pyre Lit");
		put(lines, "Woman at Work", "Start Work", "Farming", "Level Up", "Armorer at Work", "Butcher at Work", "Cartographer at Work",
				"Cleric at Work", "Farmer at Work", "Fisherman at Work", "Fletcher at Work", "Leatherworker at Work",
				"Librarian at Work", "Mason at Work", "Shepherd at Work", "Toolsmith at Work", "Weaponsmith at Work",
				"armorer chore", "butcher chore", "cartographer chore", "cleric chore", "farmer chore", "fisherman chore",
				"fletcher chore", "leatherworker chore", "librarian chore", "mason chore", "shepherd chore", "toolsmith chore",
				"weaponsmith chore");
		put(lines, "Woman Gets Hurt", "Villager Gets Hurt");
		put(lines, "Woman Calms Down", "Calm Down After Danger", "Free a Suffocating Villager");
		put(lines, "Woman Teaches the Kids", "Librarian Teaches the Kids");
		put(lines, "Woman Says Goodbye", "Neighbour Says Goodbye");
		put(lines, "Woman Settles the Hamlet", "Settlers Found a Hamlet");
		put(lines, "Elder Woman Tells a Story", "Elder Tells a Story");
		put(lines, "Woman Funeral Words", "Funeral Words");
		put(lines, "Woman Welcomes the Pet", "Man Welcomes the Pet");
		put(lines, "Woman Wakes Up", "Wake Up Naturally");
		put(lines, "Woman Wanders", "Villager Wandering", "Unemployed Villager Wandering", "Nitwit Wandering", "Villager Gathering",
				"Return Home Without a Bed");
		return Map.copyOf(lines);
	}

	private static void put(Map<String, String> lines, String woman, String... adultTitles) {
		for (String title : adultTitles) lines.put(title, woman);
	}
}
