package com.vnap.mixin;

import com.vnap.village.life.Doors;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.level.pathfinder.Node;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Doors: a door its villager walked more than three blocks past was forgotten open; now it is shut. */
@SuppressWarnings("unused")
@Mixin(InteractWithDoor.class)
public abstract class InteractWithDoorMixin {
	@Inject(method = "closeDoorsThatIHaveOpenedOrPassedThrough", at = @At("HEAD"), cancellable = true)
	private static void vnap$shut(ServerLevel level, LivingEntity entity, @Nullable Node previous, @Nullable Node next, Set<GlobalPos> doors,
			Optional<List<LivingEntity>> nearest, CallbackInfo ci) {
		ci.cancel();
		Doors.close(level, entity, previous, next, doors, nearest);
	}
}
