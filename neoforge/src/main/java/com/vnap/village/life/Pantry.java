package com.vnap.village.life;

import com.vnap.block.DecorBlock;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.dialogue.Speech;
import com.vnap.dialogue.FamilyHome;
import com.vnap.item.LifeItems;
import com.vnap.village.workshop.ProfessionWork;
import com.vnap.village.workshop.WorkshopState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * A family's food is real: the pantry crate in the house holds it, the wife fills it from the farmer's, the fisher's
 * and the butcher's chests, and every meal she serves (and every dinner the player is invited to) takes from it.
 */
public final class Pantry {
	public static final Predicate<ItemStack> FOOD = stack -> stack.has(DataComponents.FOOD) && !stack.is(Items.ROTTEN_FLESH)
			&& !stack.is(Items.SPIDER_EYE) && !stack.is(Items.POISONOUS_POTATO) && !stack.is(Items.PUFFERFISH)
			&& !stack.is(LifeItems.HOME_MEAL);
	/** The wife tops the pantry up to this much food. */
	public static final int WANTED = 6;
	static final List<String> SUPPLIERS = List.of("farmer", "fisherman", "butcher");

	private Pantry() {
	}

	/** The pantry crate standing by a floor cell of the house's room, or null. */
	public static @Nullable DecorBlockEntity of(ServerLevel level, BlockPos anchor) {
		for (BlockPos floor : FamilyHome.room(level, anchor)) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos pos = floor.relative(side);
				if (level.getBlockState(pos).getBlock() instanceof DecorBlock decor && decor.name().equals("pantry_crate")
						&& level.getBlockEntity(pos) instanceof DecorBlockEntity crate) {
					return crate;
				}
			}
		}
		return null;
	}

	public static int food(@Nullable DecorBlockEntity pantry) {
		return pantry == null ? 0 : Life.count(pantry.items, FOOD);
	}

	/** Takes {@code count} food out of the pantry; nothing unless all of it is there. */
	public static boolean take(@Nullable DecorBlockEntity pantry, int count) {
		if (pantry == null || Life.take(pantry.items, FOOD, count) == null) return false;
		pantry.items.setChanged();
		return true;
	}

	/** Puts food into the pantry; what does not fit stays in {@code stack}. */
	public static void store(DecorBlockEntity pantry, ItemStack stack) {
		ItemStack rest = pantry.items.addItem(stack.copy());
		stack.setCount(rest.getCount());
		pantry.items.setChanged();
	}

	/**
	 * One helping for {@code eater} out of the pantry: a child eats it, a husband gets a loaf in his pockets (vanilla
	 * counts that when a couple is ready for a child). False when the pantry is empty.
	 */
	public static boolean serve(ServerLevel level, Villager wife, Villager eater, @Nullable DecorBlockEntity pantry) {
		if (!eater.isAlive() || !take(pantry, 1)) return false;
		if (eater.isBaby()) {
			Speech.queue(eater, "Kid Eats Dinner", wife, 2L);
		} else {
			eater.getInventory().addItem(new ItemStack(Items.BREAD));
			Speech.queue(eater, "Man Praises Dinner", wife, 2L);
		}
		return true;
	}

	/** A supplier's chest near the village with food in it, closest first, and that chest's position. */
	static @Nullable Supply supply(ServerLevel level, Villager wife) {
		for (String profession : SUPPLIERS) {
			for (WorkshopState shop : Life.workshopsNear(level, wife, profession)) {
				Container chest = ProfessionWork.chest(level, shop);
				if (chest instanceof net.minecraft.world.level.block.entity.BlockEntity entity && Life.count(chest, FOOD) > 0) {
					return new Supply(chest, entity.getBlockPos());
				}
			}
		}
		return null;
	}

	record Supply(Container chest, BlockPos pos) {
	}
}
