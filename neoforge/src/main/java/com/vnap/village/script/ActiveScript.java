package com.vnap.village.script;

import com.vnap.mood.ScriptKind;
import com.vnap.village.build.BuildJob;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ActiveScript {
    public final long villageId;
    public ScriptKind kind;
    public ScriptPhase phase = ScriptPhase.SELECT_SITE;
    public long phaseStartTick;
    public BlockPos siteOrigin = BlockPos.ZERO;
    public BlockPos focal = BlockPos.ZERO;
    public final List<UUID> workers = new ArrayList<>();
    public final List<BlockPos> workerSlots = new ArrayList<>();
    public @Nullable BuildJob build;
    public int placeInterval = 10;
    public long nextPollTick;
    public float chargeProgress;
    public @Nullable SchematicSpec schematic;
    public boolean summonQueuedAfterAltar;
    public int ingots;
    public final List<UUID> frame = new ArrayList<>();
    public final List<UUID> thrown = new ArrayList<>();
    public BlockPos golemSpot = BlockPos.ZERO;

    public ActiveScript(long villageId, ScriptKind kind, long now) {
        this.villageId = villageId;
        this.kind = kind;
        this.phaseStartTick = now;
    }
}
