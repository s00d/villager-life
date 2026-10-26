package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.ItemFishedEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's ItemFishedEvent: the catch of a rod, before vanilla hands it out (cancelled: nothing caught). */
@SuppressWarnings("unused")
@Mixin(FishingHook.class)
public abstract class FishingMixin {
	@WrapOperation(method = "retrieve", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
	private ObjectArrayList<ItemStack> vnap$caught(LootTable table, LootParams params, Operation<ObjectArrayList<ItemStack>> vanilla) {
		ObjectArrayList<ItemStack> list = vanilla.call(table, params);
		FishingHook hook = (FishingHook) (Object) this;
		if (hook.getPlayerOwner() == null) return list;
		NonNullList<ItemStack> drops = NonNullList.create();
		drops.addAll(list);
		var event = Bridge.fire(new ItemFishedEvent(hook.getPlayerOwner(), drops, hook, hook.onGround() ? 2 : 1));
		return event.isCanceled() ? new ObjectArrayList<>() : list;
	}
}
