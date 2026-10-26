package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's ItemEntityPickupEvent.Post: an item taken into a player's inventory, with the stack as it lay. */
@SuppressWarnings("unused")
@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin {
	@WrapOperation(method = "playerTouch", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean vnap$taken(Inventory inventory, ItemStack stack, Operation<Boolean> vanilla, Player player) {
		ItemStack original = stack.copy();
		boolean added = vanilla.call(inventory, stack);
		if (added) Bridge.fire(new ItemEntityPickupEvent.Post(player, (ItemEntity) (Object) this, original));
		return added;
	}
}
