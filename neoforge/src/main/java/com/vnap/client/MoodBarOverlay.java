package com.vnap.client;

import com.vnap.block.LifeBlocks;
import com.vnap.mood.MoodThresholds;
import com.vnap.network.VillageMoodPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;

/**
 * The village's mood as a small pill: an icon, a −100…+100 scale with the rite marks, the number, and a gold line for a
 * running rite. By default it slides in for a few seconds when the mood changes or the player walks into a village.
 * It sits top centre under the boss bars, where minimaps (Xaero, JourneyMap) are not; the settings move it.
 */
public final class MoodBarOverlay {
    private static final int MARGIN = 4;
    private static final int HEIGHT = 14;
    private static final int ICON = 10;
    private static final int BAR_WIDTH = 54;
    private static final int BAR_HEIGHT = 3;
    private static final double SHOW_RANGE = 80.0;
    private static final long SHOW_MS = 6000L;
    private static final long SLIDE_MS = 300L;
    private static float shown = Float.NaN;
    private static boolean inRange;
    /** Lowest edge of the boss bars drawn this frame (0: none). */
    private static int bossBottom;

    public static void onFrameStart(RenderGuiEvent.Pre event) {
        bossBottom = 0;
    }

    public static void onBossBar(net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBottom = Math.max(bossBottom, event.getY() + event.getIncrement());
    }

    private MoodBarOverlay() {
    }

    public static void onRender(RenderGuiEvent.Post event) {
        //? if <1.21.2 {
        if (com.vnap.client.dev.Recorder.clean) return;
        //?}
        VillageMoodPayload mood = VillageClientState.lastMood;
        Minecraft mc = Minecraft.getInstance();
        if (mood == null || mc.player == null) {
            inRange = false;
            return;
        }
        boolean near = mc.player.blockPosition().closerThan(mood.meetingPos(), SHOW_RANGE);
        long now = net.minecraft.Util.getMillis();
        if (near && !inRange) VillageClientState.moodChangedAt = now;
        inRange = near;
        if (!near || McClient.hudHidden() || !LifeClientSettings.showMoodBar() || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        boolean busy = !"NONE".equals(mood.script());
        float k = 1.0F;
        if (!LifeClientSettings.moodBarAlways() && !busy) {
            long age = now - VillageClientState.moodChangedAt;
            k = Math.min(age / (float) SLIDE_MS, 1.0F - (age - SHOW_MS) / (float) SLIDE_MS);
            if (k <= 0.05F) return;
            k = Math.min(k, 1.0F);
            k = k * k * (3.0F - 2.0F * k);
        }
        float target = mood.mood();
        shown = Float.isNaN(shown) ? target : Mth.lerp(0.15F, shown, target);
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        String value = String.format(Locale.ROOT, "%+.0f", target);
        int width = MARGIN + ICON + 4 + BAR_WIDTH + 4 + font.width(value) + MARGIN;
        LifeClientSettings.Place place = LifeClientSettings.moodBarPlace();
        int x = switch (place) {
            case TOP -> (g.guiWidth() - width) / 2;
            case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
            case TOP_RIGHT, BOTTOM_RIGHT -> g.guiWidth() - width - MARGIN;
        };
        boolean bottom = place == LifeClientSettings.Place.BOTTOM_LEFT || place == LifeClientSettings.Place.BOTTOM_RIGHT;
        int y;
        if (bottom) {
            y = g.guiHeight() - HEIGHT - MARGIN;
        } else {
            // the boss bars (182 wide, top centre) push it down only where they would cover it
            boolean underBoss = bossBottom > 0 && x < g.guiWidth() / 2 + 91 && x + width > g.guiWidth() / 2 - 91;
            y = underBoss ? bossBottom + 2 : MARGIN;
        }
        y += Math.round((HEIGHT + MARGIN + 2) * (1.0F - k) * (bottom ? 1 : -1));

        int background = fade(0xC81E1913, k);
        g.fill(x + 1, y, x + width - 1, y + HEIGHT, background);
        g.fill(x, y + 1, x + 1, y + HEIGHT - 1, background);
        g.fill(x + width - 1, y + 1, x + width, y + HEIGHT - 1, background);
        g.fill(x + 2, y + HEIGHT - 1, x + width - 2, y + HEIGHT, fade((moodColor(target) & 0x00FFFFFF) | 0xA0000000, k));

        McClient.push(g);
        McClient.translate(g, x + MARGIN, y + 2, 0.0F);
        McClient.scale(g, ICON / 16.0F, ICON / 16.0F, 1.0F);
        g.renderItem(new ItemStack(icon(mood.script(), target)), 0, 0);
        McClient.pop(g);

        int barLeft = x + MARGIN + ICON + 4;
        int barTop = y + (busy ? 4 : 5);
        drawBar(g, barLeft, barTop, shown, k);
        if (busy) {
            int fill = Math.round(BAR_WIDTH * Mth.clamp(mood.progress(), 0.0F, 1.0F));
            g.fill(barLeft, barTop + BAR_HEIGHT + 2, barLeft + BAR_WIDTH, barTop + BAR_HEIGHT + 3, fade(0x50FFFFFF, k));
            g.fill(barLeft, barTop + BAR_HEIGHT + 2, barLeft + fill, barTop + BAR_HEIGHT + 3, fade(0xFFFFC04A, k));
        }
        g.drawString(font, value, barLeft + BAR_WIDTH + 4, y + 3, fade(moodColor(target), k), true);
    }

    private static void drawBar(GuiGraphics g, int left, int top, float value, float k) {
        g.fill(left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, fade(0xFF0A0908, k));
        for (int i = 0; i < BAR_WIDTH; i++) {
            float t = i / (float) (BAR_WIDTH - 1) * 2.0F - 1.0F;
            g.fill(left + i, top, left + i + 1, top + BAR_HEIGHT, fade(gradient(t), k));
        }
        float[] marks = {MoodThresholds.festoon(), MoodThresholds.festival(), MoodThresholds.happy(), MoodThresholds.pavilion(),
                MoodThresholds.vigil(), MoodThresholds.mourning(), MoodThresholds.angry(), MoodThresholds.pyre()};
        for (float mark : marks) {
            int x = left + toX(mark);
            g.fill(x, top, x + 1, top + BAR_HEIGHT, fade(0x50FFFFFF, k));
        }
        int cult = left + toX(MoodThresholds.cultReady());
        g.fill(cult, top, cult + 1, top + BAR_HEIGHT, fade(0xFFFF2A2A, k));
        int at = left + toX(value);
        g.fill(at - 1, top - 2, at + 2, top + BAR_HEIGHT + 2, fade(0xFF000000, k));
        g.fill(at, top - 1, at + 1, top + BAR_HEIGHT + 1, fade(0xFFFFFFFF, k));
    }

    /** The colour with its alpha scaled by {@code k}, never so low the font would take it for opaque. */
    private static int fade(int argb, float k) {
        int alpha = Math.max(8, Math.round((argb >>> 24) * k));
        return alpha << 24 | (argb & 0x00FFFFFF);
    }

    private static int toX(float mood) {
        return Math.round((Mth.clamp(mood, -100.0F, 100.0F) + 100.0F) / 200.0F * (BAR_WIDTH - 1));
    }

    private static int gradient(float t) {
        int r;
        int gr;
        int b;
        if (t < 0.0F) {
            float k = -t;
            r = (int) Mth.lerp(k, 120, 190);
            gr = (int) Mth.lerp(k, 112, 38);
            b = (int) Mth.lerp(k, 100, 34);
        } else {
            r = (int) Mth.lerp(t, 120, 70);
            gr = (int) Mth.lerp(t, 112, 180);
            b = (int) Mth.lerp(t, 100, 80);
        }
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private static int moodColor(float mood) {
        if (mood >= MoodThresholds.festoon()) {
            return 0xFF55D86A;
        }
        if (mood <= MoodThresholds.vigil()) {
            return 0xFFE0504A;
        }
        return 0xFFE8D67A;
    }

    private static ItemLike icon(String script, float mood) {
        return switch (script) {
            case "BUILD_FESTOON" -> Items.YELLOW_BANNER;
            case "BUILD_FESTIVAL" -> LifeBlocks.FESTIVAL_TOTEM;
            case "BUILD_MONUMENT" -> LifeBlocks.MONUMENT_PLAQUE;
            case "BUILD_PAVILION" -> Items.BELL;
            case "BUILD_VIGIL" -> Items.CANDLE;
            case "BUILD_MOURNING" -> LifeBlocks.MOURNING_TOTEM;
            case "BUILD_ALTAR", "SUMMON" -> LifeBlocks.ALTAR_CORE;
            case "BUILD_PYRE" -> Items.SOUL_CAMPFIRE;
            case "BOSS_ACTIVE" -> Items.IRON_BLOCK;
            default -> mood >= 0.0F ? Items.POPPY : Items.WITHER_ROSE;
        };
    }
}
