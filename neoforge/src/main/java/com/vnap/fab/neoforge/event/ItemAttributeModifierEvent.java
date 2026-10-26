package com.vnap.fab.neoforge.event;

import com.vnap.fab.Bridge;
import com.vnap.fab.bus.api.Event;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** A stack's attribute modifiers, as read whenever the game reads them (com.vnap.fab.mixin.ItemStackAttributesMixin). */
public final class ItemAttributeModifierEvent extends Event {

	private final ItemStack stack;
	private final ItemAttributeModifiers defaults;
	private final List<ItemAttributeModifiers.Entry> entries;

	public ItemAttributeModifierEvent(ItemStack stack, ItemAttributeModifiers defaults) {
		this.stack = stack;
		this.defaults = defaults;
		this.entries = new ArrayList<>(defaults.modifiers());
	}

	/** The stack's modifiers after the listeners (the defaults themselves when none listens). */
	public static ItemAttributeModifiers of(ItemStack stack, ItemAttributeModifiers defaults) {
		ItemAttributeModifierEvent event = Bridge.fire(new ItemAttributeModifierEvent(stack, defaults));
		if (event.entries.equals(defaults.modifiers())) return defaults;
		//? if >=1.21.5 {
		/*return new ItemAttributeModifiers(List.copyOf(event.entries));
		*///?} else
		return new ItemAttributeModifiers(List.copyOf(event.entries), defaults.showInTooltip());
	}

	public ItemStack getItemStack() {
		return stack;
	}

	public ItemAttributeModifiers getDefaultModifiers() {
		return defaults;
	}

	public List<ItemAttributeModifiers.Entry> getModifiers() {
		return List.copyOf(entries);
	}

	public boolean addModifier(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
		for (ItemAttributeModifiers.Entry entry : entries) {
			if (entry.attribute().equals(attribute) && entry.modifier().id().equals(modifier.id())) return false;
		}
		entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot));
		return true;
	}

	public boolean removeModifier(Holder<Attribute> attribute, net.minecraft.resources.ResourceLocation id) {
		return entries.removeIf(entry -> entry.attribute().equals(attribute) && entry.modifier().id().equals(id));
	}

	public void clearModifiers() {
		entries.clear();
	}
}
