package com.vnap.client.spire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vnap.VillagerLife;
import com.vnap.client.Draw;
import com.vnap.client.McClient;
import com.vnap.spire.Genome;
import com.vnap.spire.guard.SpireGuard;
import net.minecraft.client.model.geom.ModelLayerLocation;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21.2 {
/*import com.vnap.client.RenderedEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
*///?}

import java.util.EnumMap;
import java.util.Map;

/**
 * Every guard of the spire, keepers too: the model of its body (one per silhouette, swapped per guard), drawn three
 * times — its neutral texture, its body again tinted with the genome's colour, its eyes and cores full bright in the
 * theme's eye colour. Its size is its SCALE attribute.
 */
//? if >=1.21.2 {
/*public final class SpireGuardRenderer extends MobRenderer<SpireGuard, LivingEntityRenderState, SpireGuardModel> {
*///?} else
public final class SpireGuardRenderer extends MobRenderer<SpireGuard, SpireGuardModel> {
	private final Map<Genome.Body, SpireGuardModel> models = new EnumMap<>(Genome.Body.class);

	public SpireGuardRenderer(EntityRendererProvider.Context context) {
		super(context, new SpireGuardModel(context.bakeLayer(layer(Genome.Body.SENTINEL)), Genome.Body.SENTINEL), 0.5F);
		for (Genome.Body body : Genome.Body.values()) models.put(body, new SpireGuardModel(context.bakeLayer(layer(body)), body));
		addLayer(new Tint(this));
	}

	public static ModelLayerLocation layer(Genome.Body body) {
		return new ModelLayerLocation(VillagerLife.id("spire/" + body.model()), "main");
	}

	static ResourceLocation texture(Genome.Body body, String suffix) {
		return VillagerLife.id("textures/entity/spire/" + body.model() + suffix + ".png");
	}

	private void wear(SpireGuard guard) {
		Genome.Look look = guard.look();
		SpireGuardModel chosen = models.get(look.body());
		if (chosen != null) model = chosen;
		shadowRadius = look.keeper() >= 0 ? 1.0F : 0.5F;
	}

	//? if >=1.21.2 {
	/*@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public void extractRenderState(SpireGuard guard, LivingEntityRenderState state, float partialTick) {
		super.extractRenderState(guard, state, partialTick);
		wear(guard);
	}

	@Override
	public ResourceLocation getTextureLocation(LivingEntityRenderState state) {
		SpireGuard guard = RenderedEntity.of(state, SpireGuard.class);
		return texture(guard == null ? Genome.Body.SENTINEL : guard.look().body(), "");
	}
	*///?} else {
	@Override
	public void render(SpireGuard guard, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
		wear(guard);
		super.render(guard, yaw, partialTick, poseStack, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SpireGuard guard) {
		return texture(guard.look().body(), "");
	}
	//?}

	//? if >=1.21.2 {
	/*private static final class Tint extends RenderLayer<LivingEntityRenderState, SpireGuardModel> {
		Tint(SpireGuardRenderer parent) {
			super(parent);
		}
	*///?} else {
	private static final class Tint extends RenderLayer<SpireGuard, SpireGuardModel> {
		Tint(SpireGuardRenderer parent) {
			super(parent);
		}
	//?}

		//? if >=1.21.9 {
		/*@Override
		public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes, int light, LivingEntityRenderState state,
				float yRot, float xRot) {
			SpireGuard guard = RenderedEntity.of(state, SpireGuard.class);
			if (guard != null) draw(poseStack, new Draw(nodes), guard, state, light, LivingEntityRenderer.getOverlayCoords(state, 0.0F));
		}
		*///?} else if >=1.21.2 {
		/*@Override
		public void render(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntityRenderState state, float yRot, float xRot) {
			SpireGuard guard = RenderedEntity.of(state, SpireGuard.class);
			if (guard != null) draw(poseStack, new Draw(buffers), guard, state, light, LivingEntityRenderer.getOverlayCoords(state, 0.0F));
		}
		*///?} else {
		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffers, int light, SpireGuard guard, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			draw(poseStack, new Draw(buffers), guard, guard, light, LivingEntityRenderer.getOverlayCoords(guard, 0.0F));
		}
		//?}

		//? if >=1.21.2 {
		/*private void draw(PoseStack poseStack, Draw draw, SpireGuard guard, LivingEntityRenderState state, int light, int overlay) {
		*///?} else
		private <S> void draw(PoseStack poseStack, Draw draw, SpireGuard guard, S state, int light, int overlay) {
			Genome.Look look = guard.look();
			// an echo is drawn see-through by the body pass alone
			if (guard.isInvisible()) return;
			draw.entityModel(getParentModel(), state, poseStack, McClient.entityCutoutNoCull(texture(look.body(), "_tint")), light, overlay,
					0xFF000000 | look.colour(), 1);
			draw.entityModel(getParentModel(), state, poseStack, McClient.eyes(texture(look.body(), "_glow")), 0xF000F0,
					net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0xFF000000 | look.glow(), 2);
		}
	}
}
