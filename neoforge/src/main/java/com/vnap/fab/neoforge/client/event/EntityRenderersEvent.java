package com.vnap.fab.neoforge.client.event;

import com.vnap.fab.bus.api.Event;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

/** The mod's models and renderers (Fabric API's registries), and the layers it adds to vanilla's renderers once they are made. */
public final class EntityRenderersEvent {
	private EntityRenderersEvent() {
	}

	public static final class RegisterLayerDefinitions extends Event {
		public RegisterLayerDefinitions() {
		}

		public void registerLayerDefinition(ModelLayerLocation layer, Supplier<LayerDefinition> supplier) {
			net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(layer, supplier::get);
		}
	}

	public static final class RegisterRenderers extends Event {
		public RegisterRenderers() {
		}

		public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
			// vanilla's own from 1.21.11 (Fabric API's registry hands over to it)
			//? if >=1.21.11 {
			/*net.minecraft.client.renderer.entity.EntityRenderers.register(type, provider);
			*///?} else
			net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(type, provider);
		}
		//? if >=1.21.9 {

		/*public <T extends BlockEntity, S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> void registerBlockEntityRenderer(
				BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
			net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(type, provider);
		}
		*///?} else {

		public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
			net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(type, provider);
		}
		//?}
	}

	/** The renderers vanilla just made (com.vnap.fab.mixin.client.RenderersMadeMixin): layers can be added to them. */
	public static final class AddLayers extends Event {
		//? if >=1.21.2 {
		/*private final Map<EntityType<?>, EntityRenderer<?, ?>> renderers;
		*///?} else
		private final Map<EntityType<?>, EntityRenderer<?>> renderers;
		//? if >=1.21.9 {
		/*private final Map<net.minecraft.world.entity.player.PlayerModelType, net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer>> players;
		private final Map<net.minecraft.world.entity.player.PlayerModelType, net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.entity.ClientMannequin>> mannequins;

		public AddLayers(Map<EntityType<?>, EntityRenderer<?, ?>> renderers,
				Map<net.minecraft.world.entity.player.PlayerModelType, net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer>> players,
				Map<net.minecraft.world.entity.player.PlayerModelType, net.minecraft.client.renderer.entity.player.AvatarRenderer<net.minecraft.client.entity.ClientMannequin>> mannequins) {
			this.renderers = renderers;
			this.players = players;
			this.mannequins = mannequins;
		}
		*///?} else if >=1.21.2 {
		/*private final Map<net.minecraft.client.resources.PlayerSkin.Model, EntityRenderer<? extends net.minecraft.world.entity.player.Player, ?>> players;

		public AddLayers(Map<EntityType<?>, EntityRenderer<?, ?>> renderers,
				Map<net.minecraft.client.resources.PlayerSkin.Model, EntityRenderer<? extends net.minecraft.world.entity.player.Player, ?>> players) {
			this.renderers = renderers;
			this.players = players;
		}
		*///?} else {
		private final Map<net.minecraft.client.resources.PlayerSkin.Model, EntityRenderer<? extends net.minecraft.world.entity.player.Player>> players;

		public AddLayers(Map<EntityType<?>, EntityRenderer<?>> renderers,
				Map<net.minecraft.client.resources.PlayerSkin.Model, EntityRenderer<? extends net.minecraft.world.entity.player.Player>> players) {
			this.renderers = renderers;
			this.players = players;
		}
		//?}

		//? if >=1.21.2 {
		/*public <T extends LivingEntity> @Nullable EntityRenderer<?, ?> getRenderer(EntityType<? extends T> type) {
			return renderers.get(type);
		}
		*///?} else {
		public <T extends LivingEntity> @Nullable EntityRenderer<?> getRenderer(EntityType<? extends T> type) {
			return renderers.get(type);
		}
		//?}
		//? if >=1.21.9 {

		/*public Set<net.minecraft.world.entity.player.PlayerModelType> getSkins() {
			return players.keySet();
		}

		public net.minecraft.client.renderer.entity.player.@Nullable AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer> getPlayerRenderer(
				net.minecraft.world.entity.player.PlayerModelType skin) {
			return players.get(skin);
		}

		public net.minecraft.client.renderer.entity.player.@Nullable AvatarRenderer<net.minecraft.client.entity.ClientMannequin> getMannequinRenderer(
				net.minecraft.world.entity.player.PlayerModelType skin) {
			return mannequins.get(skin);
		}
		*///?} else if >=1.21.2 {

		/*public Set<net.minecraft.client.resources.PlayerSkin.Model> getSkins() {
			return players.keySet();
		}

		public @Nullable EntityRenderer<? extends net.minecraft.world.entity.player.Player, ?> getSkin(net.minecraft.client.resources.PlayerSkin.Model skin) {
			return players.get(skin);
		}
		*///?} else {

		public Set<net.minecraft.client.resources.PlayerSkin.Model> getSkins() {
			return players.keySet();
		}

		public @Nullable EntityRenderer<? extends net.minecraft.world.entity.player.Player> getSkin(net.minecraft.client.resources.PlayerSkin.Model skin) {
			return players.get(skin);
		}
		//?}
	}
}
