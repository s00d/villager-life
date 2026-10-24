package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.progress.VillageAdvancements;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A full village sends its homeless young away: a single (with a homeless sweetheart if there is one) walks off and
 * is kept as NBT, with a spot 150 to 300 blocks away. Nothing happens there until a player comes close enough for the
 * spot to tick; then a bell, a finished house and the settlers appear at once, as if they had been there a while.
 */
public final class Hamlets extends SavedData {
	private static final String NAME = "villager_life_hamlets";
	private static final int CAP = 64;
	private static final String LEAVING = "vnap_leaving_since";
	private static final int WALK_TICKS = 1200;
	private static final double CHANCE = 0.3;

	private final List<Settlement> pending = new ArrayList<>();

	/** Settlers on their way: where their hamlet will stand and the villagers themselves. */
	public record Settlement(ResourceKey<Level> dimension, BlockPos pos, List<CompoundTag> settlers) {
	}

	public static Hamlets get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Hamlets::new, Hamlets::load, Hamlets::save);
	}

	public List<Settlement> pending() {
		return pending;
	}

	/** Daily for a homeless adult: in a full village it may set off (and a homeless sweetheart with it). */
	public static void consider(ServerLevel level, Villager villager, long day) {
		if (villager.isBaby() || Family.home(villager) != null || hasBed(villager)
				|| leaving(villager) || Mayors.isMayor(villager)
				|| com.vnap.village.market.Shops.isKeeper(villager)) return;
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager, villager.blockPosition());
		if (!Towns.full(level, bell) || villager.getRandom().nextDouble() >= CHANCE) return;
		Villager partner = sweetheart(level, villager, bell, day);
		setOff(level, villager, partner);
		if (partner != null) setOff(level, partner, villager);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, villager.getBoundingBox().inflate(48.0))) {
			com.vnap.Mc.say(player, partner == null
					? Component.translatable("vnap.hamlet.leaves", VillagerHome.nameOf(villager))
					: Component.translatable("vnap.hamlet.leave_together", VillagerHome.nameOf(villager), VillagerHome.nameOf(partner)), true);
		}
	}

	private static boolean hasBed(Villager villager) {
		return villager instanceof VillagerLifeData data && data.vnap$homeBed() != null;
	}

	/** A homeless single of the other sex from the same bell, not kin. */
	private static @Nullable Villager sweetheart(ServerLevel level, Villager villager, BlockPos bell, long day) {
		boolean female = Family.female(villager);
		FamilyState family = Family.state(villager);
		return level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(bell).inflate(TownRules.SPAN),
						other -> !other.equals(villager) && other.isAlive() && Family.female(other) != female
								&& Family.single(other, Family.state(other), day) && Family.home(other) == null && !leaving(other)
								&& !hasBed(other)
								&& !family.related(other.getUUID(), Family.state(other), villager.getUUID()))
				.stream().min(Comparator.comparingDouble(other -> other.distanceToSqr(villager))).orElse(null);
	}

	private static void setOff(ServerLevel level, Villager villager, @Nullable Villager partner) {
		com.vnap.village.town.Chronicle.note(level, villager.blockPosition(), "hamlet", com.vnap.dialogue.VillagerHome.nameOf(villager));
		villager.getPersistentData().putLong(LEAVING, level.getGameTime());
		if (partner != null) Family.startCourting(villager, partner, Family.day(level));
		Speech.queue(villager, Family.female(villager) ? "Woman Leaves for a Hamlet" : "Man Leaves for a Hamlet",
				partner, 2L);
		level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(12.0),
						other -> !other.equals(villager) && !other.equals(partner) && !other.isBaby() && !leaving(other))
				.stream().findFirst()
				.ifPresent(neighbour -> Speech.queue(neighbour, "Neighbour Says Goodbye", villager, 6L));
	}

	public static boolean leaving(Villager villager) {
		return villager.getPersistentData().contains(LEAVING);
	}

	/** Every family tick: a leaving villager walks out of the village and, far enough or after a while, is gone. */
	public static void tickLeaving(ServerLevel level, Villager villager) {
		if (!leaving(villager)) return;
		long since = Nbt.getLong(villager.getPersistentData(), LEAVING);
		BlockPos bell = com.vnap.village.home.Towns.bellOf(villager, villager.blockPosition());
		if (level.getGameTime() - since < WALK_TICKS && villager.blockPosition().closerThan(bell, Towns.reach(level, bell) + 8.0)) {
			BlockPos away = bell.offset(villager.blockPosition().subtract(bell).multiply(4));
			com.vnap.village.life.Agenda.add(villager, new com.vnap.village.life.Agenda.Item("leave", away, com.vnap.village.life.Agenda.EVENT, 4, 0.6F, null, null, null));
			return;
		}
		List<Villager> group = new ArrayList<>(List.of(villager));
		if (Family.state(villager).courting != null && level.getEntity(Family.state(villager).courting) instanceof Villager partner
				&& partner.isAlive() && leaving(partner)) group.add(partner);
		depart(level, group, bell);
	}

	/** The settlers leave the world for now; their hamlet waits for a player 150 to 300 blocks from {@code bell}. */
	public static @Nullable Settlement depart(ServerLevel level, List<Villager> settlers, BlockPos bell) {
		if (settlers.isEmpty()) return null;
		Villager first = settlers.get(0);
		BlockPos from = first.blockPosition().equals(bell) ? bell.east() : first.blockPosition();
		double heading = Math.atan2(from.getZ() - bell.getZ(), from.getX() - bell.getX());
		BlockPos spot = null;
		// far enough that both may grow into full towns (TownRules.MAX_REACH each) without sharing land; the way they
		// walked first, then further round
		for (int tries = 0; tries < 16 && spot == null; tries++) {
			double angle = heading + (first.getRandom().nextDouble() - 0.5) + tries * Math.PI / 8.0;
			int distance = TownRules.HAMLET_FAR + first.getRandom().nextInt(301 - TownRules.HAMLET_FAR);
			BlockPos candidate = com.vnap.village.town.Rings.at(bell, angle, distance);
			if (roomFor(level, candidate)) spot = candidate;
		}
		return depart(level, settlers, spot == null ? bell.offset(TownRules.HAMLET_FAR * 2, 0, 0) : spot, true);
	}

	/** No town and no other hamlet on the way within {@link TownRules#HAMLET_FAR}. */
	private static boolean roomFor(ServerLevel level, BlockPos spot) {
		for (Mayors.Office office : Mayors.get(level.getServer()).offices()) {
			if (office.dimension.equals(level.dimension()) && office.bell.closerThan(spot, TownRules.HAMLET_FAR)) return false;
		}
		for (Settlement settlement : get(level.getServer()).pending) {
			if (settlement.dimension().equals(level.dimension()) && settlement.pos().closerThan(spot, TownRules.HAMLET_FAR)) return false;
		}
		return true;
	}

	/** {@code depart} to an exact spot; {@code walkAway} false keeps them in place (tests found it right there). */
	public static @Nullable Settlement depart(ServerLevel level, List<Villager> settlers, BlockPos spot, boolean walkAway) {
		List<CompoundTag> saved = new ArrayList<>();
		java.util.Map<Villager, Mayors.Office> vacant = new java.util.LinkedHashMap<>();
		for (Villager villager : settlers) {
			Mayors.Office office = Mayors.onDepart(level, villager);
			if (office != null) vacant.put(villager, office);
			com.vnap.village.market.Shops.onGone(level, villager);
		}
		for (Villager villager : settlers) {
			villager.getPersistentData().remove(LEAVING);
			Family.leaveForNewHouse(level, villager);
			villager.releasePoi(MemoryModuleType.JOB_SITE);
			villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
			villager.releasePoi(MemoryModuleType.MEETING_POINT);
			villager.getBrain().eraseMemory(MemoryModuleType.MEETING_POINT);
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			CompoundTag tag = new CompoundTag();
			if (!com.vnap.Mc.saveEntity(villager, tag)) continue;
			saved.add(tag);
			Towns.forget(level, villager.getUUID());
			com.vnap.VillagerLife.LOGGER.info("vnap gone: {} left to found a hamlet", com.vnap.dialogue.VillagerHome.nameOf(villager));
			if (villager.isSleeping()) villager.stopSleeping();
			villager.discard();
		}
		vacant.forEach((late, office) -> Mayors.succeed(level, office, late, Family.day(level)));
		// a pet stays in the village: with the next head of the house or a neighbour's family
		for (Villager settler : settlers) Pets.ownerGone(level, settler, null);
		if (saved.isEmpty()) return null;
		Settlement settlement = new Settlement(level.dimension(), spot.immutable(), saved);
		Hamlets hamlets = get(level.getServer());
		hamlets.pending.add(settlement);
		if (hamlets.pending.size() > CAP) hamlets.pending.remove(0);
		hamlets.setDirty();
		return settlement;
	}

	/** Every 100 ticks: hamlets whose spot is ticking now (a player came by) are founded. */
	public static void tick(MinecraftServer server) {
		Hamlets hamlets = get(server);
		if (hamlets.pending.isEmpty()) return;
		for (Settlement settlement : List.copyOf(hamlets.pending)) {
			ServerLevel level = server.getLevel(settlement.dimension());
			if (level == null || !level.isPositionEntityTicking(settlement.pos())) continue;
			if (found(level, settlement)) {
				hamlets.pending.remove(settlement);
				hamlets.setDirty();
			}
		}
	}

	/** Bell, settlers and their first house to build. False when the spot is no good yet (water, a cliff). */
	public static boolean found(ServerLevel level, Settlement settlement) {
		BlockPos ground = groundNear(level, settlement.pos());
		if (ground == null) return false;
		BlockPos bell = ground.above();
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		List<Villager> settlers = new ArrayList<>();
		for (CompoundTag tag : settlement.settlers()) {
			//? if >=1.21.2 {
			/*Entity entity = EntityType.loadEntityRecursive(tag, level, net.minecraft.world.entity.EntitySpawnReason.LOAD, loaded -> {
			*///?} else
			Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> {
				com.vnap.Mc.snap(loaded, bell.getX() + 2.5, bell.getY(), bell.getZ() + 0.5 + settlers.size(), loaded.getYRot(), 0.0F);
				return loaded;
			});
			if (!(entity instanceof Villager villager) || !level.addFreshEntity(villager)) continue;
			villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), bell));
			settlers.add(villager);
		}
		if (settlers.isEmpty()) return true;
		Villager head = settlers.stream().filter(villager -> !Family.female(villager)).findFirst().orElse(settlers.get(0));
		HouseProject house = HomeProjects.start(level, head, Family.day(level));
		if (house != null) {
			// the settlers brought the first house's materials along, bought with its money (HomeProjects.register):
			// they build it by hand, and the couple marries in it once it stands (the courtship goes on meanwhile)
			com.vnap.village.market.Materials.bill(level, house.job(level)).forEach(house.site::add);
			house.site.funds = 0;
			VillageMoodSavedData.get(level).markDirty();
		}
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, head.getBoundingBox().inflate(64.0))) {
			VillageAdvancements.award(player, "family/hamlet");
		}
		Speech.queue(head, "Settlers Found a Hamlet", null, 20L);
		return true;
	}

	/** Dry, sturdy ground near the spot, looking up to 16 blocks around it. */
	private static @Nullable BlockPos groundNear(ServerLevel level, BlockPos spot) {
		for (BlockPos at : com.vnap.village.town.Rings.around(spot, 0, 16, 4, radius -> radius == 0 ? 1 : 8)) {
			BlockPos ground = com.vnap.village.town.Ground.top(level, at.getX(), at.getZ());
			if (ground != null && level.getFluidState(ground.above()).isEmpty() && level.getFluidState(ground).isEmpty()
					&& wild(level, ground)) return ground;
		}
		return null;
	}

	/**
	 * Untouched land: natural ground and no chest, bed, furnace or bell within 24 blocks. A spot 150-300 blocks out
	 * can be the roof of a player's base or another village, and a hamlet's house would be set right into it.
	 */
	private static boolean wild(ServerLevel level, BlockPos ground) {
		var state = level.getBlockState(ground);
		if (!(state.is(net.minecraft.tags.BlockTags.DIRT) || state.is(net.minecraft.tags.BlockTags.SAND)
				|| state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.GRAVEL))) {
			return false;
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				var chunk = level.getChunkSource().getChunkNow((ground.getX() >> 4) + dx, (ground.getZ() >> 4) + dz);
				if (chunk == null) continue;
				for (BlockPos pos : chunk.getBlockEntitiesPos()) {
					if (Math.abs(pos.getX() - ground.getX()) <= 24 && Math.abs(pos.getZ() - ground.getZ()) <= 24
							&& Math.abs(pos.getY() - ground.getY()) <= 12) return false;
				}
			}
		}
		return true;
	}

	private static Hamlets load(CompoundTag tag, HolderLookup.Provider registries) {
		Hamlets hamlets = new Hamlets();
		for (Tag entry : Nbt.getList(tag, "Pending", Tag.TAG_COMPOUND)) {
			CompoundTag one = (CompoundTag) entry;
			ResourceLocation dimension = ResourceLocation.tryParse(Nbt.getString(one, "Dim"));
			if (dimension == null) continue;
			List<CompoundTag> settlers = new ArrayList<>();
			for (Tag settler : Nbt.getList(one, "Settlers", Tag.TAG_COMPOUND)) settlers.add((CompoundTag) settler);
			hamlets.pending.add(new Settlement(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(Nbt.getLong(one, "Pos")), settlers));
		}
		return hamlets;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Settlement settlement : pending) {
			CompoundTag one = new CompoundTag();
			one.putString("Dim", com.vnap.Mc.id(settlement.dimension()).toString());
			one.putLong("Pos", settlement.pos().asLong());
			ListTag settlers = new ListTag();
			settlers.addAll(settlement.settlers());
			one.put("Settlers", settlers);
			list.add(one);
		}
		tag.put("Pending", list);
		return tag;
	}
}
