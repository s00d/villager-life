package com.vnap.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.VillagerLife;
import com.vnap.entity.golem.GolemAction;
import com.vnap.entity.golem.TocsinGolem;
import net.minecraft.client.model.geom.ModelLayerLocation;
import com.vnap.client.Draw;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
//? if >=1.21.2 {
/*import com.vnap.client.RenderedEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
*///?} else
import net.minecraft.util.FastColor;

//? if >=1.21.2 {
/*public final class TocsinGolemRenderer extends MobRenderer<TocsinGolem, LivingEntityRenderState, TocsinGolemModel> {
*///?} else
public final class TocsinGolemRenderer extends MobRenderer<TocsinGolem, TocsinGolemModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(VillagerLife.id("tocsin_golem"), "main");
    private static final ResourceLocation TEXTURE = VillagerLife.id("textures/entity/tocsin_golem.png");
    private static final ResourceLocation GLOW = VillagerLife.id("textures/entity/tocsin_golem_glow.png");
    private static final ResourceLocation CRACKS = VillagerLife.id("textures/entity/tocsin_golem_cracks.png");
    private static final float SCALE = 1.45F;

    public TocsinGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new TocsinGolemModel(context.bakeLayer(LAYER), new TocsinGolemAnimations(context.getResourceManager())), 1.5F);
        addLayer(new Glow(this, GLOW, false));
        addLayer(new Glow(this, CRACKS, true));
    }

    //? if >=1.21.2 {
    /*@Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public ResourceLocation getTextureLocation(LivingEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    // The death clip does the falling; skip the vanilla sideways flip.
    @Override
    protected void setupRotations(LivingEntityRenderState state, PoseStack poseStack, float yBodyRot, float scale) {
        if (state.deathTime > 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot));
            return;
        }
        super.setupRotations(state, poseStack, yBodyRot, scale);
    }

    @Override
    protected float getFlipDegrees() {
        return 0.0F;
    }
    *///?} else {
    @Override
    public ResourceLocation getTextureLocation(TocsinGolem golem) {
        return TEXTURE;
    }

    @Override
    protected void scale(TocsinGolem golem, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    /** The death clip does the falling; skip the vanilla sideways flip. */
    @Override
    protected void setupRotations(TocsinGolem golem, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        if (golem.deathTime > 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot));
            return;
        }
        super.setupRotations(golem, poseStack, bob, yBodyRot, partialTick, scale);
    }

    @Override
    protected float getFlipDegrees(TocsinGolem golem) {
        return 0.0F;
    }
    //?}

    //? if >=1.21.2 {
    /*private static final class Glow extends RenderLayer<LivingEntityRenderState, TocsinGolemModel> {
    *///?} else
    private static final class Glow extends RenderLayer<TocsinGolem, TocsinGolemModel> {
        private final ResourceLocation texture;
        private final boolean cracks;

        //? if >=1.21.2 {
        /*Glow(RenderLayerParent<LivingEntityRenderState, TocsinGolemModel> parent, ResourceLocation texture, boolean cracks) {
        *///?} else
        Glow(RenderLayerParent<TocsinGolem, TocsinGolemModel> parent, ResourceLocation texture, boolean cracks) {
            super(parent);
            this.texture = texture;
            this.cracks = cracks;
        }

        //? if >=1.21.9 {
        /*@Override
        public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes, int light, LivingEntityRenderState state,
                float yRot, float xRot) {
            TocsinGolem golem = RenderedEntity.of(state, TocsinGolem.class);
            if (golem != null) glow(poseStack, new Draw(nodes), golem, state, state.partialTick, state.ageInTicks);
        }
        *///?} else if >=1.21.2 {
        /*@Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntityRenderState state, float yRot, float xRot) {
            TocsinGolem golem = RenderedEntity.of(state, TocsinGolem.class);
            if (golem != null) glow(poseStack, new Draw(buffers), golem, state, state.partialTick, state.ageInTicks);
        }
        *///?} else {
        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, TocsinGolem golem, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            glow(poseStack, new Draw(buffers), golem, golem, partialTick, ageInTicks);
        }
        //?}

        //? if >=1.21.2 {
        /*private void glow(PoseStack poseStack, Draw draw, TocsinGolem golem, LivingEntityRenderState state, float partialTick, float ageInTicks) {
        *///?} else
        private void glow(PoseStack poseStack, Draw draw, TocsinGolem golem, TocsinGolem state, float partialTick, float ageInTicks) {
            if (cracks && !golem.flag(TocsinGolem.FLAG_BELL_CRACKED)) {
                return;
            }
            float pulse = 0.65F + 0.35F * Mth.sin(ageInTicks * (golem.phase() >= 3 ? 0.35F : 0.12F));
            if (golem.flag(TocsinGolem.FLAG_VENT) || golem.flag(TocsinGolem.FLAG_BACK_OPEN)) {
                pulse = 1.0F;
            }
            if (golem.action() == GolemAction.DEATH || golem.deathTime > 0) {
                pulse *= Math.max(0.0F, 1.0F - (golem.deathTime + partialTick) / 60.0F);
            }
            //? if >=1.21.2 {
            /*int color = ARGB.colorFromFloat(1.0F, pulse, pulse, pulse);
            *///?} else
            int color = FastColor.ARGB32.colorFromFloat(1.0F, pulse, pulse, pulse);
            draw.entityModel(getParentModel(), state, poseStack, com.vnap.client.McClient.eyes(texture), 0xF000F0, OverlayTexture.NO_OVERLAY, color, cracks ? 2 : 1);
        }
    }
}
