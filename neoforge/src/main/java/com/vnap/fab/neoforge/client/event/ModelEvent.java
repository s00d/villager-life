package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Models of the mod's own, loaded besides the blocks' and items' (Fabric API's model loading plugin: com.vnap.fab.ClientHooks). */
public final class ModelEvent {
	private ModelEvent() {
	}

	//? if >=1.21.5 {
	/*public static final class RegisterStandalone extends Event {
	*///?} else
	public static final class RegisterAdditional extends Event {
		private final List<ResourceLocation> models = new ArrayList<>();

		//? if >=1.21.5 {
		/*public RegisterStandalone() {
		*///?} else
		public RegisterAdditional() {
		}

		public void register(ResourceLocation model) {
			models.add(model);
		}

		public List<ResourceLocation> vnap$models() {
			return models;
		}
		//? if >=1.21.5 {

		/*/^* The key Fabric API keeps the model by (one per id). ^/
		private static final java.util.Map<ResourceLocation, net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey<net.minecraft.client.renderer.block.model.BlockStateModel>> KEYS =
				new java.util.concurrent.ConcurrentHashMap<>();

		public static net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey<net.minecraft.client.renderer.block.model.BlockStateModel> key(ResourceLocation id) {
			return KEYS.computeIfAbsent(id, name -> net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey.create(name::toString));
		}
		*///?}
	}
}
