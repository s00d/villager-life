package com.vnap.client.spire;

import com.vnap.spire.Genome;
import com.vnap.spire.guard.SpireGuard;
//? if >=1.21.2 {
/*import com.vnap.client.RenderedEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
*///?} else
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Any of the guards' models (geo/spire/*.geo.json), animated by its bones' names: legs walk in pairs, arms swing
 * and rise while an attack is under way, wings and fins beat, tails sway, rings and gears turn, the head looks.
 * A floating body (a flyer, a swimmer, the floating keepers) bobs. The shield shows only on a guard that has one.
 * Rings named ring2 turn the other way, orbit_N roll over; seg_1, seg_2, ... (each in the one before) sway in a wave.
 */
//? if >=1.21.2 {
/*public final class SpireGuardModel extends EntityModel<LivingEntityRenderState> {
*///?} else
public final class SpireGuardModel extends HierarchicalModel<SpireGuard> {
	//? if <1.21.2
	private final ModelPart root;
	private final boolean floats;
	private final List<ModelPart> legs = new ArrayList<>();
	private final List<Boolean> legPhase = new ArrayList<>();
	private final List<ModelPart> left = new ArrayList<>();
	private final List<ModelPart> right = new ArrayList<>();
	private final List<ModelPart> arms = new ArrayList<>();
	private final List<ModelPart> tails = new ArrayList<>();
	private final List<ModelPart> spinners = new ArrayList<>();
	private final List<ModelPart> counter = new ArrayList<>();
	private final List<ModelPart> rollers = new ArrayList<>();
	/** A neck or a body of segments, each a child of the one before: they sway one after another. */
	private final List<ModelPart> segments = new ArrayList<>();
	private final @Nullable ModelPart head;
	private final @Nullable ModelPart eye;
	private final @Nullable ModelPart gear;
	private final @Nullable ModelPart shield;
	/** Parts that show only in one form (look().form()): the Heartwood's blades 1, shield 2, whips 3; the Reflection's crack 1. */
	private final List<ModelPart> blades = new ArrayList<>();
	private final List<ModelPart> whips = new ArrayList<>();
	private final @Nullable ModelPart formShield;
	private final @Nullable ModelPart crack;

	SpireGuardModel(ModelPart root, Genome.Body body) {
		//? if >=1.21.2 {
		/*super(root);
		*///?} else
		this.root = root;
		this.floats = switch (body) {
			case MOTH, RAY, WISP, FISH, ARCHIVIST, HIVE_MASTER, STARGAZER, MIND, GRAVITOR, PLANET -> true;
			default -> false;
		};
		ModelPart neck = child(root, "seg_1");
		for (int i = 2; neck != null; i++) {
			segments.add(neck);
			neck = child(neck, "seg_" + i);
		}
		ModelPart last = segments.isEmpty() ? root : segments.get(segments.size() - 1);
		head = child(last, "head") != null ? child(last, "head") : child(root, "head");
		eye = child(root, "eye");
		gear = child(root, "gear");
		shield = child(root, "shield");
		for (String name : new String[] {"leg_l", "leg_r", "leg_fl", "leg_fr", "leg_bl", "leg_br"}) {
			ModelPart leg = child(root, name);
			if (leg == null) continue;
			legs.add(leg);
			legPhase.add(name.equals("leg_l") || name.equals("leg_fl") || name.equals("leg_br"));
		}
		for (String name : new String[] {"arm_l", "arm_r"}) {
			ModelPart arm = child(root, name);
			if (arm != null) arms.add(arm);
		}
		for (String name : new String[] {"wing_l", "fin_l", "wing_l2"}) {
			ModelPart part = child(root, name);
			if (part != null) left.add(part);
		}
		for (String name : new String[] {"wing_r", "fin_r", "wing_r2"}) {
			ModelPart part = child(root, name);
			if (part != null) right.add(part);
		}
		ModelPart tail = child(root, "tail");
		if (tail != null) tails.add(tail);
		for (String side : new String[] {"l", "r"}) {
			ModelPart arm = child(root, "arm_" + side);
			if (arm == null) continue;
			ModelPart blade = child(arm, "form_blade_" + side);
			if (blade != null) blades.add(blade);
			ModelPart whip = child(arm, "form_whip_" + side);
			if (whip != null) whips.add(whip);
		}
		ModelPart trunk = child(root, "body");
		formShield = trunk == null ? null : child(trunk, "form_shield");
		crack = trunk == null ? null : child(trunk, "gem_crack");
		ModelPart ring = child(root, "ring");
		if (ring != null) spinners.add(ring);
		ModelPart ring2 = child(root, "ring2");
		if (ring2 != null) counter.add(ring2);
		ModelPart orbit = child(root, "orbit_1");
		if (orbit != null) rollers.add(orbit);
	}

	private static @Nullable ModelPart child(ModelPart root, String name) {
		return root.hasChild(name) ? root.getChild(name) : null;
	}

	//? if >=1.21.2 {
	/*@Override
	public void setupAnim(LivingEntityRenderState state) {
		super.setupAnim(state);
		SpireGuard guard = RenderedEntity.of(state, SpireGuard.class);
		if (guard == null) return;
		pose(guard, state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}
	*///?} else {
	@Override
	public ModelPart root() {
		return root;
	}

	@Override
	public void setupAnim(SpireGuard guard, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		root.getAllParts().forEach(ModelPart::resetPose);
		pose(guard, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
	}
	//?}

	private void pose(SpireGuard guard, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch) {
		boolean attacking = guard.action() != 0;
		Genome.Look look = guard.look();
		ModelPart head = this.head;
		if (head != null) {
			head.yRot = yaw * Mth.DEG_TO_RAD;
			head.xRot = pitch * Mth.DEG_TO_RAD;
		}
		ModelPart eye = this.eye;
		if (eye != null && head != null) {
			eye.yRot = head.yRot;
			eye.xRot = head.xRot;
		}
		float swing = limbSwing * 0.66F;
		for (int i = 0; i < legs.size(); i++) {
			float phase = legPhase.get(i) ? 0.0F : Mth.PI;
			legs.get(i).xRot = Mth.cos(swing + phase) * 1.0F * limbSwingAmount;
		}
		for (int i = 0; i < arms.size(); i++) {
			ModelPart arm = arms.get(i);
			if (attacking) {
				arm.xRot = -2.1F + Mth.sin(age * 0.6F) * 0.25F;
			} else {
				arm.xRot = Mth.cos(swing + (i == 0 ? Mth.PI : 0.0F)) * 0.8F * limbSwingAmount;
				arm.zRot = (i == 0 ? -1 : 1) * (0.05F + Mth.sin(age * 0.09F) * 0.05F);
			}
		}
		float beat = Mth.sin(age * (attacking ? 1.4F : 0.8F)) * 0.6F;
		for (ModelPart wing : left) wing.zRot = beat;
		for (ModelPart wing : right) wing.zRot = -beat;
		for (ModelPart tail : tails) tail.yRot = Mth.sin(age * 0.3F) * 0.35F;
		for (ModelPart ring : spinners) ring.yRot = age * (attacking ? 0.2F : 0.05F);
		for (ModelPart ring : counter) ring.yRot = -age * (attacking ? 0.25F : 0.08F);
		for (ModelPart ring : rollers) ring.xRot = age * (attacking ? 0.15F : 0.04F);
		for (int i = 0; i < segments.size(); i++) {
			segments.get(i).yRot = Mth.sin(age * 0.12F - i * 0.9F) * 0.22F;
			segments.get(i).xRot = Mth.sin(age * 0.09F - i * 0.7F) * 0.08F - (attacking ? 0.25F : 0.0F);
		}
		ModelPart gear = this.gear;
		if (gear != null) gear.xRot = age * (attacking ? 0.3F : 0.08F);
		ModelPart shield = this.shield;
		if (shield != null) shield.visible = look.shield();
		int form = look.form();
		for (ModelPart blade : blades) blade.visible = form == 1;
		for (ModelPart whip : whips) whip.visible = form == 3;
		ModelPart formShield = this.formShield;
		if (formShield != null) formShield.visible = form == 2;
		ModelPart crack = this.crack;
		if (crack != null) crack.visible = form == 1;
		if (floats) {
			float bob = Mth.sin(age * 0.1F) * 1.2F;
			root().y += bob - (look.keeper() >= 0 ? 6.0F : 0.0F);
		}
	}
}
