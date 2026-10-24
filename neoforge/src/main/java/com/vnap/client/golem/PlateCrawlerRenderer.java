package com.vnap.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vnap.VillagerLife;
import com.vnap.entity.golem.PlateCrawler;
//? if >=1.21.2 {
/*import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
*///?} else
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import com.vnap.client.Draw;
//? if <1.21.9
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

//? if >=1.21.2 {
/*public final class PlateCrawlerRenderer extends MobRenderer<PlateCrawler, LivingEntityRenderState, PlateCrawlerRenderer.Model> {
*///?} else
public final class PlateCrawlerRenderer extends MobRenderer<PlateCrawler, PlateCrawlerRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(VillagerLife.id("plate_crawler"), "main");
    private static final ResourceLocation TEXTURE = VillagerLife.id("textures/entity/plate_crawler.png");
    private static final ResourceLocation GLOW = VillagerLife.id("textures/entity/plate_crawler_glow.png");

    public PlateCrawlerRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.6F);
        addLayer(new RenderLayer<>(this) {
            @Override
            //? if >=1.21.9 {
            /*public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector nodes, int light, LivingEntityRenderState state,
                    float yRot, float xRot) {
                new Draw(nodes).entityModel(getParentModel(), state, poseStack, com.vnap.client.McClient.eyes(GLOW), 0xF000F0, OverlayTexture.NO_OVERLAY, -1, 1);
            *///?} else if >=1.21.2 {
            /*public void render(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntityRenderState state, float yRot, float xRot) {
                new Draw(buffers).entityModel(getParentModel(), state, poseStack, com.vnap.client.McClient.eyes(GLOW), 0xF000F0, OverlayTexture.NO_OVERLAY, -1, 1);
            *///?} else {
            public void render(PoseStack poseStack, MultiBufferSource buffers, int light, PlateCrawler crawler, float limbSwing,
                               float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
                new Draw(buffers).entityModel(getParentModel(), crawler, poseStack, com.vnap.client.McClient.eyes(GLOW), 0xF000F0, OverlayTexture.NO_OVERLAY, -1, 1);
            //?}
            }
        });
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
    *///?} else {
    @Override
    public ResourceLocation getTextureLocation(PlateCrawler crawler) {
        return TEXTURE;
    }
    //?}

    //? if >=1.21.2 {
    /*public static final class Model extends EntityModel<LivingEntityRenderState> {
    *///?} else
    public static final class Model extends HierarchicalModel<PlateCrawler> {
        //? if <1.21.2
        private final ModelPart root;
        private final ModelPart plate;
        private final ModelPart[] legs;

        Model(ModelPart root) {
            //? if >=1.21.2 {
            /*super(root);
            *///?} else
            this.root = root;
            this.plate = root.getChild("plate");
            this.legs = new ModelPart[] {root.getChild("leg_fl"), root.getChild("leg_fr"), root.getChild("leg_bl"), root.getChild("leg_br")};
        }

        //? if >=1.21.2 {
        /*@Override
        public void setupAnim(LivingEntityRenderState state) {
            super.setupAnim(state);
            float limbSwing = state.walkAnimationPos;
            float limbSwingAmount = state.walkAnimationSpeed;
            float ageInTicks = state.ageInTicks;
        *///?} else {
        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(PlateCrawler crawler, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
        //?}
            float swing = limbSwing * 1.6F;
            for (int i = 0; i < legs.length; i++) {
                float phase = i == 0 || i == 3 ? 0.0F : Mth.PI;
                legs[i].xRot = Mth.cos(swing + phase) * 0.9F * limbSwingAmount;
                legs[i].zRot = (i % 2 == 0 ? -1 : 1) * (0.5F + Mth.sin(swing + phase) * 0.3F * limbSwingAmount);
            }
            plate.y += Mth.sin(ageInTicks * 0.5F) * 0.3F + Math.abs(Mth.sin(swing)) * limbSwingAmount * 0.8F;
            plate.zRot = Mth.sin(swing) * 0.08F * limbSwingAmount;
        }
    }
}
