package com.vnap.village.build;

import com.vnap.Nbt;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** A queue of schematic blocks placed one at a time in the world, in the schematic's own order. */
public final class BuildJob {
    public final String schematicName;
    public final SchematicSpec schematic;
    public final BlockPos origin;
    public final Rotation rotation;
    /** Schematic layer that may replace natural ground (a floor; a cellar's below it too), or {@link #NO_GROUND}. */
    public final int groundY;
    private final int[] order;
    private int cursor;
    private final List<BlockPos> placed = new ArrayList<>();
    /** Only what this job set itself, not what already stood: taking a build back must not touch anyone else's blocks. */
    private final List<BlockPos> built = new ArrayList<>();
    /**
     * The site's store the blocks come out of: a block is set only when its items are there (a builder fetches them
     * from the stall first, Hands). Null for what nobody pays for block by block: a debug command, a rite or a
     * furnishing set the treasury bought whole beforehand.
     */
    public com.vnap.village.market.@Nullable Site site;

    /**
     * Several builders on one job each take a block of their own from the next WINDOW in order (the one furthest from
     * the others' blocks, so they spread round the walls instead of running to the same cell in a heap); a claim not
     * renewed for CLAIM_TICKS lapses. Only one of them goes to the stall at a time (the fetcher). In memory only.
     */
    static final int WINDOW = 16;
    static final long CLAIM_TICKS = 400;
    private final java.util.Map<java.util.UUID, long[]> claims = new java.util.HashMap<>();
    private java.util.@Nullable UUID fetcher;
    private long fetcherSince;

    public static final int NO_GROUND = Integer.MIN_VALUE;
    /** Natural ground may be replaced on every layer: a road lies on the surface at whatever height it is. */
    public static final int ALL_GROUND = Integer.MAX_VALUE;

    private BuildJob(String schematicName, SchematicSpec schematic, BlockPos origin, Rotation rotation, int groundY, int[] order) {
        this.schematicName = schematicName;
        this.schematic = schematic;
        this.origin = origin.immutable();
        this.rotation = rotation;
        this.groundY = groundY;
        this.order = order;
    }

    /** The lowest block of the build (its foundation or cellar floor). */
    public int bottom() {
        int lowest = Integer.MAX_VALUE;
        for (SchematicSpec.PlacedBlock block : schematic.blocks) lowest = Math.min(lowest, block.rel().getY());
        return lowest == Integer.MAX_VALUE ? origin.getY() : origin.getY() + lowest;
    }

    /** Every block of the schematic. */
    public static BuildJob all(String name, SchematicSpec schematic, BlockPos origin, Rotation rotation, int groundY) {
        int[] order = new int[schematic.blocks.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        return new BuildJob(name, schematic, origin, rotation, groundY, order);
    }

    /** Only the blocks that are missing and whose cell is free to take, in {@link BuildStages} order. */
    public static BuildJob missing(ServerLevel level, String name, SchematicSpec schematic, BlockPos origin, Rotation rotation,
                                   int groundY) {
        return missing(level, name, schematic, origin, rotation, groundY, block -> false);
    }

    /** {@link #missing}, leaving out the plan's blocks {@code skip} picks (a house's mending: what its extension carved). */
    public static BuildJob missing(ServerLevel level, String name, SchematicSpec schematic, BlockPos origin, Rotation rotation,
                                   int groundY, java.util.function.Predicate<SchematicSpec.PlacedBlock> skip) {
        int[] order = new int[schematic.blocks.size()];
        int n = 0;
        for (int i = 0; i < schematic.blocks.size(); i++) {
            SchematicSpec.PlacedBlock block = schematic.blocks.get(i);
            if (skip.test(block)) continue;
            BlockPos pos = at(origin, rotation, block.rel());
            BlockState current = level.getBlockState(pos);
            boolean wanted = block.state().isAir() ? carvable(current)
                    : !current.is(block.state().getBlock()) && canTake(current, block.rel().getY() <= groundY);
            if (wanted) {
                order[n++] = i;
            }
        }
        return new BuildJob(name, schematic, origin, rotation, groundY,
                BuildStages.sort(schematic, groundY, java.util.Arrays.copyOf(order, n)));
    }

    public static BlockPos at(BlockPos origin, Rotation rotation, BlockPos rel) {
        return origin.offset(rel.rotate(rotation));
    }

    public static boolean stands(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() ? !carvable(level.getBlockState(pos)) : level.getBlockState(pos).is(state.getBlock());
    }

    /** Air, grass and the like are always free; natural ground only on the floor layer. */
    public static boolean canTake(BlockState current, boolean groundLayer) {
        return current.isAir() || current.canBeReplaced() || (groundLayer && naturalGround(current));
    }

    /**
     * An air entry in a schematic opens a wall (an extension's doorway): any plain block goes, never a bed, a door
     * or anything with a block entity.
     */
    public static boolean carvable(BlockState current) {
        return !current.isAir() && !(current.getBlock() instanceof BedBlock) && !(current.getBlock() instanceof DoorBlock)
                && !current.hasBlockEntity();
    }

    public static boolean naturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY);
    }

    public int size() {
        return order.length;
    }

    public int cursor() {
        return cursor;
    }

    public boolean done() {
        return cursor >= order.length;
    }

    public float progress() {
        return order.length == 0 ? 1.0F : Math.min(1.0F, cursor / (float) order.length);
    }

    public List<BlockPos> built() {
        return built;
    }

    public List<BlockPos> placed() {
        return placed;
    }

    public BlockPos nextPos() {
        return done() ? origin : at(origin, rotation, schematic.blocks.get(order[cursor]).rel());
    }

    public BlockState stateOf(ServerLevel level, BlockPos pos, SchematicSpec.PlacedBlock block) {
        return block.state().rotate(level, pos, rotation);
    }

    /** Steps over blocks that already stand, for example after a reload or a helpful player. */
    public void skipDone(ServerLevel level) {
        while (!done()) {
            SchematicSpec.PlacedBlock block = schematic.blocks.get(order[cursor]);
            BlockPos pos = at(origin, rotation, block.rel());
            if (!stands(level, pos, block.state())) {
                return;
            }
            placed.add(pos);
            cursor++;
        }
    }

    /**
     * Sets every block now, quietly (a debug command, a new hamlet whose kit the village bought): a block whose items
     * are not on the site is left out rather than waited for, so this never hangs.
     */
    public void completeNow(ServerLevel level) {
        while (!done()) {
            skipDone(level);
            if (done()) return;
            if (lacksMaterials()) skip();
            else placeNext(level, null);
        }
    }

    /** Where block {@code b} (its index in the schematic) still waits in the queue, or -1 when it was set or passed. */
    private int queued(int b) {
        for (int i = cursor; i < order.length; i++) if (order[i] == b) return i;
        return -1;
    }

    /**
     * The block this builder works on (a schematic index): its claim while it stands, else the free one of the next
     * WINDOW furthest from the others' claims (the earliest of equals). -1 when the job is done.
     */
    public int claim(Villager builder, long now) {
        claims.entrySet().removeIf(entry -> now - entry.getValue()[1] > CLAIM_TICKS || queued((int) entry.getValue()[0]) < 0);
        long[] mine = claims.get(builder.getUUID());
        if (mine != null) {
            mine[1] = now;
            return (int) mine[0];
        }
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = cursor; i < Math.min(order.length, cursor + WINDOW); i++) {
            int b = order[i];
            boolean taken = false;
            double nearest = 64.0;
            BlockPos pos = at(origin, rotation, schematic.blocks.get(b).rel());
            for (long[] other : claims.values()) {
                if (other[0] == b) taken = true;
                nearest = Math.min(nearest, Math.sqrt(pos.distSqr(at(origin, rotation, schematic.blocks.get((int) other[0]).rel()))));
            }
            if (taken) continue;
            double score = nearest - (i - cursor) * 0.05;
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        if (best >= 0) claims.put(builder.getUUID(), new long[]{best, now});
        return best;
    }

    public void release(Villager builder) {
        claims.remove(builder.getUUID());
        if (builder.getUUID().equals(fetcher)) fetcher = null;
    }

    public BlockPos posOf(int b) {
        return at(origin, rotation, schematic.blocks.get(b).rel());
    }

    public BlockState stateOf(int b) {
        return schematic.blocks.get(b).state();
    }

    /** The items of block {@code b} are not on the site yet. */
    public boolean lacks(int b) {
        com.vnap.village.market.Site store = site;
        return store != null && !store.has(com.vnap.village.market.Materials.items(stateOf(b)));
    }

    /** Whether this builder is the one who goes to the stall now (the first to ask; another once it is done or lapsed). */
    public boolean fetcher(Villager builder, long now) {
        java.util.UUID current = fetcher;
        if (current == null || current.equals(builder.getUUID()) || now - fetcherSince > CLAIM_TICKS * 3) {
            if (!builder.getUUID().equals(current)) fetcherSince = now;
            fetcher = builder.getUUID();
            return true;
        }
        return false;
    }

    public void fetched(Villager builder) {
        if (builder.getUUID().equals(fetcher)) fetcher = null;
    }

    /** Brings block {@code b} to the head of the queue: the next placeNext, skip or defer is about it. */
    private boolean front(int b) {
        int i = queued(b);
        if (i < 0) return false;
        order[i] = order[cursor];
        order[cursor] = b;
        return true;
    }

    /** Places the claimed block {@code b} (placeNext for it) and lets the claim go. */
    public @Nullable BlockPos place(ServerLevel level, Villager builder, int b) {
        claims.remove(builder.getUUID());
        return front(b) ? placeNext(level, builder) : null;
    }

    /** Gives the claimed block up for now (to the queue's end) or for good. */
    public void defer(Villager builder, int b) {
        claims.remove(builder.getUUID());
        if (front(b)) defer();
    }

    public void skip(Villager builder, int b) {
        claims.remove(builder.getUUID());
        if (front(b)) skip();
    }

    /** Gives up on the next block, for example when something lives in its cell. */
    public void skip() {
        if (!done()) {
            cursor++;
        }
    }

    /** Moves the next block to the end of the queue, for example when no builder can reach it yet. */
    public void defer() {
        if (cursor < order.length - 1) {
            int next = order[cursor];
            System.arraycopy(order, cursor + 1, order, cursor, order.length - cursor - 1);
            order[order.length - 1] = next;
        }
    }

    public int remaining() {
        return order.length - cursor;
    }

    /** Where the next {@code count} blocks go, so a builder does not stand in them. */
    public List<BlockPos> upcoming(int count) {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = cursor; i < Math.min(order.length, cursor + count); i++) {
            cells.add(at(origin, rotation, schematic.blocks.get(order[i]).rel()));
        }
        return cells;
    }

    public BlockState nextState() {
        return done() ? Blocks.AIR.defaultBlockState() : schematic.blocks.get(order[cursor]).state();
    }

    /** The next block's items are not on the site yet: someone has to fetch them. */
    public boolean lacksMaterials() {
        com.vnap.village.market.Site store = site;
        return store != null && !done() && !store.has(com.vnap.village.market.Materials.items(nextState()));
    }

    /** The items the next {@code limit} cells still to set take (cells that stand already cost nothing). */
    public java.util.Map<net.minecraft.world.item.Item, Integer> bill(ServerLevel level, int limit) {
        java.util.Map<net.minecraft.world.item.Item, Integer> out = new java.util.LinkedHashMap<>();
        for (int i = cursor, n = 0; i < order.length && n < limit; i++, n++) {
            SchematicSpec.PlacedBlock block = schematic.blocks.get(order[i]);
            if (stands(level, at(origin, rotation, block.rel()), block.state())) continue;
            com.vnap.village.market.Materials.add(out, com.vnap.village.market.Materials.items(block.state()));
        }
        return out;
    }

    /**
     * Places the next block with sound and dust; the worker, if any, swings it into place. A cell that
     * something else took meanwhile is left alone and null is returned.
     */
    public @Nullable BlockPos placeNext(ServerLevel level, @Nullable Villager worker) {
        if (done()) {
            return null;
        }
        java.util.Map<net.minecraft.world.item.Item, Integer> needs = com.vnap.village.market.Materials.items(schematic.blocks.get(order[cursor]).state());
        com.vnap.village.market.Site store = site;
        if (store != null && !store.has(needs)) {
            // not on the site: the block waits (the cursor stays) until a builder brings it
            return null;
        }
        SchematicSpec.PlacedBlock block = schematic.blocks.get(order[cursor++]);
        BlockPos pos = at(origin, rotation, block.rel());
        BlockState current = level.getBlockState(pos);
        if (block.state().isAir()) {
            if (!carvable(current)) {
                return null;
            }
            level.destroyBlock(pos, false, worker);
            placed.add(pos);
            if (worker != null) {
                WorkerControl.forceIdleLook(worker, pos);
                com.vnap.Mc.swing(worker, InteractionHand.MAIN_HAND);
            }
            return pos;
        }
        if (!canTake(current, block.rel().getY() <= groundY)) {
            return null;
        }
        BlockState rotated = stateOf(level, pos, block);
        BlockPos partner = partner(pos, rotated);
        // half a door or half a bed pops off on the next block update: both halves or neither
        if (partner != null && !canTake(level.getBlockState(partner), false)) {
            return null;
        }
        BlockState target = partner != null ? rotated : Block.updateFromNeighbourShapes(rotated, level, pos);
        if (!current.isAir() && current.canBeReplaced()) {
            level.destroyBlock(pos, false);
        }
        if (store != null) store.take(needs);
        level.setBlock(pos, target, Block.UPDATE_ALL);
        placed.add(pos);
        built.add(pos);
        if (partner != null) {
            level.setBlock(partner, partnerState(rotated), Block.UPDATE_ALL);
            placed.add(partner);
            built.add(partner);
        }
        if (worker == null) {
            // a whole house set at once (a new hamlet, a debug command): hundreds of place sounds in one tick fill
            // the client's sound pool and silence everything else, voices included
            return pos;
        }
        SoundType sound = target.getSoundType(level, pos, worker);
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
        if (!target.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, target),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.05);
        }
        level.gameEvent(worker, GameEvent.BLOCK_PLACE, pos);
        WorkerControl.forceIdleLook(worker, pos);
        WorkerControl.hold(worker, new ItemStack(target.getBlock().asItem()));
        com.vnap.Mc.swing(worker, InteractionHand.MAIN_HAND);
        return pos;
    }

    /** The other half of a door or bed: both halves go in together, a lone half breaks on its next update. */
    static @Nullable BlockPos partner(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        if (state.getBlock() instanceof BedBlock) {
            Direction facing = state.getValue(BedBlock.FACING);
            return state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
        }
        return null;
    }

    static BlockState partnerState(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            return state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        }
        BedPart part = state.getValue(BedBlock.PART);
        return state.setValue(BedBlock.PART, part == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Schematic", schematicName);
        tag.putLong("Origin", origin.asLong());
        tag.putString("Rotation", rotation.getSerializedName());
        tag.putInt("GroundY", groundY);
        tag.putIntArray("Order", order);
        tag.putInt("Cursor", cursor);
        return tag;
    }

    public static @Nullable BuildJob load(CompoundTag tag, Function<String, SchematicSpec> schematics) {
        if (!tag.contains("Schematic")) {
            return null;
        }
        String name = Nbt.getString(tag, "Schematic");
        SchematicSpec schematic = schematics.apply(name);
        int[] order = Nbt.getIntArray(tag, "Order");
        for (int index : order) {
            if (index < 0 || index >= schematic.blocks.size()) {
                return null;
            }
        }
        Rotation rotation = Rotation.NONE;
        for (Rotation candidate : Rotation.values()) {
            if (candidate.getSerializedName().equals(Nbt.getString(tag, "Rotation"))) {
                rotation = candidate;
            }
        }
        BuildJob job = new BuildJob(name, schematic, BlockPos.of(Nbt.getLong(tag, "Origin")), rotation, Nbt.getInt(tag, "GroundY"), order);
        job.cursor = Math.min(Nbt.getInt(tag, "Cursor"), order.length);
        return job;
    }
}
