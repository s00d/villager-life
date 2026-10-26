package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.PlayerDestroyItemEvent;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's PlayerDestroyItemEvent: a player's item worn out, the stack as it was. */
@SuppressWarnings("unused")
@Mixin(ItemStack.class)
public abstract class ItemBreakMixin {
	// from 1.21.2 the damage is applied in a method of its own
	//? if >=1.21.2 {
	/*@Inject(method = "applyDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
	private void vnap$broken(int damage, @Nullable ServerPlayer entity, Consumer<Item> onBreak, CallbackInfo info) {
		if (entity != null) Bridge.fire(new PlayerDestroyItemEvent(entity, ((ItemStack) (Object) this).copy()));
	}
	*///?} else {
	@Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
	private void vnap$broken(int damage, ServerLevel level, @Nullable ServerPlayer entity, Consumer<Item> onBreak, CallbackInfo info) {
		if (entity != null) Bridge.fire(new PlayerDestroyItemEvent(entity, ((ItemStack) (Object) this).copy()));
	}
	//?}
}
