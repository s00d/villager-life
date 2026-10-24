package com.vnap.dialogue;

import com.vnap.Nbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Villagers who died, so a spouse loaded later still learns it is widowed. Oldest entries drop past the cap. */
public final class FamilyDeaths extends SavedData {
	private static final String NAME = "villager_life_family_deaths";
	private static final int CAP = 4096;
	private final Set<UUID> dead = new LinkedHashSet<>();

	public static FamilyDeaths get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, FamilyDeaths::new, FamilyDeaths::load, FamilyDeaths::save);
	}

	public boolean contains(UUID id) {
		return dead.contains(id);
	}

	public void add(UUID id) {
		if (dead.add(id) && dead.size() > CAP) dead.remove(dead.iterator().next());
		setDirty();
	}

	private static FamilyDeaths load(CompoundTag tag, HolderLookup.Provider registries) {
		FamilyDeaths deaths = new FamilyDeaths();
		for (Tag entry : Nbt.getList(tag, "Dead", Tag.TAG_INT_ARRAY)) deaths.dead.add(com.vnap.Mc.uuid(entry));
		return deaths;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (UUID id : dead) list.add(com.vnap.Mc.uuidTag(id));
		tag.put("Dead", list);
		return tag;
	}
}
