package com.vnap.network;

import com.vnap.client.LifeSettingsState;
import com.vnap.config.LifeSettings;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Sends the talk settings to players as they join, and takes new ones from the host or an operator. */
public final class LifeSettingsNetwork {
	private LifeSettingsNetwork() {
	}

	public static void registerPayloads(PayloadRegistrar registrar) {
		//? if >=1.21.6 {
		/*registrar.playBidirectional(LifeSettingsPayload.TYPE, LifeSettingsPayload.CODEC, LifeSettingsNetwork::fromPlayer,
				(settings, context) -> LifeSettingsState.apply(settings));
		*///?} else {
		registrar.playBidirectional(LifeSettingsPayload.TYPE, LifeSettingsPayload.CODEC,
				new net.neoforged.neoforge.network.handling.DirectionalPayloadHandler<LifeSettingsPayload>(
						(settings, context) -> LifeSettingsState.apply(settings), LifeSettingsNetwork::fromPlayer));
		//?}
	}

	public static void registerEvents() {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, (PlayerEvent.PlayerLoggedInEvent event) -> {
			if (!(event.getEntity() instanceof ServerPlayer player)) return;
			send(player);
			PacketDistributor.sendToPlayer(player, MoodRulesPayload.current());
		});
	}

	/** A player's change counts only from the host or an operator; either way everyone hears what holds now. */
	private static void fromPlayer(LifeSettingsPayload wanted, IPayloadContext context) {
		if (!(context.player() instanceof ServerPlayer player)) return;
		if (!mayEdit(player)) {
			send(player);
			return;
		}
		LifeSettings.update(wanted.chattiness(), wanted.rareVoicelines());
		com.vnap.Mc.serverLevel(player).getServer().getPlayerList().getPlayers().forEach(LifeSettingsNetwork::send);
	}

	public static void send(ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new LifeSettingsPayload(LifeSettings.chattiness(), LifeSettings.rareVoicelines(), mayEdit(player)));
	}

	private static boolean mayEdit(ServerPlayer player) {
		return com.vnap.Mc.hostOf(player) || com.vnap.Mc.op(player);
	}
}
