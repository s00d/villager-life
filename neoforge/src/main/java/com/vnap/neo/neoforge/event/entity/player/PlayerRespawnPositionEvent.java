package com.vnap.neo.neoforge.event.entity.player;

import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;

/** Where a dead player comes back (com.vnap.neo.mixin.PlayerListRespawnMixin): the transition, and whether the old spawn point goes with them. */
public final class PlayerRespawnPositionEvent extends Event {
	static {
		Bridge.own(PlayerRespawnPositionEvent.class);
	}

	private final ServerPlayer player;
	private final DimensionTransition original;
	private final boolean fromEndFight;
	private DimensionTransition transition;
	private boolean copyOriginalSpawnPosition;

	public PlayerRespawnPositionEvent(ServerPlayer player, DimensionTransition transition, boolean fromEndFight) {
		this.player = player;
		this.original = transition;
		this.transition = transition;
		this.fromEndFight = fromEndFight;
		this.copyOriginalSpawnPosition = !transition.missingRespawnBlock();
	}

	public net.minecraft.world.entity.player.Player getEntity() {
		return player;
	}

	public DimensionTransition getDimensionTransition() {
		return transition;
	}

	public void setDimensionTransition(DimensionTransition transition) {
		this.transition = transition;
	}

	public DimensionTransition getOriginalDimensionTransition() {
		return original;
	}

	public boolean isFromEndFight() {
		return fromEndFight;
	}

	public boolean copyOriginalSpawnPosition() {
		return copyOriginalSpawnPosition;
	}

	public void setCopyOriginalSpawnPosition(boolean copy) {
		this.copyOriginalSpawnPosition = copy;
	}
}
