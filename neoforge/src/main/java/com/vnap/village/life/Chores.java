package com.vnap.village.life;

import com.vnap.village.town.TownRules;
import com.vnap.fx.VillageParticles;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import com.vnap.village.market.Farm;
import com.vnap.village.market.Produce;
import com.vnap.entity.HandCart;
import com.vnap.item.LifeItems;
import com.vnap.sound.GolemSounds;
import com.vnap.village.market.Shops;

/**
 * Every profession's day as a list of chores tried in order; the first with work to do and quota left becomes the
 * next {@link Errand}. Nothing appears from nothing: crops grow from seeds in the chest, herds from animals around
 * and their calves, trees from saplings, goods from the materials in the chest.
 */
public final class Chores {
    /** Small work, a few seconds at the block. */
    static final int SHORT = 40;
    static final int LONG = 100;

    private static final Map<String, List<Function<Life, @Nullable Errand>>> BY_PROFESSION = Map.ofEntries(
            Map.entry("farmer", List.of(Chores::sell, Chores::harvest, Chores::honey, Chores::plant, Chores::fellTree, Chores::plantTree,
                    Chores::tendTree, Chores::deliverWheat, Chores::hive, Chores::bees, Chores::flowers)),
            Map.entry("butcher", List.of(Chores::tendGate, Chores::receiveStock, Chores::sell, Chores::cull, Chores::breed, Chores::adopt, Chores::buyStock)),
            Map.entry("shepherd", List.of(Chores::tendGate, Chores::receiveStock, Chores::sell, Chores::shear, Chores::dye, Chores::breed, Chores::adopt, Chores::buyStock)),
            Map.entry("fisherman", List.of(Chores::sell, Chores::fish)),
            Map.entry("fletcher", List.of(Chores::sell, Chores::gather, Chores::fletch)),
            Map.entry("leatherworker", List.of(Chores::sell, Chores::fillCauldron, Chores::fetchHides, Chores::tan)),
            Map.entry("librarian", List.of(Chores::sell, Chores::copyBook, Chores::read)),
            Map.entry("cartographer", List.of(Chores::sell, Chores::survey, Chores::drawMap)),
            Map.entry("cleric", List.of(Chores::sell, Chores::heal, Chores::candles)),
            Map.entry("mason", List.of(Chores::sell, Chores::mend, Chores::cutStone)),
            Map.entry("armorer", List.of(Chores::sell, Chores::repairGolem)),
            Map.entry("toolsmith", List.of(Chores::sell, Chores::giveHoe, Chores::sharpen)),
            Map.entry("weaponsmith", List.of(Chores::sell, Chores::temper)));

    private Chores() {
    }

    public static boolean professionHasChores(String profession) {
        return BY_PROFESSION.containsKey(profession);
    }

    static @Nullable Errand next(Life life) {
        for (Function<Life, @Nullable Errand> chore : BY_PROFESSION.getOrDefault(life.state.profession, List.of())) {
            Errand errand = chore.apply(life);
            if (errand != null) {
                return errand;
            }
        }
        return null;
    }

    /** Does the next chore on the spot, with its follow-up trips; the name of the chore, or null when there is none. */
    public static @Nullable String perform(ServerLevel level, Villager villager, WorkshopState state, long day) {
        Life life = new Life(level, villager, state, day);
        Errand errand = next(life);
        if (errand == null) {
            return null;
        }
        String chore = errand.chore;
        while (errand != null) {
            if (errand.start != null) errand.start.run();
            errand.done.run();
            errand = life.pending;
            life.pending = null;
        }
        return chore;
    }

    private static Errand counted(Life life, Errand errand) {
        Runnable done = errand.done;
        return new Errand(errand.chore, errand.at, errand.mob, errand.tool, errand.workTicks, errand.sound, errand.particle, () -> {
            life.count(errand.chore);
            done.run();
        }).withHooks(errand);
    }

    // ---------------------------------------------------------------- farmer

    private static @Nullable Errand harvest(Life life) {
        Farm.Plot farm = Farm.of(life.level, life.state);
        if (farm == null || !life.under("harvest", 32) || life.chest() == null) return null;
        for (BlockPos pos : Farm.cropCells(farm).keySet()) {
            if (!life.level.isLoaded(pos)) continue;
            BlockState crop = life.level.getBlockState(pos);
            if (crop.getBlock() instanceof CropBlock block && block.isMaxAge(crop)) {
                BlockPos at = pos.immutable();
                return counted(life, Errand.at("harvest", at, new ItemStack(Items.IRON_HOE), SHORT,
                        SoundEvents.CROP_BREAK, new BlockParticleOption(ParticleTypes.BLOCK, crop), () -> {
                    BlockState now = life.level.getBlockState(at);
                    if (!(now.getBlock() instanceof CropBlock cropBlock) || !cropBlock.isMaxAge(now)) return;
                    List<ItemStack> drops = Block.getDrops(now, life.level, at, null);
                    life.level.setBlock(at, cropBlock.getStateForAge(0), 3);
                    boolean replanted = false;
                    for (ItemStack drop : drops) {
                        if (!replanted && drop.getItem() instanceof BlockItem seed && seed.getBlock().equals(cropBlock)) {
                            drop.shrink(1);
                            replanted = true;
                        }
                        // a full chest leaves the produce on the field, it does not vanish
                        if (!drop.isEmpty() && !life.room(drop.getItem())) Block.popResource(life.level, at, drop);
                        else life.put(drop);
                    }
                    if (!replanted) life.level.removeBlock(at, false);
                }));
            }
        }
        return null;
    }

    private static @Nullable Block cropOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock ? item.getBlock() : null;
    }

    private static @Nullable Errand plant(Life life) {
        Farm.Plot farm = Farm.of(life.level, life.state);
        if (farm == null || !life.under("plant", 32) || life.has(stack -> cropOf(stack) != null) == 0) return null;
        for (BlockPos cell : Farm.cropCells(farm).keySet()) {
            if (!life.level.isLoaded(cell)) continue;
            if (life.level.getBlockState(cell.below()).is(Blocks.FARMLAND) && life.level.getBlockState(cell).isAir()) {
                return counted(life, Errand.at("plant", cell, new ItemStack(Items.WHEAT_SEEDS), SHORT,
                        SoundEvents.CROP_PLANTED, ParticleTypes.HAPPY_VILLAGER, () -> {
                    Container chest = life.chest();
                    ItemStack seed = Life.take(chest, stack -> cropOf(stack) != null, 1);
                    Block crop = seed == null ? null : cropOf(seed);
                    if (crop != null && life.level.getBlockState(cell).isAir()) {
                        life.level.setBlock(cell, crop.defaultBlockState(), 3);
                    }
                }));
            }
        }
        return null;
    }

    /** A grown tree on the farm: felled whole, its logs and two saplings into the chest. */
    private static @Nullable Errand fellTree(Life life) {
        Farm.Plot farm = Farm.of(life.level, life.state);
        Container chest = life.chest();
        if (farm == null || chest == null || !life.under("fell_tree", 3)) return null;
        for (BlockPos spot : Farm.treeSpots(farm)) {
            if (!Farm.grown(life.level, spot)) continue;
            return counted(life, Errand.at("fell_tree", spot, new ItemStack(Items.IRON_AXE), LONG * 2, SoundEvents.WOOD_HIT, null,
                    () -> Farm.fell(life.level, farm, spot, chest))
                    .onStart(() -> com.vnap.dialogue.Speech.queue(life.villager, "Villager Fells a Tree", null, 1L)));
        }
        return null;
    }

    private static @Nullable Errand plantTree(Life life) {
        Farm.Plot farm = Farm.of(life.level, life.state);
        Container chest = life.chest();
        if (farm == null || chest == null) return null;
        for (BlockPos spot : Farm.treeSpots(farm)) {
            if (!life.level.isLoaded(spot) || !life.level.getBlockState(spot).isAir() || life.has(Farm.saplingOf(farm, spot)) == 0) continue;
            return counted(life, Errand.at("plant_tree", spot, new ItemStack(Farm.saplingOf(farm, spot)), SHORT, SoundEvents.GRASS_PLACE,
                    null, () -> Farm.plant(life.level, farm, spot, chest)));
        }
        return null;
    }

    /** Each sapling once a day: one step of its growth. */
    private static @Nullable Errand tendTree(Life life) {
        Farm.Plot farm = Farm.of(life.level, life.state);
        if (farm == null || !life.under("tend_tree", Farm.treeSpots(farm).size())) return null;
        for (BlockPos spot : Farm.treeSpots(farm)) {
            if (!life.level.isLoaded(spot) || !(life.level.getBlockState(spot).getBlock() instanceof net.minecraft.world.level.block.SaplingBlock)
                    || life.state.tended.contains(spot.asLong())) continue;
            life.state.tended.add(spot.asLong());
            return counted(life, Errand.at("tend_tree", spot, new ItemStack(Items.BONE_MEAL), SHORT, SoundEvents.BONE_MEAL_USE,
                    ParticleTypes.HAPPY_VILLAGER, () -> Farm.tend(life.level, spot)));
        }
        return null;
    }

    /**
     * Every trade carries what its chest holds beyond what it keeps to the stall: its wage in kind. In its arms it
     * takes {@link Produce#HAND} items a trip; with more than two armfuls waiting it buys a hand cart, which takes
     * {@link Produce#CART}: loaded at the chest, it rolls behind the worker to the stall and back to its stand.
     */
    private static @Nullable Errand sell(Life life) {
        Container chest = life.chest();
        Shops.Shop shop = Shops.of(life.level, life.villager);
        BlockPos counter = shop == null ? null : shop.counter;
        if (chest == null || shop == null || counter == null || !Shops.open(life.level, shop)) return null;
        Map<Item, Integer> sale = Produce.forSale(life.level, life.state, chest);
        HandCart cart = HandCart.of(life.level, life.state.anchor);
        if (cart == null) {
            Errand bought = cartArrives(life, sale);
            if (bought != null) return bought;
            if (!life.under("sell", 3) || !Produce.worthSelling(shop, sale, Produce.HAND)) return null;
            ItemStack carried = sale.keySet().stream().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
            return counted(life, Errand.at("sell", counter, carried, SHORT, SoundEvents.VILLAGER_TRADE, null,
                    () -> Produce.sell(life.level, life.villager, shop, life.state, chest, Produce.HAND)));
        }
        if (!life.under("sell", 2) || !Produce.worthSelling(shop, Produce.with(sale, cart.load()), Produce.CART)) return null;
        Runnable hitch = () -> cart.hitch(life.villager);
        Errand home = Errand.at("park", cart.park(), ItemStack.EMPTY, 10, null, null, cart::parkNow).onStart(hitch).onCancel(cart::parkNow);
        Errand stall = Errand.at("sell", counter, ItemStack.EMPTY, SHORT, SoundEvents.VILLAGER_TRADE, null, () -> {
            Produce.sellLoad(life.level, life.villager, shop, cart.load());
            life.pending = home;
        }).onStart(hitch).onCancel(cart::parkNow);
        ItemStack carried = sale.keySet().stream().findFirst().map(ItemStack::new).orElse(ItemStack.EMPTY);
        return counted(life, Errand.at("sell", life.chestPos(), carried, SHORT, GolemSounds.CART_LOAD, null, () -> {
            Produce.load(life.level, life.state, chest, cart.load(), Produce.CART - cart.count());
            life.pending = stall;
        }).onStart(hitch).onCancel(cart::parkNow));
    }

    /** More than two armfuls wait: the worker means to buy a cart; bought, it sets it down by its chest. */
    private static @Nullable Errand cartArrives(Life life, Map<Item, Integer> sale) {
        if (Produce.total(sale) <= 2 * Produce.HAND || !life.under("cart", 1)
                || !com.vnap.village.market.Market.obtain(life.level, life.villager, LifeItems.HAND_CART)) return null;
        BlockPos park = parkSpot(life.level, life.chestPos());
        if (park == null) return null;
        return counted(life, Errand.at("cart", park, new ItemStack(LifeItems.HAND_CART), SHORT, SoundEvents.WOOD_PLACE, null, () -> {
            float yaw = Direction.fromYRot(life.villager.getYRot()).toYRot();
            if (HandCart.of(life.level, life.state.anchor) == null && HandCart.place(life.level, life.state.anchor, park, yaw) != null) {
                com.vnap.village.market.Market.spend(life.villager, LifeItems.HAND_CART);
            }
        }));
    }

    /** The cart's stand: free ground right by the chest (not a field, not the water), nearest first. */
    public static @Nullable BlockPos parkSpot(ServerLevel level, BlockPos chest) {
        for (int r = 1; r <= 3; r++) {
            for (int dy = 0; dy >= -1; dy--) {
                for (BlockPos at : BlockPos.betweenClosed(chest.offset(-r, dy, -r), chest.offset(r, dy, r))) {
                    if (Math.max(Math.abs(at.getX() - chest.getX()), Math.abs(at.getZ() - chest.getZ())) != r || !level.isLoaded(at)) continue;
                    if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && level.getFluidState(at.below()).isEmpty()
                            && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) return at.immutable();
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- the farmer's bees

    /** What a bee costs from the drover. */
    public static final int BEE_PRICE = 8;

    /** The farm's beehive (a grown farm sets one up by its trees), or null. */
    public static @Nullable BlockPos hive(ServerLevel level, WorkshopState state) {
        net.minecraft.world.level.levelgen.structure.BoundingBox box = Workshops.footprint(state).inflatedBy(1);
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.BEEHIVE)) return pos.immutable();
        }
        return null;
    }

    /** From tier two the farmer buys a hive and sets it beside a tree of the farm, on free ground. */
    private static @Nullable Errand hive(Life life) {
        if (life.state.tier < 2 || !life.under("hive", 1) || hive(life.level, life.state) != null) return null;
        Farm.Plot farm = Farm.of(life.level, life.state);
        if (farm == null) return null;
        BlockPos park = parkSpot(life.level, life.chestPos());
        BlockPos spot = null;
        for (BlockPos tree : Farm.treeSpots(farm)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos at = tree.relative(side, 2);
                if (!at.equals(park) && life.level.isLoaded(at) && life.level.getBlockState(at).isAir() && life.level.getBlockState(at.above()).isAir()
                        && life.level.getBlockState(at.below()).isFaceSturdy(life.level, at.below(), Direction.UP)
                        && !Farm.cropCells(farm).containsKey(at) && !Farm.cropCells(farm).containsKey(at.below())) {
                    spot = at.immutable();
                    break;
                }
            }
            if (spot != null) break;
        }
        BlockPos at = spot;
        if (at == null || !com.vnap.village.market.Market.obtain(life.level, life.villager, Items.BEEHIVE)) return null;
        Direction face = Direction.getNearest(life.chestPos().getX() - at.getX(), 0, life.chestPos().getZ() - at.getZ());
        return counted(life, Errand.at("hive", at, new ItemStack(Items.BEEHIVE), SHORT, SoundEvents.WOOD_PLACE, null, () -> {
            if (!life.level.getBlockState(at).isAir()) return;
            life.level.setBlock(at, Blocks.BEEHIVE.defaultBlockState().setValue(net.minecraft.world.level.block.BeehiveBlock.FACING,
                    face.getAxis().isHorizontal() ? face : Direction.NORTH), Block.UPDATE_ALL);
            com.vnap.village.market.Market.spend(life.villager, Items.BEEHIVE);
        }));
    }

    /** An empty hive and no bees of it about: a pair from the drover (the household pays, the treasury the rest). */
    private static @Nullable Errand bees(Life life) {
        BlockPos hive = hive(life.level, life.state);
        Shops.Shop shop = Shops.of(life.level, life.villager);
        if (hive == null || shop == null || !life.under("bees", 1)
                || !(life.level.getBlockEntity(hive) instanceof net.minecraft.world.level.block.entity.BeehiveBlockEntity combs) || !combs.isEmpty()
                || !life.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Bee.class, new AABB(hive).inflate(32.0), bee -> true).isEmpty()) return null;
        return counted(life, Errand.at("bees", hive, ItemStack.EMPTY, SHORT, SoundEvents.BEEHIVE_ENTER, null, () -> {
            com.vnap.village.market.Market.buyAbroad(life.level, life.villager, shop, 2 * BEE_PRICE);
            for (int i = 0; i < 2; i++) {
                net.minecraft.world.entity.animal.Bee bee = com.vnap.Mc.create(net.minecraft.world.entity.EntityType.BEE, life.level);
                if (bee == null) continue;
                com.vnap.Mc.snap(bee, net.minecraft.world.phys.Vec3.atBottomCenterOf(hive.above()));
                life.level.addFreshEntity(bee);
                combs.addOccupant(bee);
            }
        }));
    }

    /** A full hive: the farmer bottles its honey (bottles from the chest), calmly: the bees stay calm. */
    private static @Nullable Errand honey(Life life) {
        BlockPos hive = hive(life.level, life.state);
        if (hive == null || net.minecraft.world.level.block.entity.BeehiveBlockEntity.getHoneyLevel(life.level.getBlockState(hive)) < net.minecraft.world.level.block.BeehiveBlock.MAX_HONEY_LEVELS
                || life.has(Items.GLASS_BOTTLE) == 0 || !life.room(Items.HONEY_BOTTLE)) return null;
        return counted(life, Errand.at("honey", hive, new ItemStack(Items.GLASS_BOTTLE), SHORT, SoundEvents.BOTTLE_FILL, null, () -> {
            BlockState state = life.level.getBlockState(hive);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.BeehiveBlock block)
                    || net.minecraft.world.level.block.entity.BeehiveBlockEntity.getHoneyLevel(state) < net.minecraft.world.level.block.BeehiveBlock.MAX_HONEY_LEVELS
                    || !life.take(Items.GLASS_BOTTLE, 1)) return;
            block.resetHoneyLevel(life.level, state, hive);
            life.put(new ItemStack(Items.HONEY_BOTTLE));
        }));
    }

    /** Fewer than three flowers by the hive: bone meal from the chest on the grass near it (bees make honey from them). */
    private static @Nullable Errand flowers(Life life) {
        BlockPos hive = hive(life.level, life.state);
        if (hive == null || !life.under("flowers", 1) || life.has(Items.BONE_MEAL) == 0) return null;
        int flowers = 0;
        BlockPos grass = null;
        for (BlockPos pos : BlockPos.betweenClosed(hive.offset(-5, -2, -5), hive.offset(5, 2, 5))) {
            BlockState state = life.level.getBlockState(pos);
            if (state.is(BlockTags.FLOWERS)) flowers++;
            if (grass == null && state.is(Blocks.GRASS_BLOCK) && life.level.getBlockState(pos.above()).isAir() && pos.distManhattan(hive) >= 2) grass = pos.immutable();
        }
        BlockPos at = grass;
        if (flowers >= 3 || at == null) return null;
        return counted(life, Errand.at("flowers", at.above(), new ItemStack(Items.BONE_MEAL), SHORT, SoundEvents.BONE_MEAL_USE, ParticleTypes.HAPPY_VILLAGER, () -> {
            if (life.take(Items.BONE_MEAL, 1)) net.minecraft.world.item.BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), life.level, at, null);
        }));
    }

    private static @Nullable Errand deliverWheat(Life life) {
        if (!life.under("deliver", 1) || life.has(Items.WHEAT) < 12) return null;
        for (String to : List.of("butcher", "shepherd")) {
            for (WorkshopState shop : life.workshops(to)) {
                Container other = life.chestOf(shop);
                if (other == null || other.countItem(Items.WHEAT) >= 8 || !Life.fits(other, new ItemStack(Items.WHEAT))) continue;
                BlockPos at = Workshops.chestPos(shop);
                return counted(life, Errand.at("deliver", at, new ItemStack(Items.WHEAT), SHORT,
                        SoundEvents.CHEST_OPEN, null, () -> {
                    if (life.take(Items.WHEAT, 6)) Life.put(life.chestOf(shop), new ItemStack(Items.WHEAT, 6));
                }));
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- herds

    private static Class<? extends Animal> herdKind(Life life) {
        return life.state.profession.equals("shepherd") ? Sheep.class : life.state.profession.equals("butcher")
                && life.has(Items.CARROT) + life.has(Items.POTATO) > life.has(Items.WHEAT) ? Pig.class : Cow.class;
    }

    private static List<? extends Animal> herd(Life life) {
        List<Animal> all = new ArrayList<>();
        if (life.state.profession.equals("butcher")) {
            all.addAll(Herd.of(life, Cow.class));
            all.addAll(Herd.of(life, Pig.class));
        } else {
            all.addAll(Herd.of(life, Sheep.class));
        }
        return all;
    }

    /** The paddock behind the workshop, once its gate stands (it goes up and is mended with the workshop). */
    private static Plots.@Nullable Paddock paddock(Life life) {
        Plots.Paddock paddock = Plots.paddock(life.level, life.state);
        return paddock != null && Plots.standing(life.level, paddock) ? paddock : null;
    }

    private static List<? extends Animal> wild(Life life) {
        return life.level.getEntitiesOfClass(herdKind(life), new AABB(life.state.anchor).inflate(Life.RADIUS + 12.0),
                animal -> animal.isAlive() && Herd.free(animal)
                        && Life.allowed(life.level, life.villager, life.state.anchor, animal.blockPosition(), Life.RADIUS + 12));
    }

    private static @Nullable Errand adopt(Life life) {
        Plots.Paddock paddock = paddock(life);
        if (paddock == null || !life.under("adopt", 2) || herd(life).size() >= Herd.MIN + 1) return null;
        List<Animal> sorted = new ArrayList<>(wild(life));
        if (sorted.isEmpty()) return null;
        sorted.sort(Comparator.comparingDouble(a -> a.distanceToSqr(life.villager)));
        return adoptErrand(life, sorted.get(0), paddock);
    }

    private static Errand adoptErrand(Life life, Animal animal, Plots.Paddock paddock) {
        return counted(life, Errand.to("adopt", animal, new ItemStack(Items.LEAD), SHORT,
                SoundEvents.LEASH_KNOT_PLACE, ParticleTypes.HAPPY_VILLAGER, () -> {
            if (Herd.free(animal)) {
                animal.setLeashedTo(life.villager, true);
                life.state.nextChore = 0L;
                life.pending = leadIn(life, animal, paddock);
            }
        }));
    }

    /**
     * Second half of adopting: walk the leashed animal through the open gate into the paddock, then step out and
     * shut the gate behind. Villagers cannot open a fence gate: a gate shut on anyone inside keeps them there for good.
     */
    private static Errand leadIn(Life life, Animal animal, Plots.Paddock paddock) {
        Runnable shut = () -> shutIfEmpty(life.level, paddock);
        return Errand.at("lead", paddock.centre(), new ItemStack(Items.LEAD), 20, SoundEvents.LEASH_KNOT_BREAK, null, () -> {
            animal.dropLeash(true, false);
            Herd.adopt(animal, life.state.anchor);
            life.pending = Errand.at("lead", paddock.outside(), ItemStack.EMPTY, 10, null, null, shut).onCancel(shut);
        }).onStart(() -> setGate(life.level, paddock.gate(), true)).onCancel(() -> {
            animal.dropLeash(true, false);
            shut.run();
        });
    }

    /** What a head of livestock costs at the stall (the caravan's drover brings it). */
    public static final int STOCK_PRICE = 16;

    /**
     * No herd and nothing wild around to take in: the herder buys a pair at the stall (its household pays, the
     * treasury makes up the rest) and the morning delivery brings them into the paddock.
     */
    private static @Nullable Errand buyStock(Life life) {
        Plots.Paddock paddock = paddock(life);
        Shops.Shop shop = Shops.of(life.level, life.villager);
        BlockPos counter = shop == null ? null : shop.counter;
        if (paddock == null || shop == null || counter == null || life.state.stockDay >= 0 || !Shops.open(life.level, shop)
                || herd(life).size() >= Herd.MIN || !wild(life).isEmpty() || !life.under("stock", 1)) return null;
        String kind = herdKind(life).equals(Pig.class) ? "pig" : herdKind(life).equals(Sheep.class) ? "sheep" : "cow";
        return counted(life, Errand.at("stock", counter, new ItemStack(Items.LEAD), SHORT, SoundEvents.VILLAGER_TRADE, null, () -> {
            com.vnap.village.market.Market.buyAbroad(life.level, life.villager, shop, 2 * STOCK_PRICE);
            life.state.stockDay = com.vnap.dialogue.Family.day(life.level) + 1;
            life.state.stockKind = kind;
        }));
    }

    /** The bought pair comes with the morning delivery: it is let into the paddock, part of the herd. */
    private static @Nullable Errand receiveStock(Life life) {
        Plots.Paddock paddock = paddock(life);
        if (paddock == null || life.state.stockDay < 0 || com.vnap.dialogue.Family.day(life.level) < life.state.stockDay
                || !life.level.isLoaded(paddock.centre())) return null;
        var type = switch (life.state.stockKind) {
            case "pig" -> net.minecraft.world.entity.EntityType.PIG;
            case "sheep" -> net.minecraft.world.entity.EntityType.SHEEP;
            default -> net.minecraft.world.entity.EntityType.COW;
        };
        BlockPos centre = paddock.centre();
        for (int i = 0; i < 2; i++) {
            // the types' common bound differs between versions: an entity, then the animal it is
            net.minecraft.world.entity.@Nullable Entity created = com.vnap.Mc.create(type, life.level);
            if (!(created instanceof Animal animal)) continue;
            com.vnap.Mc.snap(animal, centre.getX() + 0.5 - i, centre.getY(), centre.getZ() + 0.5, life.level.getRandom().nextFloat() * 360.0F, 0.0F);
            Herd.adopt(animal, life.state.anchor);
            life.level.addFreshEntity(animal);
        }
        life.state.stockDay = -1L;
        return null;
    }

    /** First of a herder's chores: an open gate of an empty pen is shut, a shut one is opened for whoever is inside. */
    private static @Nullable Errand tendGate(Life life) {
        Plots.Paddock paddock = paddock(life);
        if (paddock == null) return null;
        if (villagersIn(life.level, paddock)) setGate(life.level, paddock.gate(), true);
        else shutIfEmpty(life.level, paddock);
        return null;
    }

    static boolean villagersIn(ServerLevel level, Plots.Paddock paddock) {
        return !level.getEntitiesOfClass(Villager.class, AABB.of(paddock.inside()).inflate(1.0, 2.0, 1.0),
                villager -> villager.isAlive() && paddock.contains(villager.blockPosition())).isEmpty();
    }

    private static void shutIfEmpty(ServerLevel level, Plots.Paddock paddock) {
        BlockPos gate = paddock.gate();
        if (!villagersIn(level, paddock) && level.getBlockState(gate).getBlock() instanceof FenceGateBlock
                && level.getBlockState(gate).getValue(FenceGateBlock.OPEN)) {
            setGate(level, gate, false);
        }
    }

    private static void setGate(ServerLevel level, BlockPos gate, boolean open) {
        BlockState state = level.getBlockState(gate);
        if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, open), 10);
            level.playSound(null, gate, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static @Nullable Errand breed(Life life) {
        if (paddock(life) == null || !life.under("breed", 1)) return null;
        List<? extends Animal> herd = herd(life);
        if (herd.size() >= Herd.MAX) return null;
        List<Animal> ready = new ArrayList<>();
        for (Animal animal : herd) {
            if (!animal.isBaby() && animal.canFallInLove() && animal.getAge() == 0 && life.has(animal::isFood) > 0) ready.add(animal);
        }
        for (int i = 0; i < ready.size(); i++) {
            for (int j = i + 1; j < ready.size(); j++) {
                Animal a = ready.get(i);
                Animal b = ready.get(j);
                if (a.getClass() != b.getClass() || life.has(a::isFood) < 2) continue;
                return counted(life, Errand.to("breed", a, new ItemStack(Items.WHEAT), SHORT,
                        SoundEvents.GENERIC_EAT, ParticleTypes.HEART, () -> {
                    if (life.take(a::isFood, 2) && a.isAlive() && b.isAlive()) {
                        a.setInLove(null);
                        b.setInLove(null);
                    }
                }));
            }
        }
        return null;
    }

    private static @Nullable Errand cull(Life life) {
        if (paddock(life) == null || !life.under("cull", 1) || life.chest() == null) return null;
        List<Animal> adults = new ArrayList<>();
        for (Animal animal : herd(life)) {
            if (!Herd.protectedAnimal(animal)) adults.add(animal);
        }
        if (adults.size() <= Herd.MAX - 2) return null;
        Animal animal = adults.get(0);
        return counted(life, Errand.to("cull", animal, new ItemStack(Items.IRON_AXE), SHORT,
                SoundEvents.PLAYER_ATTACK_STRONG, ParticleTypes.POOF, () -> {
            if (!animal.isAlive() || Herd.protectedAnimal(animal) || !Herd.owned(animal, life.state.anchor)) return;
            //? if >=1.21.2 {
            /*if (animal.getLootTable().isEmpty()) return;
            LootTable table = life.level.getServer().reloadableRegistries().getLootTable(animal.getLootTable().get());
            *///?} else
            LootTable table = life.level.getServer().reloadableRegistries().getLootTable(animal.getLootTable());
            LootParams params = new LootParams.Builder(life.level)
                    .withParameter(LootContextParams.THIS_ENTITY, animal)
                    .withParameter(LootContextParams.ORIGIN, animal.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, life.level.damageSources().mobAttack(life.villager))
                    .create(LootContextParamSets.ENTITY);
            boolean smoker = life.level.getBlockState(life.state.anchor).is(Blocks.SMOKER);
            for (ItemStack drop : table.getRandomItems(params)) {
                life.put(smoker ? cooked(drop) : drop);
            }
            animal.discard();
        }));
    }

    private static ItemStack cooked(ItemStack raw) {
        Item cooked = raw.is(Items.BEEF) ? Items.COOKED_BEEF : raw.is(Items.PORKCHOP) ? Items.COOKED_PORKCHOP
                : raw.is(Items.MUTTON) ? Items.COOKED_MUTTON : null;
        return cooked == null ? raw : new ItemStack(cooked, raw.getCount());
    }

    private static @Nullable Errand shear(Life life) {
        if (paddock(life) == null || !life.under("shear", 4)) return null;
        for (Sheep sheep : Herd.of(life, Sheep.class)) {
            Item wool = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(sheep.getColor().getName() + "_wool"));
            if (sheep.readyForShearing() && life.room(wool)) {
                return counted(life, Errand.to("shear", sheep, new ItemStack(Items.SHEARS), SHORT,
                        SoundEvents.SHEEP_SHEAR, null, () -> {
                    if (sheep.readyForShearing() && life.put(new ItemStack(wool, 1 + life.level.getRandom().nextInt(2)))) {
                        sheep.setSheared(true);
                    }
                }));
            }
        }
        return null;
    }

    private static @Nullable Errand dye(Life life) {
        if (paddock(life) == null || !life.under("dye", 1)) return null;
        for (Sheep sheep : Herd.of(life, Sheep.class)) {
            if (sheep.getColor() != DyeColor.WHITE || sheep.isSheared() || sheep.isBaby()) continue;
            if (life.has(stack -> stack.getItem() instanceof DyeItem) == 0) return null;
            return counted(life, Errand.to("dye", sheep, new ItemStack(Items.RED_DYE), SHORT,
                    SoundEvents.DYE_USE, ParticleTypes.HAPPY_VILLAGER, () -> {
                ItemStack dye = Life.take(life.chest(), stack -> stack.getItem() instanceof DyeItem, 1);
                if (dye != null && dye.getItem() instanceof DyeItem) sheep.setColor(com.vnap.Mc.dyeColor(dye));
            }));
        }
        return null;
    }

    // ---------------------------------------------------------------- fisherman

    private static @Nullable Errand fish(Life life) {
        if (!life.under("fish", 6) || !life.room(Items.COD)) return null;
        BlockPos shore = shore(life);
        if (shore == null) return null;
        return counted(life, Errand.at("fish", shore, new ItemStack(Items.FISHING_ROD), LONG + 60,
                SoundEvents.FISHING_BOBBER_SPLASH, ParticleTypes.FISHING, () -> {
            LootTable table = life.level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
            LootParams params = new LootParams.Builder(life.level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(shore))
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, life.villager)
                    .create(LootContextParamSets.FISHING);
            table.getRandomItems(params).forEach(life::put);
        }));
    }

    /** Dry ground next to water that is at least 3x3 wide. */
    public static @Nullable BlockPos shore(Life life) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(life.state.anchor.offset(-Life.RADIUS, -3, -Life.RADIUS),
                life.state.anchor.offset(Life.RADIUS, 1, Life.RADIUS))) {
            if (!life.level.getBlockState(pos).is(Blocks.WATER) || !inPond(life.level, pos)) continue;
            for (BlockPos side : List.of(pos.north(), pos.south(), pos.east(), pos.west())) {
                BlockPos stand = side.above();
                if (Plots.open(life.level, stand) && life.level.getBlockState(side).isFaceSturdy(life.level, side, Direction.UP)
                        && life.near(stand)) {
                    double distance = stand.distSqr(life.state.anchor);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = stand.immutable();
                    }
                }
            }
        }
        return best;
    }

    private static boolean inPond(ServerLevel level, BlockPos water) {
        for (BlockPos centre : BlockPos.betweenClosed(water.offset(-1, 0, -1), water.offset(1, 0, 1))) {
            if (wide(level, centre)) return true;
        }
        return false;
    }

    private static boolean wide(ServerLevel level, BlockPos water) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(water.offset(-1, 0, -1), water.offset(1, 0, 1))) {
            if (level.getBlockState(pos).is(Blocks.WATER)) count++;
        }
        return count == 9;
    }

    // ---------------------------------------------------------------- fletcher

    /** Saplings, sticks and apples lying about, feathers dropped by chickens. */
    private static @Nullable Errand gather(Life life) {
        if (!life.under("gather", 8)) return null;
        AABB around = new AABB(life.state.anchor).inflate(Life.RADIUS);
        for (ItemEntity item : life.level.getEntitiesOfClass(ItemEntity.class, around, entity -> entity.isAlive()
                && life.near(entity.blockPosition()) && wanted(entity.getItem()) && life.room(entity.getItem().getItem()))) {
            return counted(life, Errand.to("gather", item, ItemStack.EMPTY, 10, SoundEvents.ITEM_PICKUP, null, () -> {
                if (item.isAlive() && life.put(item.getItem().copy())) item.discard();
            }));
        }
        if (life.level.getRandom().nextInt(4) == 0) {
            for (Chicken chicken : life.level.getEntitiesOfClass(Chicken.class, around, chicken -> chicken.isAlive()
                    && !chicken.isBaby() && life.near(chicken.blockPosition())) ) {
                if (!life.room(Items.FEATHER)) break;
                return counted(life, Errand.to("gather", chicken, ItemStack.EMPTY, SHORT, com.vnap.Mc.chickenHurt(),
                        ParticleTypes.POOF, () -> life.put(new ItemStack(Items.FEATHER))));
            }
        }
        return null;
    }

    private static boolean wanted(ItemStack stack) {
        return stack.is(ItemTags.SAPLINGS) || stack.is(Items.STICK) || stack.is(Items.APPLE) || stack.is(Items.FEATHER);
    }

    private static @Nullable Errand fletch(Life life) {
        if (!life.under("fletch", 2) || !life.room(Items.ARROW) || life.has(Items.FLINT) < 1
                || life.has(Items.STICK) < 1 || life.has(Items.FEATHER) < 1) return null;
        return counted(life, Errand.at("fletch", life.state.anchor, new ItemStack(Items.FLINT), LONG,
                SoundEvents.VILLAGER_WORK_FLETCHER, null, () -> {
            if (life.take(Items.FLINT, 1) && life.take(Items.STICK, 1) && life.take(Items.FEATHER, 1)) {
                life.put(new ItemStack(Items.ARROW, 4));
            }
        }));
    }

    // ---------------------------------------------------------------- leatherworker

    private static @Nullable Errand fillCauldron(Life life) {
        BlockState cauldron = life.level.getBlockState(life.state.anchor);
        if (!cauldron.is(Blocks.CAULDRON) || !life.under("cauldron", 1)
                || life.has(stack -> stack.is(Items.ROTTEN_FLESH) || stack.is(Items.RABBIT_HIDE)) < 2) return null;
        return counted(life, Errand.at("cauldron", life.state.anchor, new ItemStack(Items.WATER_BUCKET), SHORT,
                SoundEvents.BUCKET_EMPTY, ParticleTypes.SPLASH, () -> {
            if (life.level.getBlockState(life.state.anchor).is(Blocks.CAULDRON)) {
                life.level.setBlock(life.state.anchor, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
            }
        }));
    }

    private static @Nullable Errand fetchHides(Life life) {
        if (!life.under("fetch", 1) || life.has(Items.ROTTEN_FLESH) >= 4) return null;
        for (WorkshopState shop : life.workshops("butcher")) {
            Container other = life.chestOf(shop);
            if (Life.count(other, stack -> stack.is(Items.ROTTEN_FLESH) || stack.is(Items.RABBIT_HIDE)) < 2) continue;
            BlockPos at = Workshops.chestPos(shop);
            return counted(life, Errand.at("fetch", at, ItemStack.EMPTY, SHORT, SoundEvents.CHEST_OPEN, null, () -> {
                ItemStack hides = Life.take(other, stack -> stack.is(Items.ROTTEN_FLESH) || stack.is(Items.RABBIT_HIDE), 2);
                if (hides != null) life.put(hides);
            }));
        }
        return null;
    }

    /** Two pieces of flesh or hide soaked in the cauldron give one leather; the cauldron loses a level. */
    private static @Nullable Errand tan(Life life) {
        BlockState cauldron = life.level.getBlockState(life.state.anchor);
        if (!cauldron.is(Blocks.WATER_CAULDRON) || !life.under("tan", 3) || !life.room(Items.LEATHER)
                || life.has(stack -> stack.is(Items.ROTTEN_FLESH) || stack.is(Items.RABBIT_HIDE)) < 2) return null;
        return counted(life, Errand.at("tan", life.state.anchor, new ItemStack(Items.ROTTEN_FLESH), LONG,
                SoundEvents.VILLAGER_WORK_LEATHERWORKER, ParticleTypes.SPLASH, () -> {
            BlockState now = life.level.getBlockState(life.state.anchor);
            if (now.is(Blocks.WATER_CAULDRON) && life.take(stack -> stack.is(Items.ROTTEN_FLESH) || stack.is(Items.RABBIT_HIDE), 2)) {
                LayeredCauldronBlock.lowerFillLevel(now, life.level, life.state.anchor);
                life.put(new ItemStack(Items.LEATHER));
            }
        }));
    }

    // ---------------------------------------------------------------- librarian, cartographer, cleric

    private static @Nullable Errand read(Life life) {
        if (!life.under("read", 3)) return null;
        return counted(life, Errand.at("read", life.state.anchor, new ItemStack(Items.BOOK), LONG,
                SoundEvents.BOOK_PAGE_TURN, ParticleTypes.ENCHANT, () -> { }));
    }

    private static @Nullable Errand copyBook(Life life) {
        if (!life.under("copy", 1) || life.has(Items.PAPER) < 3 || life.has(Items.LEATHER) < 1) return null;
        return counted(life, Errand.at("copy", life.state.anchor, new ItemStack(Items.WRITABLE_BOOK), LONG + 40,
                SoundEvents.VILLAGER_WORK_LIBRARIAN, ParticleTypes.ENCHANT, () -> {
            if (!life.take(Items.PAPER, 3) || !life.take(Items.LEATHER, 1)) return;
            BlockPos shelf = life.state.at(1, 1, 3);
            if (life.level.getBlockEntity(shelf) instanceof ChiseledBookShelfBlockEntity books) {
                for (int slot = 0; slot < books.getContainerSize(); slot++) {
                    if (books.getItem(slot).isEmpty()) {
                        books.setItem(slot, new ItemStack(Items.BOOK));
                        life.level.playSound(null, shelf, SoundEvents.CHISELED_BOOKSHELF_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        return;
                    }
                }
            }
            life.put(new ItemStack(Items.BOOK));
        }));
    }

    private static @Nullable Errand survey(Life life) {
        if (life.state.survey == 0b1111) return null;
        BlockPos bell = com.vnap.village.home.Towns.bellOf(life.villager);
        BlockPos centre = bell != null ? bell : life.state.anchor;
        int radius = bell != null ? 20 : 10;
        for (int i = 0; i < 4; i++) {
            if ((life.state.survey & 1 << i) != 0) continue;
            int bit = i;
            BlockPos point = centre.offset(i == 0 ? radius : i == 1 ? -radius : 0, 0, i == 2 ? radius : i == 3 ? -radius : 0);
            BlockPos ground = stand(life.level, point);
            if (ground == null || !Life.allowed(life.level, life.villager, centre, ground, TownRules.NEAR_BELL)) {
                life.state.survey |= 1 << bit;
                continue;
            }
            return Errand.at("survey", ground, new ItemStack(Items.COMPASS), SHORT, SoundEvents.BOOK_PAGE_TURN, null,
                    () -> life.state.survey |= 1 << bit);
        }
        return null;
    }

    /** A dry cell to stand on in the column of {@code point}, a few blocks up or down from it. */
    private static @Nullable BlockPos stand(ServerLevel level, BlockPos point) {
        BlockPos ground = com.vnap.village.town.Ground.near(level, point.getX(), point.getZ(), point.getY(), 3, 7);
        return ground == null ? null : ground.above();
    }

    private static @Nullable Errand drawMap(Life life) {
        if (life.state.survey != 0b1111 || !life.under("map", 1) || life.has(Items.FILLED_MAP) >= 2 || !life.room(Items.FILLED_MAP)
                || life.has(Items.PAPER) < 3) {
            return null;
        }
        return counted(life, Errand.at("map", life.state.anchor, new ItemStack(Items.MAP), LONG,
                SoundEvents.VILLAGER_WORK_CARTOGRAPHER, null, () -> {
            BlockPos bell = com.vnap.village.home.Towns.bellOf(life.villager);
            BlockPos centre = bell != null ? bell : life.state.anchor;
            if (!life.take(Items.PAPER, 3)) return;
            ItemStack map = MapItem.create(life.level, centre.getX(), centre.getZ(), (byte) 0, true, true);
            MapItem.renderBiomePreviewMap(life.level, map);
            life.put(map);
        }));
    }

    private static @Nullable Errand heal(Life life) {
        if (!life.under("heal", 6)) return null;
        AABB around = new AABB(life.state.anchor).inflate(Life.RADIUS);
        List<LivingEntity> hurt = new ArrayList<>(life.level.getEntitiesOfClass(LivingEntity.class, around,
                entity -> (entity instanceof Villager || entity instanceof IronGolem) && entity.isAlive() && !entity.equals(life.villager)
                        && entity.getHealth() < entity.getMaxHealth() - 1.0F && !entity.hasEffect(MobEffects.REGENERATION)
                        && life.near(entity.blockPosition())));
        if (hurt.isEmpty()) return null;
        LivingEntity patient = hurt.get(0);
        return counted(life, Errand.to("heal", patient, new ItemStack(Items.GLISTERING_MELON_SLICE), SHORT,
                SoundEvents.VILLAGER_WORK_CLERIC, ParticleTypes.HEART,
                () -> patient.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0))));
    }

    private static @Nullable Errand candles(Life life) {
        if (!life.under("candles", 1)) return null;
        List<BlockPos> unlit = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(life.state.at(0, 0, 0), life.state.at(Workshops.layout(life.state.profession).size().getX() - 1, 3, Workshops.layout(life.state.profession).size().getZ() - 1))) {
            BlockState candle = life.level.getBlockState(cursor);
            if (candle.is(BlockTags.CANDLES) && !candle.getValue(BlockStateProperties.LIT) && !candle.getValue(BlockStateProperties.WATERLOGGED)) {
                unlit.add(cursor.immutable());
            }
        }
        if (unlit.isEmpty()) return null;
        return counted(life, Errand.at("candles", unlit.get(0), new ItemStack(Items.FLINT_AND_STEEL), SHORT,
                SoundEvents.FLINTANDSTEEL_USE, ParticleTypes.FLAME, () -> {
            for (BlockPos pos : unlit) {
                BlockState candle = life.level.getBlockState(pos);
                if (candle.is(BlockTags.CANDLES)) life.level.setBlock(pos, candle.setValue(BlockStateProperties.LIT, true), 11);
            }
        }));
    }

    // ---------------------------------------------------------------- mason and smiths

    private static @Nullable Errand cutStone(Life life) {
        if (!life.under("cut", 8) || life.has(Items.COBBLESTONE) + life.has(Items.STONE) == 0 || !life.room(Items.STONE_BRICKS)) return null;
        return counted(life, Errand.at("cut", life.state.anchor, new ItemStack(Items.IRON_PICKAXE), SHORT,
                SoundEvents.UI_STONECUTTER_TAKE_RESULT, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), () -> {
            if (life.take(Items.STONE, 1) || life.take(Items.COBBLESTONE, 1)) life.put(new ItemStack(Items.STONE_BRICKS));
        }));
    }

    private static @Nullable Errand mend(Life life) {
        if (!life.under("mend", 4) || life.has(Items.COBBLESTONE) == 0) return null;
        for (BlockPos cursor : BlockPos.betweenClosed(life.state.at(0, 0, 0), life.state.at(Workshops.layout(life.state.profession).size().getX() - 1, Workshops.layout(life.state.profession).size().getY() - 1, Workshops.layout(life.state.profession).size().getZ() - 1))) {
            BlockState state = life.level.getBlockState(cursor);
            if (state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.MOSSY_STONE_BRICKS) || state.is(Blocks.MOSSY_COBBLESTONE)) {
                BlockPos at = cursor.immutable();
                Block fixed = state.is(Blocks.MOSSY_COBBLESTONE) ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS;
                return counted(life, Errand.at("mend", at, new ItemStack(Items.IRON_PICKAXE), LONG,
                        SoundEvents.STONE_HIT, new BlockParticleOption(ParticleTypes.BLOCK, state), () -> {
                    if (life.level.getBlockState(at).is(state.getBlock()) && life.take(Items.COBBLESTONE, 1)) {
                        life.level.setBlock(at, fixed.defaultBlockState(), 3);
                    }
                }));
            }
        }
        return null;
    }

    private static @Nullable Errand repairGolem(Life life) {
        if (!life.under("golem", 4) || life.has(Items.IRON_INGOT) == 0) return null;
        for (IronGolem golem : life.level.getEntitiesOfClass(IronGolem.class, new AABB(life.state.anchor).inflate(Life.RADIUS),
                golem -> golem.isAlive() && golem.getHealth() <= golem.getMaxHealth() - 25.0F && life.near(golem.blockPosition()))) {
            return counted(life, Errand.to("golem", golem, new ItemStack(Items.IRON_INGOT), SHORT,
                    SoundEvents.IRON_GOLEM_REPAIR, VillageParticles.IRON_SPARK, () -> {
                if (golem.isAlive() && life.take(Items.IRON_INGOT, 1)) golem.heal(25.0F);
            }));
        }
        return null;
    }

    /** Worn tools in the chest get a quarter of their durability back on the grindstone. */
    private static @Nullable Errand sharpen(Life life) {
        if (!life.under("sharpen", 3) || life.has(stack -> stack.isDamaged() && !stack.isEmpty()) == 0) return null;
        return counted(life, Errand.at("sharpen", life.state.anchor, new ItemStack(Items.IRON_AXE), LONG,
                SoundEvents.GRINDSTONE_USE, VillageParticles.IRON_SPARK, () -> {
            Container chest = life.chest();
            if (chest == null) return;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack tool = chest.getItem(slot);
                if (tool.isDamaged()) {
                    tool.setDamageValue(Math.max(0, tool.getDamageValue() - tool.getMaxDamage() / 4));
                    chest.setChanged();
                    return;
                }
            }
        }));
    }

    private static @Nullable Errand giveHoe(Life life) {
        if (Math.floorMod(life.day, 3) != 0 || !life.under("hoe", 1) || life.has(Items.IRON_INGOT) < 2) return null;
        for (WorkshopState shop : life.workshops("farmer")) {
            Container other = life.chestOf(shop);
            if (other == null || Life.count(other, stack -> stack.is(ItemTags.HOES)) > 0 || !Life.fits(other, new ItemStack(Items.IRON_HOE))) continue;
            BlockPos at = Workshops.chestPos(shop);
            return counted(life, Errand.at("hoe", at, new ItemStack(Items.IRON_HOE), SHORT, SoundEvents.CHEST_OPEN, null, () -> {
                if (life.take(Items.IRON_INGOT, 2)) Life.put(other, new ItemStack(Items.IRON_HOE));
            }));
        }
        return null;
    }

    /** Two ingots, quenched in the water cauldron with a hiss, make a sword; two at most wait in the chest. */
    private static @Nullable Errand temper(Life life) {
        if (!life.under("temper", 1) || life.has(Items.IRON_INGOT) < 2 || life.has(Items.IRON_SWORD) >= 2 || !life.room(Items.IRON_SWORD)) {
            return null;
        }
        BlockPos water = null;
        for (BlockPos cursor : BlockPos.betweenClosed(life.state.at(0, 0, 0), life.state.at(Workshops.layout(life.state.profession).size().getX() - 1, 2, Workshops.layout(life.state.profession).size().getZ() - 1))) {
            if (life.level.getBlockState(cursor).is(Blocks.WATER_CAULDRON)) {
                water = cursor.immutable();
                break;
            }
        }
        BlockPos at = water != null ? water : life.state.anchor;
        return counted(life, Errand.at("temper", at, new ItemStack(Items.IRON_SWORD), LONG,
                SoundEvents.FIRE_EXTINGUISH, ParticleTypes.CLOUD, () -> {
            if (life.take(Items.IRON_INGOT, 2)) life.put(new ItemStack(Items.IRON_SWORD));
        }));
    }
}
