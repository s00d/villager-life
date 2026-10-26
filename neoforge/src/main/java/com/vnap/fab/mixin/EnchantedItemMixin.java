package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.PlayerEnchantItemEvent;
import java.util.ArrayList;
import java.util.List;
//? if >=26.2 {
/*import net.minecraft.advancements.triggers.EnchantedItemTrigger;
*///?} else
import net.minecraft.advancements.critereon.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's PlayerEnchantItemEvent: an item just enchanted at the table (the table's advancement trigger is its moment). */
@SuppressWarnings("unused")
@Mixin(EnchantedItemTrigger.class)
public abstract class EnchantedItemMixin {
	@Inject(method = "trigger", at = @At("HEAD"))
	private void vnap$enchanted(ServerPlayer player, ItemStack stack, int levels, CallbackInfo ci) {
		List<EnchantmentInstance> list = new ArrayList<>();
		EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet().forEach(entry -> list.add(new EnchantmentInstance(entry.getKey(), entry.getIntValue())));
		Bridge.fire(new PlayerEnchantItemEvent(player, stack, list));
	}
}
