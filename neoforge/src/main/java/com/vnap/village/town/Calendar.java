package com.vnap.village.town;

import com.vnap.Nbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The days the villages live by (Family.day): one more each time the world's calendar day turns over. Game time
 * does not move when the players sleep the night away (only the time of day jumps to the morning), so days counted
 * on it fell half a day behind with every night slept, and the morning's once-a-day work (wages, the mayor's
 * speech, the stall's delivery) was skipped every other morning. A day turns over when the time of day passes a
 * morning, is slept to it, or is set (/time set) to another day; the count never goes back. With the daylight cycle
 * off the time of day stands still, and a day is counted for every day of game time.
 */
public final class Calendar extends SavedData {
	private static final String NAME = "villager_life_calendar";
	private static final long DAY = 24000L;
	private long day;
	private long calendarDay;
	private long gameMark;

	private Calendar(long day, long calendarDay, long gameMark) {
		this.day = day;
		this.calendarDay = calendarDay;
		this.gameMark = gameMark;
	}

	private static Calendar get(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		return com.vnap.Mc.data(overworld.getDataStorage(), NAME,
				() -> new Calendar(com.vnap.Mc.dayTime(overworld) / DAY, com.vnap.Mc.dayTime(overworld) / DAY, overworld.getGameTime()),
				Calendar::load, Calendar::save);
	}

	/** The village day now (it counts a turn of the calendar it is asked about first). */
	public static long day(ServerLevel level) {
		ServerLevel overworld = level.getServer().overworld();
		Calendar calendar = get(level.getServer());
		long now = com.vnap.Mc.dayTime(overworld) / DAY;
		long game = overworld.getGameTime();
		if (com.vnap.Mc.daylightCycle(overworld)) {
			if (now != calendar.calendarDay) {
				calendar.day += now > calendar.calendarDay ? now - calendar.calendarDay : 1;
				calendar.calendarDay = now;
				calendar.gameMark = game;
				calendar.setDirty();
			}
		} else if (game - calendar.gameMark >= DAY || game < calendar.gameMark) {
			calendar.day += Math.max(1, (game - calendar.gameMark) / DAY);
			calendar.calendarDay = now;
			calendar.gameMark = game;
			calendar.setDirty();
		}
		return calendar.day;
	}

	private static Calendar load(CompoundTag tag, HolderLookup.Provider registries) {
		return new Calendar(Nbt.getLong(tag, "Day"), Nbt.getLong(tag, "CalendarDay"), Nbt.getLong(tag, "GameMark"));
	}

	//? if <1.21.5
	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putLong("Day", day);
		tag.putLong("CalendarDay", calendarDay);
		tag.putLong("GameMark", gameMark);
		return tag;
	}
}
