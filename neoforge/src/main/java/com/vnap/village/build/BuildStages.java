package com.vnap.village.build;

import com.vnap.village.script.SchematicSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Build order the way a builder works: ground, then the shell bottom-up (walls, posts, stairs and the
 * floors between storeys), the roof above the top floor, openings, the interior and the beds last.
 * A layer counts as a floor when full blocks cover at least half of the schematic's footprint.
 */
public final class BuildStages {
    /** A cellar dug out under the ground floor, top down, before anything else. */
    static final int DIG = -1;
    static final int GROUND = 0;
    static final int SHELL = 1;
    static final int ROOF = 2;
    static final int OPENINGS = 3;
    static final int INTERIOR = 4;
    static final int BEDS = 5;

    private BuildStages() {
    }

    public static int[] sort(SchematicSpec schematic, int groundY, int[] indices) {
        int topFloor = topFloor(schematic, groundY);
        double cx = (schematic.sizeX - 1) / 2.0;
        double cz = (schematic.sizeZ - 1) / 2.0;
        Integer[] boxed = Arrays.stream(indices).boxed().toArray(Integer[]::new);
        Comparator<Integer> order = Comparator.comparingInt(i -> stage(schematic.blocks.get(i), groundY, topFloor));
        order = order.thenComparingInt(i -> {
            SchematicSpec.PlacedBlock block = schematic.blocks.get(i);
            int stage = stage(block, groundY, topFloor);
            return stage == INTERIOR ? 0 : stage == DIG ? -block.rel().getY() : block.rel().getY();
        });
        order = order.thenComparingDouble(i -> {
            SchematicSpec.PlacedBlock block = schematic.blocks.get(i);
            int stage = stage(block, groundY, topFloor);
            return stage <= ROOF ? Math.atan2(block.rel().getZ() - cz, block.rel().getX() - cx) : 0.0;
        });
        Arrays.sort(boxed, order);
        return Arrays.stream(boxed).mapToInt(Integer::intValue).toArray();
    }

    public static int stage(SchematicSpec.PlacedBlock block, int groundY, int topFloor) {
        BlockState state = block.state();
        int y = block.rel().getY();
        if (state.getBlock() instanceof BedBlock) {
            return BEDS;
        }
        if (state.isAir()) {
            // a cellar under the floor is dug first; a doorway into an extension opens once its walls and roof stand; on a
            // road (all ground) the grass and flowers over it are cut before the stone goes under them, or they pop off
            return groundY == BuildJob.ALL_GROUND || y < groundY ? DIG : OPENINGS;
        }
        if (y == groundY) {
            return GROUND;
        }
        if (opening(state)) {
            return OPENINGS;
        }
        if (y > topFloor) {
            // a torch on a wall upstairs goes up after the wall it hangs on (the roof's course is by angle, not by support)
            return hangs(state) ? INTERIOR : ROOF;
        }
        if (full(state) || state.getBlock() instanceof StairBlock || state.getBlock() instanceof LadderBlock) {
            return SHELL;
        }
        return INTERIOR;
    }

    /** The highest storey floor or ceiling; everything above it is roof. Integer.MAX_VALUE when there is none. */
    public static int topFloor(SchematicSpec schematic, int groundY) {
        int area = schematic.sizeX * schematic.sizeZ;
        // one pass over the blocks: full cells per layer (schematic layers are 0..sizeY-1, whatever groundY is)
        Map<Integer, Set<Long>> covered = new HashMap<>();
        for (SchematicSpec.PlacedBlock block : schematic.blocks) {
            if (full(block.state())) {
                covered.computeIfAbsent(block.rel().getY(), y -> new HashSet<>())
                        .add(BlockPos.asLong(block.rel().getX(), 0, block.rel().getZ()));
            }
        }
        // groundY may be BuildJob.NO_GROUND (Integer.MIN_VALUE): never walk below layer 0
        int bottom = Math.max(groundY, -1) + 1;
        for (int y = schematic.sizeY - 1; y > bottom; y--) {
            Set<Long> layer = covered.get(y);
            if (layer != null && layer.size() * 2 >= area) {
                return y;
            }
        }
        return Integer.MAX_VALUE;
    }

    static boolean hangs(BlockState state) {
        return state.getBlock() instanceof net.minecraft.world.level.block.WallTorchBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallBannerBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.WallSignBlock
                || (state.getBlock() instanceof net.minecraft.world.level.block.LanternBlock && state.getValue(net.minecraft.world.level.block.LanternBlock.HANGING));
    }

    static boolean opening(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof IronBarsBlock
                || state.getBlock() instanceof TransparentBlock || state.is(BlockTags.IMPERMEABLE);
    }

    static boolean full(BlockState state) {
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !opening(state);
    }
}
