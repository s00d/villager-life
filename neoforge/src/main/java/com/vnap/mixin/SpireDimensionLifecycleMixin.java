package com.vnap.mixin;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The spire's dimension and its type come from the mod's own data, which the game counts as no known pack: their
 * entries would be "experimental", and every world would open with the "Experimental Settings" warning. They are the
 * mod's, as stable as its code.
 */
@SuppressWarnings("unused")
@Mixin(RegistryDataLoader.class)
public abstract class SpireDimensionLifecycleMixin {
	@ModifyVariable(method = "loadElementFromResource", at = @At("HEAD"), argsOnly = true, require = 0)
	private static RegistrationInfo vnap$stableSpire(RegistrationInfo info, WritableRegistry<?> registry, com.mojang.serialization.Decoder<?> codec,
			RegistryOps<com.google.gson.JsonElement> ops, ResourceKey<?> key, Resource resource, RegistrationInfo same) {
		return com.vnap.Mc.id(key).getNamespace().equals(com.vnap.VillagerLife.MOD_ID) ? new RegistrationInfo(info.knownPackInfo(), Lifecycle.stable()) : info;
	}
}
