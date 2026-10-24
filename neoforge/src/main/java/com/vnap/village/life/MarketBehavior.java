package com.vnap.village.life;

import com.google.common.collect.ImmutableMap;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.Family;
import com.vnap.village.home.Mayors;
import com.vnap.village.market.Market;
import com.vnap.village.market.Shops;
import com.vnap.village.script.WorkerControl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * The market day of a villager: the shopkeeper minds the counter in the hours of the market; after work a worker
 * fetches its wage from the mayor at the bell; anyone with something to buy walks to the open stall.
 */
final class MarketBehavior extends Behavior<Villager> {
    private static final int CHECK_EVERY = 100;
    private static final int MIND = 600;
    private static final int SHOP_AGAIN = 3000;

    private final LifeGate gate;
    private final ErrandRunner runner = new ErrandRunner();
    private @Nullable Errand errand;
    private Shops.@Nullable Shop minding;
    private boolean finished;
    private boolean begun;
    private long nextCheck;
    private long nextShop;
    private int minded;
    /** Money and wants at the last visit that bought nothing for want of money (-1: none). */
    private int poorFunds = -1;
    private int poorWants = -1;

    MarketBehavior(LifeGate gate) {
        super(ImmutableMap.of(), 1200, 2400);
        this.gate = gate;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        long now = level.getGameTime();
        if (now < nextCheck || gate.busy || com.vnap.dialogue.Infants.minding(level, villager)) return false;
        nextCheck = now + CHECK_EVERY;
        if (villager.isBaby() || villager.isSleeping() || villager.isNoAi()
                || villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                || com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG)) {
            return false;
        }
        Shops.Shop shop = Shops.of(level, villager);
        if (shop == null) return false;
        float time = Family.dayFraction(level);
        long day = Family.day(level);
        errand = null;
        minding = null;
        if (Shops.isKeeper(villager) && shop.counter != null && time >= 0.04F && time < 0.5F && Shops.keeperSpot(level, shop) != null) {
            minding = shop;
            return true;
        }
        Villager mayor = time >= 0.375F && time < Market.SETTLE ? Mayors.mayorNear(level, shop.bell) : null;
        if (mayor != null && !mayor.equals(villager) && mayor.blockPosition().closerThan(shop.bell, 4.0)
                && Market.owedWage(level, villager, day)) {
            errand = Errand.to("wage", mayor, new ItemStack(Items.GOLD_NUGGET), 20, SoundEvents.ARMOR_EQUIP_GOLD.value(), null,
                    () -> Market.payWage(level, villager, day, mayor));
            return true;
        }
        BlockPos counter = shop.counter;
        int funds = Market.funds(level, villager);
        int wanted = Family.state(villager).wants.size();
        // came away with nothing for want of money: no new trip until there is more money or something new to buy
        boolean pointless = funds <= poorFunds && wanted <= poorWants;
        if (counter != null && now >= nextShop && Market.wantsAnything(villager) && !Shops.isKeeper(villager) && !pointless
                && !ErrandRunner.goingToAJob(villager) && Shops.open(level, shop)) {
            nextShop = now + SHOP_AGAIN;
            errand = Errand.at("shop", counter, ItemStack.EMPTY, 40, SoundEvents.VILLAGER_TRADE, null, () -> {
                Market.Visit visit = Market.shop(level, villager, shop);
                if (visit == Market.Visit.POOR) {
                    poorFunds = Market.funds(level, villager);
                    poorWants = Family.state(villager).wants.size();
                } else {
                    poorFunds = -1;
                    poorWants = -1;
                }
            });
            return true;
        }
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = true;
        finished = false;
        begun = false;
        minded = 0;
        runner.reset();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        Shops.Shop shop = minding;
        if (shop != null) {
            mind(level, villager, shop);
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

    /** Behind the counter: back to it when it strays, an eye on whoever comes, a word for a player who walks up. */
    private void mind(ServerLevel level, Villager keeper, Shops.Shop shop) {
        BlockPos counter = shop.counter;
        BlockPos spot = Shops.keeperSpot(level, shop);
        if (counter == null || spot == null || ++minded > MIND) {
            finished = true;
            return;
        }
        if (!keeper.blockPosition().closerThan(spot, 1.5)) {
            if (minded % 20 == 1) keeper.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, 0.5F, 0));
            return;
        }
        ServerPlayer customer = level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(counter).inflate(6.0),
                        player -> !player.isSpectator()).stream()
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(keeper))).orElse(null);
        if (customer != null) {
            keeper.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(customer, true));
            if (Family.once(keeper, "greet:" + customer.getUUID(), Family.day(level))) {
                Speech.queue(keeper, "Shopkeeper Greets a Customer", customer, 1L);
            }
        } else {
            keeper.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(counter));
        }
        // the keeper's own household shops at its own counter
        if (Market.wantsAnything(keeper) && minded % 200 == 0 && Shops.open(level, shop)) Market.shop(level, keeper, shop);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return !com.vnap.dialogue.Infants.minding(level, villager) && !finished && villager.isAlive() && !villager.isSleeping()
                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                && !com.vnap.Mc.tags(villager).contains(WorkerControl.WORKER_TAG);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long gameTime) {
        gate.busy = false;
        runner.reset();
        errand = null;
        minding = null;
        WorkerControl.hold(villager, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        // after a spell at the counter the keeper's other chores (its own house) get a turn
        nextCheck = gameTime + CHECK_EVERY * 2L;
    }
}
