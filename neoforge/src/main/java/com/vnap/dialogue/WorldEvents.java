package com.vnap.dialogue;

import com.vnap.mood.MoodHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** What a player does to the world in sight of the village: blocks broken, placed and used, items used, explosions. */
public final class WorldEvents {
	private static final double WATCHERS = 16.0;
	private static final Set<String> WORK_BLOCKS = Set.of("composter", "barrel", "blast_furnace", "smoker", "cartography_table",
			"brewing_stand", "fletching_table", "cauldron", "lectern", "stonecutter", "loom", "smithing_table", "grindstone");
	/** "id-patterns -> title", the first that fits: name exact, {@code *end}, {@code start*}, {@code *part*}, @work. */
	private static final List<Rule> BREAKING = rules("""
			wheat carrots potatoes beetroots torchflower_crop pitcher_crop -> Harvest Crops
			*flower* *candle* *coral* *banner* *decorated_pot* -> Break a Decorative Block
			*_door -> Break a Door
			*_bed -> Break a Bed
			bell -> Break a Bell
			@work -> Break a Workstation
			*log* *wood* *stem* *hyphae* -> Break Wood
			*stone* *deepslate* *cobblestone* -> Break Stone
			""");
	private static final List<Rule> PLACING = rules("""
			daylight_detector -> Place a Daylight Detector
			detector_rail -> Place a Detector Rail
			lightning_rod -> Place a Lightning Rod
			melon -> Place a Melon
			observer -> Place an Observer
			*pressure_plate -> Place a Pressure Plate
			pumpkin -> Place a Pumpkin
			redstone_lamp -> Place a Redstone Lamp
			repeater -> Place a Redstone Repeater
			redstone_torch redstone_wall_torch -> Place a Redstone Torch
			*sculk_sensor* -> Place a Sculk Sensor
			tripwire_hook -> Place a Tripwire Hook
			jack_o_lantern -> Place a Jack o'Lantern
			end_stone -> Place End Stone
			*purpur* -> Place Purpur
			*copper* -> Place a Copper Block
			*brick* -> Place Bricks
			powder_snow -> Place Powder Snow
			light -> Place a Light Block
			barrier *command_block* structure_block jigsaw -> Place a Creative-Only Block
			*sand *gravel anvil -> Place a Gravity-Affected Block
			diamond_block -> Place a Diamond Block
			emerald_block -> Place an Emerald Block
			iron_block -> Place an Iron Block
			gold_block -> Place a Gold Block
			*button -> Place a Button
			lever -> Place a Lever
			netherite_block -> Place a Valuable Block
			*redstone* *rail -> Place a Redstone Component
			*_bed -> Place a Bed
			chest -> Place a Chest
			trapped_chest -> Place a Trapped Chest
			crafting_table -> Place a Crafting Table
			furnace -> Place a Furnace
			bookshelf -> Place a Bookshelf
			jukebox -> Place a Jukebox
			armor_stand -> Place an Armor Stand
			beacon -> Place a Beacon
			@work -> Place a Workstation
			*_log *_wood *_planks -> Place Wood
			*dirt* -> Place Dirt
			*leaves* *sapling* *flower* -> Place Leaves or Plants
			*wool* -> Place Wool
			*glass* -> Place Glass
			*concrete_powder* -> Place Concrete Powder
			*concrete* -> Place Concrete
			*glazed_terracotta* -> Place Glazed Terracotta
			*terracotta* -> Place Terracotta
			lapis_block -> Place a Lapis Block
			*ice* -> Place Ice
			*snow* -> Place Snow
			""");
	private static final List<Rule> USING = rules("""
			*_button -> Press a Button
			bell -> Hear a Bell Ring
			lever -> Flip a Lever
			*_door -> Use a Door
			crafter -> Use a Crafter
			dispenser -> Use a Dispenser
			dropper -> Use a Dropper
			jukebox -> Use a Jukebox
			loom -> Use a Loom
			*shulker_box* -> Use a Shulker Box
			stonecutter -> Use a Stonecutter
			beacon -> Use a Beacon
			*campfire* -> Use a Campfire
			cartography_table -> Use a Cartography Table
			cauldron *_cauldron -> Use a Cauldron
			chiseled_bookshelf -> Use a Chiseled Bookshelf
			composter -> Use a Composter
			ender_chest -> Use an Ender Chest
			*shelf* -> Use Shelves
			*chest* -> Open a Chest
			crafting_table -> Use a Crafting Table
			furnace blast_furnace smoker -> Use a Furnace
			anvil *_anvil -> Use an Anvil
			enchanting_table -> Use an Enchanting Table
			brewing_stand -> Use a Brewing Stand
			grindstone -> Use a Grindstone
			smithing_table -> Use a Smithing Table
			""");
	private static final List<Rule> ITEMS = rules("""
			firework_rocket -> Set Off a Firework
			ender_pearl -> Teleport with an Ender Pearl
			ender_eye -> Throw an Ender Eye
			snowball -> Snowball
			milk_bucket -> Drink Milk
			honey_bottle -> Drink Honey
			goat_horn -> Blow a Goat Horn
			*apple* *bread* *carrot* *potato* *beef* *porkchop* *chicken* *mutton* *rabbit* *stew* *berries* *melon* -> Eat Food
			""");
	private static final List<Broken> BROKEN = new ArrayList<>();
	private static final List<Bell> BELLS = new ArrayList<>();
	private static final List<BellHeard> HEARD = new ArrayList<>();

	private record Rule(List<Predicate<String>> fits, String title) {
	}

	private record Broken(ServerLevel level, UUID player, BlockPos pos, BlockState state, long at) {
	}

	private record Bell(ServerLevel level, Vec3 at, long rings) {
	}

	private record BellHeard(ServerLevel level, UUID villager, Vec3 bell, long at, long until) {
	}

	private WorldEvents() {
	}

	private static List<Rule> rules(String text) {
		List<Rule> out = new ArrayList<>();
		for (String line : text.split("\n", -1)) {
			if (line.isBlank()) continue;
			String[] sides = line.split(" -> ", 2);
			List<Predicate<String>> fits = new ArrayList<>();
			for (String pattern : sides[0].strip().split(" ", -1)) fits.add(fit(pattern));
			out.add(new Rule(List.copyOf(fits), sides[1].strip()));
		}
		return List.copyOf(out);
	}

	private static Predicate<String> fit(String pattern) {
		if (pattern.equals("@work")) return WORK_BLOCKS::contains;
		boolean head = pattern.startsWith("*");
		boolean tail = pattern.endsWith("*") && pattern.length() > 1;
		String core = pattern.substring(head ? 1 : 0, pattern.length() - (tail ? 1 : 0));
		if (head && tail) return path -> path.contains(core);
		if (head) return path -> path.endsWith(core);
		if (tail) return path -> path.startsWith(core);
		return core::equals;
	}

	private static @Nullable String first(List<Rule> rules, String path) {
		for (Rule rule : rules) {
			for (Predicate<String> fit : rule.fits()) if (fit.test(path)) return rule.title();
		}
		return null;
	}

	// ---- a villager sees it -------------------------------------------------------------------------------

	/**
	 * The plain villager nearest to where it happened, who sees the player, says the title. Mayors and children do
	 * not comment on the player's doings.
	 */
	static boolean observed(ServerLevel level, Player player, Vec3 where, String title, long every) {
		Villager speaker = Near.villager(level, where, WATCHERS,
				v -> Near.awakeAdultSees(v, player) && !Voices.mayorMan(v));
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byTitle(title, "villager");
		return speaker != null && group != null
				&& Speech.speak(speaker, group, "observed:" + speaker.getUUID() + ":" + title, every, player, true);
	}

	/** For other hooks (ExpandEvents): a title a villager may remark on, and what it does to the mood. */
	public static boolean observe(Player player, Vec3 where, @Nullable String title, boolean seldom) {
		if (!(com.vnap.Mc.levelOf(player) instanceof ServerLevel level) || title == null || title.isEmpty()) return false;
		BlockPos pos = BlockPos.containing(where);
		boolean atHome = title.contains("Break Blocks Inside") || title.contains("Home");
		if (title.contains("Fire") || title.contains("Burning")) MoodHooks.onGriefObserved(level, pos, false);
		else if (atHome) {
			if (title.contains("Break")) MoodHooks.onGriefObserved(level, pos, true);
		} else if (title.contains("Steal")) MoodHooks.onSteal(level, pos);
		return observed(level, player, where, title, seldom ? Speech.SELDOM : Speech.SOON);
	}

	// ---- blocks -----------------------------------------------------------------------------------------

	/** The break event comes before the block goes: looked at a tick later. */
	static void broke(ServerLevel level, Player player, BlockPos pos, BlockState state) {
		BROKEN.add(new Broken(level, player.getUUID(), pos.immutable(), state, Speech.now() + 1L));
	}

	static void tickBroken() {
		BROKEN.removeIf(broken -> {
			if (broken.at() > Speech.now()) return false;
			ServerLevel level = broken.level();
			Player player = level.getServer().getPlayerList().getPlayer(broken.player());
			// still there: something else cancelled the break
			if (player == null || level.getBlockState(broken.pos()).is(broken.state().getBlock())) return true;
			if (Conditions.freedFrom(level, broken.pos())) return true;
			observed(level, player, Vec3.atCenterOf(broken.pos()), breakTitle(level, player, broken), Speech.SOON);
			return true;
		});
	}

	private static String breakTitle(ServerLevel level, Player player, Broken broken) {
		String path = Near.path(broken.state());
		String title = player instanceof ServerPlayer watched && PlayerWatch.of(watched).breakingAway() ? "Break Multiple Blocks" : null;
		if (title == null) title = first(BREAKING, path);
		if (title == null) title = "Break a Block";
		String modded = ModFlavorMatchers.breakTitle(broken.state());
		if (modded != null && (title.equals("Break a Block") || title.equals("Break Stone") || title.equals("Break Wood"))) title = modded;
		Vec3 at = Vec3.atCenterOf(broken.pos());
		if (title.equals("Harvest Crops") && Near.villagers(level, at, WATCHERS).stream()
				.anyMatch(v -> VillagerMoments.profession(v).equals("farmer"))) {
			title = player.getRandom().nextFloat() < 0.4F ? "Steal from a Farmer's Crops" : "Harvest Crops Near a Farmer";
		}
		boolean specific = path.endsWith("_bed") || path.endsWith("_door") || path.equals("bell") || title.startsWith("Break a Bed")
				|| title.startsWith("Break a Door") || title.equals("Break a Bell") || title.startsWith("Harvest") || title.startsWith("Steal");
		if (!specific && Near.homely(level, broken.pos(), WATCHERS, 10, 6)) title = "Break Blocks Inside a Villager's Home";
		if (path.contains("diamond_ore")) return "Mine Diamond Ore";
		if (path.contains("ancient_debris")) return "Mine Ancient Debris";
		if (path.contains("emerald_ore")) return "Mine Emerald Ore";
		return title;
	}

	static void usedBlock(Player player, ServerLevel level, ItemStack held, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		BlockState clicked = level.getBlockState(pos);
		String block = Near.path(clicked);
		if (block.equals("bell")) {
			rang(level, hit.getLocation());
			return;
		}
		String title = heldOnBlock(Near.path(held), block);
		if (title == null && held.getItem() instanceof BlockItem item) title = placeTitle(item.getBlock(), level, pos);
		else if (title == null) title = useTitle(clicked);
		title = grief(player, level, held, clicked, pos, title);
		if (block.endsWith("_door") && clicked.hasProperty(BlockStateProperties.OPEN) && clicked.getValue(BlockStateProperties.OPEN)
				&& !Near.villagers(level, hit.getLocation(), 3.0).isEmpty()) title = "Close a Door in a Villager's Face";
		String item = Near.path(held);
		if ((item.equals("pumpkin") || item.equals("carved_pumpkin")) && Near.block(level, pos, "iron_block", 3)) {
			title = "Build an Iron Golem Frame";
		}
		boolean chest = block.equals("chest") || block.equals("trapped_chest");
		if (chest && homeChest(level, player, pos)) return;
		if (title != null) observed(level, player, hit.getLocation(), title, Speech.SOON);
	}

	private static @Nullable String heldOnBlock(String item, String block) {
		boolean lighter = item.equals("flint_and_steel") || item.equals("fire_charge");
		if (item.equals("redstone")) return "Place Redstone Dust";
		if (block.equals("tnt")) return lighter ? "Light TNT" : "See TNT";
		if (lighter && block.contains("campfire")) return "Light a Campfire";
		if (lighter && block.contains("candle")) return "Light a Candle";
		boolean meat = item.contains("beef") || item.contains("porkchop") || item.contains("chicken") || item.contains("mutton")
				|| item.contains("rabbit") || item.equals("potato");
		if (block.contains("campfire") && meat) return "Cook Food on a Campfire";
		if ((item.equals("water_bucket") || item.endsWith("_shovel")) && block.contains("campfire")) return "Extinguish a Campfire";
		if (item.equals("water_bucket") && block.contains("candle")) return "Extinguish a Candle";
		return item.equals("shears") && block.equals("pumpkin") ? "Carve a Pumpkin" : null;
	}

	private static String placeTitle(Block block, ServerLevel level, BlockPos pos) {
		String modded = ModFlavorMatchers.placeTitle(block);
		if (modded != null) return modded;
		if (level.dimension().equals(Level.END)) return "Place a Block from the End";
		if (level.dimension().equals(Level.NETHER)) return "Place a Block from the Nether";
		if (level.getBiome(pos).unwrapKey().map(key -> com.vnap.Mc.id(key).getPath().contains("ocean")).orElse(false)) {
			return "Place a Block from the Ocean";
		}
		String title = first(PLACING, Near.path(block.defaultBlockState()));
		return title == null ? "Place a Block" : title;
	}

	private static @Nullable String useTitle(BlockState state) {
		String modded = ModFlavorMatchers.useBlockTitle(state);
		if (modded != null) return modded;
		String path = Near.path(state);
		if (path.endsWith("_fence_gate")) {
			boolean open = state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN);
			// the first rules, buttons to doors, come before gates; none of them ends in _fence_gate
			return open ? "Close a Fence Gate" : "Open a Fence Gate";
		}
		return first(USING, path);
	}

	/** The meaner reading of a block use near homes (fire, lava, a door walled up), and a modded block's own title. */
	private static @Nullable String grief(Player player, ServerLevel level, ItemStack held, BlockState clicked, BlockPos pos,
			@Nullable String title) {
		String item = Near.path(held);
		String block = Near.path(clicked);
		boolean home = Near.homely(level, pos, WATCHERS, 12, 6);
		if (ModFlavorMatchers.isIgniter(held)) {
			if (block.contains("bed") || Near.block(level, pos, "bed", 2)) return "Try to Burn a Villager's Bed";
			if (home && pos.getY() >= player.getBlockY() + 2) return "Light a Roof on Fire";
			if (home && (title == null || title.equals("Light a Campfire") || title.equals("Light a Candle"))) {
				return "Set Fire Near a Villager's Home";
			}
		}
		if (item.equals("lava_bucket") && home) return "Place Lava Near a Villager's Home";
		if (item.equals("water_bucket") && Near.villagers(level, Vec3.atCenterOf(pos), 2.5).stream()
				.anyMatch(v -> v.blockPosition().closerThan(pos, 2))) return "Dump Water on a Villager";
		if (item.equals("bucket") && (block.equals("water") || block.equals("lava") || block.contains("cauldron"))) return "Fill a Bucket";
		boolean light = switch (item) {
			case "torch", "soul_torch", "lantern", "soul_lantern", "campfire", "soul_campfire" -> true;
			default -> false;
		};
		boolean generic = title == null || title.equals("Place a Block");
		if (light && held.getItem() instanceof BlockItem && generic) return "Place a Torch";
		if (item.equals("bone_meal")) return "Use Bone Meal";
		if (held.getItem() instanceof BlockItem && block.endsWith("_door") && home) return "Seal a Villager's Door Shut";
		if (held.getItem() instanceof BlockItem placed) {
			String modded = ModFlavorMatchers.placeTitle(placed.getBlock());
			if (modded != null && generic) return modded;
		}
		if (ModFlavorMatchers.isHostileSpawnEgg(held) && home) return "Spawn Hostile Mobs in the Village";
		String modded = ModFlavorMatchers.useBlockTitle(clicked);
		if (modded != null && title == null) return modded;
		return "Light TNT".equals(title) && home ? "Set Off TNT Near Homes" : title;
	}

	/** A player opens a chest in a villager's home: the owner (or one who lives near) speaks up. */
	private static boolean homeChest(ServerLevel level, Player player, BlockPos chest) {
		boolean bedNear = Near.block(level, chest, "bed", 6);
		Villager owner = Near.closest(Near.villagers(level, Vec3.atCenterOf(chest), WATCHERS).stream()
				.filter(v -> Near.awakeAdultSees(v, player) && !Voices.mayorMan(v) && (bedNear || Near.homeWithin(v, chest, 12))).toList(),
				player.position());
		return owner != null && Speech.say(owner, "qfhrlh", "home_chest:" + owner.getUUID(), Speech.SOON, player);
	}

	static void usedItem(ServerLevel level, Player player, ItemStack stack) {
		String title = ModFlavorMatchers.useItemTitle(stack);
		if (title == null) title = first(ITEMS, Near.path(stack));
		if (title != null) observed(level, player, player.position(), title, Speech.SOON);
	}

	/** A player used something on an animal (or any non-villager): leads, shears, a bucket, food. */
	static void handledAnimal(ServerLevel level, Player player, Entity entity) {
		String item = Near.path(player.getMainHandItem());
		if (item.equals("lead") && !(entity instanceof AbstractVillager)) observed(level, player, entity.position(), "Use a Lead", Speech.SOON);
		if (entity instanceof Sheep && item.equals("shears")) observed(level, player, entity.position(), "Shear a Sheep", Speech.SOON);
		boolean feed = item.contains("wheat") || item.contains("carrot") || item.contains("potato") || item.contains("seeds")
				|| item.contains("beetroot") || item.contains("berries");
		if (item.equals("bucket") && Near.path(entity).equals("cow")) observed(level, player, entity.position(), "Milk a Cow", Speech.SOON);
		else if (entity instanceof Animal && feed) observed(level, player, entity.position(), "Feed an Animal", Speech.SOON);
	}

	static void exploded(ServerLevel level, Vec3 centre) {
		ServerPlayer near = Near.player(level, centre, 48.0);
		if (near == null) return;
		BlockPos pos = BlockPos.containing(centre);
		if (!Near.homely(level, pos, 24.0, 16, 16)) return;
		observed(level, near, centre, "Explode Near the Village", Speech.SOON);
		if (ModFlavorMatchers.isCbcRelated(near.getMainHandItem()) || ModFlavorMatchers.isCbcRelated(near.getOffhandItem())) {
			observed(level, near, centre, "See a Shell Explosion", Speech.SOON);
		}
	}

	static void firework(ServerLevel level, Entity rocket) {
		List<Villager> watching = Near.villagers(level, rocket.position(), WATCHERS).stream()
				.filter(v -> !v.isSleeping() && v.hasLineOfSight(rocket)).toList();
		Villager adult = Near.closest(watching.stream().filter(v -> !v.isBaby()).toList(), rocket.position());
		if (adult != null) Speech.sayAsAdult(adult, "dfdkli", "firework_spawn:" + adult.getUUID(), Speech.SOON, rocket);
		Villager kid = Near.closest(watching.stream().filter(Villager::isBaby).toList(), rocket.position());
		if (kid != null) Speech.say(kid, "zeykfp", "firework_seen:" + kid.getUUID(), Speech.SOON, rocket);
	}

	static void lightning(ServerLevel level, Entity bolt) {
		Villager witness = Near.villager(level, bolt.position(), 128.0, v -> Near.awakeAdultSees(v, bolt));
		if (witness != null) Speech.sayAsAdult(witness, "ikrwzy", "lightning:" + witness.getUUID(), Speech.SOON, bolt);
	}

	// ---- the bell -------------------------------------------------------------------------------------------

	private static void rang(ServerLevel level, Vec3 at) {
		boolean already = BELLS.stream().anyMatch(bell -> bell.level().equals(level) && bell.at().distanceToSqr(at) < 0.25
				&& bell.rings() > Speech.now());
		if (!already) BELLS.add(new Bell(level, at, Speech.now() + 15L));
	}

	/** Every plain villager within 50 who hears the bell turns to it and says so, each at its own moment. */
	static void tickBells() {
		BELLS.removeIf(bell -> {
			if (bell.rings() > Speech.now()) return false;
			for (Villager villager : Near.villagers(bell.level(), bell.at(), 50.0)) {
				if (villager.isSleeping() || Voices.mayorMan(villager)) continue;
				long at = Speech.now() + villager.getRandom().nextInt(5);
				HEARD.add(new BellHeard(bell.level(), villager.getUUID(), bell.at(), at, at + 10L));
			}
			return true;
		});
		HEARD.removeIf(heard -> {
			if (heard.at() > Speech.now()) return false;
			if (!(heard.level().getEntity(heard.villager()) instanceof Villager villager) || !villager.isAlive() || villager.isSleeping()
					|| Voices.mayorMan(villager)) return true;
			boolean late = Speech.now() >= heard.until();
			if (Speech.busy(villager)) return late;
			return Speech.say(villager, villager.isBaby() ? "nxalcz" : "kljgyu", "bell:" + heard.at() + ":" + villager.getUUID(), 1L,
					heard.bell()) || late;
		});
	}

	static void clear() {
		BROKEN.clear();
		BELLS.clear();
		HEARD.clear();
	}
}
