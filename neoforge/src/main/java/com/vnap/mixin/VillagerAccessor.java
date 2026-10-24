package com.vnap.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A craftsman taught by a Well of Lore climbs its trade as a player's trades would make it (vanilla keeps both private). */
@Mixin(Villager.class)
public interface VillagerAccessor {
	@Invoker("shouldIncreaseLevel")
	boolean vnap$shouldIncreaseLevel();

	//? if >=1.21.11 {
	/*@Invoker("increaseMerchantCareer")
	void vnap$increaseMerchantCareer(net.minecraft.server.level.ServerLevel level);
	*///?} else {
	@Invoker("increaseMerchantCareer")
	void vnap$increaseMerchantCareer();
	//?}
}
