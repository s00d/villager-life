package com.vnap.mixin;

import com.vnap.village.home.Towns;
import com.vnap.village.town.Guards;
import com.vnap.village.town.TownRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Vanilla's call for an iron golem (a panic, a gossip) wants three to five villagers within ten blocks of the caller:
 * the town of the mod has its houses on lots along streets and never had them, and its villagers died to husks in the
 * morning with nobody to call. In a town the call counts the villagers within TownRules.GOLEM_CALL, up to the
 * town's TownRules.GOLEMS_MAX golems, and does not want them to have slept that day (vanilla's guard against golem
 * farms; the cap is the town's): a fresh village's first night is a siege's favourite.
 */
@SuppressWarnings("unused")
@Mixin(Villager.class)
public abstract class VillagerGolemMixin {
	@Inject(method = "spawnGolemIfNeeded", at = @At("HEAD"), cancellable = true)
	private void vnap$townCallsAGolem(ServerLevel level, long gameTime, int minVillagerAmount, CallbackInfo ci) {
		if (!((Object) this instanceof Villager self)) return;
		BlockPos bell = Towns.bellOf(self);
		if (bell == null) return;
		ci.cancel();
		if (vnap$golemSeen(self) || Guards.golems(level, bell) >= TownRules.GOLEMS_MAX) return;
		List<Villager> near = level.getEntitiesOfClass(Villager.class, self.getBoundingBox().inflate(TownRules.GOLEM_CALL));
		if (near.stream().filter(other -> !vnap$golemSeen(other)).limit(minVillagerAmount).count() < minVillagerAmount) return;
		if (SpawnUtil.trySpawnMob(EntityType.IRON_GOLEM, MobSpawnType.MOB_SUMMONED, level, self.blockPosition(), 10, 8, 6,
				//? if >=1.21.2 {
				/*SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, true).isPresent()) {
				*///?} else
				SpawnUtil.Strategy.ON_TOP_OF_COLLIDER).isPresent()) {
			near.forEach(GolemSensor::golemDetected);
		}
	}

	private static boolean vnap$golemSeen(Villager villager) {
		return villager.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.GOLEM_DETECTED_RECENTLY);
	}
}
