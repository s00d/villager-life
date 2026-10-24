package com.vnap.dialogue;

import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.mood.MoodState;
import com.vnap.mood.VillageMoodApi;
import com.vnap.progress.VillageAdvancements;
import com.vnap.village.home.Neighbours;
import com.vnap.village.life.Pantry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The player in a family's life, and the yard around it: a groom's wedding wish (granted, the wedding gets fireworks),
 * a godparent who named a baby, the neighbour who slept in the village, spouses who quarrel and make up, and the
 * children's hide-and-seek.
 */
public final class FamilyEvents {
	static final List<String> WISHES = List.of("minecraft:cake", "minecraft:firework_rocket", "minecraft:poppy");
	private static final double QUARREL_CHANCE = 0.15;
	/** Even in good times a couple falls out now and then (over nothing much). */
	private static final double QUARREL_ANYWAY = 0.03;
	private static final int HIDE_TICKS = 400;
	private static final int GAME_TICKS = 2400;
	private static final Map<net.minecraft.core.GlobalPos, Game> GAMES = new HashMap<>();

	private FamilyEvents() {
	}

	static void clear() {
		GAMES.clear();
	}

	// ---- the wedding wish --------------------------------------------------------------

	/** A courting man asks a player nearby for one thing for the wedding (once a courtship). */
	static void askForWish(ServerLevel level, Villager groom, FamilyState family, long day) {
		if (family.courting == null || !family.wish.isEmpty()) return;
		Player player = level.getNearestPlayer(groom, 8.0);
		if (!(player instanceof ServerPlayer server) || server.isSpectator()) return;
		family.wish = WISHES.get(Math.floorMod(groom.getUUID().hashCode() + (int) day, WISHES.size()));
		family.wishGranted = false;
		Speech.queue(groom, "Man Asks for a Wedding Gift", player, 2L);
		com.vnap.Mc.say(server, Component.translatable("vnap.wedding.wish", VillagerHome.nameOf(groom),
				new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(family.wish))).getHoverName()), true);
	}

	/** The groom gets what he asked for. */
	static boolean grantWish(Villager groom, FamilyState family, Player player, ItemStack stack) {
		if (family.wish.isEmpty() || family.wishGranted || family.courting == null || !matches(family.wish, stack)) return false;
		family.wishGranted = true;
		Speech.queue(groom, "Man Thanks for the Wish", player, 1L);
		return true;
	}

	private static boolean matches(String wish, ItemStack stack) {
		if (wish.equals("minecraft:poppy")) return stack.is(ItemTags.SMALL_FLOWERS);
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(wish);
	}

	/** After the vows: a granted wish lights the sky over the bell. */
	public static void weddingFireworks(ServerLevel level, Villager groom, Villager bride, BlockPos bell) {
		boolean granted = Family.state(groom).wishGranted || Family.state(bride).wishGranted;
		Family.state(groom).wish = "";
		Family.state(bride).wish = "";
		Family.state(groom).wishGranted = false;
		Family.state(bride).wishGranted = false;
		if (!granted) return;
		int[] colours = {DyeColor.byId(Math.floorMod(groom.getUUID().hashCode(), 16)).getFireworkColor(),
				DyeColor.byId(Math.floorMod(bride.getUUID().hashCode(), 16)).getFireworkColor()};
		for (int i = 0; i < 4; i++) {
			ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
			rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + (i % 2), List.of(new FireworkExplosion(
					i % 2 == 0 ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.STAR,
					it.unimi.dsi.fastutil.ints.IntList.of(colours), it.unimi.dsi.fastutil.ints.IntList.of(), true, true))));
			level.addFreshEntity(new FireworkRocketEntity(level, bell.getX() + 0.5 + (i - 1.5) * 2, bell.getY() + 1.0, bell.getZ() + 0.5, rocket));
		}
		VillageMoodApi.applyAt(level, bell, 5.0F, 0.0F, "wedding_wish");
		VillageAdvancements.awardNear(level, bell, 32.0, "family/best_man");
	}

	/** Neighbours of the village hear about a wedding wherever they are in it. */
	static void inviteNeighbours(ServerLevel level, Villager bride, BlockPos bell) {
		Neighbours neighbours = Neighbours.get(level.getServer());
		UUID groom = Family.state(bride).courting;
		String groomName = groom != null && level.getEntity(groom) instanceof Villager him ? VillagerHome.nameOf(him) : "";
		BlockPos home = com.vnap.village.home.Towns.bellOf(bride, bell);
		com.vnap.village.post.Letters.toNeighbours(level, home, VillagerHome.nameOf(bride), "wedding", VillagerHome.nameOf(bride), groomName);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(bell).inflate(128.0))) {
			if (neighbours.is(level, bell, player.getUUID())) {
				com.vnap.Mc.say(player, Component.translatable("vnap.wedding.invite", VillagerHome.nameOf(bride)), true);
			}
		}
	}

	// ---- godparent ---------------------------------------------------------------------

	/** A name tag on a family baby: the player becomes its godparent (vanilla does the renaming right after). */
	public static void nameTag(ServerLevel level, Player player, Villager baby, ItemStack stack) {
		FamilyState family = Family.state(baby);
		if (!baby.isBaby() || family.mother == null || family.godparent != null || !stack.is(Items.NAME_TAG)
				|| !stack.has(DataComponents.CUSTOM_NAME)) return;
		family.godparent = player.getUUID();
		if (level.getEntity(family.mother) instanceof Villager mother && mother.isAlive()) {
			Speech.queue(mother, "Woman Thanks the Godparent", player, 20L);
		}
		if (player instanceof ServerPlayer server) VillageAdvancements.award(server, "family/godparent");
		UUID head = family.head;
		level.getServer().execute(() -> {
			if (head != null && level.getEntity(head) instanceof Villager found) Family.rewriteSign(level, found);
		});
	}

	/** A child greets its godparent once a day. */
	static void greetGodparent(ServerLevel level, Villager kid, FamilyState family, long day) {
		if (family.godparent == null || !(level.getPlayerByUUID(family.godparent) instanceof ServerPlayer godparent)
				|| !godparent.closerThan(kid, 8.0) || !Family.once(kid, "godparent", day)) return;
		Speech.queue(kid, "Kid Greets Godparent", godparent, 1L);
	}

	// ---- neighbours ----------------------------------------------------------------------

	/** A player woke in a bed within a village: a neighbour of that bell from now on. */
	public static void wokeUp(ServerPlayer player) {
		if (!(com.vnap.Mc.levelOf(player) instanceof ServerLevel level)) return;
		BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
		Villager villager = level.getNearestEntity(Villager.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat()
				.ignoreLineOfSight(), player, bed.getX(), bed.getY(), bed.getZ(), new AABB(bed).inflate(48.0));
		if (villager == null) return;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager, BlockPos.ZERO);
		if (bell.equals(BlockPos.ZERO) || !bell.closerThan(bed, com.vnap.village.town.TownRules.NEAR_BELL)) return;
		if (Neighbours.get(level.getServer()).add(level, bell, player.getUUID())) VillageAdvancements.award(player, "family/neighbour");
	}

	/** Afternoons: a child runs over to a neighbour player who is about. */
	static void visitNeighbour(ServerLevel level, Villager kid, float time, long day) {
		if (time < 0.25F || time >= 0.45F) return;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(kid, BlockPos.ZERO);
		if (bell.equals(BlockPos.ZERO)) return;
		Neighbours neighbours = Neighbours.get(level.getServer());
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, kid.getBoundingBox().inflate(32.0))) {
			if (!neighbours.is(level, bell, player.getUUID()) || !Family.once(kid, "visit", day)) continue;
			kid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, 0.6F, 2));
			Speech.queue(kid, "Kid Visits the Neighbour", player, 60L);
			return;
		}
	}

	// ---- quarrels ----------------------------------------------------------------------

	/** Once a day for a wife: a hungry house or a sour village makes the couple quarrel sometimes. */
	static void maybeQuarrel(ServerLevel level, Villager wife, FamilyState family, long day) {
		if (!Family.female(wife) || family.spouse == null || family.quarrelling(day)
				|| !(level.getEntity(family.spouse) instanceof Villager husband) || !husband.isAlive()) return;
		BlockPos anchor = family.home;
		DecorBlockEntity pantry = anchor == null ? null : Pantry.of(level, anchor);
		MoodState mood = anchor == null ? null : VillagerHome.moodAt(level, anchor);
		boolean hard = (pantry != null && Pantry.food(pantry) == 0) || (mood != null && mood.mood < 0.0F);
		if (wife.getRandom().nextDouble() >= (hard ? QUARREL_CHANCE : QUARREL_ANYWAY)) return;
		quarrel(wife, husband, day);
	}

	public static void quarrel(Villager wife, Villager husband, long day) {
		Family.state(wife).quarrelDay = day;
		Family.state(husband).quarrelDay = day;
		Speech.queue(wife, "Woman Scolds", husband, 2L);
		Speech.queue(husband, "Man Grumbles", wife, 6L);
	}

	/** A flower or a cake from the player ends a quarrel at once. */
	static boolean makeUp(ServerLevel level, Villager villager, FamilyState family, Player player, ItemStack stack, long day) {
		if (!family.quarrelling(day) || family.spouse == null || !(stack.is(ItemTags.SMALL_FLOWERS) || stack.is(Items.CAKE))
				|| !(level.getEntity(family.spouse) instanceof Villager spouse)) return false;
		family.quarrelDay = Long.MIN_VALUE / 2;
		Family.state(spouse).quarrelDay = Long.MIN_VALUE / 2;
		Villager wife = Family.female(villager) ? villager : spouse;
		Villager husband = wife.equals(villager) ? spouse : villager;
		Speech.queue(wife, "Woman Makes Up", husband, 1L);
		Speech.queue(husband, "Man Makes Up", wife, 5L);
		VillageMoodApi.applyAt(level, villager.blockPosition(), 1.0F, 0.0F, "make_up");
		if (player instanceof ServerPlayer server) VillageAdvancements.award(server, "family/peacemaker");
		return true;
	}

	// ---- hide-and-seek -------------------------------------------------------------------

	/** One game a village at a time: a seeker counts at its spot, the others hide behind houses, then it seeks. */
	public static final class Game {
		public final UUID seeker;
		public final List<UUID> hiders = new ArrayList<>();
		public final List<BlockPos> spots = new ArrayList<>();
		final long started;
		int found;

		Game(UUID seeker, long started) {
			this.seeker = seeker;
			this.started = started;
		}
	}

	/** Every family tick for a child in the afternoon: start a game, hide, or seek. */
	public static void hideAndSeek(ServerLevel level, Villager kid, float time, long day) {
		BlockPos bell = com.vnap.village.home.Towns.bellOf(kid, kid.blockPosition());
		Game game = GAMES.get(net.minecraft.core.GlobalPos.of(level.dimension(), bell));
		long now = level.getGameTime();
		if (game != null && now - game.started > GAME_TICKS) {
			GAMES.remove(net.minecraft.core.GlobalPos.of(level.dimension(), bell));
			game = null;
		}
		if (game == null) {
			if (time >= 0.25F && time < 0.45F) start(level, kid, bell, day);
			return;
		}
		// the hiders keep to their spots on their agendas until found
		if (!kid.getUUID().equals(game.seeker) || now - game.started < HIDE_TICKS) return;
		if (game.found >= game.hiders.size()) {
			GAMES.remove(net.minecraft.core.GlobalPos.of(level.dimension(), bell));
			return;
		}
		if (com.vnap.village.life.Agenda.has(kid, "seek")) return;
		if (!(level.getEntity(game.hiders.get(game.found)) instanceof Villager target) || !target.isAlive()) {
			game.found++;
			return;
		}
		Game seeking = game;
		com.vnap.village.life.Agenda.job(kid, "seek", game.spots.get(game.found), com.vnap.village.life.Agenda.ROUTINE, ItemStack.EMPTY, (l, seeker) -> {
			seeking.found++;
			Speech.queue(seeker, "Kid Found You", target, 1L);
			Speech.queue(target, "Kid Was Found", seeker, 4L);
		});
	}

	public static @Nullable Game start(ServerLevel level, Villager seeker, BlockPos bell, long day) {
		List<Villager> kids = level.getEntitiesOfClass(Villager.class, seeker.getBoundingBox().inflate(24.0),
				other -> !other.equals(seeker) && other.isBaby() && other.isAlive() && !other.isSleeping() && !Infants.infant(other));
		if (kids.size() < 2 || !Family.once("hide:" + bell.asLong(), day)) return null;
		Game game = new Game(seeker.getUUID(), level.getGameTime());
		for (Villager hider : kids.subList(0, Math.min(4, kids.size()))) {
			BlockPos spot = hidingSpot(level, seeker.blockPosition(), hider);
			if (spot == null) continue;
			game.hiders.add(hider.getUUID());
			game.spots.add(spot);
			int index = game.hiders.size() - 1;
			net.minecraft.core.GlobalPos key = net.minecraft.core.GlobalPos.of(level.dimension(), bell);
			com.vnap.village.life.Agenda.add(hider, new com.vnap.village.life.Agenda.Item("hide", spot, com.vnap.village.life.Agenda.ROUTINE, 1, 0.6F,
					l -> GAMES.get(key) == game && game.found <= index, null, null));
		}
		if (game.hiders.isEmpty()) return null;
		GAMES.put(net.minecraft.core.GlobalPos.of(level.dimension(), bell), game);
		Speech.queue(seeker, "Kid Counts", null, 1L);
		return game;
	}

	/** Open ground 8 to 16 blocks from the seeker with a wall right beside it, so the hider is round a corner. */
	private static @Nullable BlockPos hidingSpot(ServerLevel level, BlockPos from, Villager hider) {
		// every cell of the rings in a random order: a few random throws missed a spot by a wall where walls are few
		List<BlockPos> columns = new java.util.ArrayList<>(com.vnap.village.town.Rings.around(from, 8, 16, 1, r -> (int) Math.ceil(Math.PI * 2.0 * r)));
		net.minecraft.Util.shuffle(columns, hider.getRandom());
		for (BlockPos column : columns) {
			int x = column.getX();
			int z = column.getZ();
			if (!level.isLoaded(column)) continue;
			// ground near the seeker's height, not the top of a wall or a roof
			BlockPos ground = com.vnap.village.town.Ground.near(level, x, z, from.getY(), 0, 4);
			if (ground == null) continue;
			BlockPos cell = ground.above();
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos wall = cell.relative(side);
				if (level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite())
						&& level.getBlockState(wall.above()).isFaceSturdy(level, wall.above(), side.getOpposite())) {
					return cell;
				}
			}
		}
		return null;
	}
}
