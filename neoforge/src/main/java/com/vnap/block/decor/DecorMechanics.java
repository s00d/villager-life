package com.vnap.block.decor;

import com.vnap.VillagerLife;
import com.vnap.block.DecorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** The mechanic of each decor block and machine, by block id. */
public final class DecorMechanics {
    public static final DecorMechanic NONE = new DecorMechanic() {
        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return false;
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
        }
    };

    private static final String NS = VillagerLife.MOD_ID;
    private static final Map<String, DecorMechanic> ALL = new LinkedHashMap<>();
    private static final float[][] FOUR_HANGING = {{3.5F, 8.5F, 8, 0.4F}, {6.5F, 8.5F, 8, 0.4F}, {9.5F, 8.5F, 8, 0.4F},
            {12.5F, 8.5F, 8, 0.4F}};

    static {
        ALL.put("seed_sacks", new SeedSacks());
        ALL.put("harvest_basket", new HarvestBasket());
        ALL.put("chopping_block", new ChoppingBlock());
        ALL.put("meat_rack", new Rack(s -> s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.MUTTON)
                || s.is(Items.CHICKEN) || s.is(Items.RABBIT), 6000, FOUR_HANGING));
        ALL.put("wool_bales", new WoolBales());
        ALL.put("spinning_wheel", new MachineMechanic(Machines.SPINNING_WHEEL));
        ALL.put("fish_rack", new Rack(s -> s.is(Items.COD) || s.is(Items.SALMON), 6000, FOUR_HANGING));
        ALL.put("tackle_crate", new Crate(9));
        // a pet's bowl: one helping of food at a time; full or empty is all it shows
        ALL.put("pet_bowl", new Crate(1, com.vnap.village.life.Pantry.FOOD) {
            @Override
            public int fill(DecorBlockEntity be) {
                return com.vnap.village.life.Pantry.food(be) == 0 ? 0 : 2;
            }
        });
        // a family's food: the wife fills it from the village's chests, every meal takes from it
        ALL.put("pantry_crate", new Crate(9, com.vnap.village.life.Pantry.FOOD) {
            @Override
            public int fill(DecorBlockEntity be) {
                int food = com.vnap.village.life.Pantry.food(be);
                return food == 0 ? 0 : Math.min(4, (food + 2) / 3);
            }
        });
        ALL.put("arrow_bundle", new Stock(64, s -> s.is(ItemTags.ARROWS)));
        ALL.put("feather_basket", new FeatherBasket());
        ALL.put("book_stack", new BookStack());
        ALL.put("ink_and_quill", new MachineMechanic(Machines.INK_AND_QUILL));
        ALL.put("globe", new Globe());
        ALL.put("map_rolls", new MapRolls());
        ALL.put("incense_burner", new Incense());
        ALL.put("potion_shelf", new Shelf(3, s -> s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)
                || s.is(Items.HONEY_BOTTLE) || s.is(Items.EXPERIENCE_BOTTLE),
                new float[][]{{4.5F, 3.5F, 11, 0.4F}, {11.5F, 3.5F, 11, 0.4F}, {8, 10, 11, 0.4F}}));
        ALL.put("hide_rack", new Rack(s -> s.is(Items.ROTTEN_FLESH), 24000, FOUR_HANGING));
        ALL.put("leather_rolls", new LeatherRolls());
        ALL.put("chisel_bench", new MachineMechanic(Machines.CHISEL_BENCH));
        ALL.put("brick_pallet", new Stock(256, DecorMechanics::brick) {
            @Override
            public int fill(DecorBlockEntity be) {
                return be.stock() == 0 ? 0 : 1 + Math.min(3, be.stock() * 4 / 257);
            }
        });
        ALL.put("armor_dummy", new ArmorDummy());
        ALL.put("chainmail_rack", new MachineMechanic(Machines.CHAINMAIL_RACK));
        ALL.put("tool_rack", new ToolRack());
        ALL.put("nail_keg", new Stock(256, s -> s.is(Items.IRON_NUGGET)));
        ALL.put("weapon_rack", new Shelf(3, DecorMechanics::weapon,
                new float[][]{{4, 8, 12.2F, 0.6F}, {8, 8, 12.2F, 0.6F}, {12, 8, 12.2F, 0.6F}}));
        ALL.put("sword_barrel", new SwordBarrel());
        // the town's working details: the stall's crates, the pot, the door bell, the sign (TownDecor)
        TownDecor.register(ALL);
        // the stall's counter: the village's shop window (a counter that is no stall's does nothing)
        ALL.put("market_counter", new DecorMechanic() {
            @Override
            public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
                return true;
            }

            @Override
            public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
                if (player instanceof net.minecraft.server.level.ServerPlayer server
                        && !com.vnap.village.market.ShopMenu.open(level, server, be.getBlockPos())) {
                    com.vnap.Mc.say(server, net.minecraft.network.chat.Component.translatable("vnap.shop.no_stall"), true);
                }
            }
        });
        for (String machine : List.of("greenhouse_box", "smokehouse", "dye_vat", "fish_trap", "reading_desk", "survey_table", "font",
                "tanning_vat", "kiln", "armor_bench", "grinding_wheel", "weapon_forge")) {
            Machine recipes = Machines.get(machine);
            if (recipes == null) {
                throw new IllegalStateException("No recipes for " + machine);
            }
            ALL.put(machine, new MachineMechanic(recipes));
        }
    }

    private DecorMechanics() {
    }

    public static DecorMechanic get(String name) {
        return ALL.getOrDefault(name, NONE);
    }

    static void say(Player player, String key, Object... args) {
        com.vnap.Mc.say(player, Component.translatable("message." + NS + "." + key, args), true);
    }

    static boolean brick(ItemStack s) {
        return s.is(Items.BRICK) || s.is(Items.NETHER_BRICK) || s.is(Items.BRICKS) || s.is(Items.STONE_BRICKS) || s.is(Items.MUD_BRICKS)
                || s.is(Items.DEEPSLATE_BRICKS) || s.is(Items.NETHER_BRICKS) || s.is(Items.RED_NETHER_BRICKS)
                || s.is(Items.POLISHED_BLACKSTONE_BRICKS) || s.is(Items.END_STONE_BRICKS) || s.is(Items.QUARTZ_BRICKS)
                || s.is(Items.TUFF_BRICKS);
    }

    static boolean weapon(ItemStack s) {
        Item item = s.getItem();
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof BowItem || item instanceof CrossbowItem;
    }

    static @org.jspecify.annotations.Nullable Item cured(ItemStack raw) {
        if (raw.is(Items.BEEF)) return Items.COOKED_BEEF;
        if (raw.is(Items.PORKCHOP)) return Items.COOKED_PORKCHOP;
        if (raw.is(Items.MUTTON)) return Items.COOKED_MUTTON;
        if (raw.is(Items.CHICKEN)) return Items.COOKED_CHICKEN;
        if (raw.is(Items.RABBIT)) return Items.COOKED_RABBIT;
        if (raw.is(Items.COD)) return Items.COOKED_COD;
        if (raw.is(Items.SALMON)) return Items.COOKED_SALMON;
        if (raw.is(Items.ROTTEN_FLESH)) return Items.LEATHER;
        return null;
    }

    /** The village bell (meeting point) closest to {@code pos} within 256 blocks. */
    static Optional<BlockPos> bell(ServerLevel level, BlockPos pos) {
        // the town's own bell (the office's), not whichever bell is nearest within a long way
        com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Towns.of(level, pos);
        if (office != null) return Optional.of(office.bell);
        return level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), pos, (int) com.vnap.village.town.TownRules.SPAN, PoiManager.Occupancy.ANY);
    }

    // ------------------------------------------------------------------ kinds

    /** One kind of item counted past the stack limit: click to put the held stack in, empty hand takes a stack. */
    static class Stock implements DecorMechanic {
        private final int cap;
        private final Predicate<ItemStack> accepts;

        Stock(int cap, Predicate<ItemStack> accepts) {
            this.cap = cap;
            this.accepts = accepts;
        }

        @Override
        public int stockCap() {
            return cap;
        }

        @Override
        public boolean stocks(ItemStack stack) {
            return accepts.test(stack);
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return held.isEmpty() ? be.stock() > 0 : be.canStock(held);
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            if (held.isEmpty()) {
                Item item = be.stockItem();
                DecorBlockEntity.give(player, be.takeStock(item == null ? 64 : item.getDefaultMaxStackSize()));
            } else {
                be.addStock(held);
            }
            level.playSound(null, be.getBlockPos(), SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8F, 1.0F);
        }

        @Override
        public int signal(DecorBlockEntity be) {
            return be.stock() == 0 ? 0 : 1 + be.stock() * 14 / cap;
        }
    }

    /** Single items on display: click puts one in the first free slot, empty hand takes the last. */
    static class Shelf implements DecorMechanic {
        private final int slots;
        private final Predicate<ItemStack> accepts;
        private final float[][] display;

        Shelf(int slots, Predicate<ItemStack> accepts, float[][] display) {
            this.slots = slots;
            this.accepts = accepts;
            this.display = display;
        }

        @Override
        public int slots() {
            return slots;
        }

        boolean accepts(int slot, ItemStack stack) {
            return accepts.test(stack);
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            if (held.isEmpty()) {
                return be.filled() > 0;
            }
            for (int i = 0; i < slots; i++) {
                if (be.items.getItem(i).isEmpty() && accepts(i, held)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            if (held.isEmpty()) {
                DecorBlockEntity.give(player, take(be));
            } else {
                for (int i = 0; i < slots; i++) {
                    if (be.items.getItem(i).isEmpty() && accepts(i, held)) {
                        be.items.setItem(i, held.split(1));
                        placed(be, i);
                        break;
                    }
                }
            }
            level.playSound(null, be.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
        }

        void placed(DecorBlockEntity be, int slot) {
        }

        ItemStack take(DecorBlockEntity be) {
            for (int i = slots - 1; i >= 0; i--) {
                if (!be.items.getItem(i).isEmpty()) {
                    return be.items.removeItemNoUpdate(i).copy();
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int signal(DecorBlockEntity be) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(be.items);
        }

        @Override
        public int fill(DecorBlockEntity be) {
            return be.filled() > 0 ? 1 : 0;
        }

        @Override
        public float[][] display() {
            return display;
        }
    }

    /** Hangs raw items that turn into their cured form after {@code ticks}; finished ones come off first. */
    static final class Rack extends Shelf {
        private final int ticks;

        Rack(Predicate<ItemStack> raw, int ticks, float[][] display) {
            super(4, raw, display);
            this.ticks = ticks;
        }

        @Override
        void placed(DecorBlockEntity be, int slot) {
            be.timers[slot] = 0;
        }

        @Override
        ItemStack take(DecorBlockEntity be) {
            for (int i = 3; i >= 0; i--) {
                ItemStack stack = be.items.getItem(i);
                if (!stack.isEmpty() && cured(stack) == null) {
                    ItemStack out = stack.copy();
                    be.items.setItem(i, ItemStack.EMPTY);
                    return out;
                }
            }
            return super.take(be);
        }

        @Override
        public boolean ticks() {
            return true;
        }

        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            for (int i = 0; i < 4; i++) {
                ItemStack stack = be.items.getItem(i);
                Item done = stack.isEmpty() ? null : cured(stack);
                if (done == null || (be.timers[i] += 20) < ticks) {
                    continue;
                }
                be.timers[i] = 0;
                be.items.setItem(i, new ItemStack(done));
            }
            be.setChanged();
        }
    }

    /** Slots for anything it accepts: click stores the held stack, empty hand takes everything. */
    static class Crate implements DecorMechanic {
        private final int slots;
        private final Predicate<ItemStack> accepts;

        Crate(int slots) {
            this(slots, stack -> true);
        }

        Crate(int slots, Predicate<ItemStack> accepts) {
            this.slots = slots;
            this.accepts = accepts;
        }

        @Override
        public int slots() {
            return slots;
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return held.isEmpty() ? be.filled() > 0 : accepts.test(held) && be.items.canAddItem(held);
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            if (held.isEmpty()) {
                for (ItemStack stack : be.items.removeAllItems()) {
                    DecorBlockEntity.give(player, stack);
                }
                be.items.setChanged();
            } else {
                player.setItemInHand(hand, be.items.addItem(held.copy()));
            }
            level.playSound(null, be.getBlockPos(), SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.6F, 1.1F);
        }

        @Override
        public int signal(DecorBlockEntity be) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(be.items);
        }
    }

    /** Opens the machine menu; the machine runs from the block entity tick. */
    static final class MachineMechanic implements DecorMechanic {
        private final Machine machine;

        MachineMechanic(Machine machine) {
            this.machine = machine;
        }

        @Override
        public int slots() {
            return machine.inputs() + 1;
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return true;
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            // a menu with data of its own is a server player's on Forge, Fabric API's extended one on Fabric
            //? if forge {
            /*if (player instanceof net.minecraft.server.level.ServerPlayer server) server.openMenu(be, buf -> buf.writeUtf(machine.id()));
            *///?} else
            player.openMenu(be, buf -> buf.writeUtf(machine.id()));
        }

        @Override
        public Machine machine() {
            return machine;
        }

        @Override
        public int fill(DecorBlockEntity be) {
            return be.work.progress > 0 ? 1 : 0;
        }

        @Override
        public int signal(DecorBlockEntity be) {
            return AbstractContainerMenu.getRedstoneSignalFromContainer(be.items);
        }
    }

    // ------------------------------------------------------------------ farmer

    static final class SeedSacks extends Stock {
        SeedSacks() {
            super(256, s -> s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS));
        }

        @Override
        public boolean ticks() {
            return true;
        }

        /** Each morning sows up to eight empty farmland blocks within 4. */
        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            long day = com.vnap.dialogue.Family.day(level);
            if (be.day == day || com.vnap.dialogue.Family.timeOfDay(level) > 2000L || be.stock() == 0) {
                return;
            }
            be.day = day;
            be.setChanged();
            int sown = 0;
            for (BlockPos p : BlockPos.betweenClosed(be.getBlockPos().offset(-4, -1, -4), be.getBlockPos().offset(4, 1, 4))) {
                if (sown >= 8 || !(be.stockItem() instanceof BlockItem seed)) {
                    break;
                }
                BlockPos above = p.above();
                BlockState crop = seed.getBlock().defaultBlockState();
                if (level.getBlockState(p).is(Blocks.FARMLAND) && level.isEmptyBlock(above) && crop.canSurvive(level, above)) {
                    level.setBlock(above, crop, 3);
                    be.takeStock(1);
                    sown++;
                }
            }
        }
    }

    static final class HarvestBasket extends Crate {
        HarvestBasket() {
            super(9);
        }

        static boolean crop(ItemStack s) {
            return s.is(Items.WHEAT) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT) || s.is(Items.WHEAT_SEEDS)
                    || s.is(Items.BEETROOT_SEEDS) || s.is(Items.MELON_SLICE) || s.is(Items.PUMPKIN) || s.is(Items.SWEET_BERRIES)
                    || s.is(Items.POISONOUS_POTATO) || s.is(Items.COCOA_BEANS);
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return held.isEmpty() ? be.filled() > 0 : crop(held) && be.items.canAddItem(held);
        }

        @Override
        public boolean ticks() {
            return true;
        }

        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(be.getBlockPos()).inflate(4),
                    e -> e.isAlive() && crop(e.getItem()))) {
                ItemStack rest = be.items.addItem(drop.getItem().copy());
                if (rest.isEmpty()) {
                    drop.discard();
                } else {
                    drop.setItem(rest);
                }
            }
        }
    }

    // ------------------------------------------------------------------ butcher

    /** A log on top; three axe blows split it into six planks. */
    static final class ChoppingBlock implements DecorMechanic {
        @Override
        public int slots() {
            return 1;
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            boolean log = !be.items.getItem(0).isEmpty();
            return held.isEmpty() ? log : log ? held.is(ItemTags.AXES) : held.is(ItemTags.LOGS);
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            BlockPos pos = be.getBlockPos();
            if (held.isEmpty()) {
                DecorBlockEntity.give(player, be.items.removeItemNoUpdate(0).copy());
                be.timers[0] = 0;
                be.changed();
                return;
            }
            if (held.is(ItemTags.LOGS)) {
                be.items.setItem(0, held.split(1));
                be.timers[0] = 0;
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 0.8F + level.getRandom().nextFloat() * 0.4F);
            held.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            if (++be.timers[0] < 3) {
                be.setChanged();
                return;
            }
            ItemStack log = be.items.removeItemNoUpdate(0);
            be.timers[0] = 0;
            be.changed();
            CraftingInput input = CraftingInput.of(1, 1, List.of(log));
            ItemStack planks = com.vnap.Mc.recipes(level).getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(holder -> com.vnap.Mc.assemble(holder.value(), input, level.registryAccess())).orElse(ItemStack.EMPTY);
            if (!planks.isEmpty()) {
                planks.setCount(planks.getCount() + 2);
                ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, planks);
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }

        @Override
        public float[][] display() {
            return new float[][]{{8, 12.5F, 8, 0.5F}};
        }
    }

    // ------------------------------------------------------------------ shepherd

    /** While it holds wool, one sheared sheep within 6 regrows its coat every 30 seconds. */
    static final class WoolBales extends Stock {
        WoolBales() {
            super(256, s -> s.is(ItemTags.WOOL));
        }

        @Override
        public boolean ticks() {
            return true;
        }

        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            if (be.stock() == 0 || (be.timers[0] += 20) < 600) {
                return;
            }
            be.timers[0] = 0;
            level.getEntitiesOfClass(Sheep.class, new AABB(be.getBlockPos()).inflate(6), s -> s.isSheared() && !s.isBaby())
                    .stream().findFirst().ifPresent(sheep -> sheep.setSheared(false));
        }
    }

    // ------------------------------------------------------------------ fletcher

    /** A chicken within 6 drops a feather in now and then, up to 16. */
    static final class FeatherBasket extends Stock {
        FeatherBasket() {
            super(16, s -> s.is(Items.FEATHER));
        }

        @Override
        public boolean ticks() {
            return true;
        }

        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            if ((be.timers[0] += 20) < 1200) {
                return;
            }
            be.timers[0] = 0;
            boolean chicken = !level.getEntitiesOfClass(Chicken.class, new AABB(be.getBlockPos()).inflate(6), c -> !c.isBaby()).isEmpty();
            if (chicken && level.getRandom().nextBoolean()) {
                be.addStock(new ItemStack(Items.FEATHER));
            }
        }
    }

    // ------------------------------------------------------------------ librarian

    /** Up to four books; any book adds a level of enchanting power (see {@link DecorBlock}). */
    static final class BookStack extends Shelf {
        BookStack() {
            super(4, s -> s.is(Items.BOOK) || s.is(Items.WRITABLE_BOOK) || s.is(Items.WRITTEN_BOOK) || s.is(Items.ENCHANTED_BOOK)
                    || s.is(Items.KNOWLEDGE_BOOK), NO_DISPLAY);
        }

        @Override
        public int fill(DecorBlockEntity be) {
            return be.filled();
        }
    }

    // ------------------------------------------------------------------ cartographer

    /** Turns to face the village bell and says how far it is. */
    static final class Globe implements DecorMechanic {
        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return held.isEmpty();
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            BlockPos pos = be.getBlockPos();
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F);
            Optional<BlockPos> bell = bell(level, pos);
            if (bell.isEmpty()) {
                say(player, "globe.none");
                return;
            }
            int dx = bell.get().getX() - pos.getX();
            int dz = bell.get().getZ() - pos.getZ();
            Direction facing = Direction.fromYRot(Math.toDegrees(Math.atan2(-dx, dz)));
            BlockState state = be.getBlockState();
            if (state.getValue(DecorBlock.FACING) != facing) {
                level.setBlock(pos, state.setValue(DecorBlock.FACING, facing), 3);
            }
            int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
            say(player, "globe.bell", distance, Component.translatable("message." + NS + ".globe." + compass(dx, dz)));
        }

        static String compass(int dx, int dz) {
            String[] names = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
            double angle = Math.toDegrees(Math.atan2(-dx, dz));
            return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
        }
    }

    /** Holds empty maps; empty hand takes a map centred on the village bell. */
    static final class MapRolls extends Stock {
        MapRolls() {
            super(64, s -> s.is(Items.MAP));
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return held.isEmpty() ? be.stock() > 0 : be.canStock(held);
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            if (!held.isEmpty() || player.isShiftKeyDown()) {
                super.use(level, be, player, hand, held);
                return;
            }
            Optional<BlockPos> bell = bell(level, be.getBlockPos());
            if (bell.isEmpty()) {
                say(player, "globe.none");
                return;
            }
            be.takeStock(1);
            ItemStack map = MapItem.create(level, bell.get().getX(), bell.get().getZ(), (byte) 1, true, false);
            MapItem.renderBiomePreviewMap(level, map);
            map.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable("message." + NS + ".village_map"));
            DecorBlockEntity.give(player, map);
            level.playSound(null, be.getBlockPos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ cleric

    /** Blaze powder burns two minutes; while lit it gives regeneration to people within 6 every five seconds. */
    static final class Incense implements DecorMechanic {
        static final int PER_POWDER = 2400;
        static final int MAX_FUEL = PER_POWDER * 4;

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            return held.is(Items.BLAZE_POWDER) && be.fuel + PER_POWDER <= MAX_FUEL;
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            held.shrink(1);
            be.fuel += PER_POWDER;
            be.changed();
            level.playSound(null, be.getBlockPos(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        }

        @Override
        public boolean ticks() {
            return true;
        }

        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            if (be.fuel <= 0) {
                return;
            }
            be.fuel = Math.max(0, be.fuel - 20);
            if (be.fuel == 0) {
                be.changed();
            } else {
                be.setChanged();
            }
            if ((be.timers[0] += 20) < 100) {
                return;
            }
            be.timers[0] = 0;
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(be.getBlockPos()).inflate(6),
                    e -> e.isAlive() && (e instanceof Player || e instanceof Villager))) {
                living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, true, true));
            }
        }

        @Override
        public int fill(DecorBlockEntity be) {
            return be.fuel > 0 ? 1 : 0;
        }

        @Override
        public int signal(DecorBlockEntity be) {
            return be.fuel == 0 ? 0 : 1 + be.fuel * 14 / MAX_FUEL;
        }
    }

    // ------------------------------------------------------------------ leatherworker

    /** Leather in stock mends one hung leather armor piece by a quarter each minute. */
    static final class LeatherRolls extends Stock {
        LeatherRolls() {
            super(64, s -> s.is(Items.LEATHER));
        }

        static boolean armor(ItemStack s) {
            return s.is(Items.LEATHER_HELMET) || s.is(Items.LEATHER_CHESTPLATE) || s.is(Items.LEATHER_LEGGINGS) || s.is(Items.LEATHER_BOOTS);
        }

        @Override
        public int slots() {
            return 1;
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            if (held.isEmpty()) {
                return !be.items.getItem(0).isEmpty() || be.stock() > 0;
            }
            return armor(held) ? be.items.getItem(0).isEmpty() : be.canStock(held);
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            if (held.isEmpty() && !be.items.getItem(0).isEmpty()) {
                DecorBlockEntity.give(player, be.items.removeItemNoUpdate(0).copy());
                be.changed();
            } else if (armor(held)) {
                be.items.setItem(0, held.split(1));
            } else {
                super.use(level, be, player, hand, held);
            }
        }

        @Override
        public boolean ticks() {
            return true;
        }

        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            ItemStack armor = be.items.getItem(0);
            if (armor.getDamageValue() == 0 || be.stock() == 0 || (be.timers[0] += 20) < 1200) {
                return;
            }
            be.timers[0] = 0;
            be.takeStock(1);
            armor.setDamageValue(Math.max(0, armor.getDamageValue() - armor.getMaxDamage() / 4));
            be.items.setChanged();
        }

        @Override
        public float[][] display() {
            return new float[][]{{8, 12, 8, 0.5F}};
        }
    }

    // ------------------------------------------------------------------ armorer

    /** Wears one armor piece per body slot. */
    static final class ArmorDummy extends Shelf {
        private static final EquipmentSlot[] ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

        ArmorDummy() {
            super(4, com.vnap.Mc::isArmor,
                    new float[][]{{8, 15.5F, 6.8F, 0.5F}, {8, 11, 6.8F, 0.6F}, {8, 6, 6.8F, 0.55F}, {8, 2, 6.8F, 0.5F}});
        }

        @Override
        boolean accepts(int slot, ItemStack stack) {
            return com.vnap.Mc.armorSlot(stack) == ORDER[slot];
        }
    }

    // ------------------------------------------------------------------ toolsmith

    /** Holds four tools and mends one by a twentieth every 30 seconds with a nugget from a nail keg within 3. */
    static final class ToolRack extends Shelf {
        ToolRack() {
            super(4, s -> s.isDamageableItem() && !com.vnap.Mc.isArmor(s),
                    new float[][]{{3, 8, 12.3F, 0.45F}, {6.3F, 8, 12.3F, 0.45F}, {9.6F, 8, 12.3F, 0.45F}, {12.9F, 8, 12.3F, 0.45F}});
        }

        @Override
        public boolean ticks() {
            return true;
        }

        @Override
        public void tick(ServerLevel level, DecorBlockEntity be) {
            if ((be.timers[0] += 20) < 600) {
                return;
            }
            be.timers[0] = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack tool = be.items.getItem(i);
                if (tool.getDamageValue() > 0) {
                    DecorBlockEntity keg = nailKeg(level, be.getBlockPos());
                    if (keg == null) {
                        return;
                    }
                    keg.takeStock(1);
                    tool.setDamageValue(Math.max(0, tool.getDamageValue() - Math.max(1, tool.getMaxDamage() / 20)));
                    be.items.setChanged();
                    return;
                }
            }
        }

        private static @org.jspecify.annotations.Nullable DecorBlockEntity nailKeg(ServerLevel level, BlockPos pos) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -1, -3), pos.offset(3, 1, 3))) {
                if (level.getBlockEntity(p) instanceof DecorBlockEntity keg && keg.name().equals("nail_keg") && keg.stock() > 0) {
                    return keg;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ weaponsmith

    /** Flint in stock; a sword clicked on it is whetted for the next 20 hits. */
    static final class SwordBarrel extends Stock {
        SwordBarrel() {
            super(64, s -> s.is(Items.FLINT));
        }

        @Override
        public boolean handles(DecorBlockEntity be, Player player, ItemStack held) {
            if (held.is(ItemTags.SWORDS)) {
                return be.stock() > 0 && Sharpening.charges(held) < Sharpening.CHARGES;
            }
            return super.handles(be, player, held);
        }

        @Override
        public void use(ServerLevel level, DecorBlockEntity be, Player player, InteractionHand hand, ItemStack held) {
            if (!held.is(ItemTags.SWORDS)) {
                super.use(level, be, player, hand, held);
                return;
            }
            be.takeStock(1);
            Sharpening.set(held, Sharpening.CHARGES);
            level.playSound(null, be.getBlockPos(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
            say(player, "sharpened", Sharpening.CHARGES);
        }
    }
}
