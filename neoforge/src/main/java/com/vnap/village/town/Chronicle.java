package com.vnap.village.town;

import com.vnap.Nbt;
import com.vnap.dialogue.Family;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's chronicle: what happened in it, day by day (a birth, a wedding, a death, a house finished, a traveller
 * who stayed, the day's event: TownStory), read on the notice board. So a player away at their own affairs sees
 * that the town lived on: a line a thing, its words a lang key (chronicle.villager_life.*) and names.
 */
public final class Chronicle {
	/** The lines a town keeps (the oldest go). */
	static final int KEEP = 80;

	public record Entry(long day, String key, List<String> args) {
	}

	private Chronicle() {
	}

	/** A line in the chronicle of the town round {@code pos} (none when it stands in no town). */
	public static void note(ServerLevel level, BlockPos pos, String key, String... args) {
		Roads roads = Roads.get(level.getServer());
		Roads.Network network = roads.near(level.dimension(), pos);
		if (network == null) return;
		network.chronicle.add(new Entry(Family.day(level), key, List.of(args)));
		while (network.chronicle.size() > KEEP) network.chronicle.remove(0);
		roads.setDirty();
	}

	static ListTag save(List<Entry> entries) {
		ListTag list = new ListTag();
		for (Entry entry : entries) {
			CompoundTag one = new CompoundTag();
			one.putLong("Day", entry.day);
			one.putString("Key", entry.key);
			ListTag args = new ListTag();
			for (String arg : entry.args) args.add(StringTag.valueOf(arg));
			one.put("Args", args);
			list.add(one);
		}
		return list;
	}

	static List<Entry> load(ListTag list) {
		List<Entry> out = new ArrayList<>();
		for (Tag tag : list) {
			CompoundTag one = (CompoundTag) tag;
			List<String> args = new ArrayList<>();
			for (Tag arg : Nbt.getList(one, "Args", Tag.TAG_STRING)) args.add(com.vnap.Mc.text(arg));
			out.add(new Entry(Nbt.getLong(one, "Day"), Nbt.getString(one, "Key"), List.copyOf(args)));
		}
		return out;
	}
}
