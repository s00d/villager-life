package com.vnap.client.spire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vnap.VillagerLife;
import com.vnap.client.Draw;
import com.vnap.client.McClient;
import com.vnap.spire.Heroes;
import com.vnap.spire.gear.GearItem;
import com.vnap.spire.gear.SpireGear;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A hero's gear on a villager (spire/Heroes): plate armour cut to a villager's robe — a cuirass with shoulder plates,
 * a tasset over the robe, greaves below it; the helm is worn (it counts) but not drawn, so the villager keeps its own
 * face and profession hat — in its tier's colours at its hone's stage (textures/entity/hero/<look>.png, tools/art/paint_spire_gear.py:
 * HERO is these boxes' texture offsets and sizes), and the sword slung across its back.
 */
public final class HeroLook {
	private static final ModelPart PARTS = parts();

	private HeroLook() {
	}

	private static ModelPart parts() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("cuirass", CubeListBuilder.create().texOffs(0, 16).addBox(-4.5F, -0.3F, -3.5F, 9.0F, 7.0F, 7.0F), PartPose.ZERO);
		root.addOrReplaceChild("pauldron_l", CubeListBuilder.create().texOffs(32, 16).addBox(3.6F, -0.8F, -3.5F, 3.0F, 3.0F, 7.0F), PartPose.ZERO);
		root.addOrReplaceChild("pauldron_r", CubeListBuilder.create().texOffs(32, 16).mirror().addBox(-6.6F, -0.8F, -3.5F, 3.0F, 3.0F, 7.0F), PartPose.ZERO);
		root.addOrReplaceChild("tasset", CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, 6.6F, -4.0F, 10.0F, 3.0F, 8.0F), PartPose.ZERO);
		root.addOrReplaceChild("greave", CubeListBuilder.create().texOffs(36, 32).addBox(-2.5F, 5.8F, -2.5F, 5.0F, 6.0F, 5.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64).bakeRoot();
	}

	/** The surcoat over the villager's robe when it wears a spire cuirass (its tier and stage), or null. */
	public static @org.jspecify.annotations.Nullable ResourceLocation surcoat(Villager villager) {
		ItemStack chest = villager.getItemBySlot(EquipmentSlot.CHEST);
		if (villager.isBaby() || !(chest.getItem() instanceof GearItem gear)) return null;
		return VillagerLife.id("textures/entity/hero/" + SpireGear.look(gear.tier(), SpireGear.stage(chest)) + "_coat.png");
	}

	/** Draws whatever of its gear the villager wears; {@code model} is its model's root, posed. */
	public static void draw(ModelPart model, PoseStack pose, Draw draw, int light, Villager villager) {
		if (villager.isBaby()) return;
		ItemStack chest = villager.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack legs = villager.getItemBySlot(EquipmentSlot.LEGS);
		ItemStack feet = villager.getItemBySlot(EquipmentSlot.FEET);
		ItemStack weapon = Heroes.weapon(villager);
		if (chest.getItem() instanceof GearItem) on(model.getChild("body"), pose, draw, light, chest, "cuirass", "pauldron_l", "pauldron_r");
		if (legs.getItem() instanceof GearItem) on(model.getChild("body"), pose, draw, light, legs, "tasset");
		if (feet.getItem() instanceof GearItem) {
			on(model.getChild("right_leg"), pose, draw, light, feet, "greave");
			on(model.getChild("left_leg"), pose, draw, light, feet, "greave");
		}
		if (!weapon.isEmpty()) {
			// slung across the back, the hilt over the right shoulder
			pose.pushPose();
			model.getChild("body").translateAndRotate(pose);
			pose.translate(0.0, 0.3, 0.24);
			pose.mulPose(Axis.ZP.rotationDegrees(180.0F + 45.0F));
			pose.scale(0.85F, 0.85F, 0.85F);
			draw.item(weapon, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, villager.level(), villager.getId());
			pose.popPose();
		}
	}

	private static void on(ModelPart part, PoseStack pose, Draw draw, int light, ItemStack stack, String... pieces) {
		if (!(stack.getItem() instanceof GearItem gear)) return;
		ResourceLocation texture = VillagerLife.id("textures/entity/hero/" + SpireGear.look(gear.tier(), SpireGear.stage(stack)) + ".png");
		pose.pushPose();
		part.translateAndRotate(pose);
		draw.geometry(pose, McClient.entityCutoutNoCull(texture), (at, consumer) -> {
			for (String piece : pieces) PARTS.getChild(piece).render(Draw.stack(at), consumer, light, OverlayTexture.NO_OVERLAY);
		});
		pose.popPose();
	}
}
