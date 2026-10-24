package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.FamilyHome;
import com.vnap.dialogue.FamilyState;
import com.vnap.item.LifeItems;
import com.vnap.village.home.Pets;
import com.vnap.village.market.Market;
import com.vnap.village.script.WorkerControl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A wife keeps the home: in the morning and the evening she cooks at the hearth (a smoker, furnace or campfire
 * will do) and carries the meal to her children and her husband; in the afternoon she puts up one piece of home
 * decor. Bread in the husband's pockets is what vanilla counts when a couple is ready for a child.
 */
final class HomeChoreBehavior extends Behavior<Villager> {
    private static final int CHECK_EVERY = 200;
    private static final int COOK_WORK = 160;
    private static final List<String> DECOR = List.of("hearth", "pantry_crate", "cooking_pot", "vanity_table", "flower_vase",
            "standing_mirror", "family_portrait", "aquarium");

    private final LifeGate gate;
    private final ErrandRunner runner = new ErrandRunner();
    private final List<Errand> queue = new ArrayList<>();
    private boolean finished;
    private boolean begun;
    private long nextCheck;

    HomeChoreBehavior(LifeGate gate) {
        super(ImmutableMap.of(), 600, 1600);
        this.gate = gate;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
        nextCheck = now + CHECK_EVERY + villager.getRandom().nextInt(CHECK_EVERY);
        if (villager.isBaby() || villager.isSleeping() || villager.isNoAi() || !Family.female(villager)
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)) {
            return false;
        }
        FamilyState family = Family.state(villager);
        BlockPos anchor = Family.home(villager);
        if (anchor == null || !Family.homeNear(level, villager, anchor) || (!family.married() && Family.householdOf(level, villager).size() <= 1)) return false;
        long day = Family.day(level);
        float time = Family.dayFraction(level);
        queue.clear();
        String meal = time < 0.15F ? "breakfast" : time >= 0.42F && time < 0.55F ? "dinner" : null;
        if ("breakfast".equals(meal)) candles(level, anchor, false);
        if (time >= 0.5F && time < 0.58F && Family.once(villager, "candle", day)) {
            Errand light = lightCandle(level, villager, anchor);
            if (light != null) {
                queue.add(light);
                return true;
            }
        }
        if (meal != null && Family.once(villager, meal, day)) {
            BlockPos kitchen = kitchen(level, anchor);
            DecorBlockEntity pantry = Pantry.of(level, anchor);
            if (kitchen != null && Pantry.food(pantry) == 0) {
                Speech.queue(villager, "Woman Pantry Empty", null, 1L);
            } else if (kitchen != null) {
                queue.add(cook(villager, kitchen));
                for (Villager eater : eaters(level, villager)) queue.add(feed(level, villager, eater, anchor));
                Errand pet = "breakfast".equals(meal) ? feedPet(level, villager) : null;
                if (pet != null) queue.add(pet);
                return true;
            }
        }
        if (time >= 0.15F && time < 0.5F) {
            DecorBlockEntity pantry = Pantry.of(level, anchor);
            if (pantry != null && Pantry.food(pantry) < Pantry.WANTED && Market.carriesFood(villager)) {
                // back from the stall: the food goes into the pantry
                queue.add(Errand.at("fill_pantry", pantry.getBlockPos(), new ItemStack(Items.BREAD), 30, SoundEvents.BARREL_CLOSE, null, () -> {
                    if (pantry.isRemoved()) return;
                    for (ItemStack stack : Market.takeFood(villager)) {
                        Pantry.store(pantry, stack);
                        if (!stack.isEmpty()) villager.getInventory().addItem(stack);
                    }
                    Speech.queue(villager, "Woman Fills the Pantry", null, 1L);
                }));
                return true;
            }
            if (time < 0.42F && pantry != null && Pantry.food(pantry) < Pantry.WANTED && Family.once(villager, "pantry", day)) {
                Pantry.Supply supply = Pantry.supply(level, villager);
                if (supply != null) {
                    List<ItemStack> basket = new ArrayList<>();
                    queue.add(fetch(villager, supply, basket, Pantry.WANTED - Pantry.food(pantry)));
                    queue.add(store(villager, pantry, basket));
                    return true;
                }
                // no supplier has food: the stall sells it (a village without one goes without, as before)
                Market.wantFood(level, villager, Pantry.WANTED - Pantry.food(pantry));
            }
        }
        // a wife in the mayor's hall furnishes the family's floor of it, a piece at a time
        HouseProject hall = time >= 0.15F && time < 0.42F ? Mayors.hallAt(level, anchor) : null;
        com.vnap.village.build.BuildJob home = hall == null ? null : Mayors.furnishing(level, hall, Mayors.HOME);
        if (home != null) {
            queue.add(FamilyBuildBehavior.furnish(level, villager, home, "Woman Settles Into the Hall"));
            return true;
        }
        UUID husbandId = family.spouse;
        if (time >= 0.15F && time < 0.42F && husbandId != null && level.getEntity(husbandId) instanceof Villager husband
                && Family.NO_CRADLE.equals(Family.whyNoChild(level, villager, husband)) && Family.once(villager, "cradle", day)) {
            // the cradle for the baby they hope for, carried in and set down by the parents' bed
            queue.add(Errand.at("cradle", anchor, new ItemStack(Market.decor(com.vnap.dialogue.Infants.CRADLE)), 80, SoundEvents.WOOD_PLACE, null,
                    () -> com.vnap.dialogue.Infants.placeCradle(level, villager, anchor)));
            return true;
        }
        if (time >= 0.2F && time < 0.42F && Family.once(villager, "decor", day)) {
            Errand decor = decorate(level, villager, anchor);
            // the clothesline is the last thing: not while a piece of decor waits to be bought
            if (decor == null && Family.state(villager).wants.stream().noneMatch(want -> !want.item.equals(Market.FOOD))) {
                decor = clothesline(level, villager, anchor);
            }
            if (decor != null) {
                queue.add(decor);
                return true;
            }
        }
        return false;
    }

    /** The floor cell in front of a cooking block of the room: the hearth, a smoker, a furnace or a lit campfire. */
    static @Nullable BlockPos kitchen(ServerLevel level, BlockPos anchor) {
        // the pot by the hearth is where the meal is cooked, when there is one
        BlockPos pot = pot(level, anchor);
        if (pot != null) return pot;
        for (BlockPos floor : FamilyHome.room(level, anchor)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (isKitchen(level.getBlockState(floor.relative(side)))) return floor;
            }
        }
        return null;
    }

    /** The floor cell in front of the room's cooking pot. */
    private static @Nullable BlockPos pot(ServerLevel level, BlockPos anchor) {
        for (BlockPos floor : FamilyHome.room(level, anchor)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(floor.relative(side)).getBlock() instanceof DecorBlock decor && decor.name().equals("cooking_pot")) return floor;
            }
        }
        return null;
    }

    private static boolean isKitchen(BlockState state) {
        return state.is(Blocks.SMOKER) || state.is(Blocks.FURNACE) || (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT))
                || (state.getBlock() instanceof DecorBlock decor && decor.name().equals("hearth"));
    }

    private static List<Villager> eaters(ServerLevel level, Villager wife) {
        List<Villager> out = new ArrayList<>();
        for (Villager member : Family.householdOf(level, wife)) {
            if (!member.equals(wife) && member.closerThan(wife, 24.0)) out.add(member);
        }
        Villager husband = Family.state(wife).spouse != null && level.getEntity(Family.state(wife).spouse) instanceof Villager found
                && found.closerThan(wife, 24.0) && !out.contains(found) ? found : null;
        if (husband != null) out.add(husband);
        out.sort(Comparator.comparing((Villager member) -> !member.isBaby()));
        return out.size() > 3 ? out.subList(0, 3) : out;
    }

    private static Errand cook(Villager wife, BlockPos kitchen) {
        return Errand.at("cook", kitchen, new ItemStack(Items.POTATO), COOK_WORK, SoundEvents.SMOKER_SMOKE, ParticleTypes.SMOKE,
                () -> Speech.queue(wife, "Woman Dinner Is Ready", null, 1L))
                .onStart(() -> {
                    Speech.queue(wife, "Woman Cooks", null, 1L);
                    // the pot by the kitchen cell simmers while she cooks: steam, bubbling, the lid up
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        if (wife.level().getBlockEntity(kitchen.relative(side)) instanceof DecorBlockEntity be && be.name().equals("cooking_pot")) {
                            com.vnap.block.decor.TownDecor.simmer(be);
                        }
                    }
                });
    }

    private static Errand feed(ServerLevel level, Villager wife, Villager eater, BlockPos anchor) {
        return Errand.to("feed", eater, new ItemStack(LifeItems.HOME_MEAL), 20, SoundEvents.GENERIC_EAT, ParticleTypes.HEART,
                () -> Pantry.serve(level, wife, eater, Pantry.of(level, anchor)));
    }

    /** To a supplier's chest: takes what the pantry lacks (all the chest has, if less) into the basket. */
    private static Errand fetch(Villager wife, Pantry.Supply supply, List<ItemStack> basket, int wanted) {
        return Errand.at("fetch_food", supply.pos(), ItemStack.EMPTY, 30, SoundEvents.CHEST_OPEN, null, () -> {
            int count = Math.min(wanted, Life.count(supply.chest(), Pantry.FOOD));
            for (int i = 0; i < count; i++) {
                ItemStack one = Life.take(supply.chest(), Pantry.FOOD, 1);
                if (one != null) basket.add(one);
            }
        }).onStart(() -> Speech.queue(wife, "Woman Fetches Food", null, 1L));
    }

    private static Errand store(Villager wife, DecorBlockEntity pantry, List<ItemStack> basket) {
        return Errand.at("fill_pantry", pantry.getBlockPos(), new ItemStack(Items.BREAD), 30, SoundEvents.BARREL_CLOSE, null, () -> {
            if (basket.isEmpty() || pantry.isRemoved()) return;
            for (ItemStack stack : basket) Pantry.store(pantry, stack);
            basket.clear();
            Speech.queue(wife, "Woman Fills the Pantry", null, 1L);
        });
    }

    /** One piece of home decor against a wall of the room (the cradle is the newborn's alone: Infants). */
    private static @Nullable Errand decorate(ServerLevel level, Villager wife, BlockPos anchor) {
        List<String> wanted = new ArrayList<>();
        FamilyState house = headState(level, wife);
        Entity pet = house == null || house.pet == null ? null : level.getEntity(house.pet);
        if (pet instanceof Cat) wanted.addAll(List.of("cat_bed", "pet_bowl"));
        if (pet instanceof Parrot) wanted.addAll(List.of("parrot_perch", "pet_bowl"));
        wanted.addAll(DECOR);
        Set<BlockPos> room = FamilyHome.room(level, anchor);
        String pick = null;
        for (String name : wanted) {
            if (!present(level, room, name)) {
                pick = name;
                break;
            }
        }
        DecorBlock block = pick == null ? null : decorBlock(pick);
        if (block == null) return null;
        List<BlockPos> spots = new ArrayList<>(FamilyHome.decorSpots(level, anchor));
        BlockPos hearth = "cooking_pot".equals(pick) ? kitchen(level, anchor) : null;
        // the pot stands by the hearth (no hearth yet: it waits for one)
        if ("cooking_pot".equals(pick) && hearth == null) return null;
        if (hearth != null) spots.sort(java.util.Comparator.comparingDouble(cell -> cell.distSqr(hearth)));
        for (BlockPos cell : spots) {
            Direction wall = wallSide(level, cell);
            if (wall == null) continue;
            BlockState state = block.defaultBlockState().setValue(DecorBlock.FACING, wall.getOpposite());
            // a village with a stall: bought first, put up the next time
            if (!Market.obtain(level, wife, block.asItem())) return null;
            return Errand.at("decorate", cell, new ItemStack(block), 80, SoundEvents.WOOD_PLACE, null, () -> {
                if (!level.getBlockState(cell).isAir()) return;
                level.setBlock(cell, state, Block.UPDATE_ALL);
                Market.spend(wife, block.asItem());
                Speech.queue(wife, "Woman Decorates", null, 1L);
            });
        }
        return null;
    }

    /** The household head's family state (the pet, the kennel live there). */
    static @Nullable FamilyState headState(ServerLevel level, Villager member) {
        FamilyState family = Family.state(member);
        return family.head != null && level.getEntity(family.head) instanceof Villager head ? Family.state(head) : null;
    }

    /** Mornings: a helping from the pantry into the pet's bowl, by the kennel or in the house. */
    private static @Nullable Errand feedPet(ServerLevel level, Villager wife) {
        FamilyState house = headState(level, wife);
        if (house == null || house.pet == null) return null;
        DecorBlockEntity bowl = Pets.bowl(level, house);
        if (bowl == null || Pantry.food(bowl) > 0) return null;
        return Errand.at("feed_pet", bowl.getBlockPos(), new ItemStack(Items.BREAD), 30, SoundEvents.GENERIC_EAT, null,
                () -> Pets.fillBowl(level, wife, house));
    }

    /** Evenings: a lit candle by the head's bed, so the windows of a family house glow at night. */
    private static @Nullable Errand lightCandle(ServerLevel level, Villager wife, BlockPos anchor) {
        BlockPos candle = candleNear(level, anchor);
        if (candle == null) {
            for (BlockPos cell : FamilyHome.decorSpots(level, anchor)) {
                if (cell.closerThan(anchor, 2.5)) {
                    candle = cell;
                    break;
                }
            }
        }
        if (candle == null || (level.getBlockState(candle).getBlock() instanceof CandleBlock && level.getBlockState(candle).getValue(CandleBlock.LIT))) {
            return null;
        }
        BlockPos at = candle;
        // a new candle is bought (with a stall in the village); an old one is only lit
        if (level.getBlockState(at).isAir() && !Market.obtain(level, wife, Items.CANDLE)) return null;
        return Errand.at("light_candle", at, new ItemStack(Items.FLINT_AND_STEEL), 20, SoundEvents.FLINTANDSTEEL_USE, null, () -> {
            BlockState state = level.getBlockState(at);
            if (state.isAir()) {
                level.setBlock(at, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), Block.UPDATE_ALL);
                Market.spend(wife, Items.CANDLE);
            }
            else if (state.getBlock() instanceof CandleBlock) level.setBlock(at, state.setValue(CandleBlock.LIT, true), Block.UPDATE_ALL);
            Speech.queue(wife, "Woman Lights the Candle", null, 1L);
        });
    }

    /** Mornings: the candle goes out. */
    static void candles(ServerLevel level, BlockPos anchor, boolean lit) {
        BlockPos candle = candleNear(level, anchor);
        if (candle != null) level.setBlock(candle, level.getBlockState(candle).setValue(CandleBlock.LIT, lit), Block.UPDATE_ALL);
    }

    static @Nullable BlockPos candleNear(ServerLevel level, BlockPos anchor) {
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-2, -1, -2), anchor.offset(2, 1, 2))) {
            if (level.getBlockState(pos).getBlock() instanceof CandleBlock) return pos.immutable();
        }
        return null;
    }

    /** The last thing a wife puts up: a clothesline in the open yard near the house, once. */
    private static @Nullable Errand clothesline(ServerLevel level, Villager wife, BlockPos anchor) {
        DecorBlock line = decorBlock("clothesline");
        if (line == null) return null;
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-10, -2, -10), anchor.offset(10, 2, 10))) {
            if (level.getBlockState(pos).is(line)) return null;
        }
        Set<BlockPos> room = FamilyHome.room(level, anchor);
        BlockPos best = null;
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-8, -1, -8), anchor.offset(8, 1, 8))) {
            if (pos.closerThan(anchor, 4.0) || room.contains(pos) || !level.getBlockState(pos).isAir() || !level.canSeeSky(pos)
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                    || !level.getBlockState(pos.above()).isAir()) continue;
            boolean nearDoor = false;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(side)).is(BlockTags.DOORS) || level.getBlockState(pos.relative(side, 2)).is(BlockTags.DOORS)) {
                    nearDoor = true;
                }
            }
            if (nearDoor) continue;
            if (best == null || pos.distSqr(anchor) < best.distSqr(anchor)) best = pos.immutable();
        }
        if (best == null) return null;
        BlockPos at = best;
        BlockState state = line.defaultBlockState().setValue(DecorBlock.FACING,
                Direction.getNearest(anchor.getX() - at.getX(), 0, anchor.getZ() - at.getZ()).getClockWise());
        if (!Market.obtain(level, wife, line.asItem())) return null;
        return Errand.at("clothesline", at, new ItemStack(line), 80, SoundEvents.WOOL_PLACE, null, () -> {
            if (!level.getBlockState(at).isAir()) return;
            level.setBlock(at, state, Block.UPDATE_ALL);
            Market.spend(wife, line.asItem());
            Speech.queue(wife, "Woman Decorates", null, 1L);
        });
    }

    private static boolean present(ServerLevel level, Set<BlockPos> room, String name) {
        for (BlockPos floor : room) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(floor.relative(side)).getBlock() instanceof DecorBlock decor && decor.name().equals(name)) return true;
            }
        }
        return false;
    }

    private static @Nullable DecorBlock decorBlock(String name) {
        for (DecorBlock block : LifeBlocks.HOME_DECOR) {
            if (block.name().equals(name)) return block;
        }
        return null;
    }

    private static @Nullable Direction wallSide(ServerLevel level, BlockPos cell) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = cell.relative(side);
            if (level.getBlockState(next).isFaceSturdy(level, next, side.getOpposite())) return side;
        }
        return null;
    }


    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        finished = false;
        begun = false;
        runner.reset();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        if (queue.isEmpty()) {
            finished = true;
            return;
        }
        Errand errand = queue.get(0);
        if (!begun) {
            // ErrandRunner leaves the start hook to the behaviour (WorkBehavior does the same)
            begun = true;
            if (errand.start != null) errand.start.run();
        }
        switch (runner.tick(level, villager, errand)) {
            case DONE, FAILED -> {
                queue.remove(0);
                runner.reset();
                begun = false;
                finished = queue.isEmpty();
            }
            default -> {
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !com.vnap.dialogue.Infants.minding(level, villager) && !finished && villager.isAlive() && !villager.isSleeping() && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = false;
        queue.clear();
        runner.reset();
        WorkerControl.hold(villager, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
}
