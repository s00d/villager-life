package com.vnap.village.home;

import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyHome;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodApi;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.progress.VillageAdvancements;
import com.vnap.village.build.BuildJob;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A family that needs more room grows its house. A house the villagers built gets one extension room on its east
 * side, built by hand by the head, with a child's bed in it; the youngest child moves in. A family in a house of
 * the world grows on where it stands when its template has a plan (HouseLevels.adopt); one with none is taken down and a
 * house of the villagers' own built there (or elsewhere), and the family moves in when it stands.
 */
public final class FamilyRooms {
    private FamilyRooms() {
    }

    /** The finished house of the villagers' own making this head lives in, or null. */
    public static @Nullable HouseProject houseOf(ServerLevel level, Villager head) {
        BlockPos anchor = head instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
        if (anchor == null) return null;
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (!house.done) continue;
            BlockPos bed = house.bed(level);
            if (bed != null && VillagerHome.sameBed(level, anchor, bed)) return house;
        }
        return null;
    }

    /** A married head whose house is full, or who has children and no room for them yet. */
    public static boolean wantsRoom(ServerLevel level, Villager head) {
        if (!Family.isHead(head) || !Family.state(head).married()) return false;
        BlockPos anchor = Family.home(head);
        if (anchor == null) return false;
        HouseProject house = houseOf(level, head);
        boolean full = !FamilyHome.roomForChild(level, anchor);
        if (house == null) return full;
        // a house with levels grows a storey while it has fewer beds than the family and one more for a child
        if (HouseLevels.next(level, house.plan) != null && !house.hasRoom()) {
            return HouseLevels.beds(level, house) < Family.household(level, head).size() + 1;
        }
        return !house.hasRoom() && (full || Family.state(head).childrenBorn > 0);
    }

    /** Why the last {@link #start} did not begin (/vnap family info). */
    public static String lastRefusal = "";

    /** Starts the extension (or, for a house of the world, a new family house); true when building can begin. */
    public static boolean start(ServerLevel level, Villager head, long day) {
        lastRefusal = "";
        HouseProject house = houseOf(level, head);
        if (house == null) {
            for (HouseProject other : VillageMoodSavedData.get(level).houses()) {
                if (!other.done && other.owner.equals(head.getUUID())) return refuse("building one already");
            }
            // a house of the world too small: taken down by hand and a house of the villagers' own (with levels to
            // grow into) built on its ground, as the mayor does for his hall; when it cannot be, a new one elsewhere
            BlockPos bed = head instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
            HomeProjects.WorldPiece piece = bed == null ? null : HomeProjects.worldPiece(level, bed);
            // a small house of the world with a plan of its own grows where it stands, like a house of the villagers'
            if (piece != null && HomeProjects.canGrow(level, piece.box(), head)) {
                HouseProject own = HouseLevels.adopt(level, head, piece, day);
                if (own != null && HouseLevels.upgrade(level, head, own, day) != null) return true;
                // a house with a plan of its own waits to grow (next time there is room, money): it is not pulled down
                if (own != null) return refuse("world house " + piece.template() + ": " + HouseLevels.refusal);
            }
            BoundingBox world = piece == null ? null : piece.box();
            if (world != null && HomeProjects.canClear(level, world, head)) {
                int floor = Mayors.floorOf(level, world);
                HouseProject rebuilt = HomeProjects.planAt(level, head, day, HomeProjects.PLANS, Mayors.centreOf(world, floor), null, world);
                if (rebuilt != null) {
                    HomeProjects.clearForHall(level, rebuilt, world, floor);
                    if (!HomeProjects.register(level, rebuilt)) return refuse("cannot pay to rebuild");
                    Family.leaveForNewHouse(level, head);
                    Speech.queue(head, "Man Starts a Room", null, 2L);
                    return true;
                }
            }
            return HomeProjects.start(level, head, day) != null || refuse(lastRefusal + " no spot for a new house (world piece " + (world == null ? "none" : "kept") + ")");
        }
        if (house.hasRoom()) return refuse("has its room");
        // the next level (a storey, a cellar) first; a plan without one grows a room on its side
        if (HouseLevels.next(level, house.plan) != null) return HouseLevels.upgrade(level, head, house, day) != null || refuse("next level: " + HouseLevels.refusal);
        SchematicSpec room = house.roomSchematic(level);
        // a plan without an extension (the hall, the shop house) does not grow one; any other house at its last level
        // (a house of the world grown to its cellar) is left for a new one, as a cramped house of the world is: it
        // asked for a room every day and was refused, and that family never had another child
        if (room.blocks.isEmpty()) {
            if (house.base()) return refuse("no room plan");
            for (HouseProject other : VillageMoodSavedData.get(level).houses()) {
                if (!other.done && other.owner.equals(head.getUUID())) return refuse("building one already");
            }
            return HomeProjects.start(level, head, day) != null || refuse(lastRefusal + " no spot for a new house (at its last level)");
        }
        com.vnap.village.town.Land others = com.vnap.village.town.Land.around(level, house.origin).without(claim -> claim.box().equals(house.footprint(level))
                || (claim.layer() == com.vnap.village.town.Land.Layer.LOT && claim.box().isInside(house.footprint(level).getCenter())));
        if (!Workshops.fits(level, room, house.origin, house.rotation, others)) return refuse("the room does not fit");
        house.roomJob = BuildJob.missing(level, house.roomName(), room, house.origin, house.rotation, 0);
        if (!com.vnap.village.market.Market.finance(level, head, house.bell, house.site, house.roomJob.bill(level, Integer.MAX_VALUE),
                com.vnap.village.market.Housing.Purpose.HOME)) {
            house.roomJob = null;
            return refuse("cannot pay for a room");
        }
        VillageMoodSavedData.get(level).markDirty();
        Speech.queue(head, "Man Starts a Room", null, 2L);
        return true;
    }

    private static boolean refuse(String why) {
        lastRefusal = why;
        return false;
    }

    /** A player set a block the room still needed: the builder counts it and says thank you (twice a minute at most). */
    public static void onPlayerPlaced(ServerLevel level, BlockPos pos, Player player) {
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (!house.done || !house.origin.closerThan(pos, 24.0)) continue;
            BuildJob job = house.roomJob(level);
            if (job == null || !job.upcoming(job.remaining()).contains(pos)) continue;
            if (level.getEntity(house.owner) instanceof Villager owner && owner.isAlive()
                    && Family.once(owner, "help", level.getGameTime() / 600L)) {
                Speech.queue(owner, "Man Thanks for the Help", player, 1L);
                VillageMoodApi.applyAt(level, pos, 1.0F, 0.0F, "build_help");
            }
            return;
        }
    }

    /** The room stands: the youngest child takes its bed, the family is pleased and so is the village. */
    public static void finish(ServerLevel level, HouseProject house, Villager head) {
        house.rooms = 1;
        house.roomJob = null;
        com.vnap.village.market.Market.settle(level, house.site, head, house.bell);
        VillageMoodSavedData.get(level).markDirty();
        BlockPos bed = house.roomBed(level);
        // the room's bed is left out when a player's block stood in its place
        if (bed != null && level.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
            Family.household(level, head).stream()
                    .filter(Villager::isBaby)
                    .min(Comparator.comparingInt(Villager::getAge))
                    .ifPresent(kid -> {
                        Family.giveBed(level, kid, bed);
                        Speech.queue(kid, "Kid Gets a Room", head, 40L);
                    });
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, head.getBoundingBox().inflate(32.0))) {
            VillageAdvancements.award(player, "family/room");
        }
        Speech.queue(head, "Man Finishes the Room", null, 2L);
    }
}
