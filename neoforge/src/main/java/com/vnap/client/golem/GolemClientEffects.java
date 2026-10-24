package com.vnap.client.golem;

//? if <1.21.6
import com.mojang.blaze3d.shaders.FogShape;
import com.vnap.client.VillageClientState;
import com.vnap.entity.golem.TocsinGolem;
import com.vnap.mood.MoodConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/** Boss bar with health segments and rage pips, fight music, rusty fog and distance-scaled camera shake. */
public final class GolemClientEffects {
    private static final Music FIGHT = new Music(SoundEvents.MUSIC_BIOME_BASALT_DELTAS, 0, 0, true);
    private static final double NEAR = 48.0;
    private static final int BAR_W = 182;
    private static final String[] ROMAN = {"", "I", "II", "III"};
    private static float fog;

    private GolemClientEffects() {
    }

    private static @Nullable TocsinGolem nearest(double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        TocsinGolem best = null;
        double bestDist = range * range;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof TocsinGolem golem && golem.isAlive()) {
                double d = golem.distanceToSqr(mc.player);
                if (d < bestDist) {
                    bestDist = d;
                    best = golem;
                }
            }
        }
        return best;
    }

    private static @Nullable TocsinGolem byBar(LerpingBossEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof TocsinGolem golem && golem.barId().filter(event.getId()::equals).isPresent()) {
                return golem;
            }
        }
        return null;
    }

    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        TocsinGolem golem = byBar(event.getBossEvent());
        if (golem == null) {
            return;
        }
        event.setCanceled(true);
        event.setIncrement(28);
        GuiGraphics g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int x = event.getX();
        int y = event.getY();
        float progress = event.getBossEvent().getProgress();
        int phase = golem.phase();
        float time = golem.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(false);

        Component name = event.getBossEvent().getName().copy()
                .append(Component.literal("  " + ROMAN[Mth.clamp(phase, 1, 3)]));
        int nameW = mc.font.width(name);
        g.drawString(mc.font, name, x + BAR_W / 2 - nameW / 2, y - 9, phase >= 3 ? 0xFFFF8A3C : 0xFFE8D7B0, true);

        g.fill(x - 2, y - 1, x + BAR_W + 2, y + 7, 0xFF141216);
        g.fill(x - 1, y, x + BAR_W + 1, y + 6, 0xFF3A363E);
        int fill = Math.round(BAR_W * progress);
        int top = phase >= 3 ? 0xFFFF6A1E : phase == 2 ? 0xFFE89A3A : 0xFFC9C4B8;
        int bottom = phase >= 3 ? 0xFF8A1A08 : phase == 2 ? 0xFF8A4A12 : 0xFF6E6A62;
        if (phase >= 3) {
            float pulse = 0.5F + 0.5F * Mth.sin(time * 0.3F);
            top = blend(top, 0xFFFFE08A, pulse * 0.4F);
        }
        g.fillGradient(x, y + 1, x + fill, y + 5, top, bottom);
        for (float mark : new float[] {0.66F, 0.33F}) {
            int mx = x + Math.round(BAR_W * mark);
            g.fill(mx, y - 1, mx + 1, y + 7, 0xFF141216);
            g.fill(mx - 1, y - 2, mx + 2, y - 1, 0xFFB08A3A);
        }

        int rage = golem.rage();
        int pipW = 10;
        int pipsX = x + BAR_W / 2 - (TocsinGolem.RAGE_MAX * (pipW + 2)) / 2;
        for (int i = 0; i < TocsinGolem.RAGE_MAX; i++) {
            int px = pipsX + i * (pipW + 2);
            g.fill(px - 1, y + 9, px + pipW + 1, y + 14, 0xFF141216);
            int color = i < rage ? (rage >= TocsinGolem.RAGE_MAX - 1 ? 0xFFFF4A1A : 0xFFE0882C) : 0xFF3A363E;
            g.fill(px, y + 10, px + pipW, y + 13, color);
        }
    }

    private static int blend(int a, int b, float t) {
        int r = Mth.lerpInt(t, a >> 16 & 255, b >> 16 & 255);
        int gr = Mth.lerpInt(t, a >> 8 & 255, b >> 8 & 255);
        int bl = Mth.lerpInt(t, a & 255, b & 255);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    public static void onMusic(SelectMusicEvent event) {
        if (!MoodConfig.bossMusic()) {
            return;
        }
        TocsinGolem golem = nearest(64.0);
        if (golem != null && golem.phase() >= 1) {
            //? if >=1.21.9 {
            /*event.overrideMusic(golem.phase() >= 3 ? Musics.END_BOSS : FIGHT);
            *///?} else if >=1.21.4 {
            /*event.overrideMusic(new net.minecraft.client.sounds.MusicInfo(golem.phase() >= 3 ? Musics.END_BOSS : FIGHT));
            *///?} else
            event.overrideMusic(golem.phase() >= 3 ? Musics.END_BOSS : FIGHT);
        }
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (fog <= 0.01F) {
            return;
        }
        float k = fog * 0.55F;
        event.setRed(Mth.lerp(k, event.getRed(), 0.22F));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.11F));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.07F));
    }

    public static void onFog(ViewportEvent.RenderFog event) {
        if (fog <= 0.01F) {
            return;
        }
        float far = Mth.lerp(fog, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), 42.0F));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.25F));
        //? if <1.21.6 {
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
        //?}
    }

    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float shake = VillageClientState.shakeIntensity;
        if (shake <= 0.01F || !MoodConfig.bossScreenShake()) {
            return;
        }
        double partial = event.getPartialTick();
        float t = (float) (Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0) + (float) partial;
        event.setYaw(event.getYaw() + Mth.sin(t * 2.7F) * shake * 1.2F);
        event.setPitch(event.getPitch() + Mth.sin(t * 3.3F + 1.0F) * shake * 1.0F);
        event.setRoll(event.getRoll() + Mth.sin(t * 2.1F + 2.0F) * shake * 2.0F);
    }

    /** Called every client tick: eases the fog in and out around a fighting golem. */
    public static void tick() {
        TocsinGolem golem = MoodConfig.bossDarkFog() ? nearest(NEAR) : null;
        float target = golem != null && golem.phase() >= 1 ? (golem.phase() >= 3 ? 1.0F : 0.6F) : 0.0F;
        fog += (target - fog) * 0.05F;
        VillageClientState.shakeIntensity *= 0.86F;
    }
}
