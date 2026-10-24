package com.vnap.spire.gear;

import com.vnap.item.LifeItems;
import com.vnap.spire.SpireBlocks;
import com.vnap.village.market.Coins;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

/**
 * The Core Anvil's window, laid out as a smithing table: the piece, the shards of the Core that pay for the work and
 * the chapter's seal go in their slots, and the anvil shows what comes out. With the next chapter's seal in its slot
 * the work is raising the piece a tier (a vanilla iron, diamond or netherite one becomes a Root piece; honing and
 * enchantments kept), and the seal is spent: a chapter's keeper gives one for each piece of a set (SpireRewards.SEALS),
 * so beating it is what opens its tier. Without the seal the work is honing +1 (never fails). The shards come out of
 * their slot, the smith's coins out of the purse. The client reads it all off the slots (SpireGear), the click comes
 * back as CoreAnvilPayload.
 */
public final class CoreAnvilMenu extends AbstractContainerMenu {
	public static final int WORK = 0;
	public static final int PIECE = 0;
	public static final int SHARDS = 1;
	public static final int SEAL = 2;
	/** Where the items of the piece, shards and seal slots and the preview are drawn. */
	public static final int ROW_Y = 30;
	public static final int PIECE_X = 16;
	public static final int SHARDS_X = 54;
	public static final int SEAL_X = 88;
	public static final int RESULT_X = 150;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 116;

	/** What a click came to. */
	public enum Outcome { DONE, NOTHING, TOP, NO_SEAL, NO_SHARDS, NO_COINS }

	/** What the work on the anvil is. */
	public enum Work { NONE, HONE, RAISE }

	private final SimpleContainer input = new SimpleContainer(3) {
		@Override
		public void setChanged() {
			super.setChanged();
			slotsChanged(this);
		}
	};
	private final ContainerLevelAccess access;
	/** The opener's inventory: the coins are paid from it. */
	public final Inventory pockets;

	public CoreAnvilMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public CoreAnvilMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(SpireBlocks.CORE_ANVIL_MENU, id);
		this.access = access;
		this.pockets = inventory;
		addSlot(new Only(input, PIECE, PIECE_X, ROW_Y, 1, CoreAnvilMenu::accepts));
		addSlot(new Only(input, SHARDS, SHARDS_X, ROW_Y, 64, stack -> stack.is(LifeItems.CORE_SHARD)));
		addSlot(new Only(input, SEAL, SEAL_X, ROW_Y, 16, CoreAnvilMenu::isSeal));
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
		}
		for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
	}

	/** A slot that takes only what {@code fits}, at most {@code size}. */
	private static final class Only extends Slot {
		private final java.util.function.Predicate<ItemStack> fits;
		private final int size;

		Only(SimpleContainer container, int index, int x, int y, int size, java.util.function.Predicate<ItemStack> fits) {
			super(container, index, x, y);
			this.fits = fits;
			this.size = size;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return fits.test(stack);
		}

		@Override
		public int getMaxStackSize() {
			return size;
		}
	}

	public static boolean accepts(ItemStack stack) {
		return stack.getItem() instanceof GearItem || GearPiece.source(stack.getItem()) != null;
	}

	public static boolean isSeal(ItemStack stack) {
		for (GearTier tier : GearTier.values()) if (stack.is(SpireGearItems.seal(tier))) return true;
		return false;
	}

	public ItemStack piece() {
		return input.getItem(PIECE);
	}

	public ItemStack shards() {
		return input.getItem(SHARDS);
	}

	public ItemStack seal() {
		return input.getItem(SEAL);
	}

	/** The tier the piece would become: the next one, Root for a vanilla piece; null at Summit or for nothing. */
	public static @Nullable GearTier nextTier(ItemStack stack) {
		if (stack.getItem() instanceof GearItem gear) return gear.tier().next();
		return GearPiece.source(stack.getItem()) != null ? GearTier.ROOT : null;
	}

	/** Shards of the Core anywhere in {@code inventory}. */
	public static int shards(Inventory inventory) {
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(LifeItems.CORE_SHARD)) total += inventory.getItem(i).getCount();
		}
		return total;
	}

	public static boolean hasSeal(Inventory inventory, GearTier tier) {
		for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(SpireGearItems.seal(tier))) return true;
		return false;
	}

	/** The work the slots hold: raising with the next chapter's seal in its slot (or for a vanilla piece), else honing. */
	public static Work work(ItemStack piece, ItemStack seal) {
		if (piece.isEmpty() || !accepts(piece)) return Work.NONE;
		GearTier next = nextTier(piece);
		if (!(piece.getItem() instanceof GearItem)) return Work.RAISE;
		return next != null && seal.is(SpireGearItems.seal(next)) ? Work.RAISE : Work.HONE;
	}

	/** Shards the work takes (0 when there is none). */
	public static int shardCost(ItemStack piece, ItemStack seal) {
		return switch (work(piece, seal)) {
			case HONE -> piece.getItem() instanceof GearItem gear ? SpireGear.honeShards(gear.tier(), SpireGear.honed(piece)) : 0;
			case RAISE -> nextTier(piece) == null ? 0 : SpireGear.craftShards(java.util.Objects.requireNonNull(nextTier(piece)));
			case NONE -> 0;
		};
	}

	public static int coinCost(ItemStack piece, ItemStack seal) {
		return switch (work(piece, seal)) {
			case HONE -> piece.getItem() instanceof GearItem gear ? SpireGear.honeCoins(gear.tier(), SpireGear.honed(piece)) : 0;
			case RAISE -> nextTier(piece) == null ? 0 : SpireGear.craftCoins(java.util.Objects.requireNonNull(nextTier(piece)));
			case NONE -> 0;
		};
	}

	/** What the work would make of the piece (nothing paid); empty when it cannot be done (Summit, a wrong seal). */
	public static ItemStack result(ItemStack piece, ItemStack seal) {
		Work work = work(piece, seal);
		if (work == Work.HONE) {
			int n = SpireGear.honed(piece);
			if (n == Integer.MAX_VALUE) return ItemStack.EMPTY;
			ItemStack out = piece.copy();
			SpireGear.setHoned(out, n + 1);
			SpireGear.sanitize(out);
			return out;
		}
		if (work != Work.RAISE) return ItemStack.EMPTY;
		GearTier next = nextTier(piece);
		GearPiece kind = piece.getItem() instanceof GearItem gear ? gear.piece() : GearPiece.source(piece.getItem());
		if (next == null || kind == null || !seal.is(SpireGearItems.seal(next))) return ItemStack.EMPTY;
		ItemStack out = new ItemStack(SpireGearItems.get(next, kind));
		out.applyComponents(piece.getComponentsPatch());
		out.set(DataComponents.DAMAGE, 0);
		out.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		SpireGear.sanitize(out);
		return out;
	}

	/** Why the work cannot go ahead, or DONE when it can. */
	public static Outcome check(Inventory pockets, ItemStack piece, ItemStack shards, ItemStack seal) {
		Work work = work(piece, seal);
		if (work == Work.NONE) return Outcome.NOTHING;
		if (work == Work.RAISE && nextTier(piece) == null) return Outcome.TOP;
		if (result(piece, seal).isEmpty()) return work == Work.RAISE ? Outcome.NO_SEAL : Outcome.TOP;
		if (shards.getCount() < shardCost(piece, seal) || (!shards.isEmpty() && !shards.is(LifeItems.CORE_SHARD))) return Outcome.NO_SHARDS;
		if (Coins.count(pockets) < coinCost(piece, seal)) return Outcome.NO_COINS;
		return Outcome.DONE;
	}

	/**
	 * Does the work: the shards out of {@code shards}, the coins out of the player's purse; the piece made, or empty
	 * (and {@code outcome[0]} why not).
	 */
	public static ItemStack work(Player player, ItemStack piece, ItemStack shards, ItemStack seal, Outcome[] outcome) {
		outcome[0] = check(player.getInventory(), piece, shards, seal);
		if (outcome[0] != Outcome.DONE) return ItemStack.EMPTY;
		ItemStack made = result(piece, seal);
		int coins = coinCost(piece, seal);
		boolean raise = work(piece, seal) == Work.RAISE;
		shards.shrink(shardCost(piece, seal));
		if (raise) seal.shrink(1);
		Coins.pay(player, coins);
		return made;
	}

	/** A click in the window (CoreAnvilPayload). */
	public void act(Player player, int action) {
		if (action != WORK) return;
		Work work = work(piece(), seal());
		Outcome[] outcome = {Outcome.NOTHING};
		ItemStack made = work(player, piece(), shards(), seal(), outcome);
		if (outcome[0] == Outcome.DONE) {
			input.setItem(PIECE, made);
			com.vnap.progress.VillageAdvancements.award(player, work == Work.RAISE ? "spire/raise" : "spire/hone");
			if (SpireGear.stage(made) >= 3) com.vnap.progress.VillageAdvancements.award(player, "spire/honed");
			access.execute((level, pos) -> level.playSound(null, pos, work == Work.HONE ? com.vnap.sound.SpireSounds.HONE : com.vnap.sound.SpireSounds.RAISE,
					SoundSource.BLOCKS, 0.8F, work == Work.HONE ? 1.3F : 1.0F));
		} else if (outcome[0] != Outcome.NOTHING) {
			com.vnap.Mc.say(player, Component.translatable("spire_gear.villager_life.anvil." + outcome[0].name().toLowerCase(java.util.Locale.ROOT)), true);
		}
		broadcastChanges();
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(access, player, SpireBlocks.CORE_ANVIL);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		access.execute((level, pos) -> clearContainer(player, input));
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) return ItemStack.EMPTY;
		ItemStack stack = slot.getItem();
		ItemStack copy = stack.copy();
		if (index <= SEAL) {
			if (!moveItemStackTo(stack, SEAL + 1, slots.size(), true)) return ItemStack.EMPTY;
		} else {
			int to = stack.is(LifeItems.CORE_SHARD) ? SHARDS : isSeal(stack) ? SEAL : accepts(stack) ? PIECE : -1;
			if (to < 0 || !moveItemStackTo(stack, to, to + 1, false)) return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
		else slot.setChanged();
		slot.onTake(player, stack);
		return copy;
	}
}
