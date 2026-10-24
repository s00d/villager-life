package com.vnap.client.spire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vnap.VillagerLife;
import com.vnap.client.Draw;
import com.vnap.client.McClient;
import com.vnap.spire.gear.GearItem;
import com.vnap.spire.gear.SpireGear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Spire armour in the round: over the flat armour every armour has, shoulder plates, a crest on the helm, a
 * breastplate, tassets, knee plates and boot cuffs, more of them stage by stage of its hone (SpireGear.stage): stage 1
 * a second shoulder plate, the breastplate and the knees, stage 2 a collar, the helm's wings and toe caps, stage 3
 * spikes on the shoulders. Each plate follows the wearer's part it sits on. Its texture is the 64x64 sheet under the
 * flat layer (tools/art/paint_spire_gear.py paints both: its EXTRAS are these boxes' texture offsets and sizes).
 */
//? if >=1.21.2 {
/*public final class SpireArmorLayer<S extends net.minecraft.client.renderer.entity.state.HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
*///?} else
public final class SpireArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(VillagerLife.id("spire_armor"), "plates");
	private static @Nullable ModelPart plates;

	public static LayerDefinition layer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(0, 32).addBox(-0.5F, -11.5F, -4.5F, 1.0F, 3.0F, 9.0F), PartPose.ZERO);
		head.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(20, 32).addBox(4.6F, -10.0F, -1.5F, 1.0F, 4.0F, 3.0F),
				PartPose.rotation(0.0F, 0.0F, -0.35F));
		head.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(20, 32).mirror().addBox(-5.6F, -10.0F, -1.5F, 1.0F, 4.0F, 3.0F),
				PartPose.rotation(0.0F, 0.0F, 0.35F));
		PartDefinition body = root.getChild("body");
		body.addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(42, 44).addBox(-3.0F, 1.0F, -3.7F, 6.0F, 5.0F, 1.0F), PartPose.ZERO);
		body.addOrReplaceChild("collar", CubeListBuilder.create().texOffs(0, 53).addBox(-4.5F, -1.2F, -3.2F, 9.0F, 2.0F, 6.4F), PartPose.ZERO);
		body.addOrReplaceChild("tasset_l", CubeListBuilder.create().texOffs(32, 32).addBox(0.4F, 10.5F, -3.0F, 4.0F, 3.0F, 1.0F), PartPose.ZERO);
		body.addOrReplaceChild("tasset_r", CubeListBuilder.create().texOffs(32, 32).mirror().addBox(-4.4F, 10.5F, -3.0F, 4.0F, 3.0F, 1.0F), PartPose.ZERO);
		for (String side : new String[] {"right_arm", "left_arm"}) {
			boolean right = side.startsWith("right");
			float x = right ? -4.6F : -0.4F;
			PartDefinition arm = root.getChild(side);
			arm.addOrReplaceChild("pauldron", CubeListBuilder.create().texOffs(0, 44).mirror(!right).addBox(x, -3.6F, -3.0F, 5.0F, 3.0F, 6.0F), PartPose.ZERO);
			arm.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(22, 44).mirror(!right).addBox(x + 0.5F, -1.2F, -2.8F, 4.0F, 2.0F, 5.6F), PartPose.ZERO);
			arm.addOrReplaceChild("spike", CubeListBuilder.create().texOffs(28, 32).addBox(right ? -3.0F : 2.0F, -5.6F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
		}
		for (String side : new String[] {"right_leg", "left_leg"}) {
			PartDefinition leg = root.getChild(side);
			leg.addOrReplaceChild("knee", CubeListBuilder.create().texOffs(42, 32).addBox(-2.0F, 3.5F, -3.1F, 4.0F, 3.0F, 1.0F), PartPose.ZERO);
			leg.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(32, 53).addBox(-2.9F, 8.0F, -2.9F, 5.8F, 1.0F, 5.8F), PartPose.ZERO);
			leg.addOrReplaceChild("toe", CubeListBuilder.create().texOffs(52, 32).addBox(-2.2F, 10.5F, -3.4F, 4.4F, 1.5F, 1.0F), PartPose.ZERO);
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	private static ModelPart plates() {
		if (plates == null) plates = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
		return plates;
	}

	//? if >=1.21.9 {
	/*public SpireArmorLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector nodes, int light, S state, float yRot, float xRot) {
		if (!state.isInvisible) dress(pose, new Draw(nodes), light, state.headEquipment, state.chestEquipment, state.legsEquipment, state.feetEquipment);
	}
	*///?} else if >=1.21.2 {
	/*public SpireArmorLayer(RenderLayerParent<S, M> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, S state, float yRot, float xRot) {
		if (!state.isInvisible) dress(pose, new Draw(buffers), light, state.headEquipment, state.chestEquipment, state.legsEquipment, state.feetEquipment);
	}
	*///?} else {
	public SpireArmorLayer(RenderLayerParent<T, M> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, T wearer, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!wearer.isInvisible()) dress(pose, new Draw(buffers), light, wearer.getItemBySlot(EquipmentSlot.HEAD), wearer.getItemBySlot(EquipmentSlot.CHEST),
				wearer.getItemBySlot(EquipmentSlot.LEGS), wearer.getItemBySlot(EquipmentSlot.FEET));
	}
	//?}

	private void dress(PoseStack pose, Draw draw, int light, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		HumanoidModel<?> wearer = getParentModel();
		ModelPart root = plates();
		int stage = stage(head);
		if (stage >= 0) {
			ResourceLocation sheet = sheet(head, false);
			plate(pose, draw, light, sheet, wearer.head, root.getChild("head"), "crest");
			if (stage >= 2) {
				plate(pose, draw, light, sheet, wearer.head, root.getChild("head"), "wing_l");
				plate(pose, draw, light, sheet, wearer.head, root.getChild("head"), "wing_r");
			}
		}
		stage = stage(chest);
		if (stage >= 0) {
			ResourceLocation sheet = sheet(chest, false);
			if (stage >= 1) plate(pose, draw, light, sheet, wearer.body, root.getChild("body"), "chest_plate");
			if (stage >= 2) plate(pose, draw, light, sheet, wearer.body, root.getChild("body"), "collar");
			for (boolean right : new boolean[] {true, false}) {
				ModelPart arm = right ? wearer.rightArm : wearer.leftArm;
				ModelPart plates = root.getChild(right ? "right_arm" : "left_arm");
				plate(pose, draw, light, sheet, arm, plates, "pauldron");
				if (stage >= 1) plate(pose, draw, light, sheet, arm, plates, "plate");
				if (stage >= 3) plate(pose, draw, light, sheet, arm, plates, "spike");
			}
		}
		stage = stage(legs);
		if (stage >= 0) {
			ResourceLocation sheet = sheet(legs, true);
			plate(pose, draw, light, sheet, wearer.body, root.getChild("body"), "tasset_l");
			plate(pose, draw, light, sheet, wearer.body, root.getChild("body"), "tasset_r");
			if (stage >= 1) {
				plate(pose, draw, light, sheet, wearer.rightLeg, root.getChild("right_leg"), "knee");
				plate(pose, draw, light, sheet, wearer.leftLeg, root.getChild("left_leg"), "knee");
			}
		}
		stage = stage(feet);
		if (stage >= 0) {
			ResourceLocation sheet = sheet(feet, false);
			for (boolean right : new boolean[] {true, false}) {
				ModelPart leg = right ? wearer.rightLeg : wearer.leftLeg;
				ModelPart plates = root.getChild(right ? "right_leg" : "left_leg");
				plate(pose, draw, light, sheet, leg, plates, "cuff");
				if (stage >= 2) plate(pose, draw, light, sheet, leg, plates, "toe");
			}
		}
	}

	/** The hone stage of a piece of Spire armour, or -1 for anything else. */
	private static int stage(ItemStack stack) {
		return stack.getItem() instanceof GearItem ? SpireGear.stage(stack) : -1;
	}

	private static ResourceLocation sheet(ItemStack stack, boolean legs) {
		GearItem gear = (GearItem) stack.getItem();
		return VillagerLife.id("textures/models/armor/" + SpireGear.look(gear.tier(), SpireGear.stage(stack)) + "_3d_layer_" + (legs ? 2 : 1) + ".png");
	}

	/** A plate where the wearer's part is now (the wearer's model is posed before its layers are drawn). */
	private static void plate(PoseStack pose, Draw draw, int light, ResourceLocation sheet, ModelPart on, ModelPart plates, String name) {
		if (!on.visible) return;
		ModelPart plate = plates.getChild(name);
		pose.pushPose();
		on.translateAndRotate(pose);
		draw.geometry(pose, McClient.entityCutoutNoCull(sheet), (at, consumer) -> plate.render(Draw.stack(at), consumer, light, OverlayTexture.NO_OVERLAY));
		pose.popPose();
	}
}
