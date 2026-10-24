package com.vnap.village.home;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Players who slept a night in a village are its neighbours (by bell): invited to weddings, visited by the children. */
public final class Neighbours extends SavedData {
	private static final String NAME = "villager_life_neighbours";
	private final Map<String, Set<UUID>> byBell = new HashMap<>();

	/** A bell in one world (the same position may hold a bell in another dimension). */
	private static String key(net.minecraft.world.level.Level level, BlockPos bell) {
		return com.vnap.Mc.id(level.dimension()) + "@" + bell.asLong();
	}

	public static Neighbours get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Neighbours::new, Neighbours::load, Neighbours::save);
	}

	/** True when the player was not yet a neighbour of this bell. */
	public boolean add(net.minecraft.world.level.Level level, BlockPos bell, UUID player) {
		boolean added = byBell.computeIfAbsent(key(level, bell), ignored -> new HashSet<>()).add(player);
		if (added) setDirty();
		return added;
	}

	/** The players who are this bell's neighbours (slept by it). */
	public Set<UUID> of(net.minecraft.world.level.Level level, BlockPos bell) {
		return byBell.getOrDefault(key(level, bell), Set.of());
	}

	/** The village's bell was hung anew (BellMove): its neighbours are the new bell's. */
	public void move(net.minecraft.world.level.Level level, BlockPos from, BlockPos to) {
		Set<UUID> players = byBell.remove(key(level, from));
		if (players == null) return;
		byBell.computeIfAbsent(key(level, to), ignored -> new HashSet<>()).addAll(players);
		setDirty();
	}

	public boolean is(net.minecraft.world.level.Level level, BlockPos bell, UUID player) {
		Set<UUID> players = byBell.get(key(level, bell));
		return players != null && players.contains(player);
	}

	private static Neighbours load(CompoundTag tag, HolderLookup.Provider registries) {
		Neighbours neighbours = new Neighbours();
		CompoundTag bells = Nbt.getCompound(tag, "Bells");
		for (String key : com.vnap.Mc.keys(bells)) {
			Set<UUID> players = new HashSet<>();
			for (Tag entry : Nbt.getList(bells, key, Tag.TAG_INT_ARRAY)) players.add(com.vnap.Mc.uuid(entry));
			neighbours.byBell.put(key, players);
		}
		return neighbours;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag bells = new CompoundTag();
		for (Map.Entry<String, Set<UUID>> entry : byBell.entrySet()) {
			ListTag list = new ListTag();
			for (UUID player : entry.getValue()) list.add(com.vnap.Mc.uuidTag(player));
			bells.put(entry.getKey(), list);
		}
		tag.put("Bells", bells);
		return tag;
	}
}
