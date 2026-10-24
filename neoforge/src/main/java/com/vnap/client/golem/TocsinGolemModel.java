package com.vnap.client.golem;

import com.vnap.entity.golem.GolemAction;
import com.vnap.entity.golem.TocsinGolem;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
//? if >=1.21.2 {
/*import com.vnap.client.RenderedEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
*///?} else
import net.minecraft.client.model.HierarchicalModel;

//? if >=1.21.2 {
/*public final class TocsinGolemModel extends EntityModel<LivingEntityRenderState> {
*///?} else
public final class TocsinGolemModel extends HierarchicalModel<TocsinGolem> {
    //? if <1.21.2
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart shoulderLeft;
    private final ModelPart shoulderRight;
    //? if >=1.21.6 {
    /*// clips are baked onto the model's parts from 1.21.6
    private final net.minecraft.client.animation.KeyframeAnimation walk;
    private final net.minecraft.client.animation.KeyframeAnimation idle;
    private final net.minecraft.client.animation.KeyframeAnimation[] actions;
    *///?} else
    private final TocsinGolemAnimations clips;

    public TocsinGolemModel(ModelPart root, TocsinGolemAnimations clips) {
        //? if >=1.21.2 {
        /*super(root);
        *///?} else
        this.root = root;
        //? if >=1.21.6 {
        /*this.walk = clips.walk.bake(root);
        this.idle = clips.idle.bake(root);
        this.actions = new net.minecraft.client.animation.KeyframeAnimation[com.vnap.entity.golem.GolemAction.values().length];
        for (int i = 1; i < actions.length; i++) actions[i] = clips.forAction(i).bake(root);
        *///?} else
        this.clips = clips;
        ModelPart body = root.getChild("body");
        this.head = body.getChild("head");
        this.shoulderLeft = body.getChild("shoulder_left");
        this.shoulderRight = body.getChild("shoulder_right");
    }

    //? if >=1.21.2 {
    /*@Override
    public void setupAnim(LivingEntityRenderState state) {
        super.setupAnim(state);
        TocsinGolem golem = RenderedEntity.of(state, TocsinGolem.class);
        if (golem != null) pose(golem, state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
    }
    *///?} else {
    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(TocsinGolem golem, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        pose(golem, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
    //?}

    private void pose(TocsinGolem golem, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        GolemAction action = golem.action();
        if (action == GolemAction.NONE) {
            head.yRot = Mth.clamp(netHeadYaw, -35.0F, 35.0F) * Mth.DEG_TO_RAD;
            head.xRot = Mth.clamp(headPitch, -20.0F, 25.0F) * Mth.DEG_TO_RAD;
            //? if >=1.21.6 {
            /*walk.applyWalk(limbSwing, limbSwingAmount, 2.2F, 2.5F);
            idle.apply(golem.idleState, ageInTicks);
            *///?} else {
            animateWalk(clips.walk, limbSwing, limbSwingAmount, 2.2F, 2.5F);
            animate(golem.idleState, clips.idle, ageInTicks);
            //?}
        }
        for (int i = 1; i < golem.actionStates.length; i++) {
            //? if >=1.21.6 {
            /*actions[i].apply(golem.actionStates[i], ageInTicks);
            *///?} else
            animate(golem.actionStates[i], clips.forAction(i), ageInTicks);
        }
        boolean shed = golem.flag(TocsinGolem.FLAG_PLATES_SHED);
        shoulderLeft.visible = !shed;
        shoulderRight.visible = !shed;
    }
}
