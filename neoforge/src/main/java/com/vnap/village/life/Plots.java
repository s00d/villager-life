package com.vnap.village.life;

import com.vnap.village.build.BuildJob;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * The butcher's and the shepherd's paddock: part of their workshop plans (the fence ring behind the workshop and its
 * gate), so it goes up and is mended with the workshop. Nothing a herder works on lies outside its plan.
 */
public final class Plots {
    private Plots() {
    }

    /** The fenced paddock in the world: the gate, the cell in front of it outside, and the ground inside the fence. */
    public record Paddock(BlockPos gate, BlockPos outside, BoundingBox inside) {
        public BlockPos centre() {
            return inside.getCenter();
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= inside.minX() && pos.getX() <= inside.maxX() && pos.getZ() >= inside.minZ()
                    && pos.getZ() <= inside.maxZ() && Math.abs(pos.getY() - inside.minY()) <= 1;
        }
    }

    /** The workshop's paddock by its current plan, or null (another trade, nothing built, no fence in the plan). */
    public static @Nullable Paddock paddock(ServerLevel level, WorkshopState state) {
        if (state.tier <= 0 || !(state.profession.equals("butcher") || state.profession.equals("shepherd"))) return null;
        SchematicSpec spec = Workshops.schematic(level, Workshops.schematicName(state.profession, state.tier));
        BlockPos gate = null;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, y = 0;
        for (SchematicSpec.PlacedBlock block : spec.blocks) {
            boolean isGate = block.state().getBlock() instanceof FenceGateBlock;
            if (!isGate && !(block.state().getBlock() instanceof FenceBlock)) continue;
            BlockPos rel = block.rel();
            if (isGate) gate = rel;
            minX = Math.min(minX, rel.getX());
            maxX = Math.max(maxX, rel.getX());
            minZ = Math.min(minZ, rel.getZ());
            maxZ = Math.max(maxZ, rel.getZ());
            y = rel.getY();
        }
        if (gate == null || maxX - minX < 2 || maxZ - minZ < 2) return null;
        // the gate's side of the ring: the cell beyond it is outside
        BlockPos out = gate.getZ() == minZ ? gate.north() : gate.getZ() == maxZ ? gate.south() : gate.getX() == minX ? gate.west() : gate.east();
        BlockPos a = BuildJob.at(state.origin, state.rotation, new BlockPos(minX + 1, y, minZ + 1));
        BlockPos b = BuildJob.at(state.origin, state.rotation, new BlockPos(maxX - 1, y, maxZ - 1));
        return new Paddock(BuildJob.at(state.origin, state.rotation, gate), BuildJob.at(state.origin, state.rotation, out),
                BoundingBox.fromCorners(a, b));
    }

    /** The gate stands in the world: the paddock is built (a broken one waits for the workshop's mending). */
    public static boolean standing(ServerLevel level, Paddock paddock) {
        return level.isLoaded(paddock.gate()) && level.getBlockState(paddock.gate()).getBlock() instanceof FenceGateBlock;
    }

    static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && (state.isAir() || (state.canBeReplaced() && !state.is(BlockTags.SAPLINGS)));
    }
}
