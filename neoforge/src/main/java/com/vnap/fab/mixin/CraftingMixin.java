package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's ItemCraftedEvent: a crafted result taken, after vanilla's onCraftedBy. */
@SuppressWarnings("unused")
@Mixin(ResultSlot.class)
public abstract class CraftingMixin {
	@Shadow
	@Final
	private Player player;

	@Shadow
	@Final
	private CraftingContainer craftSlots;

	//? if >=1.21.5 {
	/*@Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/entity/player/Player;I)V", shift = At.Shift.AFTER))
	*///?} else
	@Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;I)V", shift = At.Shift.AFTER))
	private void vnap$crafted(ItemStack stack, CallbackInfo info) {
		Bridge.fire(new PlayerEvent.ItemCraftedEvent(player, stack, craftSlots));
	}
}
