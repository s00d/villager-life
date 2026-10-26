package com.vnap.fab.neoforge.event.entity.player;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/** An item enchanted at a table (com.vnap.fab.mixin.EnchantmentMenuMixin). */
public final class PlayerEnchantItemEvent extends Event {

	private final Player player;
	private final ItemStack enchanted;
	private final List<EnchantmentInstance> enchantments;

	public PlayerEnchantItemEvent(Player player, ItemStack enchanted, List<EnchantmentInstance> enchantments) {
		this.player = player;
		this.enchanted = enchanted;
		this.enchantments = enchantments;
	}

	public Player getEntity() {
		return player;
	}

	public ItemStack getEnchantedItem() {
		return enchanted;
	}

	public List<EnchantmentInstance> getEnchantments() {
		return enchantments;
	}
}
