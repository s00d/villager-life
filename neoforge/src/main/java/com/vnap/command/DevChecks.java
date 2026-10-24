package com.vnap.command;

import com.vnap.village.town.TownRules;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyState;
import com.vnap.dialogue.VillagerHome;
import com.vnap.entity.VillagerLifeData;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.life.Plots;
import com.vnap.village.script.VillageScriptDirector;
import com.vnap.village.script.WorkerControl;
import com.vnap.village.workshop.WorkshopState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * /vnap check: every kind of trouble found by hand in the dev world, looked for in one pass around the player, so
 * tools/dev/scenario.py can run a village for days and assert that none of it came back.
 */
final class DevChecks {
    private DevChecks() {
    }

    static List<String> run(ServerLevel level, BlockPos centre, int radius) {
        List<String> problems = new ArrayList<>();
        float time = Family.dayFraction(level);
        boolean night = time >= 0.58F && time <= 0.96F;
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, new AABB(centre).inflate(radius), Villager::isAlive);
        for (Villager villager : villagers) {
            String who = VillagerHome.nameOf(villager) + " @" + villager.blockPosition().toShortString() + ": ";
            VillagerLifeData data = (VillagerLifeData) villager;
            BlockPos bed = data.vnap$homeBed();
            if (com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG) && !VillageScriptDirector.isWorker(villager.getUUID())) {
                problems.add(who + "worker tag with no rite running");
            }
            if (bed != null && level.isLoaded(bed)) {
                BlockState state = level.getBlockState(bed);
                if (!(state.getBlock() instanceof BedBlock)) problems.add(who + "its bed " + bed.toShortString() + " is gone");
                else if (state.getValue(BedBlock.PART) != BedPart.HEAD) problems.add(who + "claim on the foot of " + bed.toShortString());
                else if (bed.closerThan(villager.blockPosition(), 48.0) && !villager.getBrain().getMemory(MemoryModuleType.HOME)
                        .map(GlobalPos::pos).filter(bed::equals).isPresent()) {
                    problems.add(who + "HOME does not point at its bed");
                }
            }
            FamilyState family = Family.state(villager);
            if (family.spouse != null && level.getEntity(family.spouse) instanceof Villager spouse
                    && !villager.getUUID().equals(Family.state(spouse).spouse)) {
                problems.add(who + "married to " + VillagerHome.nameOf(spouse) + " who is not married to it");
            }
            if (villager.isInPowderSnow || level.getBlockState(villager.blockPosition()).is(Blocks.POWDER_SNOW)) {
                problems.add(who + "in powder snow");
            }
            if (night && !villager.isSleeping() && !villager.isBaby() && bed != null && !bed.closerThan(villager.blockPosition(), 8.0)) {
                problems.add(who + "awake at night " + (int) Math.sqrt(bed.distSqr(villager.blockPosition())) + " blocks from its bed");
            }
            WorkshopState shop = data.vnap$workshop();
            Plots.Paddock paddock = shop == null ? null : Plots.paddock(level, shop);
            if (paddock != null && Plots.standing(level, paddock)) {
                BlockState gate = level.getBlockState(paddock.gate());
                boolean shut = !gate.getValue(FenceGateBlock.OPEN);
                for (Villager inside : villagers) {
                    if (shut && paddock.contains(inside.blockPosition())) {
                        problems.add(VillagerHome.nameOf(inside) + ": shut in the paddock of " + VillagerHome.nameOf(villager));
                    }
                }
            }
        }
        Mayors mayors = Mayors.get(level.getServer());
        java.util.Map<Mayors.Office, Villager> seated = new java.util.HashMap<>();
        for (Villager villager : villagers) {
            if (!Mayors.isMayor(villager)) continue;
            String who = VillagerHome.nameOf(villager) + " @" + villager.blockPosition().toShortString() + ": ";
            Mayors.Office office = mayors.officeOf(villager.getUUID());
            if (office == null) problems.add(who + "mayor with no office");
            else if (seated.put(office, villager) != null) problems.add(who + "a second mayor of the bell " + office.bell.toShortString());
            if (villager.isBaby()) problems.add(who + "a child mayor");
            if (!com.vnap.Mc.is(villager.getVillagerData(), net.minecraft.world.entity.npc.VillagerProfession.NONE)) {
                problems.add(who + "mayor with a trade");
            }
        }
        for (Mayors.Office office : mayors.offices()) {
            if (!office.dimension.equals(level.dimension()) || !office.bell.closerThan(centre, radius)) continue;
            for (Mayors.Office other : mayors.offices()) {
                if (other != office && other.mayor != null && office.mayor != null && other.dimension.equals(office.dimension)
                        && other.bell.closerThan(office.bell, TownRules.SPAN)) {
                    problems.add("two mayors' offices " + office.bell.toShortString() + " and " + other.bell.toShortString() + " in one village");
                }
            }
        }
        com.vnap.village.market.Shops shops = com.vnap.village.market.Shops.get(level.getServer());
        for (com.vnap.village.market.Shops.Shop shop : shops.shops()) {
            if (!shop.bell.closerThan(centre, radius + TownRules.SPAN)) continue;
            if (shop.till < 0) problems.add("stall at " + shop.bell.toShortString() + ": till below zero (" + shop.till + ")");
            for (com.vnap.village.market.Shops.Lot lot : shop.stock) {
                if (lot.count < 0) problems.add("stall at " + shop.bell.toShortString() + ": negative stock of " + lot.item);
            }
            if (shop.keeper != null && level.getEntity(shop.keeper) instanceof Villager keeper
                    && (!com.vnap.village.market.Shops.isKeeper(keeper)
                    || !com.vnap.Mc.is(keeper.getVillagerData(), net.minecraft.world.entity.npc.VillagerProfession.NONE))) {
                problems.add(VillagerHome.nameOf(keeper) + ": keeps the stall but is not a plain shopkeeper");
            }
        }
        for (Villager villager : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(centre).inflate(radius))) {
            if (com.vnap.village.market.Shops.isKeeper(villager) && shops.shopOf(villager.getUUID()) == null) {
                problems.add(VillagerHome.nameOf(villager) + ": a shopkeeper without a stall");
            }
            if (Family.state(villager).coins < 0) problems.add(VillagerHome.nameOf(villager) + ": negative purse");
        }
        for (HouseProject house : VillageMoodSavedData.get(level).houses()) {
            if (!house.done || !house.origin.closerThan(centre, radius)
                    || com.vnap.village.market.Shops.STALLS.contains(house.plan)) continue;
            BlockPos houseBed = house.bed(level);
            if (level.getEntity(house.owner) instanceof Villager owner
                    && ((VillagerLifeData) owner).vnap$homeBed() == null && !Family.member(owner)
                    && (houseBed == null || VillagerHome.claimable(level, houseBed))) {
                problems.add(VillagerHome.nameOf(owner) + ": its house at " + house.origin.toShortString() + " is done and empty, it is homeless");
            }
        }
        int scan = Math.min(radius, 48);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-scan, -16, -scan), centre.offset(scan, 16, scan))) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !(level.getBlockState(pos.above()).getBlock() instanceof DoorBlock)) {
                problems.add("half a door at " + pos.toShortString());
            } else if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD
                    && !(level.getBlockState(pos.relative(state.getValue(BedBlock.FACING).getOpposite())).getBlock() instanceof BedBlock)) {
                problems.add("half a bed at " + pos.toShortString());
            } else if (level.getBlockEntity(pos) instanceof SignBlockEntity sign
                    && VillagerHome.isMarker(com.vnap.Mc.signLine(sign, 1))) {
                String owner = com.vnap.Mc.signLine(sign, 0);
                boolean lives = !level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(96.0), villager -> villager.isAlive()
                        && villager.getCustomName() != null && Objects.equals(villager.getCustomName().getString().trim(), owner)).isEmpty();
                if (!lives) problems.add("home sign of " + owner + " at " + pos.toShortString() + ": nobody of that name around");
            }
        }
        // a town whose bell the world buried in a cave gets a new one above (BellMove)
        for (com.vnap.village.town.Roads.Network network : com.vnap.village.town.Roads.get(level.getServer()).networks()) {
            if (network.dimension.equals(level.dimension()) && network.bell.closerThan(centre, radius)
                    && com.vnap.village.town.BellMove.buried(level, network.bell) && network.bellMove == null) {
                problems.add("bell " + network.bell.toShortString() + ": buried, no new bell planned");
            }
        }
        return problems;
    }
}
