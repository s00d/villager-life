package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.village.build.BuildJob;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** A homeless villager's own house: the plan in its biome palette, where it stands and the build so far. */
public final class HouseProject {
    public final long village;
    public final BlockPos bell;
    public UUID owner;
    public final String plan;
    public final String palette;
    public final BlockPos origin;
    public final Rotation rotation;
    public final long startedDay;
    public @Nullable BuildJob job;
    @Nullable CompoundTag savedJob;
    public long pausedDay = -1L;
    public boolean done;
    /** A finished house found with gaps (Family.day it was seen), and whether its household is mending it now. */
    public long damagedDay = -1L;
    public boolean mending;
    /** Extension rooms finished on this house (one at most). */
    public int rooms;
    public @Nullable BuildJob roomJob;
    @Nullable CompoundTag savedRoomJob;
    /**
     * A house standing on this ground that is taken down by hand first, top down (HomeProjects.clearOne): the cells
     * left to clear. The floor layer {@link #clearFloor} turns to grass, the rest to air; {@link #clearArea} is
     * where its pictures hang.
     */
    public final java.util.List<BlockPos> clearing = new java.util.ArrayList<>();
    public int clearFloor;
    public @Nullable BoundingBox clearArea;
    /** The plan's ground floor layer: 0, or deeper for a level with a cellar (HouseLevels); natural ground below it is dug or built into. */
    public int groundY;
    /** The hall's furnishing sets already in (Mayors.OFFICE_SET, HOME_SET bits): each goes in once. */
    public int furnished;
    /** The materials brought for it and the money left to buy the rest (the rooms' too). */
    public final com.vnap.village.market.Site site = new com.vnap.village.market.Site();
    /** What stood in the chests and decor of a house taken down for this one: carried into its chests when it is done. */
    public final java.util.List<net.minecraft.world.item.ItemStack> stash = new java.util.ArrayList<>();
    /** Built on a lot of the town's plan: what it clears first is a lot's leftovers (a player's things go to the lost and found). */
    public boolean junk;
    /** A player who bought the house with a deed (HouseDeedItem): no villager moves in. */
    public @Nullable UUID player;

    public HouseProject(long village, BlockPos bell, UUID owner, String plan, String palette, BlockPos origin,
                        Rotation rotation, long startedDay) {
        this.village = village;
        this.bell = bell.immutable();
        this.owner = owner;
        this.plan = plan;
        this.palette = palette;
        this.origin = origin.immutable();
        this.rotation = rotation;
        this.startedDay = startedDay;
    }

    public String schematicName() {
        return HousePalette.schematicName(plan, palette);
    }

    public SchematicSpec schematic(ServerLevel level) {
        return Workshops.schematic(level, schematicName());
    }

    /** The extension room of this plan in the house's palette, in the house's own frame. */
    public String roomName() {
        return HousePalette.schematicName("room_" + plan, palette);
    }

    public SchematicSpec roomSchematic(ServerLevel level) {
        return Workshops.schematic(level, roomName());
    }

    public boolean hasRoom() {
        return rooms > 0 || roomJob != null || savedRoomJob != null;
    }

    /** The room job, picked up again after a reload; null when no room is being built. */
    public @Nullable BuildJob roomJob(ServerLevel level) {
        if (roomJob == null && savedRoomJob != null) {
            roomJob = BuildJob.load(savedRoomJob, name -> Workshops.schematic(level, name));
            savedRoomJob = null;
        }
        if (roomJob != null) roomJob.site = siteFor();
        return roomJob;
    }

    /** Where the room's bed head stands, or null. */
    public @Nullable BlockPos roomBed(ServerLevel level) {
        for (SchematicSpec.PlacedBlock block : roomSchematic(level).blocks) {
            if (block.state().getBlock() instanceof BedBlock && block.state().getValue(BedBlock.PART) == BedPart.HEAD) {
                return BuildJob.at(origin, rotation, block.rel());
            }
        }
        return null;
    }

    public static BoundingBox footprint(SchematicSpec spec, BlockPos origin, Rotation rotation) {
        return BoundingBox.fromCorners(origin, BuildJob.at(origin, rotation, new BlockPos(spec.sizeX - 1, spec.sizeY - 1, spec.sizeZ - 1)));
    }

    public BoundingBox footprint(ServerLevel level) {
        return footprint(schematic(level), origin, rotation);
    }

    /** The head of the owner's bed in the world (where the HOME point of interest is), or null without a bed. */
    public @Nullable BlockPos bed(ServerLevel level) {
        BlockPos rel = ownerBed(schematic(level));
        return rel == null ? null : BuildJob.at(origin, rotation, rel);
    }

    /** The owner's bed head in the plan: the one nearest the door (the hall has its family's beds too). */
    public static @Nullable BlockPos ownerBed(SchematicSpec spec) {
        BlockPos best = null;
        for (SchematicSpec.PlacedBlock block : spec.blocks) {
            if (block.state().getBlock() instanceof BedBlock && block.state().getValue(BedBlock.PART) == BedPart.HEAD
                    && (best == null || block.rel().distManhattan(spec.focal) < best.distManhattan(spec.focal))) {
                best = block.rel();
            }
        }
        return best;
    }

    /**
     * The town's own base, the hall and the shop house: built and mended for nothing (no materials bought, no money
     * spent), so a town is never left without its mayor's house or its shop for want of coins or of the shop itself.
     */
    public boolean base() {
        return Mayors.HALLS.contains(plan) || com.vnap.village.market.Shops.STALLS.contains(plan);
    }

    /** The site the job takes its materials from: none for the town's base (free). */
    private com.vnap.village.market.@org.jspecify.annotations.Nullable Site siteFor() {
        return base() ? null : site;
    }

    /** The job, picked up again after a reload or started over from what is missing. */
    public BuildJob job(ServerLevel level) {
        if (!clearing.isEmpty()) {
            // the old house still stands: what is missing is only known once it is down
            BuildJob ahead = BuildJob.missing(level, schematicName(), schematic(level), origin, rotation, groundY);
            ahead.site = siteFor();
            return ahead;
        }
        BuildJob current = job;
        if (current == null && savedJob != null) {
            current = BuildJob.load(savedJob, name -> Workshops.schematic(level, name));
            savedJob = null;
        }
        if (current == null) {
            current = BuildJob.missing(level, schematicName(), schematic(level), origin, rotation, groundY);
        }
        current.site = siteFor();
        job = current;
        return current;
    }

    public CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Village", village);
        tag.putLong("Bell", bell.asLong());
        Nbt.putUUID(tag, "Owner", owner);
        tag.putString("Plan", plan);
        tag.putString("Palette", palette);
        tag.putLong("Origin", origin.asLong());
        tag.putString("Rotation", rotation.getSerializedName());
        tag.putLong("Started", startedDay);
        tag.putLong("Paused", pausedDay);
        tag.putBoolean("Done", done);
        tag.putLong("Damaged", damagedDay);
        tag.putBoolean("Mending", mending);
        tag.putInt("Rooms", rooms);
        tag.putInt("Furnished", furnished);
        tag.putInt("GroundY", groundY);
        if (player != null) Nbt.putUUID(tag, "Player", player);
        tag.putBoolean("Junk", junk);
        tag.put("Site", site.save());
        if (!stash.isEmpty()) {
            net.minecraft.nbt.ListTag things = new net.minecraft.nbt.ListTag();
            for (net.minecraft.world.item.ItemStack stack : stash) if (!stack.isEmpty()) things.add(com.vnap.Mc.saveStack(stack, registries));
            tag.put("Stash", things);
        }
        if (!clearing.isEmpty()) {
            tag.putLongArray("Clearing", clearing.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putInt("ClearFloor", clearFloor);
        }
        BoundingBox area = clearArea;
        if (area != null) tag.putIntArray("ClearArea", new int[]{area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ()});
        BuildJob room = roomJob;
        if (room != null) {
            tag.put("RoomJob", room.save());
        } else if (savedRoomJob != null) {
            tag.put("RoomJob", savedRoomJob);
        }
        BuildJob current = job;
        if (current != null && (!done || mending)) {
            tag.put("Job", current.save());
        } else if (savedJob != null) {
            tag.put("Job", savedJob);
        }
        return tag;
    }

    public static HouseProject load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        Rotation rotation = Rotation.NONE;
        for (Rotation candidate : Rotation.values()) {
            if (candidate.getSerializedName().equals(Nbt.getString(tag, "Rotation"))) rotation = candidate;
        }
        HouseProject project = new HouseProject(Nbt.getLong(tag, "Village"), BlockPos.of(Nbt.getLong(tag, "Bell")), Nbt.getUUID(tag, "Owner"),
                Nbt.getString(tag, "Plan"), Nbt.getString(tag, "Palette"), BlockPos.of(Nbt.getLong(tag, "Origin")), rotation, Nbt.getLong(tag, "Started"));
        project.pausedDay = Nbt.getLong(tag, "Paused");
        project.done = Nbt.getBoolean(tag, "Done");
        project.damagedDay = tag.contains("Damaged") ? Nbt.getLong(tag, "Damaged") : -1L;
        project.mending = Nbt.getBoolean(tag, "Mending");
        if (tag.contains("Job")) project.savedJob = Nbt.getCompound(tag, "Job");
        project.rooms = Nbt.getInt(tag, "Rooms");
        for (long pos : Nbt.getLongArray(tag, "Clearing")) project.clearing.add(BlockPos.of(pos));
        project.clearFloor = Nbt.getInt(tag, "ClearFloor");
        project.furnished = Nbt.getInt(tag, "Furnished");
        project.groundY = Nbt.getInt(tag, "GroundY");
        if (Nbt.hasUUID(tag, "Player")) project.player = Nbt.getUUID(tag, "Player");
        project.junk = Nbt.getBoolean(tag, "Junk");
        for (net.minecraft.nbt.Tag thing : Nbt.getList(tag, "Stash", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            com.vnap.Mc.parseStack(registries, thing).ifPresent(project.stash::add);
        }
        com.vnap.village.market.Site site = com.vnap.village.market.Site.load(Nbt.getCompound(tag, "Site"));
        project.site.funds = site.funds;
        project.site.wages = site.wages;
        project.site.stock.putAll(site.stock);
        int[] area = Nbt.getIntArray(tag, "ClearArea");
        if (area.length == 6) project.clearArea = new BoundingBox(area[0], area[1], area[2], area[3], area[4], area[5]);
        if (tag.contains("RoomJob")) project.savedRoomJob = Nbt.getCompound(tag, "RoomJob");
        return project;
    }
}
