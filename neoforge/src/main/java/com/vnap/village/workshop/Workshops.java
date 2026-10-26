package com.vnap.village.workshop;

import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Speech;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HousePalette;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.script.WorkerControl;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.NeoForge;
//? if <26.1
import net.neoforged.neoforge.common.BasicItemListing;
//? if >=1.21.4 {
/*import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
*///?} else
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
//? if <26.1
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every villager with a profession grows a workshop around its job site: a corner at levels 1-2, an
 * awning at 3-4 and a walled workshop at 5. The villager builds it by hand in work hours (see
 * {@code WorkBehavior}) and puts back what goes missing once a day.
 */
public final class Workshops {
    public static final List<String> PROFESSIONS = List.of("armorer", "butcher", "cartographer", "cleric", "farmer",
            "fisherman", "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith");
    public static final int SIZE = 7;
    public static final BlockPos FOCAL = new BlockPos(3, 1, 1);
    public static final BlockPos CHEST = new BlockPos(1, 1, 1);

    /**
     * Where a profession's job block and chest stand in its plans and how big its top tier is: the 7x7 workshop's
     * constants, or for a plan with its own focal mark (the farmer's farm) that mark, its chest and its size.
     */
    public record Layout(BlockPos focal, BlockPos chest, BlockPos size) {
    }

    private static final Map<String, Layout> LAYOUTS = new java.util.concurrent.ConcurrentHashMap<>();

    public static Layout layout(String profession) {
        return LAYOUTS.computeIfAbsent(profession, name -> {
            SchematicSpec top = SchematicSpec.loadBundled(schematicName(name, 3));
            if (!top.focalMarked) return new Layout(FOCAL, CHEST, new BlockPos(SIZE, SIZE, SIZE));
            BlockPos chest = CHEST;
            for (SchematicSpec.PlacedBlock block : top.blocks) {
                if (block.state().is(net.minecraft.world.level.block.Blocks.CHEST)) {
                    chest = block.rel();
                    break;
                }
            }
            return new Layout(top.focal, chest, new BlockPos(top.sizeX, top.sizeY, top.sizeZ));
        });
    }

    /** The workshop's chest in the world. */
    public static BlockPos chestPos(WorkshopState state) {
        BlockPos chest = layout(state.profession).chest();
        return state.at(chest.getX(), chest.getY(), chest.getZ());
    }

    public static BoundingBox footprint(WorkshopState state) {
        BlockPos size = layout(state.profession).size();
        return BoundingBox.fromCorners(state.origin, BuildJob.at(state.origin, state.rotation,
                new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1)));
    }
    static final int FLOOR_Y = 0;
    private static final Map<String, SchematicSpec> SCHEMATICS = new ConcurrentHashMap<>();

    private Workshops() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, (ServerStartingEvent event) -> SCHEMATICS.clear());
        // structures edited in game and saved by a structure block come back on /reload
        //? if >=1.21.4 {
        /*NeoForge.EVENT_BUS.addListener(AddServerReloadListenersEvent.class, (AddServerReloadListenersEvent event) -> event.addListener(com.vnap.VillagerLife.id("schematics"),
                (ResourceManagerReloadListener) manager -> SCHEMATICS.clear()));
        *///?} else {
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, (AddReloadListenerEvent event) ->
                event.addListener((ResourceManagerReloadListener) manager -> SCHEMATICS.clear()));
        //?}
        // from 26.1 the trades are data (generateTrades in the build)
        //? if <26.1 {
        NeoForge.EVENT_BUS.addListener(VillagerTradesEvent.class, (VillagerTradesEvent event) -> {
            //? if >=1.21.5 {
            /*ResourceLocation key = com.vnap.Mc.id(event.getType());
            *///?} else
            ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(event.getType());
            for (DecorBlock decor : LifeBlocks.DECOR) {
                if (key.getNamespace().equals("minecraft") && key.getPath().equals(decor.profession())) {
                    event.getTrades().get(3).add(new BasicItemListing(4, new ItemStack(decor), 6, 10));
                }
            }
            for (DecorBlock machine : LifeBlocks.MACHINES) {
                if (key.getNamespace().equals("minecraft") && key.getPath().equals(machine.profession())) {
                    event.getTrades().get(4).add(new BasicItemListing(12, new ItemStack(machine), 3, 15));
                }
            }
        });
        //?}
    }

    public static String schematicName(String profession, int tier) {
        return "workshop/" + profession + "_t" + tier;
    }

    /** A schematic by name; {@code plan@palette} is the plan with its blocks swapped for that palette. */
    public static SchematicSpec schematic(ServerLevel level, String name) {
        SchematicSpec cached = SCHEMATICS.get(name);
        if (cached != null) {
            return cached;
        }
        int at = name.indexOf('@');
        SchematicSpec loaded = at < 0
                ? SchematicSpec.load(level.getServer().getResourceManager(), name)
                : HousePalette.apply(schematic(level, name.substring(0, at)), name.substring(at + 1));
        SCHEMATICS.put(name, loaded);
        return loaded;
    }

    public static int tierFor(int villagerLevel) {
        return villagerLevel >= 5 ? 3 : villagerLevel >= 3 ? 2 : 1;
    }

    public static boolean workTime(ServerLevel level) {
        long time = com.vnap.dialogue.Family.timeOfDay(level);
        return time >= 2000L && time < 9000L;
    }

    public static @Nullable String profession(Villager villager) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(com.vnap.Mc.profession(villager.getVillagerData()));
        return key.getNamespace().equals("minecraft") && PROFESSIONS.contains(key.getPath()) ? key.getPath() : null;
    }

    public static @Nullable BlockPos jobSite(ServerLevel level, Villager villager) {
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        return site.isPresent() && site.get().dimension().equals(level.dimension()) ? site.get().pos() : null;
    }

    /**
     * The villager's workshop, planned on first sight and kept even while the job site memory is lost: a
     * free job block at the old anchor is claimed back. A new tier or a repair starts once a day in work
     * hours. Null when the villager has no workshop to look after.
     */
    public static @Nullable WorkshopState prepare(ServerLevel level, Villager villager, long day) {
        if (!(villager instanceof VillagerLifeData data) || villager.isBaby() || villager.isSleeping()
                || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)) {
            return null;
        }
        String profession = profession(villager);
        if (profession == null) {
            return null;
        }
        WorkshopState state = data.vnap$workshop();
        BlockPos site = jobSite(level, villager);
        if (site == null && state != null && state.profession.equals(profession) && reclaim(level, villager, state.anchor)) {
            site = state.anchor;
        }
        if (site == null) {
            return null;
        }
        // a plan laid out for another layout of the trade (the farmer's farm is no 7x7 workshop) is planned anew
        boolean stale = state != null && state.maxTier > 0
                && !state.origin.equals(site.subtract(layout(profession).focal().rotate(state.rotation)));
        if (state == null || stale || !state.anchor.equals(site) || !state.profession.equals(profession)) {
            if (state != null && !stale && state.maxTier == 0 && state.checkedDay == day && state.anchor.equals(site)) {
                return null;
            }
            state = plan(level, villager, profession, site);
            if (state.maxTier > 0) com.vnap.village.town.Roads.claimWorkshop(level, site, footprint(state));
            state.checkedDay = day;
            data.vnap$setWorkshop(state);
        }
        if (state.maxTier == 0) {
            return null;
        }
        resolveSavedJob(level, state);
        if (state.job == null && state.checkedDay != day && workTime(level)) {
            state.checkedDay = day;
            startNext(level, villager, state);
        }
        return state;
    }

    /** Takes the job block at {@code anchor} back when nobody else holds it. */
    static boolean reclaim(ServerLevel level, Villager villager, BlockPos anchor) {
        var heldJobSite = com.vnap.Mc.profession(villager.getVillagerData()).heldJobSite();
        Optional<BlockPos> taken = level.getPoiManager().take(heldJobSite, (type, pos) -> pos.equals(anchor), anchor, 1);
        taken.ifPresent(pos -> villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), pos)));
        return taken.isPresent();
    }

    /** A finished job raises the tier; the villager says so. */
    public static void finish(Villager villager, WorkshopState state) {
        state.job = null;
        if (villager.level() instanceof ServerLevel level) {
            com.vnap.village.market.Market.settle(level, state.site, villager, com.vnap.village.home.Towns.bellOf(villager));
        }
        state.tier = Math.max(state.tier, state.jobTier);
        say(villager, "life_job_" + state.profession);
    }

    private static void resolveSavedJob(ServerLevel level, WorkshopState state) {
        if (state.savedJob != null) {
            state.job = BuildJob.load(state.savedJob, name -> schematic(level, name));
            state.savedJob = null;
        }
    }

    /** Picks the rotation around the job site that fits the highest tier. */
    public static WorkshopState plan(ServerLevel level, Villager villager, String profession, BlockPos site) {
        // a job block planned anew: what this town kept for it before is given back first
        com.vnap.village.town.Roads.claimWorkshop(level, site, null);
        com.vnap.village.town.Land others = otherWorkshops(level, villager);
        WorkshopState best = new WorkshopState(profession, site, site, Rotation.NONE, 0);
        for (Rotation rotation : Rotation.values()) {
            BlockPos origin = site.subtract(layout(profession).focal().rotate(rotation));
            for (int tier = 3; tier > best.maxTier; tier--) {
                if (fits(level, schematic(level, schematicName(profession, tier)), origin, rotation, others)) {
                    best = new WorkshopState(profession, site, origin, rotation, tier);
                    break;
                }
            }
        }
        return best;
    }

    public static BoundingBox footprint(BlockPos origin, Rotation rotation) {
        return BoundingBox.fromCorners(origin, BuildJob.at(origin, rotation, new BlockPos(SIZE - 1, SIZE - 1, SIZE - 1)));
    }

    /**
     * The ground given away round the villager (Land: houses, other workshops, the town's buildings and ways), but not
     * its own workshop's and not the plan's free lots: a workshop stands where its job block is.
     */
    private static com.vnap.village.town.Land otherWorkshops(ServerLevel level, Villager self) {
        WorkshopState own = self instanceof VillagerLifeData data ? data.vnap$workshop() : null;
        BoundingBox mine = own == null || own.maxTier == 0 ? null : footprint(own);
        return com.vnap.village.town.Land.around(level, self.blockPosition())
                .without(claim -> claim.layer() == com.vnap.village.town.Land.Layer.LOT || claim.box().equals(mine));
    }

    private static BlockState state(ServerLevel level, BlockPos pos, @Nullable BoundingBox cleared, int floor) {
        if (cleared == null || !cleared.isInside(pos) || pos.getY() < floor) return level.getBlockState(pos);
        return (pos.getY() == floor ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState();
    }

    /**
     * Every cell is free (air, plants, or natural ground on the floor layer), nothing floats, no fluid is
     * built over and no other workshop is touched.
     */
    public static boolean fits(ServerLevel level, SchematicSpec schematic, BlockPos origin, Rotation rotation, com.vnap.village.town.Land others) {
        return fits(level, schematic, origin, rotation, others, null, 0);
    }

    /**
     * {@code fits} as if a house in {@code cleared} were taken down first: inside it the floor at {@code floor} is
     * grass and everything above is air (what HomeProjects.demolish leaves).
     */
    public static boolean fits(ServerLevel level, SchematicSpec schematic, BlockPos origin, Rotation rotation, com.vnap.village.town.Land others,
                               @Nullable BoundingBox cleared, int clearedFloor) {
        return fits(level, schematic, origin, rotation, others, cleared, clearedFloor, FLOOR_Y);
    }

    /** {@code fits} for a plan whose ground floor is layer {@code groundY} (a house level with a cellar under it). */
    public static boolean fits(ServerLevel level, SchematicSpec schematic, BlockPos origin, Rotation rotation, com.vnap.village.town.Land others,
                               @Nullable BoundingBox cleared, int clearedFloor, int groundY) {
        if (schematic.blocks.isEmpty()) {
            return false;
        }
        Set<BlockPos> cells = new HashSet<>();
        for (SchematicSpec.PlacedBlock block : schematic.blocks) {
            cells.add(block.rel());
        }
        for (SchematicSpec.PlacedBlock block : schematic.blocks) {
            BlockPos rel = block.rel();
            BlockPos pos = BuildJob.at(origin, rotation, rel);
            if (!level.isLoaded(pos)) {
                return false;
            }
            if (block.state().isAir()) {
                // a doorway carved into a wall that already stands
                continue;
            }
            if (others.blocked(pos, 0)) {
                return false;
            }
            BlockState current = state(level, pos, cleared, clearedFloor);
            if (current.is(block.state().getBlock())) {
                continue;
            }
            boolean floor = rel.getY() <= groundY;
            if (!current.getFluidState().isEmpty() || !BuildJob.canTake(current, floor)) {
                return false;
            }
            boolean supported = rel.getY() > groundY + 1 || cells.contains(rel.below());
            if (!supported && !(floor && !current.isAir() && !current.canBeReplaced())) {
                BlockPos below = pos.below();
                if (!state(level, below, cleared, clearedFloor).isFaceSturdy(level, below, Direction.UP)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean loaded(ServerLevel level, SchematicSpec schematic, WorkshopState state) {
        BoundingBox box = com.vnap.village.home.HouseProject.footprint(schematic, state.origin, state.rotation);
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    /** Starts the next tier, or a repair of the current one. True when a job started. */
    static boolean startNext(ServerLevel level, Villager villager, WorkshopState state) {
        int want = Math.min(tierFor(com.vnap.Mc.villagerLevel(villager.getVillagerData())), state.maxTier);
        // a better workshop waits while the household owes the treasury a purse or more; the first one does not
        if (state.tier < want && (state.tier == 0 || !com.vnap.village.market.Market.deepInDebt(level, villager))) {
            int next = state.tier + 1;
            String name = schematicName(state.profession, next);
            SchematicSpec schematic = schematic(level, name);
            // part of the site out of the loaded land says nothing about the room there: try another day
            if (!loaded(level, schematic, state)) return false;
            if (!fits(level, schematic, state.origin, state.rotation, otherWorkshops(level, villager))) {
                state.maxTier = state.tier;
                return false;
            }
            BuildJob job = BuildJob.missing(level, name, schematic, state.origin, state.rotation, FLOOR_Y);
            if (job.size() == 0) {
                state.tier = next;
                return false;
            }
            state.job = job;
            state.jobTier = next;
            // the worker's household pays its workshop's materials (Market.finance): it buys them at the stall and builds
            // the first workshop is the worker's living (the town makes sure of it); a better one only on credit it can carry
            if (!com.vnap.village.market.Market.finance(level, villager, com.vnap.village.home.Towns.bellOf(villager), state.site, job.bill(level, Integer.MAX_VALUE),
                    state.tier == 0 ? com.vnap.village.market.Housing.Purpose.UPKEEP : com.vnap.village.market.Housing.Purpose.GROWTH)) {
                state.job = null;
                return false;
            }
            say(villager, "life_ws_begin");
            return true;
        }
        if (state.tier > 0 && loaded(level, schematic(level, schematicName(state.profession, state.tier)), state)) {
            String name = schematicName(state.profession, state.tier);
            BuildJob job = BuildJob.missing(level, name, schematic(level, name), state.origin, state.rotation, FLOOR_Y);
            if (job.size() > 0) {
                state.job = job;
                state.jobTier = state.tier;
                com.vnap.village.market.Market.finance(level, villager, com.vnap.village.home.Towns.bellOf(villager), state.site, job.bill(level, Integer.MAX_VALUE),
                        com.vnap.village.market.Housing.Purpose.UPKEEP);
                say(villager, "life_ws_repair");
                return true;
            }
        }
        return false;
    }

    static void say(Villager villager, String id) {
        Speech.lifeLine(villager, id);
    }
}
