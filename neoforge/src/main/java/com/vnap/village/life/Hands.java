package com.vnap.village.life;

import com.vnap.mood.MoodConfig;
import com.vnap.village.build.BuildJob;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a {@link BuildJob} from where the builder stands: every block within twice REACH of it (any height under
 * it) is set from there, a block a swing, facing it; a block further off is walked towards, and a builder the walk does
 * not get there is put on the ground by it (no way is looked for: a build never waits on a path).
 */
public final class Hands {
    /**
     * How far across from a block's column a builder stands to set it. A builder stays where it is and sets every
     * block within this from there, a block a swing (it walked to each block and worked it for one to two seconds:
     * the largest plan, 982 blocks, took five to six working days); it moves only for a block out of it.
     */
    public static final double REACH = 8.0;
    /** How far below its feet (a cellar dug from the floor; stone thrown into water from the bank, Wall.dams). */
    public static final double SINK_REACH = 10.0;
    /** Across to a block in water or over it (a dam, a pier, a wall across a lake): set from the shore, nobody wades in. */
    public static final double WATER_REACH = 16.0;
    /** How far over its feet: a tower's roof is set from its floor (a bit of a stretch, as a demolisher pulls a beam down). */
    static final int UP = 32;
    static final int SWING_EVERY = 7;
    private static final int WALK_LIMIT = 200;
    private static final int STUCK_AFTER = 4;
    private static final int WAIT_LIMIT = 60;
    /**
     * Spots looked for in one path search, nearest first: fewer than 8 left the high parts of a hall with nowhere to
     * stand (the plans' test). One search to all of them, not one search each: a cell nobody can reach was eight
     * failed searches over the whole area, and a wrecker or a roadman going down a list of such cells took 30 ms a
     * tick each (41% of the server's time in a taiga town, 20.10.2026).
     */
    private static final int PATH_TRIES = 24;

    private enum Phase { PICK, WALK, WORK, FETCH }
    /** A supply trip buys what the next this many cells take. */
    static final int SUPPLY_CELLS = 96;
    private static final int FETCH_LIMIT = 600;

    private Phase phase = Phase.PICK;
    private @Nullable BlockPos target;
    /** The block of the job this builder took (BuildJob.claim: a schematic index), -1 none. */
    private int block = -1;
    private int timer;
    /** The nearest the builder has come to the counter on this trip: a long way that is still being walked does not time out. */
    private double fetchBest = Double.MAX_VALUE;
    private int lowest = Integer.MIN_VALUE;
    private int deferred;
    /** Swings made on the current block; tests read it to see the work happen before the block stands. */
    private int swings;
    /** The walk to this block got no nearer: it is set from where the builder stands. */
    private boolean fromHere;

    /** WAITING: the materials are not to be had now (the stall is shut, sold out until its morning delivery). */
    public enum Result { WORKING, PLACED, DONE, STUCK, WAITING }

    public Result tick(ServerLevel level, Villager villager, BuildJob job) {
        job.skipDone(level);
        if (job.done()) {
            job.release(villager);
            reset();
            return Result.DONE;
        }
        long now = level.getGameTime();
        if (phase == Phase.FETCH) {
            return fetch(level, villager, job);
        }
        int b = job.claim(villager, now);
        // every block near the head of the queue is someone's: this one waits its turn by the site
        if (b < 0) return Result.WORKING;
        if (job.lacks(b)) {
            // one builder goes to the stall; the others wait for what it brings
            return job.fetcher(villager, now) ? fetch(level, villager, job) : Result.WORKING;
        }
        BlockPos next = job.posOf(b);
        if (b != block || !next.equals(target)) {
            block = b;
            target = next;
            lowest = lowest(level, job, next);
            phase = Phase.PICK;
            timer = 0;
            swings = 0;
            fromHere = false;
        }
        return switch (phase) {
            case PICK -> pick(level, villager, job, next);
            case WALK -> walk(villager, next);
            case WORK -> work(level, villager, job, next);
            case FETCH -> fetch(level, villager, job);
        };
    }

    /**
     * A trip to the village stall for what the next stage of the build takes: walk to the counter, buy it (out of
     * the site's money), come back with it. Nothing to be had now (the stall shut, its stock sold out until the
     * morning's delivery, no way to the counter) means the build waits.
     */
    private Result fetch(ServerLevel level, Villager villager, BuildJob job) {
        com.vnap.village.market.Site site = job.site;
        com.vnap.village.market.Shops.Shop shop = com.vnap.village.market.Shops.of(level, villager);
        BlockPos counter = shop == null ? null : shop.counter;
        if (site == null || shop == null || counter == null || !com.vnap.village.market.Shops.open(level, shop)) {
            phase = Phase.PICK;
            job.fetched(villager);
            return Result.WAITING;
        }
        if (phase != Phase.FETCH) {
            phase = Phase.FETCH;
            timer = 0;
            fetchBest = Double.MAX_VALUE;
        }
        if (!villager.blockPosition().closerThan(counter, 3.0)) {
            // the limit is for a trip that gets nowhere: a stall a hundred blocks off took longer than it, every trip
            // was given up on the way, and a house stood a block a day short of cobblestone
            double distance = villager.blockPosition().distSqr(counter);
            if (distance < fetchBest - 4) {
                fetchBest = distance;
                timer = 0;
            }
            if (++timer > FETCH_LIMIT) {
                phase = Phase.PICK;
                job.fetched(villager);
                return Result.WAITING;
            }
            if (timer % 20 == 1) villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(counter, 0.6F, 2));
            return Result.WORKING;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        com.vnap.village.market.Market.supply(level, villager, shop, site, job.bill(level, SUPPLY_CELLS));
        job.fetched(villager);
        phase = Phase.PICK;
        timer = 0;
        if (job.lacksMaterials()) return Result.WAITING;
        WorkerControl.hold(villager, new ItemStack(job.nextState().getBlock().asItem()));
        return Result.WORKING;
    }

    public int swings() {
        return swings;
    }

    public void reset() {
        phase = Phase.PICK;
        target = null;
        timer = 0;
        swings = 0;
    }

    private Result pick(ServerLevel level, Villager villager, BuildJob job, BlockPos next) {
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, new AABB(next), LivingEntity::isAlive);
        inside.remove(villager);
        if (!inside.isEmpty() && inside.stream().allMatch(entity -> entity instanceof net.minecraft.world.entity.decoration.ArmorStand)) {
            // an armour stand never walks off: that block is given up, not waited for every day
            job.skip(villager, block);
            return Result.WORKING;
        }
        if (!inside.isEmpty()) {
            return ++timer > WAIT_LIMIT ? defer(job, villager, "occupied by " + inside.get(0).getType().toShortString() + " at " + inside.get(0).blockPosition().toShortString()) : Result.WORKING;
        }
        WorkerControl.hold(villager, new ItemStack(job.stateOf(block).getBlock().asItem()));
        if (near(villager, next)) {
            phase = Phase.WORK;
            timer = 0;
            return Result.WORKING;
        }
        // no way looked for: over towards the block, and put by it if the walk does not get there
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(next, 0.6F, (int) REACH));
        phase = Phase.WALK;
        timer = 0;
        return Result.WORKING;
    }

    private Result walk(Villager villager, BlockPos next) {
        if (near(villager, next) && villager.onGround()) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
            phase = Phase.WORK;
            timer = 0;
            return Result.WORKING;
        }
        if (++timer > WALK_LIMIT) {
            // as close as the walk got it: the block is set from there (no teleport into water or a pit)
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            fromHere = true;
            phase = Phase.WORK;
            timer = 0;
        }
        return Result.WORKING;
    }

    private Result work(ServerLevel level, Villager villager, BuildJob job, BlockPos next) {
        if (!fromHere && !near(villager, next)) {
            phase = Phase.PICK;
            return Result.WORKING;
        }
        BlockState state = job.stateOf(block);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(next));
        WorkerControl.hold(villager, new ItemStack(state.getBlock().asItem()));
        if (timer % SWING_EVERY == 0) {
            com.vnap.Mc.swing(villager, InteractionHand.MAIN_HAND);
            swings++;
            SoundType sound = state.getSoundType(level, next, villager);
            level.playSound(null, next, sound.getHitSound(), SoundSource.BLOCKS, sound.getVolume() * 0.5F, sound.getPitch());
            if (!state.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.02);
            }
        }
        if (++timer < workTicks(level, next, state)) {
            return Result.WORKING;
        }
        // someone stepped into the cell while it was being worked: a solid block there would bury them
        if (!state.getCollisionShape(level, next).isEmpty()
                && !level.getEntitiesOfClass(LivingEntity.class, new AABB(next), LivingEntity::isAlive).isEmpty()) {
            phase = Phase.PICK;
            timer = 0;
            return Result.WORKING;
        }
        job.place(level, villager, block);
        deferred = 0;
        phase = Phase.PICK;
        timer = 0;
        swings = 0;
        return Result.PLACED;
    }

    /** Why the last block was put off (debug: a stuck build says it). */
    public String why = "";

    private Result defer(BuildJob job, Villager villager, String why) {
        this.why = why + " (" + job.nextPos().toShortString() + ")";
        job.defer(villager, block);
        phase = Phase.PICK;
        timer = 0;
        // the last block or two of a build are put off by a passing body as easily as by no way there: a few tries
        // before the build waits a whole day
        if (++deferred >= Math.max(job.remaining(), STUCK_AFTER)) {
            deferred = 0;
            return Result.STUCK;
        }
        return Result.WORKING;
    }

    static int workTicks(ServerLevel level, BlockPos pos, BlockState state) {
        return SWING_EVERY;
    }

    /**
     * How low a builder of {@code target} may stand: just under the first firm block down its column (the house's floor,
     * the ground by a pond). Building from the ground is the build's own ground: a taiga village's shop house by a
     * ravine sent its builders to stand at the bottom, 30 blocks under the wall, where cave zombies killed them.
     */
    public static int groundUnder(ServerLevel level, BlockPos target) {
        BlockPos.MutableBlockPos pos = target.mutable();
        for (int y = target.getY() - 1; y >= target.getY() - UP; y--) {
            pos.setY(y);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return y - 2;
        }
        return target.getY() - UP;
    }

    /** How low a builder of {@code target} in {@code job} may stand: the ground under it, or the build's own foot (a roof from the floors below). */
    public static int lowest(ServerLevel level, BuildJob job, BlockPos target) {
        return Math.min(groundUnder(level, target), job.bottom() - 1);
    }

    /** In reach of this builder now: by the column, or for a block no spot was found for, anywhere a few steps under it (not below its ground). */
    private boolean near(Villager villager, BlockPos pos) {
        if (villager.getY() < lowest) return false;
        return covers(villager.position(), pos, Math.max(REACH * 2, reach(villager.level(), pos)));
    }

    /** How far across a builder reaches to {@code pos}: from the shore to a block in water or over it, else REACH. */
    public static double reach(net.minecraft.world.level.Level level, BlockPos pos) {
        for (int d = 0; d <= 2; d++) {
            if (!level.getFluidState(pos.below(d)).isEmpty()) return WATER_REACH;
        }
        return REACH;
    }

    public static boolean inReach(Villager villager, BlockPos pos) {
        return covers(villager.position(), pos, REACH);
    }

    /** A builder standing at {@code feet} works on {@code pos}: close across to its column, under it or a little over it. */
    public static boolean covers(Vec3 feet, BlockPos pos, double across) {
        double dx = pos.getX() + 0.5 - feet.x;
        double dz = pos.getZ() + 0.5 - feet.z;
        double dy = pos.getY() - feet.y;
        return dx * dx + dz * dz <= across * across && dy >= -SINK_REACH && dy <= UP;
    }

    /** The closest standable cell by {@code target}'s column that the villager can actually walk to. */
    public static @Nullable BlockPos findSpot(ServerLevel level, Villager villager, BuildJob job, BlockPos target) {
        return findSpot(level, villager, new HashSet<>(job.upcoming(8)), target, reach(level, target), lowest(level, job, target));
    }

    /** {@link #findSpot} with the cells to keep clear and how far across (a wrecker reaches further). */
    public static @Nullable BlockPos findSpot(ServerLevel level, Villager villager, Set<BlockPos> busy, BlockPos target, double reach, int lowest) {
        List<BlockPos> spots = new ArrayList<>();
        int side = (int) Math.floor(reach);
        // not below the ground under the block (groundUnder)
        int down = Math.min(UP, Math.max(0, target.getY() - lowest));
        // nor where another stands (a second builder by the same stretch): pushed off it, the walker never got there
        // and three such walks put the whole stretch off till tomorrow
        Set<BlockPos> stood = new HashSet<>();
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, new AABB(target).inflate(side + 1, SINK_REACH + 2, side + 1),
                other -> !other.equals(villager) && other.isAlive())) {
            stood.add(other.blockPosition());
        }
        for (BlockPos cursor : BlockPos.betweenClosed(target.offset(-side, -down, -side), target.offset(side, (int) SINK_REACH, side))) {
            BlockPos cell = cursor.immutable();
            if (busy.contains(cell) || busy.contains(cell.above()) || stood.contains(cell) || cell.equals(target) || cell.above().equals(target)
                    || !covers(Vec3.atBottomCenterOf(cell), target, reach - 0.3) || !standable(level, cell, villager)) {
                continue;
            }
            spots.add(cell);
        }
        if (spots.contains(villager.blockPosition())) {
            return villager.blockPosition();
        }
        // the builder's own level first: a column's tall search finds the tops of walls and floors no stair reaches yet,
        // and the nearest two dozen of those left no way to anything
        double feet = villager.getY();
        spots.sort(Comparator.comparingDouble(cell -> cell.distToCenterSqr(villager.position()) + 16.0 * Math.abs(cell.getY() - feet)));
        Path path = villager.getNavigation().createPath(new HashSet<>(spots.subList(0, Math.min(PATH_TRIES, spots.size()))), 0);
        return path != null && path.canReach() ? path.getTarget() : null;
    }

    public static boolean standable(ServerLevel level, BlockPos cell, Villager villager) {
        BlockPos below = cell.below();
        return level.isLoaded(cell)
                && floor(level, below, villager)
                && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                && level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                && level.getFluidState(cell).isEmpty();
    }

    /** A full top face, or a stair: anything whose collision reaches the top of the cell carries a walker. */
    private static boolean floor(ServerLevel level, BlockPos pos, Villager villager) {
        BlockState state = level.getBlockState(pos);
        if (state.entityCanStandOn(level, pos, villager)) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty() && Math.abs(shape.max(Direction.Axis.Y) - 1.0) < 1.0E-6;
    }
}
