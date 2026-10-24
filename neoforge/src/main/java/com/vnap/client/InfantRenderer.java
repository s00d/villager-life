package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.VillagerLife;
import com.vnap.block.DecorBlock;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.dialogue.Family;
import com.vnap.dialogue.Infants;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The newborn in its cradle: a swaddled baby of its own model (the villager stays invisible there, Infants.lying),
 * on its back along the cradle, its head on the pillow at the back end, in the cradle's light. It turns its head,
 * waves its hands and breathes; harder while it cries. The swaddle is pink for a girl, blue for a boy.
 */
final class InfantRenderer {
    private static final ResourceLocation TEXTURE = VillagerLife.id("textures/entity/infant.png");
    /** The mattress's top (9.5/16): the swaddle lies on it, the head on the pillow above. */
    private static final float MATTRESS = 9.5F / 16.0F;
    private static final int GIRL = 0xFFF4B8CC;
    private static final int BOY = 0xFFB4D2F2;
    static final ModelLayerLocation LAYER = new ModelLayerLocation(VillagerLife.id("infant"), "main");
    private final ModelPart head;
    private final ModelPart swaddle;
    private final ModelPart leftHand;
    private final ModelPart rightHand;

    /** {@code geo/infant.geo.json}: pixels, Y up, +Z towards the pillow; the cradle's inside is x -4..4, z -6..6. */
    InfantRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart model = context.bakeLayer(LAYER);
        head = model.getChild("head");
        swaddle = model.getChild("swaddle");
        leftHand = model.getChild("left_hand");
        rightHand = model.getChild("right_hand");
    }

    void render(DecorBlockEntity be, float partialTick, PoseStack pose, Draw draw, int light) {
        Level level = be.getLevel();
        if (level == null) return;
        List<Villager> babies = level.getEntitiesOfClass(Villager.class, new AABB(be.getBlockPos()), Infants::lying);
        if (babies.isEmpty()) return;
        Villager baby = babies.get(0);
        boolean crying = DialogueSoundState.speaking(baby.getUUID());
        // each baby its own rhythm
        float time = level.getGameTime() + partialTick + (baby.getId() % 97) * 7.0F;
        float headZ = crying ? 0.25F * Mth.sin(time / 2.5F) : 0.12F * Mth.sin(time / 41.0F);
        float headX = crying ? 0.15F : -0.06F * Mth.sin(time / 29.0F);
        float wave = crying ? 0.7F : 0.35F;
        float speed = crying ? 2.0F : 9.0F;
        float leftX = 0.3F - wave * Mth.sin(time / speed);
        float rightX = 0.3F - wave * Mth.sin(time / speed + 2.1F);
        float leftZ = 0.2F * Mth.sin(time / 13.0F);
        float rightZ = -0.2F * Mth.sin(time / 11.0F);
        float breath = 1.0F + (crying ? 0.06F : 0.035F) * Mth.sin(time / (crying ? 3.0F : 9.0F));
        pose.pushPose();
        pose.translate(0.5F, MATTRESS, 0.5F);
        // the cradle model's pillow lies at its back end, away from the facing
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - be.getBlockState().getValue(DecorBlock.FACING).toYRot()));
        // a touch smaller than the pixels it is built in: head and swaddle inside the rails and the end boards
        pose.scale(0.85F, 0.85F, 0.85F);
        // vanilla entity axes (Y down from 24 px), as Blockbench shows the model
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.5F, 0.0F);
        int colour = Family.female(baby) ? GIRL : BOY;
        // the parts are shared by every cradle: posed when drawn
        draw.geometry(pose, com.vnap.client.McClient.entityCutoutNoCull(TEXTURE), (at, buffer) -> {
            head.zRot = headZ;
            head.xRot = headX;
            leftHand.xRot = leftX;
            rightHand.xRot = rightX;
            leftHand.zRot = leftZ;
            rightHand.zRot = rightZ;
            swaddle.yScale = breath;
            PoseStack stack = Draw.stack(at);
            swaddle.render(stack, buffer, light, OverlayTexture.NO_OVERLAY, colour);
            head.render(stack, buffer, light, OverlayTexture.NO_OVERLAY);
            leftHand.render(stack, buffer, light, OverlayTexture.NO_OVERLAY);
            rightHand.render(stack, buffer, light, OverlayTexture.NO_OVERLAY);
        });
        pose.popPose();
    }
}
