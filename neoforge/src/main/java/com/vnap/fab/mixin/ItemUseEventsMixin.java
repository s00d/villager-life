package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.ArrowNockEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's ArrowNockEvent: a bow drawn. */
@SuppressWarnings("unused")
@Mixin(BowItem.class)
public abstract class ItemUseEventsMixin {
	@Inject(method = "use", at = @At("HEAD"))
	private void vnap$nock(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<?> info) {
		ItemStack bow = player.getItemInHand(hand);
		Bridge.fire(new ArrowNockEvent(player, bow, level, hand, !player.getProjectile(bow).isEmpty()));
	}
}
