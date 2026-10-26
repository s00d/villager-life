package com.vnap.client;

import com.vnap.VillagerLife;
import com.vnap.dialogue.DialogueCatalog;
import com.vnap.dialogue.VoicePitch;
import com.vnap.network.DialogueAnimationPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The words of the lines within 16 blocks, nearest at the bottom above the hotbar, up to four at once. */
public final class DialogueSubtitleState {
	private static final double HEARING = 16.0;
	private static final int LINES = 4;
	/** The action bar sits right above the first line: the others go over it. */
	private static final float ACTION_BAR = 14.0F;
	private static final Map<UUID, Shown> SHOWN = new HashMap<>();

	/** A line's subtitle frames on the clip's clock, which runs {@code pitch} times as fast as real time. */
	private record Shown(long from, long until, float pitch, List<DialogueCatalog.SubtitleFrame> frames) {
		String text(long now) {
			double seconds = (now - from) / 1.0E9 * pitch;
			String key = null;
			for (DialogueCatalog.SubtitleFrame frame : frames) {
				if (frame.time() > seconds) break;
				key = frame.key();
			}
			// Fish S2 emotion tags ("[gasp] Oh!") are for the voice: the subtitle leaves them out
			return key == null ? "" : I18n.get(key).replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\s{2,}", " ").strip();
		}
	}

	private record Row(double distance, Component text) {
	}

	private DialogueSubtitleState() {
	}

	public static void register(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE, VillagerLife.id("dialogue_subtitles"), DialogueSubtitleState::render);
	}

	public static void start(DialogueAnimationPayload line) {
		if (line.groupId().isEmpty() || line.durationTicks() <= 0) {
			SHOWN.remove(line.entityId());
			return;
		}
		DialogueCatalog.DialogueGroup group = DialogueCatalog.byId(line.groupId());
		DialogueCatalog.DialogueVariant take = group == null ? null : group.variant(line.variantIndex());
		if (take == null || take.subtitles().isEmpty()) return;
		long now = System.nanoTime();
		SHOWN.put(line.entityId(), new Shown(now, now + line.durationTicks() * 50_000_000L,
				VoicePitch.of(ClientEntities.find(line.entityId())), take.subtitles()));
	}

	public static void tick(Minecraft game) {
		if (game.level == null || game.player == null) {
			clear();
			return;
		}
		long now = System.nanoTime();
		SHOWN.entrySet().removeIf(entry -> {
			Entity speaker = ClientEntities.find(entry.getKey());
			return now >= entry.getValue().until() || (speaker != null && !speaker.isAlive());
		});
	}

	public static void clear() {
		SHOWN.clear();
	}

	private static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft game = Minecraft.getInstance();
		var player = game.player;
		if (game.level == null || player == null || !LifeClientSettings.showSubtitles()) return;
		long now = System.nanoTime();
		List<Row> rows = new ArrayList<>();
		SHOWN.forEach((id, shown) -> {
			Entity speaker = ClientEntities.find(id);
			if (now >= shown.until() || speaker == null || !speaker.isAlive()) return;
			double distance = Math.sqrt(player.distanceToSqr(speaker));
			String words = shown.text(now);
			if (distance > HEARING || words.isEmpty()) return;
			rows.add(new Row(distance, Component.empty()
					.append(speaker.getName().copy().append(": ").withStyle(ChatFormatting.YELLOW))
					.append(Component.literal(words).withStyle(ChatFormatting.WHITE))));
		});
		rows.sort(Comparator.comparingDouble(Row::distance));
		float y = graphics.guiHeight() - 59.0F;
		for (int i = 0; i < Math.min(LINES, rows.size()); i++) {
			Row row = rows.get(i);
			// the nearest at full size, the others smaller the further they are
			float scale = i == 0 ? 1.0F : (float) Math.max(0.65, Math.min(0.9, 0.95 - row.distance() / HEARING * 0.3));
			int width = game.font.width(row.text());
			McClient.push(graphics);
			McClient.translate(graphics, graphics.guiWidth() / 2.0F, y, 0.0F);
			McClient.scale(graphics, scale, scale, 1.0F);
			graphics.drawString(game.font, row.text(), -width / 2, 0, 0xFFFFFFFF, true);
			McClient.pop(graphics);
			y -= game.font.lineHeight + 3.0F + (i == 0 ? ACTION_BAR : 0.0F);
		}
	}
}
