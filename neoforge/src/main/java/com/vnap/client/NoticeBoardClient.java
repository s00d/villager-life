package com.vnap.client;

import com.vnap.network.NoticeBoardPayload;
import net.minecraft.client.Minecraft;

/** Opens the notice board's screen; kept out of ClientPayloadHandlers, which a dedicated server loads too. */
final class NoticeBoardClient {
	private NoticeBoardClient() {
	}

	static void errand(com.vnap.network.ErrandPayload payload) {
		ErrandHud.update(payload);
	}

	static void open(NoticeBoardPayload payload) {
		com.vnap.client.McClient.setScreen(new NoticeBoardScreen(payload));
	}
}
