package com.vnap.spire;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A player's climb of each spire, kept with the player (it outlives death, Player.PERSISTED_NBT_TAG): the floor it
 * stands at (the highest open to it), the floors whose chest it has had (first time only), the notes read, and where
 * it came in from.
 */
public final class SpireProgress {
	private static final String KEY = "vnap_spire";

	private SpireProgress() {
	}

	private static CompoundTag root(Player player) {
		CompoundTag persisted = Nbt.getCompound(player.getPersistentData(), Player.PERSISTED_NBT_TAG);
		player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
		CompoundTag mine = Nbt.getCompound(persisted, KEY);
		persisted.put(KEY, mine);
		return mine;
	}

	private static CompoundTag of(Player player, int spire) {
		CompoundTag root = root(player);
		String key = Integer.toString(spire);
		CompoundTag one = Nbt.getCompound(root, key);
		root.put(key, one);
		return one;
	}

	/** The floor this player climbs from (1 before it ever went in). */
	public static int floor(Player player, int spire) {
		return Math.max(1, Nbt.getInt(of(player, spire), "Floor"));
	}

	public static void setFloor(Player player, int spire, int floor) {
		of(player, spire).putInt("Floor", Math.max(1, floor));
	}

	/** Whether this floor's first chest is still to be had (not marked). */
	public static boolean firstChestPending(Player player, int spire, int floor) {
		return floor > Nbt.getInt(of(player, spire), "Chests");
	}

	/** Whether its first chest of this floor was had already; marks it had. */
	public static boolean firstChest(Player player, int spire, int floor) {
		CompoundTag one = of(player, spire);
		int chests = Nbt.getInt(one, "Chests");
		if (floor <= chests) return false;
		one.putInt("Chests", floor);
		return true;
	}

	/** Whether this player's first look into a floor's secret cache; marks it had. */
	public static boolean secret(Player player, int spire, int floor) {
		return once(player, spire, floor, "Secrets");
	}

	/** Whether this player's first take of a floor's riddle cache (Puzzles); marks it had. */
	public static boolean riddle(Player player, int spire, int floor) {
		return once(player, spire, floor, "Riddles");
	}

	private static boolean once(Player player, int spire, int floor, String list) {
		CompoundTag one = of(player, spire);
		int[] had = com.vnap.Nbt.getIntArray(one, list);
		for (int f : had) if (f == floor) return false;
		int[] more = java.util.Arrays.copyOf(had, had.length + 1);
		more[had.length] = floor;
		one.putIntArray(list, more);
		return true;
	}

	/** Notes are read in order, one a floor: the highest floor whose note this player has read. */
	public static int notesRead(Player player, int spire) {
		return Nbt.getInt(of(player, spire), "Notes");
	}

	public static void read(Player player, int spire, int floor) {
		CompoundTag one = of(player, spire);
		one.putInt("Notes", Math.max(Nbt.getInt(one, "Notes"), floor));
	}

	/** Where the player stood before going in: where the exit sends it. */
	public static void setReturn(Player player, ResourceKey<Level> dimension, BlockPos pos, int spire) {
		CompoundTag root = root(player);
		root.putString("FromDim", com.vnap.Mc.id(dimension).toString());
		root.putLong("FromPos", pos.asLong());
		root.putInt("In", spire);
	}

	/** The spire the player is in (or last was): 0 for none. */
	public static int inside(Player player) {
		return Nbt.getInt(root(player), "In");
	}

	public static @Nullable ResourceKey<Level> returnDimension(Player player) {
		ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(root(player), "FromDim"));
		return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
	}

	public static BlockPos returnPos(Player player) {
		return BlockPos.of(Nbt.getLong(root(player), "FromPos"));
	}
}
