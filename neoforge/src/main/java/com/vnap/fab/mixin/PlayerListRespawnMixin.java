package com.vnap.fab.mixin;

import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoForge's PlayerRespawnPositionEvent: where the player comes back, and whether the old spawn point goes along. */
@SuppressWarnings("unused")
@Mixin(PlayerList.class)
public abstract class PlayerListRespawnMixin {
	@Unique
	private boolean vnap$copySpawn;

	@Redirect(method = "respawn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerPlayer;findRespawnPositionAndUseSpawnBlock(ZLnet/minecraft/world/level/portal/DimensionTransition$PostDimensionTransition;)Lnet/minecraft/world/level/portal/DimensionTransition;"))
	private DimensionTransition vnap$position(ServerPlayer player, boolean keepInventory, DimensionTransition.PostDimensionTransition after) {
		PlayerRespawnPositionEvent event = Bridge.fire(new PlayerRespawnPositionEvent(player, player.findRespawnPositionAndUseSpawnBlock(keepInventory, after), keepInventory));
		vnap$copySpawn = event.copyOriginalSpawnPosition();
		return event.getDimensionTransition();
	}

	@Redirect(method = "respawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/DimensionTransition;missingRespawnBlock()Z", ordinal = 0))
	private boolean vnap$copy(DimensionTransition transition) {
		return !vnap$copySpawn;
	}
}
