package com.vnap.fab.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's lifespan of an item on the ground: it goes when its age reaches it (vanilla's 6000 unless set). */
@SuppressWarnings("unused")
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin implements com.vnap.fab.ItemLifespan {
	@Unique
	private int vnap$lifespan = 6000;

	@Shadow
	private int age;

	@Override
	public void vnapLifespan(int ticks) {
		vnap$lifespan = ticks;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void vnap$expire(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (!self.level().isClientSide() && vnap$lifespan < 6000 && age >= vnap$lifespan && !self.isRemoved()) self.discard();
	}
}
