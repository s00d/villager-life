package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's PlayerBrewedPotionEvent: a potion taken from a brewing stand. */
@SuppressWarnings("unused")
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public abstract class BrewingMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void vnap$brewed(Player player, ItemStack stack, CallbackInfo info) {
		if (player instanceof ServerPlayer && stack.has(DataComponents.POTION_CONTENTS)) Bridge.fire(new PlayerBrewedPotionEvent(player, stack));
	}
}
