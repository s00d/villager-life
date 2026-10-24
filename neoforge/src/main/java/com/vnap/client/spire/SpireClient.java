package com.vnap.client.spire;

import com.vnap.client.McClient;
import com.vnap.network.SpireJournalPayload;
import com.vnap.network.SpireNotePayload;

/** Opens the spire's screens from their packets (the packet handlers stay free of screen classes). */
public final class SpireClient {
	private SpireClient() {
	}

	public static void note(SpireNotePayload payload) {
		McClient.setScreen(new SpireNoteScreen(SpireText.of(payload), null));
	}

	public static void journal(SpireJournalPayload payload) {
		McClient.setScreen(new SpireJournalScreen(payload));
	}
}
