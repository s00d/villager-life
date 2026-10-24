package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyHome;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.FamilyRooms;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Pets;
import com.vnap.village.market.Market;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * The head of a family keeps the house fit for it, by hand and in daylight: a bed for a spouse or a child who has
 * none (carried in and set down beside the head's own), and an extension room when the family grows.
 */
final class FamilyBuildBehavior extends Behavior<Villager> {
    private static final int CHECK_EVERY = 100;
    private static final int BED_WORK = 120;
    private static final int KENNEL_WORK = 200;
    static final int FURNISH_WORK = 60;

    private final LifeGate gate;
    private final Hands hands = new Hands();
    private final ErrandRunner runner = new ErrandRunner();
    private @Nullable HouseProject room;
    private @Nullable Errand errand;
    private boolean finished;
    private boolean begun;
    private long nextCheck;
    private long plannedDay = -1L;
    private long stuckDay = -1L;

    FamilyBuildBehavior(LifeGate gate) {
        super(ImmutableMap.of(), 1200, 2400);
        this.gate = gate;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
        nextCheck = now + CHECK_EVERY;
        long day = Family.day(level);
        if (villager.isBaby() || villager.isSleeping() || !com.vnap.Mc.isDay(level) || villager.isNoAi() || stuckDay == day
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG) || !Family.isHead(villager)
                || !Family.homeNear(level, villager, Family.home(villager))) {
            return false;
        }
        room = null;
        errand = null;
        HouseProject house = FamilyRooms.houseOf(level, villager);
        if (house != null && house.roomJob(level) != null) {
            room = house;
            return true;
        }
        // the mayor furnishes the office upstairs in his hall, piece by piece
        HouseProject hall = Mayors.isMayor(villager) ? Mayors.hall(level, villager) : null;
        BuildJob office = hall == null ? null : Mayors.furnishing(level, hall, Mayors.OFFICE);
        if (office != null) {
            errand = furnish(level, villager, office, "Mayor Furnishes the Office");
            return true;
        }
        BlockPos old = Pets.nextTakeUp(level, villager);
        if (old != null) {
            // the pet's things at the house the family left: fetched before a new kennel is knocked together
            errand = Errand.at("take_up", old, new ItemStack(net.minecraft.world.item.Items.IRON_AXE), KENNEL_WORK, SoundEvents.WOOD_HIT, null,
                    () -> Pets.takeUp(level, villager, old));
            return true;
        }
        if (Pets.wantsKennel(level, villager)) {
            BlockPos home = Family.home(villager);
            // the yard is looked for (a look at the whole town's ground) only with the kennel in hand
            BlockPos spot = home == null || !Market.obtain(level, villager, Market.decor("dog_kennel")) ? null : Pets.kennelSpot(level, villager, home);
            if (spot != null) {
                errand = kennel(level, villager, spot);
                return true;
            }
        }
        // the things from the house taken down for this one: carried into its chest (a new one bought if it has none)
        HouseProject moved = FamilyRooms.houseOf(level, villager);
        if (moved != null && !moved.stash.isEmpty()) {
            BlockPos chest = chestFor(level, villager, moved);
            if (chest != null) {
                errand = Errand.at("move_things", chest, moved.stash.get(0).copyWithCount(1), FURNISH_WORK, SoundEvents.CHEST_CLOSE, null,
                        () -> com.vnap.village.home.HomeProjects.unpack(level, moved));
                return true;
            }
        }
        Villager member = Family.needsBed(level, villager);
        BlockPos anchor = Family.home(villager);
        if (member != null && anchor != null) {
            boolean spouse = member.getUUID().equals(Family.state(villager).spouse);
            BlockPos[] spot = FamilyHome.bedSpot(level, anchor, spouse);
            if (spot != null && Market.obtain(level, villager, Items.WHITE_BED)) {
                errand = bed(level, villager, member, spot[0], spot[1], anchor, spouse);
                return true;
            }
        }
        Villager wife = Family.state(villager).spouse != null && level.getEntity(Family.state(villager).spouse) instanceof Villager found ? found : null;
        if (wife != null && anchor != null && Family.NO_BABY_BED.equals(Family.whyNoChild(level, villager, wife))) {
            BlockPos[] spot = FamilyHome.bedSpot(level, anchor, false);
            if (spot != null && Market.obtain(level, villager, Items.WHITE_BED)) {
                errand = nursery(level, villager, spot[0], spot[1]);
                return true;
            }
        }
        if (plannedDay != day && FamilyRooms.wantsRoom(level, villager)) {
            plannedDay = day;
            FamilyRooms.start(level, villager, day);
        }
        return false;
    }

    /**
     * A container in the house with room, or a chest set down in a free spot of the family's room (bought at the
     * stall: none to buy, none set); null when there is neither.
     */
    static @Nullable BlockPos chestFor(ServerLevel level, Villager head, HouseProject house) {
        net.minecraft.world.level.levelgen.structure.BoundingBox box = house.footprint(level);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container
                    && !(level.getBlockEntity(pos) instanceof com.vnap.block.decor.DecorBlockEntity) && hasRoom(container)) return pos.immutable();
        }
        BlockPos anchor = Family.home(head);
        if (anchor == null) return null;
        java.util.List<BlockPos> spots = FamilyHome.decorSpots(level, anchor);
        if (spots.isEmpty()) {
            // every chest full and no floor for another: the things go to the town's lost and found (by its board),
            // not into the save for good with a chest wanted every day that can never be set down
            com.vnap.village.town.NoticeBoard.lose(level, house.bell, java.util.List.copyOf(house.stash));
            house.stash.clear();
            com.vnap.mood.VillageMoodSavedData.get(level).markDirty();
            return null;
        }
        if (!Market.obtain(level, head, Items.CHEST)) return null;
        java.util.Set<BlockPos> room = FamilyHome.room(level, anchor);
        for (BlockPos cell : spots) {
            net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                if (room.contains(cell.relative(side)) && level.getBlockState(cell.relative(side)).isAir()) facing = side;
            }
            level.setBlock(cell, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing), 3);
            Market.spend(head, Items.CHEST);
            return cell.immutable();
        }
        return null;
    }

    private static boolean hasRoom(net.minecraft.world.Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (container.getItem(slot).isEmpty()) return true;
        return false;
    }

    /** One piece of a hall's furnishing carried in and set down where the set has it; a line on the first of the day. */
    static Errand furnish(ServerLevel level, Villager villager, BuildJob job, String title) {
        BlockPos at = job.nextPos();
        ItemStack carried = new ItemStack(job.nextState().getBlock().asItem());
        return Errand.at("furnish", at, carried, FURNISH_WORK, SoundEvents.WOOD_PLACE, null, () -> {
            if (job.placeNext(level, villager) != null) VillageMoodSavedData.get(level).markDirty();
        }).onStart(() -> {
            if (Family.once(villager, "furnish", Family.day(level))) Speech.queue(villager, title, null, 1L);
        });
    }

    /** The dog's kennel: carried out to the yard and knocked together there, then the post and the bowl. */
    private static Errand kennel(ServerLevel level, Villager head, BlockPos spot) {
        ItemStack carried = new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS);
        return Errand.at("kennel", spot, carried, KENNEL_WORK, SoundEvents.WOOD_HIT, null, () -> {
                    Pets.placeKennel(level, head, spot);
                    Market.spend(head, Market.decor("dog_kennel"));
                })
                .onStart(() -> Speech.queue(head, "Man Builds a Kennel", null, 1L));
    }

    /** A bed for the baby the couple hopes for, set down before it is born. */
    private static Errand nursery(ServerLevel level, Villager head, BlockPos foot, BlockPos headCell) {
        return Errand.at("nursery_bed", foot, new ItemStack(Blocks.WHITE_BED), BED_WORK, SoundEvents.WOOD_PLACE, null, () -> {
            if (Family.makeNursery(level, head, foot, headCell)) Market.spend(head, Items.WHITE_BED);
        }).onStart(() -> Speech.queue(head, "Man Carries a Bed", null, 1L));
    }

    private static Errand bed(ServerLevel level, Villager head, Villager member, BlockPos foot, BlockPos headCell, BlockPos anchor,
                              boolean spouse) {
        ItemStack carried = new ItemStack(level.getBlockState(anchor).getBlock() instanceof BedBlock own ? own : Blocks.WHITE_BED);
        return Errand.at("family_bed", foot, carried, BED_WORK, SoundEvents.WOOD_PLACE, null, () -> {
            if (!Family.buildBed(level, head, member, foot, headCell)) return;
            Market.spend(head, Items.WHITE_BED);
            Speech.queue(head, "Man Builds a Bed", member, 2L);
            if (spouse) Speech.queue(member, "Woman Thanks for the Bed", head, 6L);
        }).onStart(() -> Speech.queue(head, "Man Carries a Bed", member, 1L));
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        finished = false;
        begun = false;
        hands.reset();
        runner.reset();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        HouseProject house = room;
        if (house != null) {
            BuildJob job = house.roomJob(level);
            if (job == null) {
                finished = true;
                return;
            }
            switch (hands.tick(level, villager, job)) {
                case PLACED -> VillageMoodSavedData.get(level).markDirty();
                case DONE -> {
                    FamilyRooms.finish(level, house, villager);
                    finished = true;
                }
                case STUCK -> {
                    stuckDay = Family.day(level);
                    finished = true;
                }
                case WAITING -> finished = true;
                default -> {
                }
            }
            return;
        }
        Errand now = errand;
        if (now == null) {
            finished = true;
            return;
        }
        if (!begun) {
            begun = true;
            if (now.start != null) now.start.run();
        }
        switch (runner.tick(level, villager, now)) {
            case DONE, FAILED -> finished = true;
            default -> {
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !com.vnap.dialogue.Infants.minding(level, villager) && !finished && villager.isAlive() && !villager.isSleeping() && com.vnap.Mc.isDay(level)
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                && !com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = false;
        hands.reset();
        runner.reset();
        room = null;
        errand = null;
        WorkerControl.hold(villager, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextCheck = gameTime + CHECK_EVERY;
    }
}
