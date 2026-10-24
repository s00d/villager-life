package com.vnap.mood;

import com.vnap.Nbt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import com.vnap.village.home.HouseProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class VillageMoodSavedData extends SavedData {
    private static final String DATA_NAME = "vnap_village_mood";

    private final Long2ObjectMap<MoodState> villages = new Long2ObjectOpenHashMap<>();
    /** Houses villagers build for themselves; finished ones stay so new plans keep clear of them. */
    private final List<HouseProject> houses = new ArrayList<>();

    public VillageMoodSavedData() {
    }

    public static VillageMoodSavedData get(ServerLevel level) {
        return com.vnap.Mc.data(level.getDataStorage(), DATA_NAME, VillageMoodSavedData::new, VillageMoodSavedData::load, VillageMoodSavedData::save);
    }

    public static VillageMoodSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageMoodSavedData data = new VillageMoodSavedData();
        ListTag list = Nbt.getList(tag, "villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MoodState state = MoodState.load(Nbt.getCompound(list, i));
            data.villages.put(state.villageId, state);
        }
        ListTag houses = Nbt.getList(tag, "houses", Tag.TAG_COMPOUND);
        for (int i = 0; i < houses.size(); i++) {
            data.houses.add(HouseProject.load(Nbt.getCompound(houses, i), registries));
        }
        return data;
    }

    //? if <1.21.5
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MoodState state : villages.values()) {
            list.add(state.save());
        }
        tag.put("villages", list);
        ListTag houseList = new ListTag();
        for (HouseProject house : houses) {
            houseList.add(house.save(registries));
        }
        tag.put("houses", houseList);
        return tag;
    }

    public List<HouseProject> houses() {
        return houses;
    }

    public MoodState get(long villageId) {
        return villages.get(villageId);
    }

    public MoodState getOrCreate(long villageId, net.minecraft.core.BlockPos meetingPos) {
        MoodState existing = villages.get(villageId);
        if (existing != null) {
            return existing;
        }
        MoodState created = new MoodState(villageId, meetingPos);
        villages.put(villageId, created);
        setDirty();
        return created;
    }

    public Collection<MoodState> all() {
        return villages.values();
    }

    public void markDirty() {
        setDirty();
    }
}
