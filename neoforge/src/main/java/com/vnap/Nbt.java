package com.vnap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.UUID;

/**
 * NBT reads as 1.21.1 has them: a missing key gives 0, false, "" or an empty tag. From 1.21.5 a getter with a key is
 * an Optional; these keep the old defaults on every version.
 */
public final class Nbt {
    private Nbt() {
    }

    //? if >=1.21.5 {
    /*public static int getInt(CompoundTag tag, String key) {
        return tag.getIntOr(key, 0);
    }

    public static long getLong(CompoundTag tag, String key) {
        return tag.getLongOr(key, 0L);
    }

    public static float getFloat(CompoundTag tag, String key) {
        return tag.getFloatOr(key, 0.0F);
    }

    public static double getDouble(CompoundTag tag, String key) {
        return tag.getDoubleOr(key, 0.0);
    }

    public static byte getByte(CompoundTag tag, String key) {
        return tag.getByteOr(key, (byte) 0);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBooleanOr(key, false);
    }

    public static String getString(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompoundOrEmpty(key);
    }

    @SuppressWarnings("UnusedVariable") // the element type is checked by the getter until 1.21.4
    public static ListTag getList(CompoundTag tag, String key, int type) {
        return tag.getListOrEmpty(key);
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        return tag.getIntArray(key).orElse(new int[0]);
    }

    public static long[] getLongArray(CompoundTag tag, String key) {
        return tag.getLongArray(key).orElse(new long[0]);
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        return tag.read(key, net.minecraft.core.UUIDUtil.CODEC).orElse(new UUID(0L, 0L));
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.read(key, net.minecraft.core.UUIDUtil.CODEC).isPresent();
    }

    public static void putUUID(CompoundTag tag, String key, UUID id) {
        tag.store(key, net.minecraft.core.UUIDUtil.CODEC, id);
    }

    public static int getInt(ListTag list, int index) {
        return list.getIntOr(index, 0);
    }

    public static String getString(ListTag list, int index) {
        return list.getStringOr(index, "");
    }

    public static CompoundTag getCompound(ListTag list, int index) {
        return list.getCompoundOrEmpty(index);
    }
    *///?} else {
    public static int getInt(CompoundTag tag, String key) {
        return tag.getInt(key);
    }

    public static long getLong(CompoundTag tag, String key) {
        return tag.getLong(key);
    }

    public static float getFloat(CompoundTag tag, String key) {
        return tag.getFloat(key);
    }

    public static double getDouble(CompoundTag tag, String key) {
        return tag.getDouble(key);
    }

    public static byte getByte(CompoundTag tag, String key) {
        return tag.getByte(key);
    }

    public static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBoolean(key);
    }

    public static String getString(CompoundTag tag, String key) {
        return tag.getString(key);
    }

    public static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompound(key);
    }

    public static ListTag getList(CompoundTag tag, String key, int type) {
        return tag.getList(key, type);
    }

    public static int[] getIntArray(CompoundTag tag, String key) {
        return tag.getIntArray(key);
    }

    public static long[] getLongArray(CompoundTag tag, String key) {
        return tag.getLongArray(key);
    }

    public static UUID getUUID(CompoundTag tag, String key) {
        return tag.getUUID(key);
    }

    public static boolean hasUUID(CompoundTag tag, String key) {
        return tag.hasUUID(key);
    }

    public static void putUUID(CompoundTag tag, String key, UUID id) {
        tag.putUUID(key, id);
    }

    public static int getInt(ListTag list, int index) {
        return list.getInt(index);
    }

    public static String getString(ListTag list, int index) {
        return list.getString(index);
    }

    public static CompoundTag getCompound(ListTag list, int index) {
        return list.getCompound(index);
    }
    //?}
}
