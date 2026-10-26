package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's TradeWithVillagerEvent: a trade done. */
@SuppressWarnings("unused")
@Mixin(AbstractVillager.class)
public abstract class VillagerTradeMixin {
	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void vnap$traded(MerchantOffer offer, CallbackInfo info) {
		AbstractVillager villager = (AbstractVillager) (Object) this;
		if (villager.getTradingPlayer() != null) Bridge.fire(new TradeWithVillagerEvent(villager.getTradingPlayer(), offer, villager));
	}
}
