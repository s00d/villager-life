package com.vnap.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** The mod's settings: the player's own (subtitles, mood bar) and the server's talk settings, for the host or an operator. */
public final class SettingsScreen extends Screen {
	private static final String KEY = "screen.villager_life.settings.";
	private static final int CHATTINESS = 4;
	private static final int RARE = 3;
	private final @Nullable Screen back;

	public SettingsScreen(@Nullable Screen back) {
		super(Component.translatable(KEY + "title"));
		this.back = back;
		LifeSettingsState.prepareConfigScreen();
	}

	@Override
	protected void init() {
		int wide = Math.min(380, width - 32);
		int left = (width - wide) / 2;
		int label = Math.min(166, wide / 2);
		addRenderableWidget(new MultiLineTextWidget(left, 16, getTitle().copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), font)
				.setMaxWidth(wide).setCentered(true));
		String where = !LifeSettingsState.canEdit() ? "operator_only" : LifeSettingsState.localSettings() ? "saved_local" : "saved_server";
		addRenderableWidget(new MultiLineTextWidget(left, 40, Component.translatable(KEY + where), font).setMaxWidth(wide).setCentered(true));
		int step = Math.min(26, (height - 100) / 6);
		int y = 66;
		row(left, y, label, wide, "subtitles", () -> onOff(LifeClientSettings.showSubtitles()), true,
				() -> LifeClientSettings.setShowSubtitles(!LifeClientSettings.showSubtitles()));
		y += step;
		row(left, y, label, wide, "mood_bar", SettingsScreen::moodBar, true, SettingsScreen::nextMoodBar);
		y += step;
		row(left, y, label, wide, "mood_bar_place",
				() -> Component.translatable(KEY + "mood_bar_place." + LifeClientSettings.moodBarPlace().name().toLowerCase(Locale.ROOT)), true,
				() -> LifeClientSettings.setMoodBarPlace(LifeClientSettings.moodBarPlace().next()));
		y += step;
		row(left, y, label, wide, "chattiness", () -> Component.translatable(KEY + "chattiness." + Math.floorMod(LifeSettingsState.chattiness(), CHATTINESS)), LifeSettingsState.canEdit(),
				() -> LifeSettingsState.setChattiness(LifeSettingsState.chattiness() + 1));
		y += step;
		row(left, y, label, wide, "rare", () -> Component.translatable(KEY + "rare." + Math.floorMod(LifeSettingsState.rareVoicelines(), RARE)), LifeSettingsState.canEdit(),
				() -> LifeSettingsState.setRareVoicelines(LifeSettingsState.rareVoicelines() + 1));
		addRenderableWidget(Button.builder(Component.translatable(back == null ? "gui.done" : "gui.back"), button -> onClose())
				.bounds(left, height - 30, wide, 20).build());
	}

	/** A label on the left, a button on the right that shows the value and steps it on a click. */
	private void row(int left, int y, int label, int wide, String name, Supplier<Component> value, boolean active, Runnable step) {
		addRenderableWidget(new MultiLineTextWidget(left, y + 6, Component.translatable(KEY + name), font).setMaxWidth(label - 6));
		Button button = Button.builder(value.get(), pressed -> {
			step.run();
			pressed.setMessage(value.get());
		}).bounds(left + label, y, wide - label, 20).build();
		button.active = active;
		addRenderableWidget(button);
	}

	private static Component moodBar() {
		if (!LifeClientSettings.showMoodBar()) return Component.translatable("options.off");
		return Component.translatable(KEY + (LifeClientSettings.moodBarAlways() ? "mood_bar.always" : "mood_bar.changes"));
	}

	/** When it changes → always → off → when it changes. */
	private static void nextMoodBar() {
		if (!LifeClientSettings.showMoodBar()) {
			LifeClientSettings.setMoodBarAlways(false);
			LifeClientSettings.setShowMoodBar(true);
		} else if (!LifeClientSettings.moodBarAlways()) {
			LifeClientSettings.setMoodBarAlways(true);
		} else {
			LifeClientSettings.setShowMoodBar(false);
		}
	}

	private static Component onOff(boolean on) {
		return Component.translatable(on ? "options.on" : "options.off");
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		McClient.setScreen(back);
	}
}
