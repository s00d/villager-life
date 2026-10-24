package com.vnap.mega;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every whole, keyed Waygate of the server, by its controller and keystone colour (MC Stargate's address book, one
 * colour an address): two gates of a colour are one way; a third of that colour makes it no way at all, and all three
 * go dim until one is keyed otherwise.
 */
public final class Gates extends SavedData {
	private static final String NAME = "villager_life_gates";
	private final Map<GlobalPos, DyeColor> gates = new LinkedHashMap<>();

	public static Gates get(MinecraftServer server) {
		return com.vnap.Mc.data(server.overworld().getDataStorage(), NAME, Gates::new, Gates::load, Gates::save);
	}

	/** A gate stands keyed with this colour (or, with null, stands no more or has no key). */
	public void set(GlobalPos gate, @Nullable DyeColor colour) {
		DyeColor was = colour == null ? gates.remove(gate) : gates.put(gate, colour);
		if (was != colour) setDirty();
	}

	public java.util.Set<GlobalPos> all() {
		return gates.keySet();
	}

	public @Nullable DyeColor colour(GlobalPos gate) {
		return gates.get(gate);
	}

	/**
	 * Drops the gates whose controller is gone from a loaded place (taken by a command, an explosion that skipped its
	 * removal): a gate that is not there links nothing. One in an unloaded place stays: it may well be there.
	 */
	public void prune(MinecraftServer server) {
		if (gates.keySet().removeIf(gate -> {
			net.minecraft.server.level.ServerLevel level = server.getLevel(gate.dimension());
			return level == null || (level.isLoaded(gate.pos()) && !(level.getBlockState(gate.pos()).getBlock() instanceof MegaControllerBlock));
		})) setDirty();
	}

	/** Every gate of a colour out of the book (a test begins clean; the gates themselves enter it again at their next look). */
	public void forget(DyeColor colour) {
		if (gates.values().removeIf(colour::equals)) setDirty();
	}

	/** The one other gate of this gate's colour, or null when there is none or more than one. */
	public @Nullable GlobalPos other(GlobalPos gate) {
		DyeColor colour = gates.get(gate);
		if (colour == null) return null;
		List<GlobalPos> same = new ArrayList<>();
		for (Map.Entry<GlobalPos, DyeColor> entry : gates.entrySet()) {
			if (entry.getValue() == colour && !entry.getKey().equals(gate)) same.add(entry.getKey());
		}
		return same.size() == 1 ? same.get(0) : null;
	}

	private static Gates load(CompoundTag tag, HolderLookup.Provider registries) {
		Gates gates = new Gates();
		CompoundTag all = Nbt.getCompound(tag, "Gates");
		for (String key : com.vnap.Mc.keys(all)) {
			int at = key.lastIndexOf('@');
			if (at < 0) continue;
			ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(key.substring(0, at)));
			DyeColor colour = DyeColor.byName(Nbt.getString(all, key), null);
			if (colour != null) gates.gates.put(GlobalPos.of(dimension, BlockPos.of(Long.parseLong(key.substring(at + 1)))), colour);
		}
		return gates;
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag all = new CompoundTag();
		for (Map.Entry<GlobalPos, DyeColor> entry : gates.entrySet()) {
			all.putString(com.vnap.Mc.id(entry.getKey().dimension()) + "@" + entry.getKey().pos().asLong(), entry.getValue().getName());
		}
		tag.put("Gates", all);
		return tag;
	}
}
