package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's ItemSmeltedEvent: a furnace's result taken. */
@SuppressWarnings("unused")
@Mixin(FurnaceResultSlot.class)
public abstract class SmeltingMixin {
	@Shadow
	@Final
	private Player player;

	@Inject(method = "checkTakeAchievements", at = @At("TAIL"))
	private void vnap$smelted(ItemStack stack, CallbackInfo info) {
		Bridge.fire(new PlayerEvent.ItemSmeltedEvent(player, stack));
	}
}
