package com.vnap.fab.neoforge.common.extensions;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * NeoForge's say of an item in what may enchant it (com.vnap.fab.mixin.EnchantmentSupportMixin asks it wherever vanilla
 * asks the enchantment): an item of the mod that implements this decides for itself.
 */
public interface IItemExtension {
	default boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		return stack.is(Items.ENCHANTED_BOOK) || stack.is(enchantment.value().definition().supportedItems());
	}

	default boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
		var primary = enchantment.value().definition().primaryItems();
		return supportsEnchantment(stack, enchantment) && (primary.isEmpty() || stack.is(primary.get()));
	}

	// built into Item (Loom injects the interface; com.vnap.fab.mixin.ItemExtensionMixin)
	/** The stack's modifiers when it has none of its own (com.vnap.fab.mixin.ItemStackAttributesMixin asks it). */
	default net.minecraft.world.item.component.ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
		return net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY;
	}


	//? if <1.21.2 {
	/** The texture of an armour layer for this stack, or null for the material's (com.vnap.fab.mixin.client.ArmorTextureMixin asks it). */
	default net.minecraft.resources.@org.jspecify.annotations.Nullable ResourceLocation getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity,
			net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ArmorMaterial.Layer layer, boolean inner) {
		return null;
	}
	//?}

	/** The enchantment's holder in the registries of the game running (the server's, else the client's). */
	static Holder<Enchantment> holder(Enchantment enchantment) {
		var server = com.vnap.fab.neoforge.server.ServerLifecycleHooks.getCurrentServer();
		net.minecraft.core.RegistryAccess registries = server != null ? server.registryAccess()
				: java.util.Objects.requireNonNull(com.vnap.Mc.clientLevel.get(), "no game to ask for the enchantment").registryAccess();
		return registries.registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).wrapAsHolder(enchantment);
	}
}
