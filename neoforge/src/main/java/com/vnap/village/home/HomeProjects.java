package com.vnap.village.home;

import com.vnap.Nbt;
import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;

import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.MoodConfig;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * A villager without a bed for a few days, in a village without a free one, may start building its own house
 * near the bell, a 10% chance a day; several houses may go up side by side (their plots never overlap). The owner builds it, villagers without
 * a trade help, and on the last block the owner moves in.
 */
public final class HomeProjects {
    public static final List<String> PLANS = List.of("cottage", "bay_house", "loft", "tower", "longhouse", "manor", "gable_bay", "timber_house", "stone_house", "town_house", "farmstead");
    public static final String HOMELESS_SINCE = "vnap_homeless_since";
    static final String CONSIDERED = "vnap_home_considered";

    private HomeProjects() {
    }

    /**
     * Once a day for every villager: counts homeless days; a homeless grown-up of a town buys a home or begins one
     * ({@link com.vnap.village.market.Housing#daily}). Nobody is given one: a bed it sleeps in meanwhile is lodging.
     */
    public static void consider(ServerLevel level, Villager villager, long day) {
        var tag = villager.getPersistentData();
        if (tag.contains(CONSIDERED) && Nbt.getLong(tag, CONSIDERED) == day) {
            return;
        }
        tag.putLong(CONSIDERED, day);
        if (hasHome(villager)) {
            tag.remove(HOMELESS_SINCE);
            return;
        }
        if (!tag.contains(HOMELESS_SINCE)) {
            tag.putLong(HOMELESS_SINCE, day);
        }
        BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
        if (bell != null && com.vnap.village.market.Housing.buyer(villager)) {
            com.vnap.village.market.Housing.daily(level, villager, bell, day, Nbt.getLong(tag, HOMELESS_SINCE));
        }
    }

    /** The village's beds nobody holds: where the homeless lodge until they buy a home (Shelter). */
    public static long freeBeds(ServerLevel level, BlockPos bell, java.util.List<Villager> homeless) {
        if (homeless.isEmpty()) return 0;
        return level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), bell, (int) Towns.reach(level, bell), PoiManager.Occupancy.HAS_SPACE).count();
    }

    /** A bed in the village nobody holds, nearest first, or null. */
    public static @Nullable BlockPos freeBed(ServerLevel level, Villager villager, BlockPos bell) {
        return level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), bell, (int) Towns.reach(level, bell), PoiManager.Occupancy.HAS_SPACE)
                .map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos)
                .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(villager.blockPosition()))).orElse(null);
    }

    /** A home of its own the mod knows (a vanilla HOME may be a guest's spare bed: a keeper sleeping in its own stall's
     * second bed was never moved in, and a homeless one took its bed). */
    public static boolean claimed(Villager villager) {
        return villager instanceof VillagerLifeData data && data.vnap$homeBed() != null;
    }

    /** A home of its own (bought, built, a founder's) or its household's: a bed slept in without one is lodging. */
    public static boolean hasHome(Villager villager) {
        return claimed(villager) || Family.home(villager) != null;
    }

    /** Why this villager may not start a house today, or null when it may. */
    public static @Nullable String blocker(ServerLevel level, Villager villager, long day) {
        if (villager.isBaby()) return "baby";
        if (Hamlets.leaving(villager)) return "leaving for a hamlet";
        if (hasHome(villager)) return "home";
        BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
        if (bell == null) return "no bell";
        var tag = villager.getPersistentData();
        if (!tag.contains(HOMELESS_SINCE) || day - Nbt.getLong(tag, HOMELESS_SINCE) < TownRules.HOMELESS_DAYS) return "not long homeless";
        // houses go up side by side (their plots never overlap: taken()); one of its own at a time
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (!house.done && house.owner.equals(villager.getUUID())) return "building its own house";
        }
        // a full village grows no more: its homeless young leave to found a hamlet (Hamlets)
        if (Towns.full(level, bell)) return "the village is full";
        return null;
    }

    public static @Nullable HouseProject active(ServerLevel level, BlockPos bell) {
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (!house.done && house.village == bell.asLong()) return house;
        }
        return null;
    }

    /** Plans a site and registers the project; null when nothing fits around the bell. */
    public static @Nullable HouseProject start(ServerLevel level, Villager villager, long day) {
        return start(level, villager, day, PLANS);
    }

    /** {@code start} with a choice of plans: the mayor's halls ({@link Mayors#HALLS}) are their own. */
    public static @Nullable HouseProject start(ServerLevel level, Villager villager, long day, List<String> choice) {
        BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
        if (bell == null) {
            return null;
        }
        HouseProject house = plan(level, villager, bell, day, choice);
        if (house == null) return null;
        if (register(level, house)) return house;
        unlot(level, house);
        return null;
    }

    /** Why the last {@link #startHome} began nothing. */
    public static String lastHomeRefusal = "";

    /**
     * A homeless household's house of its own on a lot (or round the bell): begun only when it can pay down on the lot
     * and the build ({@code helped}: the mayor pays that for one homeless long enough).
     */
    public static @Nullable HouseProject startHome(ServerLevel level, Villager villager, long day, boolean helped) {
        BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
        if (bell == null) return null;
        HouseProject house = plan(level, villager, bell, day, PLANS);
        if (house == null) {
            lastHomeRefusal = "no spot for a house";
            return null;
        }
        int price = (int) Math.ceil(com.vnap.village.market.Materials.cost(bill(level, house)) * 1.1) + land(level, house);
        if (com.vnap.village.market.Housing.split(level, villager, price, com.vnap.village.market.Housing.Purpose.HOME, helped) == null) {
            lastHomeRefusal = "saving " + com.vnap.village.market.Market.funds(level, villager) + "/"
                    + (com.vnap.village.market.Housing.down(price) + TownRules.RESERVE) + " to build (" + price + "c)";
            unlot(level, house);
            return null;
        }
        if (register(level, house)) return house;
        unlot(level, house);
        return null;
    }

    /** A house planned and then not begun gives its lot back (TownPlan.house took it). */
    private static void unlot(ServerLevel level, HouseProject house) {
        com.vnap.village.town.Roads roads = com.vnap.village.town.Roads.get(level.getServer());
        com.vnap.village.town.Roads.Network network = roads.near(level.dimension(), house.bell);
        com.vnap.village.town.TownPlan.Lot lot = network == null ? null : com.vnap.village.town.TownPlan.at(network, house.footprint(level).getCenter());
        if (lot != null && lot.player == null && lot.taken) {
            lot.taken = false;
            roads.setDirty();
        }
    }

    /** What the lot under a new house costs (it goes to the treasury with the rest of the price); none off the plan. */
    static int land(ServerLevel level, HouseProject house) {
        com.vnap.village.town.Roads.Network network = com.vnap.village.town.Roads.get(level.getServer()).near(level.dimension(), house.bell);
        com.vnap.village.town.TownPlan.Lot lot = network == null ? null : com.vnap.village.town.TownPlan.at(network, house.footprint(level).getCenter());
        return lot == null || lot.player != null ? 0 : lot.price();
    }

    /** What is still to go up: a plan over an old house is paid in full (its cells are taken down first). */
    static java.util.Map<net.minecraft.world.item.Item, Integer> bill(ServerLevel level, HouseProject house) {
        return house.clearing.isEmpty()
                ? com.vnap.village.market.Materials.bill(level, house.job(level))
                : com.vnap.village.market.Materials.bill(house.schematic(level));
    }

    static @Nullable HouseProject plan(ServerLevel level, Villager villager, BlockPos bell, long day, List<String> choice) {
        List<String> plans = new ArrayList<>(choice);
        Collections.shuffle(plans, new Random(villager.getUUID().getLeastSignificantBits() ^ day));
        // the town's plan first: the nearest free lot its street runs past (TownPlan)
        HouseProject onLot = com.vnap.village.town.TownPlan.house(level, villager, bell, plans, day);
        if (onLot != null) return onLot;
        com.vnap.village.town.Land taken = taken(level, villager);
        // the mayor's hall is wider than any house: it looks further out, up to the village's edge, and finer around;
        // the stall wants to be near the bell, where people pass, and is small enough to squeeze in
        boolean hall = choice.stream().anyMatch(Mayors.HALLS::contains);
        boolean stall = choice.stream().anyMatch(com.vnap.village.market.Shops.STALLS::contains);
        // a growing town builds further out (Towns.reach): a house keeps its plot inside, a hall reaches the edge
        double reach = Towns.reach(level, bell);
        int farthest = (int) (hall || stall ? reach : reach - (TownRules.NEAR_BELL - TownRules.HOUSE_MAX_DISTANCE));
        // about one spot every six blocks around a ring (a far ring has room for many houses)
        int fewest = hall || stall ? 32 : 16;
        // a stall right by the bell gathers the whole village there: it keeps a house's distance
        for (BlockPos at : com.vnap.village.town.Rings.around(bell, TownRules.HOUSE_MIN_DISTANCE, farthest, 3,
                distance -> Math.max(fewest, (int) (Math.PI * 2.0 * distance / 6.0)))) {
            if (!level.isLoaded(at)) continue;
            BlockPos centre = com.vnap.village.town.Ground.near(level, at.getX(), at.getZ(), bell.getY());
            if (centre == null || !Towns.ours(level, bell, centre)) continue;
            String palette = HousePalette.forBiome(level.getBiome(centre));
            for (String plan : plans.subList(0, Math.min(3, plans.size()))) {
                HouseProject house = fit(level, villager, bell, centre, plan, palette, taken, day);
                if (house != null) return house;
            }
        }
        return null;
    }

    private static @Nullable HouseProject fit(ServerLevel level, Villager villager, BlockPos bell, BlockPos centre, String plan,
                                              String palette, com.vnap.village.town.Land taken, long day) {
        Direction towardBell = Direction.getNearest(bell.getX() - centre.getX(), 0, bell.getZ() - centre.getZ());
        return fit(level, villager, bell, centre, plan, palette, taken, day, facing(towardBell), null);
    }

    private static @Nullable HouseProject fit(ServerLevel level, Villager villager, BlockPos bell, BlockPos centre, String plan,
                                              String palette, com.vnap.village.town.Land taken, long day, List<Rotation> rotations,
                                              @Nullable BoundingBox cleared) {
        SchematicSpec spec = Workshops.schematic(level, HousePalette.schematicName(plan, palette));
        if (spec.blocks.isEmpty()) return null;
        for (Rotation rotation : rotations) {
            BlockPos origin = centre.subtract(new BlockPos(spec.sizeX / 2, 0, spec.sizeZ / 2).rotate(rotation));
            BoundingBox box = HouseProject.footprint(spec, origin, rotation);
            if (taken.intersects(box, 2)) continue;
            if (Workshops.fits(level, spec, origin, rotation, com.vnap.village.town.Land.of(List.of()), cleared, centre.getY())) {
                return new HouseProject(bell.asLong(), bell, villager.getUUID(), plan, palette, origin, rotation, day);
            }
        }
        return null;
    }

    /**
     * Empty houses the villagers built near the bell, nearest first: done, nobody's home (its bed free, no home
     * sign, no villager's bed or HOME inside), not a hall, nothing in them but their own blocks and furnishings.
     * Whether a hall fits on the ground is for the caller to try (planAt).
     */
    public static List<HouseProject> emptyHouses(ServerLevel level, BlockPos bell) {
        List<HouseProject> found = new ArrayList<>();
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (!house.done || house.player != null || Mayors.HALLS.contains(house.plan) || com.vnap.village.market.Shops.STALLS.contains(house.plan)
                    || !Towns.ours(level, bell, house.origin)) continue;
            BlockPos bed = house.bed(level);
            if (bed == null || !level.isLoaded(bed) || !VillagerHome.claimable(level, bed) || !onlyItsOwn(level, house)) continue;
            BoundingBox box = house.footprint(level);
            boolean lived = !level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(TownRules.NEAR_BELL), villager -> {
                BlockPos own = villager instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
                BlockPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
                return (own != null && box.isInside(own)) || (home != null && box.isInside(home));
            }).isEmpty();
            if (!lived) found.add(house);
        }
        found.sort(java.util.Comparator.comparingDouble(house -> house.origin.distSqr(bell)));
        return found;
    }

    /**
     * Houses of the world near the bell, nearest first (village houses of the structure, jigsaw pieces from a
     * {@code houses} pool): no job site or bell in them, no non-empty chest (a player's store), no home sign, nobody's
     * bed but {@code owner}'s household's (null: nobody's), no project on them.
     */
    public static List<BoundingBox> worldHouses(ServerLevel level, BlockPos bell, @Nullable Villager owner) {
        List<BoundingBox> found = new ArrayList<>();
        List<BlockPos> beds = level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), bell, TownRules.NEAR_BELL, PoiManager.Occupancy.ANY)
                .map(net.minecraft.world.entity.ai.village.poi.PoiRecord::getPos).toList();
        for (BlockPos bed : beds) {
            BoundingBox box = piece(level, bed);
            if (box != null && !found.contains(box) && canClear(level, box, owner)) found.add(box);
        }
        found.sort(java.util.Comparator.comparingDouble(box -> box.getCenter().distSqr(bell)));
        return found;
    }

    /** The village-structure house piece holding this bed, or null (no village here, a street, a centre, a workshop). */
    public static @Nullable BoundingBox piece(ServerLevel level, BlockPos bed) {
        WorldPiece piece = worldPiece(level, bed);
        return piece == null ? null : piece.box();
    }

    /** A generated village's house: its box, the template's file name (plains_small_house_1), where and turned how it was placed. */
    public record WorldPiece(BoundingBox box, String template, BlockPos origin, Rotation rotation) {
    }

    public static @Nullable WorldPiece worldPiece(ServerLevel level, BlockPos bed) {
        var start = level.structureManager().getStructureWithPieceAt(bed, net.minecraft.tags.StructureTags.VILLAGE);
        if (!start.isValid()) return null;
        for (var piece : start.getPieces()) {
            if (!(piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool) || !piece.getBoundingBox().isInside(bed)) continue;
            // Single[Left[minecraft:village/plains/houses/plains_small_house_1]] (LegacySingle[...] the same)
            String element = pool.getElement().toString();
            if (!element.contains("/houses/")) continue;
            int end = element.indexOf(']');
            String template = end < 0 ? "" : element.substring(element.lastIndexOf('/', end) + 1, end);
            return new WorldPiece(piece.getBoundingBox(), template, pool.getPosition(), pool.getRotation());
        }
        return null;
    }

    /** A world house's box may come down: vacant but for {@code owner}'s household, no project on it. */
    public static boolean canClear(ServerLevel level, BoundingBox box, @Nullable Villager owner) {
        return vacant(level, box, owner) && !com.vnap.village.town.Roads.bought(level, box.getCenter())
                && VillageMoodSavedData.get(level).houses().stream().noneMatch(house -> house.footprint(level).intersects(box));
    }

    /** A world house may be grown on where it stands: no stranger lives there and no project is on it (its chests stay). */
    public static boolean canGrow(ServerLevel level, BoundingBox box, Villager owner) {
        return vacant(level, box, owner, false) && !com.vnap.village.town.Roads.bought(level, box.getCenter())
                && VillageMoodSavedData.get(level).houses().stream().noneMatch(house -> house.footprint(level).intersects(box));
    }

    private static boolean vacant(ServerLevel level, BoundingBox box, @Nullable Villager owner) {
        return vacant(level, box, owner, true);
    }

    private static boolean vacant(ServerLevel level, BoundingBox box, @Nullable Villager owner, boolean emptyChests) {
        BlockPos centre = box.getCenter();
        int reach = Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
        boolean work = level.getPoiManager().getInRange(type -> !type.is(PoiTypes.HOME), centre, reach, PoiManager.Occupancy.ANY)
                .anyMatch(poi -> box.isInside(poi.getPos()));
        if (work) return false;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var entity = level.getBlockEntity(pos);
            if (emptyChests && entity instanceof net.minecraft.world.Container container && !container.isEmpty()) return false;
            if (owner == null && entity instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign
                    && VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) return false;
        }
        return level.getEntitiesOfClass(Villager.class, AABB.of(box).inflate(TownRules.NEAR_BELL), villager -> {
            if (owner != null && (villager.equals(owner) || owner.getUUID().equals(Family.state(villager).head))) return false;
            BlockPos own = villager instanceof VillagerLifeData data ? data.vnap$homeBed() : null;
            BlockPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
            return (own != null && box.isInside(own)) || (home != null && box.isInside(home));
        }).isEmpty();
    }

    /** Nothing in the house but its own blocks and the villagers' furnishings: a player's block keeps it standing. */
    public static boolean onlyItsOwn(ServerLevel level, HouseProject house) {
        List<SchematicSpec> parts = new ArrayList<>(List.of(house.schematic(level)));
        if (house.rooms > 0) parts.add(house.roomSchematic(level));
        java.util.Set<BlockPos> own = new java.util.HashSet<>();
        for (SchematicSpec part : parts) {
            for (SchematicSpec.PlacedBlock block : part.blocks) own.add(BuildJob.at(house.origin, house.rotation, block.rel()));
        }
        for (SchematicSpec part : parts) {
            BoundingBox box = HouseProject.footprint(part, house.origin, house.rotation);
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                var state = level.getBlockState(pos);
                if (!state.isAir() && !own.contains(pos) && !furnishing(state) && !state.canBeReplaced()
                        && !(state.getBlock() instanceof net.minecraft.world.level.block.SignBlock)
                        && !(state.getBlock() instanceof com.vnap.block.town.HouseSignBlock)) return false;
            }
        }
        return true;
    }

    /**
     * What taking the empty house down means, for the hall going up on its ground: its own blocks and the
     * furnishings villagers set in it (pots, lanterns, candles, carpets, decor), top down; its floor turns to grass.
     */
    public static void clearForHall(ServerLevel level, HouseProject hall, HouseProject old) {
        List<SchematicSpec> parts = new ArrayList<>(List.of(old.schematic(level)));
        if (old.rooms > 0) parts.add(old.roomSchematic(level));
        java.util.Set<BlockPos> cells = new java.util.LinkedHashSet<>();
        BoundingBox area = null;
        for (SchematicSpec part : parts) {
            BoundingBox box = HouseProject.footprint(part, old.origin, old.rotation);
            area = area == null ? box : BoundingBox.encapsulatingBoxes(List.of(area, box)).orElse(box);
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (furnishing(level.getBlockState(pos))) cells.add(pos.immutable());
            }
            for (SchematicSpec.PlacedBlock block : part.blocks) {
                BlockPos pos = BuildJob.at(old.origin, old.rotation, block.rel());
                if (!block.state().isAir() && level.getBlockState(pos).is(block.state().getBlock())) cells.add(pos);
            }
        }
        clearing(hall, cells, old.origin.getY(), area);
        VillageMoodSavedData.get(level).houses().remove(old);
        VillageMoodSavedData.get(level).markDirty();
    }

    /** The same for a house of the world: every built block from its floor up, natural ground and water left alone. */
    public static void clearForHall(ServerLevel level, HouseProject hall, BoundingBox box, int floor) {
        java.util.Set<BlockPos> cells = new java.util.LinkedHashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), floor, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || BuildJob.naturalGround(state)
                    || (pos.getY() > floor && state.canBeReplaced())) continue;
            cells.add(pos.immutable());
        }
        clearing(hall, cells, floor, box);
    }

    private static void clearing(HouseProject hall, java.util.Set<BlockPos> cells, int floor, @Nullable BoundingBox area) {
        hall.clearing.clear();
        hall.clearing.addAll(cells);
        // top down, the way one takes a house apart
        hall.clearing.sort(java.util.Comparator.comparingInt((BlockPos pos) -> -pos.getY()));
        hall.clearFloor = floor;
        hall.clearArea = area;
    }

    /**
     * Animals and golems inside the walls of a house going up are set outside it: a builder waits for a cell to be
     * free but the walls still rise around them, and a golem (wider than a door) or a cow never got out again.
     * Villagers walk out by the door, a family's pet lives in the house, players see for themselves. Every 40 ticks
     * while building (HouseBehavior), when it is done, and daily for a house lived in (VillagerHome).
     */
    public static void shooOut(ServerLevel level, HouseProject house) {
        List<BoundingBox> boxes = new ArrayList<>(List.of(house.footprint(level)));
        if (house.hasRoom()) boxes.add(HouseProject.footprint(house.roomSchematic(level), house.origin, house.rotation));
        for (BoundingBox box : boxes) {
            for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, AABB.of(box),
                    mob -> mob.isAlive() && !(mob instanceof Villager) && !mob.isPassenger() && !mob.isLeashed()
                            && !(mob instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame()))) {
                BlockPos out = outside(level, box, mob);
                if (out != null) mob.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5);
            }
        }
    }

    /** The nearest ground just outside the box where the mob stands clear (a golem needs three free cells up). */
    private static @Nullable BlockPos outside(ServerLevel level, BoundingBox box, net.minecraft.world.entity.Mob mob) {
        int tall = (int) Math.ceil(mob.getBbHeight());
        BlockPos best = null;
        for (int ring = 1; ring <= 4 && best == null; ring++) {
            for (int x = box.minX() - ring; x <= box.maxX() + ring; x++) {
                for (int z = box.minZ() - ring; z <= box.maxZ() + ring; z++) {
                    if (x > box.minX() - ring && x < box.maxX() + ring && z > box.minZ() - ring && z < box.maxZ() + ring) continue;
                    BlockPos ground = com.vnap.village.town.Ground.near(level, x, z, box.minY());
                    if (ground == null) continue;
                    BlockPos feet = ground.above();
                    boolean clear = level.getFluidState(feet).isEmpty();
                    for (int up = 0; up < tall && clear; up++) {
                        clear = level.getBlockState(feet.above(up)).getCollisionShape(level, feet.above(up)).isEmpty();
                    }
                    if (clear && (best == null || feet.distSqr(mob.blockPosition()) < best.distSqr(mob.blockPosition()))) best = feet;
                }
            }
        }
        return best;
    }

    /** One cell of the old house comes down (a villager worked on it): broken quietly, nothing drops. */
    public static void clearOne(ServerLevel level, HouseProject hall, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (hall.junk) {
            // a lot's own clearing: a player's things go to the town's lost and found, the hill and its trees nowhere
            List<net.minecraft.world.item.ItemStack> found = new ArrayList<>();
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    net.minecraft.world.item.ItemStack stack = container.removeItemNoUpdate(slot);
                    if (!stack.isEmpty()) found.add(stack);
                }
            }
            if (!terrain(state)) com.vnap.village.market.Materials.items(state).forEach((item, count) -> found.add(new net.minecraft.world.item.ItemStack(item, count)));
            com.vnap.village.town.NoticeBoard.lose(level, hall.bell, found);
        } else {
            stash(level, hall, pos);
            // what comes down is used again in the new build or sold when it is done (Market.settle), not thrown away
            if (!BuildJob.naturalGround(state) && !furnishing(state) && !state.hasBlockEntity()) com.vnap.village.market.Market.salvage(hall.site, state);
        }
        // the household's things (decor, pots, lanterns, a chest) are kept too, not only what is in them
        if (!hall.junk && (furnishing(state) || state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)) {
            com.vnap.village.market.Materials.items(state).forEach((item, count) -> hall.stash.add(new net.minecraft.world.item.ItemStack(item, count)));
        }
        if (!state.isAir()) level.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
                | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;
        level.setBlock(pos, (pos.getY() == hall.clearFloor ? net.minecraft.world.level.block.Blocks.GRASS_BLOCK
                : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), flags);
        hall.clearing.remove(pos);
        if (hall.clearing.isEmpty()) {
            BoundingBox area = hall.clearArea;
            if (area != null) level.getEntitiesOfClass(net.minecraft.world.entity.decoration.HangingEntity.class, AABB.of(area)).forEach(Entity::discard);
            hall.clearArea = null;
            VillagerHome.noteStructureChange();
        }
        VillageMoodSavedData.get(level).markDirty();
    }

    /** A chest's or a shelf's things are kept for the new house (taking a block down would spill them). */
    public static void stash(ServerLevel level, HouseProject into, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                net.minecraft.world.item.ItemStack stack = container.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) into.stash.add(stack);
            }
            container.setChanged();
        }
    }

    /**
     * The house's stash put away in the containers inside it (chests, barrels: any with room), as much as fits;
     * true when nothing is left.
     */
    public static boolean unpack(ServerLevel level, HouseProject house) {
        BoundingBox box = house.footprint(level);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (house.stash.isEmpty()) break;
            if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container container)
                    || level.getBlockEntity(pos) instanceof com.vnap.block.decor.DecorBlockEntity) continue;
            java.util.Iterator<net.minecraft.world.item.ItemStack> things = house.stash.iterator();
            while (things.hasNext()) {
                net.minecraft.world.item.ItemStack left = put(container, things.next());
                if (left.isEmpty()) things.remove();
                else break;
            }
        }
        VillageMoodSavedData.get(level).markDirty();
        return house.stash.isEmpty();
    }

    /** A stack into a container: onto stacks of its kind first, then empty slots; what does not fit comes back. */
    private static net.minecraft.world.item.ItemStack put(net.minecraft.world.Container container, net.minecraft.world.item.ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            net.minecraft.world.item.ItemStack there = container.getItem(slot);
            if (there.isEmpty()) {
                container.setItem(slot, stack.copy());
                stack.setCount(0);
            } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
                there.grow(moved);
                stack.shrink(moved);
            }
        }
        container.setChanged();
        return stack;
    }

    /**
     * A cell of the old house that is gone already (or was never more than ground): nothing to do there. A stall's
     * counter is never cleared: the keeper sets it on its cell before the lot's clearing list (made earlier) gets
     * there, and taking it down shut the shop for good (nothing to buy the materials to put it back with).
     */
    public static boolean cleared(ServerLevel level, HouseProject hall, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || (pos.getY() == hall.clearFloor && BuildJob.naturalGround(state)) || com.vnap.village.market.Shops.isCounter(level, pos);
    }

    /** What grows or lies on a lot by itself: earth, stone, trees and plants (nobody's things). */
    static boolean terrain(net.minecraft.world.level.block.state.BlockState state) {
        return BuildJob.naturalGround(state) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced()
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.ICE) || state.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK);
    }

    public static boolean furnishing(net.minecraft.world.level.block.state.BlockState state) {
        var block = state.getBlock();
        return block instanceof com.vnap.block.DecorBlock || block instanceof net.minecraft.world.level.block.FlowerPotBlock
                || block instanceof net.minecraft.world.level.block.LanternBlock || block instanceof net.minecraft.world.level.block.CandleBlock
                || block instanceof net.minecraft.world.level.block.CarpetBlock;
    }

    /**
     * A plan for the ground of a house that is to be taken down ({@code cleared}, its floor at {@code centre}'s y),
     * tried as if it were gone already: the house's own turn first (it faced the street), then any other. Not
     * registered: {@link #register} it once the house is down. Null when nothing fits there.
     */
    public static @Nullable HouseProject planAt(ServerLevel level, Villager villager, long day, List<String> choice, BlockPos centre,
                                                @Nullable Rotation turn, BoundingBox cleared) {
        BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
        if (bell == null) return null;
        // the house coming down no longer takes its own ground
        com.vnap.village.town.Land taken = taken(level, villager).without(claim -> cleared.isInside(claim.box().getCenter()));
        String palette = HousePalette.forBiome(level.getBiome(centre));
        List<Rotation> rotations = new ArrayList<>();
        if (turn != null) rotations.add(turn);
        for (Rotation rotation : facing(Direction.getNearest(bell.getX() - centre.getX(), 0, bell.getZ() - centre.getZ()))) {
            if (!rotations.contains(rotation)) rotations.add(rotation);
        }
        for (Rotation rotation : Rotation.values()) if (!rotations.contains(rotation)) rotations.add(rotation);
        for (String plan : choice) {
            HouseProject house = fit(level, villager, bell, centre, plan, palette, taken, day, rotations, cleared);
            if (house != null) return house;
        }
        return null;
    }

    /** {@code register} with the bill worked out by the caller (a house rebuilt into its next level): false, and nothing registered, when the household cannot carry it. */
    public static boolean register(ServerLevel level, HouseProject house, java.util.Map<net.minecraft.world.item.Item, Integer> bill) {
        if (!pay(level, house, bill)) return false;
        VillageMoodSavedData.get(level).houses().add(house);
        VillageMoodSavedData.get(level).markDirty();
        return true;
    }

    /**
     * The hall and the shop house cost nothing (HouseProject.base); a family's house its family pays for (Housing.build): its
     * first home with the lot under it, a storey or a room when the family has outgrown it (a house grows only for a
     * bed it lacks: FamilyRooms) — a home the town makes sure of, the loan never over the cap.
     */
    static boolean pay(ServerLevel level, HouseProject house, java.util.Map<net.minecraft.world.item.Item, Integer> bill) {
        if (!(level.getEntity(house.owner) instanceof Villager owner)) {
            com.vnap.village.market.Market.grant(level, house.bell, house.site, bill);
            return true;
        }
        boolean first = !hasHome(owner);
        return com.vnap.village.market.Housing.build(level, owner, house.bell, house.site, bill,
                com.vnap.village.market.Housing.Purpose.HOME, first ? land(level, house) : 0);
    }

    /**
     * Records a new project and pays for it ({@link #pay}: the builders buy the materials at the stall with the money);
     * a stall comes with its goods, its keeper brought them (Shops). False when the household cannot pay.
     */
    public static boolean register(ServerLevel level, HouseProject house) {
        VillageMoodSavedData data = VillageMoodSavedData.get(level);
        java.util.Map<net.minecraft.world.item.Item, Integer> bill = bill(level, house);
        if (!house.base() && !pay(level, house, bill)) return false;
        data.houses().add(house);
        data.markDirty();
        return true;
    }

    /** The front of a plan faces north; the rotation that turns it toward the bell first, then its sides. */
    static List<Rotation> facing(Direction front) {
        List<Rotation> order = new ArrayList<>();
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(Direction.NORTH) == front) order.add(0, rotation);
            else if (rotation.rotate(Direction.NORTH) != front.getOpposite()) order.add(rotation);
        }
        return order;
    }

    /** The town's given-away ground (Land) and a little more round the bell: where a house off the plan may not go. */
    static com.vnap.village.town.Land taken(ServerLevel level, Villager villager) {
        BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
        BlockPos at = bell == null ? villager.blockPosition() : bell;
        com.vnap.village.town.Land land = com.vnap.village.town.Land.around(level, at);
        return bell == null ? land : land.with(com.vnap.village.town.Land.Layer.BUILDING, List.of(BoundingBox.fromCorners(bell, bell).inflatedBy(2)));
    }

    /** The house this villager works on: its own, or as a villager without a trade, a neighbour's nearby. */
    public static @Nullable HouseProject forBuilder(ServerLevel level, Villager villager) {
        return forBuilder(level, villager, house -> true);
    }

    /** {@link #forBuilder}, the helper's house the nearest one {@code takesHands} (not waiting for the day, not full). */
    public static @Nullable HouseProject forBuilder(ServerLevel level, Villager villager, java.util.function.Predicate<HouseProject> takesHands) {
        // the town's base (the shop house, the hall) going up or being mended comes before anything: without the shop a
        // town has nothing to buy its builds with. Its official (keeper, mayor) and one other grown villager (a worker
        // too, never the other official: HouseBehavior.full) drop what they do for it, any hour of work
        if (!villager.isBaby()) {
            BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
            for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
                if (!house.base() || (house.done && !house.mending) || bell == null || !house.bell.closerThan(bell, TownRules.SPAN)) continue;
                if (house.owner.equals(villager.getUUID())) return house;
                if (!com.vnap.village.life.Life.official(villager) && house.origin.closerThan(villager.blockPosition(), TownRules.HELP_RADIUS)
                        && takesHands.test(house)) return house;
            }
        }
        HouseProject helping = null;
        // the shopkeeper has no trade, yet its work is the counter: it builds only its own
        boolean helper = com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE) && !villager.isBaby()
                && !com.vnap.village.life.Life.official(villager);
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (house.done) {
                // a house being mended is its household's work alone, done when they are free
                if (house.mending && house.owner.equals(villager.getUUID()) && freeToMend(level, villager)) return house;
                continue;
            }
            if (house.owner.equals(villager.getUUID())) return house;
            if (helper && house.origin.closerThan(villager.blockPosition(), TownRules.HELP_RADIUS) && takesHands.test(house)
                    && (helping == null || house.origin.distSqr(villager.blockPosition()) < helping.origin.distSqr(villager.blockPosition()))) {
                helping = house;
            }
        }
        return helping;
    }

    /**
     * Its owner died with a build going (a storey, a cellar, a mending): the widow carries on with it, the site's money and
     * goods as they are (a hall's and a stall's go to the next mayor and keeper: Mayors, Shops).
     */
    public static void handOver(ServerLevel level, java.util.UUID dead, java.util.@Nullable UUID heir) {
        for (HouseProject house : List.copyOf(VillageMoodSavedData.get(level).houses())) {
            if (!house.owner.equals(dead) || (house.done && !house.mending)) continue;
            if (Mayors.HALLS.contains(house.plan) || com.vnap.village.market.Shops.STALLS.contains(house.plan)) continue;
            if (heir != null) {
                house.owner = heir;
            } else if (house.done) {
                house.mending = false;
            } else {
                // nobody to build it for: what was bought goes back to the stall, the money to the treasury, the land is
                // free (it stood waiting for a helper to wander by, its ground and money locked for good)
                com.vnap.village.market.Market.settle(level, house.site, null, house.bell);
                VillageMoodSavedData.get(level).houses().remove(house);
            }
            VillageMoodSavedData.get(level).markDirty();
        }
    }

    /** A worker mends its house after work; one without a trade in the day. */
    public static boolean freeToMend(ServerLevel level, Villager villager) {
        long time = Family.timeOfDay(level);
        boolean trade = !com.vnap.Mc.is(villager.getVillagerData(), VillagerProfession.NONE);
        return trade ? time >= 9000L && time < 11500L : Workshops.workTime(level);
    }

    /** The plan's blocks a mending leaves alone: natural ground (grass dies under a house), what the extension carved. */
    private static java.util.function.Predicate<SchematicSpec.PlacedBlock> notMended(ServerLevel level, HouseProject house) {
        java.util.Set<BlockPos> carved = new java.util.HashSet<>();
        if (house.hasRoom()) for (SchematicSpec.PlacedBlock block : house.roomSchematic(level).blocks) carved.add(block.rel());
        return block -> BuildJob.naturalGround(block.state()) || carved.contains(block.rel());
    }

    /**
     * A household's daily look at its finished house: blocks gone from it (a creeper, a player's pickaxe) are noticed
     * the first day and mended from the next, by the one who lives there, in its free time. Its household pays
     * (Housing: upkeep the town makes sure of); blocks set in their place by someone else are left be. Nothing is judged on land not loaded.
     */
    public static void inspect(ServerLevel level, HouseProject house, Villager head, long day) {
        if (!house.done || house.mending || !house.clearing.isEmpty()) return;
        BoundingBox box = house.footprint(level);
        for (BlockPos corner : List.of(new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.minY(), box.maxZ()),
                new BlockPos(box.minX(), box.minY(), box.maxZ()), new BlockPos(box.maxX(), box.minY(), box.minZ()))) {
            if (!level.isLoaded(corner)) return;
        }
        BuildJob gaps = BuildJob.missing(level, house.schematicName(), house.schematic(level), house.origin, house.rotation, 0,
                notMended(level, house));
        if (gaps.size() == 0) {
            house.damagedDay = -1L;
        } else if (house.damagedDay < 0 && !house.base()) {
            house.damagedDay = day;
        } else if (house.base() || day > house.damagedDay) {
            house.damagedDay = -1L;
            house.mending = true;
            house.owner = head.getUUID();
            house.pausedDay = -1L;
            gaps.site = house.base() ? null : house.site;
            house.job = gaps;
            // the hall and the shop house are mended for nothing; any other house its owners pay for
            if (!house.base()) com.vnap.village.market.Market.finance(level, head, house.bell, house.site,
                    gaps.bill(level, Integer.MAX_VALUE), com.vnap.village.market.Housing.Purpose.UPKEEP);
        }
        VillageMoodSavedData.get(level).markDirty();
    }

    /** Gaps left that nobody could reach: the mending stops and the damage is looked at again in {@link TownRules#MEND_RETRY_DAYS} days. */
    public static void giveUpMending(ServerLevel level, HouseProject house, long day) {
        mended(level, house);
        house.damagedDay = day + TownRules.MEND_RETRY_DAYS;
    }


    /** The last gap is filled: the house stands whole again, the money left goes back to the village. */
    public static void mended(ServerLevel level, HouseProject house) {
        house.mending = false;
        house.job = null;
        com.vnap.village.market.Market.settle(level, house.site, null, house.bell);
        VillageMoodSavedData.get(level).markDirty();
    }

    /** A neighbour may grab the new bed the tick it is set; the house is the owner's. */
    private static void evict(ServerLevel level, BlockPos bed, Villager owner) {
        for (Villager other : level.getEntitiesOfClass(Villager.class, new AABB(bed).inflate(TownRules.NEAR_BELL), v -> !v.equals(owner))) {
            Optional<GlobalPos> home = other.getBrain().getMemory(MemoryModuleType.HOME);
            if (home.isPresent() && home.get().pos().equals(bed) && home.get().dimension().equals(level.dimension())) {
                other.getBrain().eraseMemory(MemoryModuleType.HOME);
                level.getPoiManager().release(bed);
            }
            if (other instanceof VillagerLifeData data && bed.equals(data.vnap$homeBed())) {
                data.vnap$setHomeBed(null);
            }
        }
    }

    /** Sets every block of the house at once and moves the owner in: debug commands and new hamlets. */
    public static void buildNow(ServerLevel level, HouseProject house) {
        while (!house.clearing.isEmpty()) clearOne(level, house, house.clearing.get(0));
        BuildJob job = house.job(level);
        // all at once: the village buys the whole bill (the caravan brings what the stall lacks)
        com.vnap.village.market.Market.kit(level, house.bell, house.site, job.bill(level, Integer.MAX_VALUE));
        job.completeNow(level);
        finish(level, house);
    }

    /** The last block is in: the owner moves in. */
    public static void finish(ServerLevel level, HouseProject house) {
        shooOut(level, house);
        house.done = true;
        house.job = null;
        // the money left: a family's house gives it to the family, a hall, a stall or a farm to the village
        boolean villageOwns = Mayors.HALLS.contains(house.plan) || com.vnap.village.market.Shops.STALLS.contains(house.plan);
        com.vnap.village.market.Market.settle(level, house.site, villageOwns ? null : level.getEntity(house.owner) instanceof Villager owner ? owner : null, house.bell);
        if (Mayors.HALLS.contains(house.plan)) {
            // the office's and the home's furnishings, bought for the hall by the treasury; the mayor and the wife set them
            for (String part : List.of(Mayors.OFFICE, Mayors.HOME)) {
                com.vnap.village.market.Market.kit(level, house.bell, null, com.vnap.village.market.Materials.bill(
                        Workshops.schematic(level, HousePalette.schematicName(house.plan + "_" + part, house.palette))));
            }
        }
        VillageMoodSavedData.get(level).markDirty();
        // shutters, window boxes, a lantern at the door, firewood: the town's builders dress it
        com.vnap.village.town.Dressing.dress(level, house);
        String builder = level.getEntity(house.owner) instanceof Villager owner ? com.vnap.dialogue.VillagerHome.nameOf(owner) : "";
        com.vnap.village.town.Chronicle.note(level, house.bell, Mayors.HALLS.contains(house.plan) ? "hall"
                : com.vnap.village.market.Shops.STALLS.contains(house.plan) ? "stall" : builder.isEmpty() ? "house_empty" : "house", builder);
        // a stall is the village's work place, nobody lives there
        if (com.vnap.village.market.Shops.STALLS.contains(house.plan)) {
            com.vnap.village.market.Shops.onStallDone(level, house);
            // the shop house is the keeper's home too when it has none (a keeper who came to the village)
            if (level.getEntity(house.owner) instanceof Villager keeper && keeper.isAlive() && !claimed(keeper)) moveIn(level, keeper, house);
            return;
        }
        // a hall is the office's: whoever is mayor now moves in (the one who started it may have died or stepped
        // down meanwhile); with the office empty it waits for the next mayor (Mayors.house)
        Villager owner = Mayors.HALLS.contains(house.plan) ? Mayors.mayorNear(level, house.bell)
                : level.getEntity(house.owner) instanceof Villager villager ? villager : null;
        // a house rebuilt into its next level: its family lives there already and takes the new beds
        if (owner != null && !moveIn(level, owner, house) && Family.home(owner) != null && house.footprint(level).isInside(Family.home(owner))) {
            Family.gather(level, owner, null);
        }
    }

    /**
     * The villager and its family move into the finished house (its own, or the free hall of the village for a new
     * mayor). False when a door or the bed was left out (a player's block stood there): the family keeps its home then.
     */
    public static boolean moveIn(ServerLevel level, Villager villager, HouseProject house) {
        BlockPos bed = house.bed(level);
        if (bed == null || !VillagerHome.claimable(level, bed)) return false;
        evict(level, bed, villager);
        // a family outgrew its old house: it gives that one up, the members follow the head here
        BlockPos oldHome = Family.home(villager);
        Family.leaveForNewHouse(level, villager);
        VillagerHome.claim(level, villager, bed);
        Family.gather(level, villager, oldHome);
        villager.getPersistentData().remove(HOMELESS_SINCE);
        if (Mayors.HALLS.contains(house.plan)) Speech.queue(villager, "Mayor Moves Into the Hall", null, 2L);
        return true;
    }
}
