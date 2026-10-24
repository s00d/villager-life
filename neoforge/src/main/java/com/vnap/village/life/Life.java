package com.vnap.village.life;

import com.vnap.village.town.TownRules;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.workshop.ProfessionWork;
import com.vnap.village.workshop.WorkshopState;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a working villager may touch: blocks and animals within {@link #RADIUS} of its workshop, never further
 * than {@link TownRules#NEAR_BELL} from the village bell, only in loaded chunks. Daily quotas and the workshop chest
 * live here too; a full chest stops every chore that would fill it.
 */
public final class Life {
    public static final int RADIUS = 12;

    /**
     * The mayor and the shopkeeper: they have their own duties (the bell and the hall, the counter) and are never taken
     * for the town's chores, nobody's hands at a feast or a grave, nor the spire.
     */
    public static boolean official(Villager villager) {
        return com.vnap.village.home.Mayors.isMayor(villager) || com.vnap.village.market.Shops.isKeeper(villager);
    }
    public static final int STOCK_CAP = 16;

    final ServerLevel level;
    final Villager villager;
    final WorkshopState state;
    final long day;
    /** The trip that follows the one just done, such as leading a leashed animal into the pen. */
    @Nullable Errand pending;

    Life(ServerLevel level, Villager villager, WorkshopState state, long day) {
        this.level = level;
        this.villager = villager;
        this.state = state;
        this.day = day;
        if (state.choreDay != day) {
            state.choreDay = day;
            state.chores.clear();
            state.survey = 0;
            state.tended.clear();
        }
    }

    public static Life of(ServerLevel level, Villager villager, WorkshopState state, long day) {
        return new Life(level, villager, state, day);
    }

    public static boolean allowed(ServerLevel level, Villager villager, BlockPos anchor, BlockPos pos, int radius) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        int dx = pos.getX() - anchor.getX();
        int dz = pos.getZ() - anchor.getZ();
        if (dx * dx + dz * dz > radius * radius || Math.abs(pos.getY() - anchor.getY()) > 6) {
            return false;
        }
        // the town's own land: it reaches further as the town grows
        BlockPos bell = com.vnap.village.home.Towns.bellOf(villager);
        return bell == null || com.vnap.village.home.Towns.ours(level, bell, pos);
    }

    boolean near(BlockPos pos) {
        return allowed(level, villager, state.anchor, pos, RADIUS);
    }

    int done(String chore) {
        return state.chores.getOrDefault(chore, 0);
    }

    boolean under(String chore, int quota) {
        return done(chore) < quota;
    }

    void count(String chore) {
        state.chores.merge(chore, 1, Integer::sum);
    }

    @Nullable Container chest() {
        return ProfessionWork.chest(level, state);
    }

    BlockPos chestPos() {
        return Workshops.chestPos(state);
    }

    int has(Item item) {
        Container chest = chest();
        return chest == null ? 0 : chest.countItem(item);
    }

    int has(Predicate<ItemStack> test) {
        return count(chest(), test);
    }

    static int count(@Nullable Container chest, Predicate<ItemStack> test) {
        if (chest == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && test.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Room for {@code item} under the stock cap, and a free slot or a stack to grow. */
    boolean room(Item item) {
        Container chest = chest();
        return chest != null && chest.countItem(item) < STOCK_CAP && fits(chest, new ItemStack(item));
    }

    static boolean fits(Container chest, ItemStack stack) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack in = chest.getItem(slot);
            if (in.isEmpty() || (ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    /** Puts what fits under the cap; the rest is lost, as a villager does not carry it around. */
    boolean put(ItemStack stack) {
        return put(chest(), stack);
    }

    static boolean put(@Nullable Container chest, ItemStack stack) {
        if (chest == null || stack.isEmpty()) {
            return false;
        }
        int room = STOCK_CAP - chest.countItem(stack.getItem());
        if (room <= 0) {
            return false;
        }
        ItemStack part = stack.copyWithCount(Math.min(room, stack.getCount()));
        ItemStack rest = HopperBlockEntity.addItem(null, chest, part, Direction.UP);
        chest.setChanged();
        return rest.getCount() < part.getCount();
    }

    /** Takes {@code count} matching items out of the chest; nothing is taken unless all of them are there. */
    boolean take(Predicate<ItemStack> test, int count) {
        return take(chest(), test, count) != null;
    }

    boolean take(Item item, int count) {
        return take(stack -> stack.is(item), count);
    }

    static @Nullable ItemStack take(@Nullable Container chest, Predicate<ItemStack> test, int count) {
        if (chest == null || count(chest, test) < count) {
            return null;
        }
        ItemStack taken = ItemStack.EMPTY;
        int left = count;
        for (int slot = 0; slot < chest.getContainerSize() && left > 0; slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && test.test(stack)) {
                int n = Math.min(left, stack.getCount());
                if (taken.isEmpty()) taken = stack.copyWithCount(0);
                taken.grow(n);
                stack.shrink(n);
                left -= n;
            }
        }
        chest.setChanged();
        return taken;
    }

    /** Workshops of {@code profession} near the bell (or near this one), closest first. */
    List<WorkshopState> workshops(String profession) {
        return workshopsNear(level, villager, profession);
    }

    /** {@link #workshops} for a villager without a workshop of its own (a wife fetching food). */
    static List<WorkshopState> workshopsNear(ServerLevel level, Villager villager, String profession) {
        List<WorkshopState> found = new ArrayList<>();
        for (Villager other : level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(TownRules.NEAR_BELL),
                v -> !v.equals(villager) && v.isAlive())) {
            WorkshopState shop = other instanceof VillagerLifeData data ? data.vnap$workshop() : null;
            if (shop != null && shop.profession.equals(profession) && shop.tier > 0
                    && allowed(level, villager, villager.blockPosition(), shop.anchor, TownRules.NEAR_BELL)) {
                found.add(shop);
            }
        }
        found.sort(Comparator.comparingDouble(shop -> shop.anchor.distSqr(villager.blockPosition())));
        return found;
    }

    @Nullable Container chestOf(WorkshopState shop) {
        return ProfessionWork.chest(level, shop);
    }
}
