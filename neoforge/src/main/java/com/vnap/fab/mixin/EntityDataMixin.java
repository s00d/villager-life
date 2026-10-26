package com.vnap.fab.mixin;

import com.vnap.fab.neoforge.common.extensions.IEntityExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's data of the mods on every entity, saved with it under its key. */
@SuppressWarnings("unused")
@Mixin(Entity.class)
public abstract class EntityDataMixin implements IEntityExtension {
	@Unique
	private static final String VNAP_KEY = "NeoForgeData";
	@Unique
	private @Nullable CompoundTag vnap$data;

	@Override
	public CompoundTag getPersistentData() {
		CompoundTag data = vnap$data;
		if (data == null) {
			data = new CompoundTag();
			vnap$data = data;
		}
		return data;
	}

	//? if >=1.21.6 {
	/*@Inject(method = "saveWithoutId", at = @At("TAIL"))
	private void vnap$save(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
		if (vnap$data != null && !vnap$data.isEmpty()) output.store(VNAP_KEY, CompoundTag.CODEC, vnap$data);
	}

	@Inject(method = "load", at = @At("HEAD"))
	private void vnap$load(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
		vnap$data = input.read(VNAP_KEY, CompoundTag.CODEC).map(CompoundTag::copy).orElse(null);
	}
	*///?} else {
	@Inject(method = "saveWithoutId", at = @At("TAIL"))
	private void vnap$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> ci) {
		if (vnap$data != null && !vnap$data.isEmpty()) tag.put(VNAP_KEY, vnap$data.copy());
	}

	@Inject(method = "load", at = @At("HEAD"))
	private void vnap$load(CompoundTag tag, CallbackInfo ci) {
		vnap$data = tag.contains(VNAP_KEY) ? tag.getCompound(VNAP_KEY).copy() : null;
	}
	//?}
}
