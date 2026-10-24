package com.vnap.client;

import com.vnap.dialogue.DialogueCatalog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Every situation the villagers have something to say about, from the dialogue catalogue: search by words, open one
 * to see how it comes about, who says it and a line of it.
 */
public final class ReactionsScreen extends Screen {
	private static final int ROW = 22;
	private final @Nullable Screen back;
	private String query = "";
	private int page;
	private @Nullable Situation open;

	/** One title and all the groups (voices) under it. */
	private record Situation(String title, List<DialogueCatalog.DialogueGroup> groups) {
		boolean matches(String words) {
			return title.toLowerCase(Locale.ROOT).contains(words)
					|| groups.stream().anyMatch(group -> group.body().toLowerCase(Locale.ROOT).contains(words));
		}
	}

	public ReactionsScreen(@Nullable Screen back) {
		super(Component.translatable("screen.villager_life.reactions.title"));
		this.back = back;
	}

	private static List<Situation> situations() {
		Map<String, List<DialogueCatalog.DialogueGroup>> byTitle = new LinkedHashMap<>();
		for (DialogueCatalog.DialogueGroup group : DialogueCatalog.groups().values()) {
			if (!group.title().isBlank()) byTitle.computeIfAbsent(group.title(), ignored -> new ArrayList<>()).add(group);
		}
		List<Situation> all = new ArrayList<>();
		byTitle.forEach((title, groups) -> all.add(new Situation(title, List.copyOf(groups))));
		all.sort(Comparator.comparing(Situation::title, String.CASE_INSENSITIVE_ORDER));
		return all;
	}

	@Override
	protected void init() {
		int wide = Math.min(380, width - 32);
		int left = (width - wide) / 2;
		Situation shown = open;
		text(left, 16, wide, Component.literal(shown == null ? getTitle().getString() : shown.title())
				.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
		if (shown != null) {
			details(left, wide, shown);
			return;
		}
		EditBox search = new EditBox(font, left, 40, wide - 62, 20, Component.translatable("screen.villager_life.reactions.search"));
		search.setValue(query);
		search.setHint(Component.translatable("screen.villager_life.reactions.search"));
		addRenderableWidget(search);
		addRenderableWidget(Button.builder(Component.translatable("screen.villager_life.reactions.go"), button -> {
			query = search.getValue().strip().toLowerCase(Locale.ROOT);
			page = 0;
			rebuildWidgets();
		}).bounds(left + wide - 58, 40, 58, 20).build());
		List<Situation> found = situations().stream().filter(s -> query.isEmpty() || s.matches(query)).toList();
		int rows = Math.max(1, (height - 32 - 68) / ROW);
		int pages = Math.max(1, (found.size() + rows - 1) / rows);
		page = Math.min(page, pages - 1);
		for (int i = page * rows; i < Math.min(found.size(), (page + 1) * rows); i++) {
			Situation situation = found.get(i);
			addRenderableWidget(Button.builder(Component.literal(situation.title()), button -> {
				open = situation;
				rebuildWidgets();
			}).bounds(left, 68 + (i - page * rows) * ROW, wide, 20).build());
		}
		if (found.isEmpty()) text(left, 80, wide, Component.translatable("screen.villager_life.reactions.nothing").withStyle(ChatFormatting.RED), true);
		int third = (wide - 12) / 3;
		Button previous = Button.builder(Component.translatable("screen.villager_life.reactions.previous"), button -> {
			page--;
			rebuildWidgets();
		}).bounds(left, height - 30, third, 20).build();
		previous.active = page > 0;
		addRenderableWidget(previous);
		addRenderableWidget(Button.builder(Component.translatable(back == null ? "gui.done" : "gui.back"), button -> onClose())
				.bounds(left + third + 6, height - 30, third, 20).build());
		Button next = Button.builder(Component.translatable("screen.villager_life.reactions.next"), button -> {
			page++;
			rebuildWidgets();
		}).bounds(left + (third + 6) * 2, height - 30, third, 20).build();
		next.active = page + 1 < pages;
		addRenderableWidget(next);
	}

	private void details(int left, int wide, Situation situation) {
		StringBuilder about = new StringBuilder();
		for (DialogueCatalog.DialogueGroup group : situation.groups()) {
			about.append(group.speaker().isEmpty() ? "?" : I18n.get("screen.villager_life.reactions.speaker." + group.speaker())).append(": ");
			String body = group.body().replaceAll("§[0-9a-fk-or]", "").strip();
			about.append(body.isEmpty() ? "-" : body);
			String said = firstWords(group);
			if (!said.isEmpty()) about.append("\n  \"").append(said).append('"');
			about.append("\n\n");
		}
		text(left, 44, wide, Component.literal(about.toString().strip()), false);
		addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> {
			open = null;
			rebuildWidgets();
		}).bounds(left, height - 30, wide, 20).build());
	}

	private static String firstWords(DialogueCatalog.DialogueGroup group) {
		if (group.variants().isEmpty() || group.variants().getFirst().subtitles().isEmpty()) return "";
		return I18n.get(group.variants().getFirst().subtitles().getFirst().key()).replaceAll("\\[[^\\]]*\\]", "").strip();
	}

	private void text(int x, int y, int wide, Component words, boolean centred) {
		addRenderableWidget(new MultiLineTextWidget(x, y, words, font).setMaxWidth(wide).setCentered(centred));
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
