package com.vnap.client;

import com.vnap.config.LifeSettings;
import com.vnap.network.LifeSettingsPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** The talk settings as the client knows them: the server's, or the local file's from the mod list (no world open). */
public final class LifeSettingsState {
	private static LifeSettingsPayload known = new LifeSettingsPayload(2, 1, false);
	/** No connection: the settings screen edits config/villager-life.json directly. */
	private static boolean offline;

	private LifeSettingsState() {
	}

	public static void apply(LifeSettingsPayload fromServer) {
		known = new LifeSettingsPayload(Math.clamp(fromServer.chattiness(), 0, 3), Math.clamp(fromServer.rareVoicelines(), 0, 2),
				fromServer.canEdit());
		offline = false;
	}

	public static void prepareConfigScreen() {
		if (Minecraft.getInstance().getConnection() != null) return;
		known = new LifeSettingsPayload(LifeSettings.chattiness(), LifeSettings.rareVoicelines(), true);
		offline = true;
	}

	public static int chattiness() {
		return known.chattiness();
	}

	public static int rareVoicelines() {
		return known.rareVoicelines();
	}

	public static boolean canEdit() {
		return known.canEdit();
	}

	public static boolean localSettings() {
		return offline;
	}

	public static void setChattiness(int value) {
		change(Math.floorMod(value, 4), known.rareVoicelines());
	}

	public static void setRareVoicelines(int value) {
		change(known.chattiness(), Math.floorMod(value, 3));
	}

	private static void change(int chattiness, int rare) {
		if (!known.canEdit()) return;
		known = new LifeSettingsPayload(chattiness, rare, true);
		if (offline) LifeSettings.update(chattiness, rare);
		else if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(new LifeSettingsPayload(chattiness, rare, false));
	}

	public static void reset() {
		known = new LifeSettingsPayload(known.chattiness(), known.rareVoicelines(), false);
		offline = false;
	}
}
