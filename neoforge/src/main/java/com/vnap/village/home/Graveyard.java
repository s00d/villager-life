package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.VillagerHome;
import com.vnap.progress.VillageAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The village remembers its dead: a gravestone with a name sign on a plot of the town's churchyard out at its edge
 * (Cemetery), a funeral the next morning (whoever is around walks to the grave and says goodbye) and a widow who visits
 * now and then. Graves do not pile up for ever: after TownRules.GRAVE_DAYS one is taken away by hand (the stone and
 * its sign), and a full churchyard gives the oldest plot (GRAVE_REUSE_DAYS old at least) to the newly dead; with no
 * plot to be had, nobody gets a stone (the village has no churchyard yet, or a young and full one).
 */
public final class Graveyard extends SavedData {
	private static final String NAME = "villager_life_graves";
	private static final int CAP = 2048;
	private final List<Grave> graves = new ArrayList<>();
	/** The last day old graves were looked at (in memory: a restart looks again). */
	private long agedDay = Long.MIN_VALUE;

	/** One grave; {@code funeral} is false until the village held it. */
	public static final class Grave {
		public final UUID who;
		public final BlockPos pos;
		public final BlockPos bell;
		public final String name;
		public long day;
		public boolean funeral;

		Grave(UUID who, BlockPos pos, BlockPos bell, String name, long day, boolean funeral) {
			this.who = who;
			this.pos = pos;
			this.bell = bell;
			this.name = name;
			this.day = day;
			this.funeral = funeral;
		}
	}

	public static Graveyard get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Graveyard::new, Graveyard::load, Graveyard::save);
	}

	public List<Grave> graves() {
		return graves;
	}

	public @Nullable Grave of(UUID who) {
		for (Grave grave : graves) if (grave.who.equals(who)) return grave;
		return null;
	}

	/** A gravestone and a name sign for {@code villager} by its bell; null when it knew no bell or no spot was free. */
	public static @Nullable Grave bury(ServerLevel level, Villager villager) {
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager, BlockPos.ZERO);
		if (bell.equals(BlockPos.ZERO) || !level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return null;
		long born = Family.state(villager).bornDay;
		String years = born == Long.MIN_VALUE ? "" : born + " - " + Family.day(level);
		return bury(level, villager.getUUID(), bell, VillagerHome.nameOf(villager), years);
	}

	public static @Nullable Grave bury(ServerLevel level, UUID who, BlockPos bell, String name, String years) {
		Graveyard yard = get(level.getServer());
		if (yard.of(who) != null) return null;
		com.vnap.village.town.Roads roads = com.vnap.village.town.Roads.get(level.getServer());
		com.vnap.village.town.Roads.Network network = roads.near(level.dimension(), bell);
		if (network == null || !com.vnap.village.town.Cemetery.plan(level, roads, network)) return null;
		BlockPos spot = plot(level, yard, network);
		DecorBlock stone = gravestone();
		if (spot == null || stone == null) return null;
		Direction facing = network.cemeteryFace;
		// the village pays for the stone (the treasury) when it has a stall; the mayor sets it down, or anyone (Placements)
		com.vnap.village.market.Market.order(level, null, bell, stone.asItem());
		Mayors.Office office = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		UUID digger = office == null ? null : office.mayor;
		Placements.add(level, spot, stone.defaultBlockState().setValue(DecorBlock.FACING, facing), digger);
		BlockPos signPos = spot.relative(facing);
		com.vnap.village.market.Market.order(level, null, bell, net.minecraft.world.item.Items.SPRUCE_SIGN);
		Placements.add(level, signPos, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, facing.get2DDataValue() * 4),
				digger, "", name, years);
		Grave grave = new Grave(who, spot.immutable(), bell.immutable(), name, Family.day(level), false);
		Mayors.Office town = Mayors.get(level.getServer()).officeNear(level.dimension(), bell);
		com.vnap.village.post.Letters.toNeighbours(level, bell, town != null && !town.name.isEmpty() ? town.name : "—", "funeral", name);
		yard.graves.add(grave);
		if (yard.graves.size() > CAP) yard.graves.remove(0);
		yard.setDirty();
		return grave;
	}

	private static @Nullable DecorBlock gravestone() {
		for (DecorBlock block : LifeBlocks.HOME_DECOR) if (block.name().equals("gravestone")) return block;
		return null;
	}

	/** A free plot of the churchyard, else the oldest grave old enough to give way (taken up now), else null. */
	private static @Nullable BlockPos plot(ServerLevel level, Graveyard yard, com.vnap.village.town.Roads.Network network) {
		BlockPos free = com.vnap.village.town.Cemetery.freePlot(level, network);
		if (free != null) return free;
		List<BlockPos> plots = com.vnap.village.town.Cemetery.plots(level, network);
		long day = Family.day(level);
		Grave oldest = yard.graves.stream().filter(grave -> plots.contains(grave.pos) && grave.funeral && day - grave.day >= com.vnap.village.town.TownRules.GRAVE_REUSE_DAYS)
				.min(Comparator.comparingLong(grave -> grave.day)).orElse(null);
		if (oldest == null || !level.isLoaded(oldest.pos)) return null;
		takeUp(level, yard, oldest);
		return oldest.pos;
	}

	/** The grave's stone and its name sign gone (nothing dropped) and the grave forgotten. */
	static void takeUp(ServerLevel level, Graveyard yard, Grave grave) {
		if (level.getBlockState(grave.pos).getBlock() instanceof DecorBlock) level.setBlock(grave.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos at = grave.pos.relative(side);
			if (level.getBlockEntity(at) instanceof SignBlockEntity sign && com.vnap.Mc.signLine(sign, 1).equals(grave.name)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		yard.graves.remove(grave);
		yard.setDirty();
	}

	/** The day's look at old graves now (debug, tests). */
	public static void ageNow(ServerLevel level) {
		age(level, get(level.getServer()), Family.day(level));
	}

	/** Once a day: graves older than GRAVE_DAYS are taken away by someone of the village (at once with nobody about). */
	private static void age(ServerLevel level, Graveyard yard, long day) {
		for (Grave grave : List.copyOf(yard.graves)) {
			if (!grave.funeral || day - grave.day < com.vnap.village.town.TownRules.GRAVE_DAYS || !level.isPositionEntityTicking(grave.pos)) continue;
			Villager hand = level.getEntitiesOfClass(Villager.class, new AABB(grave.pos).inflate(com.vnap.village.town.TownRules.NEAR_BELL),
					v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !v.isNoAi() && !com.vnap.village.life.Life.official(v)).stream()
					.min(Comparator.comparingDouble(v -> v.distanceToSqr(grave.pos.getX(), grave.pos.getY(), grave.pos.getZ()))).orElse(null);
			if (hand == null) {
				takeUp(level, yard, grave);
				continue;
			}
			com.vnap.village.life.Agenda.job(hand, "grave_" + grave.pos.asLong(), grave.pos, com.vnap.village.life.Agenda.ROUTINE,
					net.minecraft.world.item.ItemStack.EMPTY, (l, v) -> takeUp(l, yard, grave));
		}
	}

	/** Every 100 ticks in a ticking village: the morning after a death the village holds the funeral. */
	public static void tick(ServerLevel level) {
		Graveyard yard = get(level.getServer());
		float time = Family.dayFraction(level);
		long day = Family.day(level);
		if (yard.agedDay != day) {
			yard.agedDay = day;
			age(level, yard, day);
		}
		for (Grave grave : yard.graves) {
			if (grave.funeral || !level.isPositionEntityTicking(grave.pos)) continue;
			if (day > grave.day + 2) {
				grave.funeral = true;
				yard.setDirty();
				continue;
			}
			if (day == grave.day || time < 0.02F || time >= 0.3F) continue;
			grave.funeral = true;
			yard.setDirty();
			funeral(level, grave);
		}
	}

	/** Mourners walk to the grave, the eldest says a few words, the family grieves; the village holds together. */
	public static void funeral(ServerLevel level, Grave grave) {
		List<Villager> mourners = level.getEntitiesOfClass(Villager.class, new AABB(grave.pos).inflate(32.0),
				villager -> villager.isAlive() && !villager.isSleeping() && !villager.isBaby());
		if (mourners.isEmpty()) return;
		float until = Family.dayFraction(level) + 0.04F;
		for (Villager mourner : mourners) {
			com.vnap.village.life.Agenda.add(mourner, new com.vnap.village.life.Agenda.Item("funeral", grave.pos, com.vnap.village.life.Agenda.EVENT, 3, 0.5F,
					com.vnap.village.life.Agenda.until(until), null, null));
		}
		// the mayor speaks over the grave, else the eldest
		Villager eldest = mourners.stream().filter(Mayors::isMayor).findFirst()
				.orElseGet(() -> mourners.stream().min(Comparator.comparingLong(villager -> Family.state(villager).bornDay)).orElse(mourners.get(0)));
		Speech.queue(eldest, Mayors.isMayor(eldest) ? "Mayor Funeral Words" : "Funeral Words", null, 200L);
		for (Villager mourner : mourners) {
			if (mourner.equals(eldest)) continue;
			boolean kin = grave.who.equals(Family.state(mourner).mother) || grave.who.equals(Family.state(mourner).father)
					|| grave.name.equals(Family.state(mourner).spouseName);
			if (kin) {
				Speech.queue(mourner, Family.female(mourner) ? "Woman Mourns" : "Man Mourns", null, 320L);
				break;
			}
		}
		VillageAdvancements.awardNear(level, grave.pos, 32.0, "family/farewell");
		// the chapel tolls for the dead
		com.vnap.village.town.TownLife.ringChapel(level, grave.bell, com.vnap.sound.GolemSounds.CHAPEL_TOLL);
	}

	private static Graveyard load(CompoundTag tag, HolderLookup.Provider registries) {
		Graveyard yard = new Graveyard();
		for (Tag entry : Nbt.getList(tag, "Graves", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			if (!Nbt.hasUUID(one, "Who")) continue;
			yard.graves.add(new Grave(Nbt.getUUID(one, "Who"), BlockPos.of(Nbt.getLong(one, "Pos")), BlockPos.of(Nbt.getLong(one, "Bell")),
					Nbt.getString(one, "Name"), Nbt.getLong(one, "Day"), Nbt.getBoolean(one, "Funeral")));
		}
		return yard;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Grave grave : graves) {
			CompoundTag one = new CompoundTag();
			Nbt.putUUID(one, "Who", grave.who);
			one.putLong("Pos", grave.pos.asLong());
			one.putLong("Bell", grave.bell.asLong());
			one.putString("Name", grave.name);
			one.putLong("Day", grave.day);
			one.putBoolean("Funeral", grave.funeral);
			list.add(one);
		}
		tag.put("Graves", list);
		return tag;
	}
}
