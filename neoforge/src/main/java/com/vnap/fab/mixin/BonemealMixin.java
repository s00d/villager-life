package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.BonemealEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's BonemealEvent of a player's bone meal on a block. */
@SuppressWarnings("unused")
@Mixin(BoneMealItem.class)
public abstract class BonemealMixin {
	@Inject(method = "useOn", at = @At("HEAD"))
	private void vnap$bonemeal(UseOnContext context, CallbackInfoReturnable<InteractionResult> info) {
		var level = context.getLevel();
		var pos = context.getClickedPos();
		Bridge.fire(new BonemealEvent(context.getPlayer(), level, pos, level.getBlockState(pos), context.getItemInHand()));
	}
}
