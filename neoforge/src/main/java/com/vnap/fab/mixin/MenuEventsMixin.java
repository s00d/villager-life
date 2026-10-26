package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.AnvilCraftEvent;
import com.vnap.fab.neoforge.event.entity.player.AnvilRepairEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's anvil events: the result taken (the repair before the inputs are used, the craft both, as NeoForge's versions name it). */
@SuppressWarnings("unused")
@Mixin(AnvilMenu.class)
public abstract class MenuEventsMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void vnap$anvil(Player player, ItemStack stack, CallbackInfo info) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		Bridge.fire(new AnvilRepairEvent(player, menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), stack));
		Bridge.fire(new AnvilCraftEvent.Post(player, stack));
	}
}
