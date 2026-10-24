package com.vnap.village.workshop;

import com.vnap.Nbt;
import com.vnap.village.build.BuildJob;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** One villager's workshop: where it stands, how far it is built and what is being built now. */
public final class WorkshopState {
    public final String profession;
    public final BlockPos anchor;
    public final BlockPos origin;
    public final Rotation rotation;
    /** Highest tier that fit when the site was chosen; 0 means nothing fits around this job site. */
    public int maxTier;
    public int tier;
    public int jobTier;
    public @Nullable BuildJob job;
    /** Saved job not yet resolved against the server's schematics. */
    @Nullable CompoundTag savedJob;
    public long checkedDay = -1L;
    public long producedDay = -1L;
    /** Day on which a full pass found no reachable block; the job rests until the next day. */
    public long pausedDay = -1L;
    /** Livestock bought at the stall (butcher, shepherd): the day the delivery brings it (-1: none due) and its kind. */
    public long stockDay = -1L;
    public String stockKind = "";
    /** Survey points the cartographer has walked today, as bits. */
    public int survey;
    /** Day the counters below belong to; chore id to how often it was done that day. */
    public long choreDay = -1L;
    public final Map<String, Integer> chores = new HashMap<>();
    /** The farm's saplings tended today (each once a day); cleared with the day's chore counts. */
    public final java.util.Set<Long> tended = new java.util.HashSet<>();
    public long nextChore;
    /** The materials for the tier (or pen) going up and the money left for the rest. */
    public final com.vnap.village.market.Site site = new com.vnap.village.market.Site();

    public WorkshopState(String profession, BlockPos anchor, BlockPos origin, Rotation rotation, int maxTier) {
        this.profession = profession;
        this.anchor = anchor.immutable();
        this.origin = origin.immutable();
        this.rotation = rotation;
        this.maxTier = maxTier;
    }

    public BlockPos at(int x, int y, int z) {
        return BuildJob.at(origin, rotation, new BlockPos(x, y, z));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Profession", profession);
        tag.putLong("Anchor", anchor.asLong());
        tag.putLong("Origin", origin.asLong());
        tag.putString("Rotation", rotation.getSerializedName());
        tag.putInt("MaxTier", maxTier);
        tag.putInt("Tier", tier);
        tag.putInt("JobTier", jobTier);
        tag.putLong("CheckedDay", checkedDay);
        tag.putLong("ProducedDay", producedDay);
        tag.putLong("PausedDay", pausedDay);
        tag.putLong("StockDay", stockDay);
        tag.putString("StockKind", stockKind);
        tag.putInt("Survey", survey);
        tag.putLong("ChoreDay", choreDay);
        tag.put("Site", site.save());
        CompoundTag counts = new CompoundTag();
        chores.forEach(counts::putInt);
        tag.put("Chores", counts);
        if (job != null) {
            tag.put("Job", job.save());
        } else if (savedJob != null) {
            tag.put("Job", savedJob);
        }
        return tag;
    }

    public static WorkshopState load(CompoundTag tag) {
        Rotation rotation = Rotation.NONE;
        for (Rotation candidate : Rotation.values()) {
            if (candidate.getSerializedName().equals(Nbt.getString(tag, "Rotation"))) {
                rotation = candidate;
            }
        }
        WorkshopState state = new WorkshopState(Nbt.getString(tag, "Profession"), BlockPos.of(Nbt.getLong(tag, "Anchor")),
                BlockPos.of(Nbt.getLong(tag, "Origin")), rotation, Nbt.getInt(tag, "MaxTier"));
        state.tier = Nbt.getInt(tag, "Tier");
        state.jobTier = Nbt.getInt(tag, "JobTier");
        state.checkedDay = Nbt.getLong(tag, "CheckedDay");
        state.producedDay = Nbt.getLong(tag, "ProducedDay");
        state.pausedDay = tag.contains("PausedDay") ? Nbt.getLong(tag, "PausedDay") : -1L;
        state.savedJob = tag.contains("Job") ? Nbt.getCompound(tag, "Job") : null;
        com.vnap.village.market.Site site = com.vnap.village.market.Site.load(Nbt.getCompound(tag, "Site"));
        state.site.funds = site.funds;
        state.site.wages = site.wages;
        state.site.stock.putAll(site.stock);
        state.stockDay = tag.contains("StockDay") ? Nbt.getLong(tag, "StockDay") : -1L;
        state.stockKind = Nbt.getString(tag, "StockKind");
        state.survey = Nbt.getInt(tag, "Survey");
        state.choreDay = tag.contains("ChoreDay") ? Nbt.getLong(tag, "ChoreDay") : -1L;
        CompoundTag counts = Nbt.getCompound(tag, "Chores");
        for (String key : com.vnap.Mc.keys(counts)) state.chores.put(key, Nbt.getInt(counts, key));
        return state;
    }
}
