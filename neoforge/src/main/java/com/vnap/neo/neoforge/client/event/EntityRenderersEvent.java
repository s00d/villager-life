package com.vnap.neo.neoforge.client.event;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

public final class EntityRenderersEvent {
	private EntityRenderersEvent() {
	}

	public static final class RegisterLayerDefinitions extends Event {
		static {
			Bridge.bind(RegisterLayerDefinitions.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions.class,
					priority, RegisterLayerDefinitions::new, sink));
		}

		private final net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions f;

		RegisterLayerDefinitions(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions f) {
			super(f);
			this.f = f;
		}

		public void registerLayerDefinition(ModelLayerLocation layer, Supplier<LayerDefinition> supplier) {
			f.registerLayerDefinition(layer, supplier);
		}
	}

	public static final class RegisterRenderers extends Event {
		static {
			Bridge.bind(RegisterRenderers.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers.class,
					priority, RegisterRenderers::new, sink));
		}

		private final net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers f;

		RegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers f) {
			super(f);
			this.f = f;
		}

		public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
			f.registerEntityRenderer(type, provider);
		}

		//? if >=1.21.9 {
		/*public <T extends BlockEntity, S extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState> void registerBlockEntityRenderer(
				BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
			f.registerBlockEntityRenderer(type, provider);
		}
		*///?} else {
		public <T extends BlockEntity> void registerBlockEntityRenderer(BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
			f.registerBlockEntityRenderer(type, provider);
		}
		//?}
	}

	public static final class AddLayers extends Event {
		static {
			Bridge.bind(AddLayers.class, (priority, sink) -> Bridge.mod(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers.class, priority, AddLayers::new, sink));
		}

		private final net.minecraftforge.client.event.EntityRenderersEvent.AddLayers f;

		AddLayers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers f) {
			super(f);
			this.f = f;
		}

		//? if >=1.21.2 {
		/*public <T extends LivingEntity> @Nullable EntityRenderer<?, ?> getRenderer(EntityType<? extends T> type) {
			return f.<T, net.minecraft.client.renderer.entity.state.EntityRenderState, EntityRenderer<T, net.minecraft.client.renderer.entity.state.EntityRenderState>>getEntityRenderer(type);
		}
		*///?} else {
		public <T extends LivingEntity> @Nullable EntityRenderer<?> getRenderer(EntityType<? extends T> type) {
			return f.<T, EntityRenderer<T>>getEntityRenderer(type);
		}
		//?}

		//? if >=1.21.9 {
		/*public Set<net.minecraft.world.entity.player.PlayerModelType> getSkins() {
			return f.getModelTypes();
		}

		public net.minecraft.client.renderer.entity.player.@Nullable AvatarRenderer<net.minecraft.client.player.AbstractClientPlayer> getPlayerRenderer(
				net.minecraft.world.entity.player.PlayerModelType skin) {
			return f.getPlayerRenderer(skin);
		}

		public net.minecraft.client.renderer.entity.player.@Nullable AvatarRenderer<net.minecraft.client.entity.ClientMannequin> getMannequinRenderer(
				net.minecraft.world.entity.player.PlayerModelType skin) {
			return f.getMannequinRenderer(skin);
		}
		*///?} else if >=1.21.2 {
		/*public Set<net.minecraft.client.resources.PlayerSkin.Model> getSkins() {
			return f.getSkins();
		}

		public @Nullable EntityRenderer<? extends Player, ?> getSkin(net.minecraft.client.resources.PlayerSkin.Model skin) {
			return f.<EntityRenderer<? extends Player, ?>>getPlayerSkin(skin);
		}
		*///?} else {
		public Set<net.minecraft.client.resources.PlayerSkin.Model> getSkins() {
			return f.getSkins();
		}

		public @Nullable EntityRenderer<? extends Player> getSkin(net.minecraft.client.resources.PlayerSkin.Model skin) {
			return f.<EntityRenderer<? extends Player>>getPlayerSkin(skin);
		}
		//?}
	}
}
