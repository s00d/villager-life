package com.vnap.mixin;

import com.vnap.dialogue.FamilyHome;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * A villager in its own closed room (every way in has a door), or in any room of its home (or of its shelter for
 * the night) at night, is not
 * threatened by a monster outside it, seen through a window or a gap: vanilla panicked it out of bed, and it ran out of the door into the dark, to the husk on
 * the porch. A monster in the room is a threat as before.
 */
@SuppressWarnings("unused")
@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {
	@Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
	//? if >=1.21.2 {
	/*private void vnap$safeIndoors(ServerLevel world, LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
	*///?} else
	private void vnap$safeIndoors(LivingEntity attacker, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (!(attacker instanceof Villager villager) || !(villager.level() instanceof ServerLevel level)) return;
		// a hero whole enough to fight does not run from a monster: it goes for it (village/life/HeroBehavior)
		if (com.vnap.spire.Heroes.fighting(villager)) {
			cir.setReturnValue(false);
			return;
		}
		BlockPos bed = villager.getBrain().getMemory(MemoryModuleType.HOME)
				.filter(home -> home.dimension().equals(level.dimension())).map(home -> home.pos()).orElse(null);
		// the room for the night of one sent to a shelter (homeless, or its bed out of reach tonight) is the shelter's
		BlockPos shelter = com.vnap.village.life.Agenda.where(villager, com.vnap.village.town.Shelter.WHY);
		if (shelter != null) bed = null;
		BlockPos anchor = bed != null ? bed : shelter;
		boolean thrower = target.getType().equals(net.minecraft.world.entity.EntityType.DROWNED)
				&& target.getMainHandItem().is(net.minecraft.world.item.Items.TRIDENT)
				&& target.distanceToSqr(villager) <= TRIDENT_REACH * TRIDENT_REACH;
		if (anchor == null || !anchor.closerThan(villager.blockPosition(), 8)) {
			if (thrower) cir.setReturnValue(true);
			return;
		}
		Set<BlockPos> room = bed != null ? FamilyHome.room(level, bed) : FamilyHome.roomAt(level, anchor);
		boolean inside = villager.isSleeping() || room.contains(villager.blockPosition());
		if (inside && !room.contains(target.blockPosition()) && !target.blockPosition().closerThan(villager.blockPosition(), 1.5)
				&& (night(level) || FamilyHome.closed(level, room))) {
			cir.setReturnValue(false);
			return;
		}
		if (thrower) cir.setReturnValue(true);
	}

	private static final double TRIDENT_REACH = 16;

	/** At night (or in a thunderstorm) out of the door is worse than any room: a zombie by the step killed four who ran out past it. */
	private static boolean night(ServerLevel level) {
		return com.vnap.village.town.TownRules.monstersOut(level);
	}
}
