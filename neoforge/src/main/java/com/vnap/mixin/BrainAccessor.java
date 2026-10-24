package com.vnap.mixin;

import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

/** The brain's behaviours and memories for LifeBrain.running and /vnap who (vanilla's own getters of them are debug accessors). */
@Mixin(Brain.class)
public interface BrainAccessor<E extends net.minecraft.world.entity.LivingEntity> {
	@Accessor("availableBehaviorsByPriority")
	Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> vnap$behaviours();

	// a memory is an Optional of an ExpirableValue until 1.21.11, a MemorySlot from 26.1 (Mc.remembers)
	@Accessor("memories")
	Map<net.minecraft.world.entity.ai.memory.MemoryModuleType<?>, ?> vnap$memories();
}
