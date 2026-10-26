package com.vnap.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.item.BlessingStaffItem;
import com.vnap.item.LifeItems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
//? if <1.21.4
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jspecify.annotations.Nullable;

/** Hold-to-charge visuals of the elder's staff: model property, raised arm, trembling first-person hand. */
public final class StaffClient {
    // an arm pose also says from 1.21.11 whether it moves the off hand (META-INF/enumextensions.json, rewritten by the build)
    public static final EnumProxy<HumanoidModel.ArmPose> TOLL_POSE = new EnumProxy<>(HumanoidModel.ArmPose.class,
            //? if >=1.21.11
            /*false,*/
            false, (IArmPoseTransformer) StaffClient::raiseArm);

    private StaffClient() {
    }

    /** The charged model: items/villager_blessing_staff.json picks it by use duration from 1.21.4. */
    @SuppressWarnings("FutureReturnValueIgnored")
    public static void onSetup(FMLClientSetupEvent event) {
        //? if <1.21.4 {
        event.enqueueWork(() -> ItemProperties.register(LifeItems.BLESSING_STAFF,
                ResourceLocation.fromNamespaceAndPath("villager_life", "charge"),
                (stack, level, entity, seed) -> charge(entity, stack)));
        //?}
    }

    public static void onExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return entity.isUsingItem() && entity.getUsedItemHand() == hand ? TOLL_POSE.getValue() : HumanoidModel.ArmPose.ITEM;
            }

            @Override
            // NeoForge hands the player's render state from 26.3; Forge still the local player
            //? if >=26.3 && neoforge {
            /*public boolean applyForgeHandTransform(PoseStack pose, net.minecraft.client.renderer.state.level.PlayerRenderState state, HumanoidArm arm,
                    ItemStack stack, float partialTick, float equipProcess, float swingProcess) {
                // the hand drawn in first person is the local player's
                LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
                if (player == null || !player.isUsingItem() || player.getUseItem() != stack) {
            *///?} else {
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                    float partialTick, float equipProcess, float swingProcess) {
                if (!player.isUsingItem() || player.getUseItem() != stack) {
            //?}
                    return false;
                }
                float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
                float charge = charge(player, stack);
                float shake = charge >= 1.0F ? Mth.sin((player.tickCount + partialTick) * 2.6F) * 0.012F : 0.0F;
                pose.translate(side * 0.56F + shake, -0.42F + charge * 0.22F, -0.72F);
                pose.mulPose(Axis.XP.rotationDegrees(charge * 28.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(side * charge * -6.0F));
                return true;
            }
        }, LifeItems.BLESSING_STAFF);
    }

    private static float charge(@Nullable LivingEntity entity, ItemStack stack) {
        if (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) {
            return 0.0F;
        }
        int held = stack.getUseDuration(entity) - entity.getUseItemRemainingTicks();
        return Math.min(1.0F, held / (float) BlessingStaffItem.CHARGE_TICKS);
    }

    //? if >=1.21.2 {
    /*private static void raiseArm(HumanoidModel<?> model, net.minecraft.client.renderer.entity.state.HumanoidRenderState entity, HumanoidArm arm) {
        float charge = Math.min(1.0F, entity.ticksUsingItem / (float) BlessingStaffItem.CHARGE_TICKS);
        int tickCount = (int) entity.ageInTicks;
    *///?} else {
    private static void raiseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float charge = charge(entity, entity.getUseItem());
        int tickCount = entity.tickCount;
    //?}
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        part.xRot = -1.2F - charge * 1.5F + model.head.xRot * 0.3F;
        part.yRot = side * -0.25F;
        part.zRot = side * 0.12F;
        if (charge >= 1.0F) {
            part.zRot += Mth.sin(tickCount * 2.6F) * 0.03F;
        }
    }
}
