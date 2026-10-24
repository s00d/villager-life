package com.vnap.client.dev;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vnap.Mc;
import com.vnap.VillagerLife;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Films the game from its own picture, not the screen: each frame the main render target is read and piped to ffmpeg
 * (the window may be hidden or covered). Frames are laid on a fixed clock: a slow frame is written again, frames that
 * come too fast are skipped. The sound is not here: OpenAL Soft writes the game's mix to the WAV named by {@code
 * VL_WAV} (its "wave" driver, {@code ALSOFT_CONF}), and the WAV's length at the first and last frame goes into
 * {@code <name>.json} to cut it to the picture. Dev builds only.
 */
public final class Recorder {
	private static final int QUEUED = 4;
	private static @Nullable Recording now;
	/** Only the villagers' subtitles on screen: no hotbar, hearts, crosshair, hand, mood bar (a camera shot). */
	public static boolean clean;

	private Recorder() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		if (Mc.production()) return;
		dispatcher.register(Commands.literal("vnaprec")
				.then(Commands.literal("start").then(Commands.argument("name", StringArgumentType.word())
						.executes(ctx -> start(StringArgumentType.getString(ctx, "name"), 30))
						.then(Commands.argument("fps", IntegerArgumentType.integer(10, 60))
								.executes(ctx -> start(StringArgumentType.getString(ctx, "name"),
										IntegerArgumentType.getInteger(ctx, "fps"))))))
				.then(Commands.literal("lapse").then(Commands.argument("name", StringArgumentType.word())
						.then(Commands.argument("every", IntegerArgumentType.integer(1))
								.executes(ctx -> lapse(StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "every"), 0, 24000))
								.then(Commands.argument("from", IntegerArgumentType.integer(0, 24000))
										.then(Commands.argument("to", IntegerArgumentType.integer(0, 24000))
												.executes(ctx -> lapse(StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "every"),
														IntegerArgumentType.getInteger(ctx, "from"), IntegerArgumentType.getInteger(ctx, "to"))))))))
				.then(Commands.literal("stop").executes(ctx -> {
					Minecraft.getInstance().execute(Recorder::stop);
					return 1;
				}))
				// the HUD (as F1) and the chat out of the picture, or back
				// the HUD: true (as played), false (none, as F1), clean (the villagers' subtitles only, no hand)
				.then(Commands.literal("hud").then(Commands.argument("mode", StringArgumentType.word()).executes(ctx -> {
					String mode = StringArgumentType.getString(ctx, "mode");
					Minecraft.getInstance().execute(() -> {
						clean = mode.equals("clean");
						Minecraft.getInstance().options.hideGui = mode.equals("false");
					});
					return 1;
				})))
				.then(Commands.literal("chat").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(ctx -> {
					boolean on = com.mojang.brigadier.arguments.BoolArgumentType.getBool(ctx, "on");
					Minecraft.getInstance().execute(() -> Minecraft.getInstance().options.chatVisibility().set(
							on ? net.minecraft.world.entity.player.ChatVisiblity.FULL : net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
					return 1;
				})))
				// the game's language now, and the resources again (the voice clips are swapped on disk before it)
				.then(Commands.literal("lang").then(Commands.argument("code", StringArgumentType.word()).executes(ctx -> {
					String code = StringArgumentType.getString(ctx, "code");
					Minecraft mc = Minecraft.getInstance();
					mc.execute(() -> {
						mc.getLanguageManager().setSelected(code);
						mc.options.languageCode = code;
						mc.reloadResourcePacks().exceptionally(error -> {
							VillagerLife.LOGGER.error("vnaprec: resource reload failed", error);
							return null;
						});
					});
					return 1;
				})))
				// the window in pixels of its picture (a Retina window has twice as many pixels as its size)
				.then(Commands.literal("size").then(Commands.argument("width", IntegerArgumentType.integer(320, 7680))
						.then(Commands.argument("height", IntegerArgumentType.integer(240, 4320)).executes(ctx -> {
							int w = IntegerArgumentType.getInteger(ctx, "width");
							int h = IntegerArgumentType.getInteger(ctx, "height");
							Minecraft.getInstance().execute(() -> size(w, h));
							return 1;
						})))));
	}

	private static void size(int width, int height) {
		var window = Minecraft.getInstance().getWindow();
		double scale = (double) window.getWidth() / window.getScreenWidth();
		GLFW.glfwSetWindowSize(window.getWindow(), (int) Math.round(width / scale), (int) Math.round(height / scale));
	}

	/** A timelapse: one frame each {@code every} game ticks while the time of day is in [from, to) (sprint the game meanwhile). */
	private static int lapse(String name, int every, int from, int to) {
		Minecraft.getInstance().execute(() -> {
			if (start(name, 30) == 1 && now != null) {
				now.every = every;
				now.from = from;
				now.to = to;
			}
		});
		return 1;
	}

	private static int start(String name, int fps) {
		Minecraft.getInstance().executeBlocking(() -> {
			stop();
			RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
			Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("recordings");
			try {
				Files.createDirectories(dir);
				now = new Recording(dir, name, fps, target.width, target.height);
				VillagerLife.LOGGER.info("vnaprec: {} {}x{} @{}", name, target.width, target.height, fps);
			} catch (IOException e) {
				VillagerLife.LOGGER.error("vnaprec: ffmpeg did not start", e);
			}
		});
		return 1;
	}

	private static void stop() {
		Recording r = now;
		now = null;
		if (r != null) r.close();
	}

	public static void onLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
		if (clean && !event.getName().equals(VillagerLife.id("dialogue_subtitles"))) event.setCanceled(true);
	}

	public static void onHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
		if (clean) event.setCanceled(true);
	}

	public static boolean recording() {
		return now != null;
	}

	/** After the frame is drawn, before it goes to the window. */
	public static void onFrame(RenderFrameEvent.Post event) {
		Recording r = now;
		if (r == null) return;
		// no advancement or recipe pop-ups over a shot
		Minecraft.getInstance().getToasts().clear();
		RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
		if (target.width != r.width || target.height != r.height) {
			VillagerLife.LOGGER.warn("vnaprec: the picture is {}x{} now, not {}x{}: stopped", target.width, target.height, r.width, r.height);
			stop();
			return;
		}
		r.frame(target);
	}

	private record Shot(ByteBuffer pixels, int copies) {
	}

	private static final class Recording {
		final int width;
		final int height;
		final int fps;
		final Process ffmpeg;
		final Thread writer;
		final BlockingQueue<Shot> queue = new ArrayBlockingQueue<>(QUEUED + 3);
		final BlockingQueue<ByteBuffer> free = new ArrayBlockingQueue<>(QUEUED + 2);
		final Path json;
		final long startNanos;
		final long wavStart;
		long written;
		int every;
		int from;
		int to = 24000;
		long nextTick = Long.MIN_VALUE;
		long dropped;
		volatile boolean failed;

		Recording(Path dir, String name, int fps, int width, int height) throws IOException {
			this.width = width;
			this.height = height;
			this.fps = fps;
			Path out = dir.resolve(name + ".mp4");
			json = dir.resolve(name + ".json");
			ffmpeg = new ProcessBuilder(List.of("/opt/homebrew/bin/ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
					"-f", "rawvideo", "-pix_fmt", "bgra", "-s", width + "x" + height, "-r", String.valueOf(fps), "-i", "-",
					"-vf", "vflip,format=yuv420p", "-c:v", "h264_videotoolbox", "-b:v", "24M", "-maxrate", "32M",
					"-movflags", "+faststart", out.toString()))
					.redirectErrorStream(true)
					.redirectOutput(dir.resolve(name + ".ffmpeg.log").toFile())
					.start();
			for (int i = 0; i < QUEUED + 2; i++) free.add(MemoryUtil.memAlloc(width * height * 4));
			OutputStream stream = ffmpeg.getOutputStream();
			writer = new Thread(() -> write(stream), "vnaprec");
			writer.setDaemon(true);
			writer.start();
			startNanos = System.nanoTime();
			wavStart = wavBytes();
		}

		void frame(RenderTarget target) {
			if (failed) {
				stop();
				return;
			}
			int copies;
			if (every > 0) {
				var level = Minecraft.getInstance().level;
				if (level == null) return;
				long tick = level.getGameTime();
				long day = level.getDayTime() % 24000;
				if (tick < nextTick || day < from || day >= to) return;
				nextTick = tick + every;
				copies = 1;
			} else {
				long due = (System.nanoTime() - startNanos) * fps / 1_000_000_000L + 1;
				copies = (int) (due - written);
			}
			if (copies <= 0) return;
			ByteBuffer pixels = free.poll();
			if (pixels == null) {
				// ffmpeg is behind: this moment is lost, the next frame stands in for it
				dropped++;
				return;
			}
			pixels.clear();
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
			GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
			GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
			written += copies;
			queue.add(new Shot(pixels, copies));
		}

		private void write(OutputStream stream) {
			try (WritableByteChannel channel = Channels.newChannel(stream)) {
				while (true) {
					Shot shot = queue.poll(200, TimeUnit.MILLISECONDS);
					if (shot == null) {
						if (now != this) break;
						continue;
					}
					if (shot.copies() < 0) break;
					for (int i = 0; i < shot.copies(); i++) {
						ByteBuffer view = shot.pixels().duplicate();
						view.position(0).limit(width * height * 4);
						while (view.hasRemaining()) channel.write(view);
					}
					free.add(shot.pixels());
				}
			} catch (IOException | InterruptedException e) {
				failed = true;
				VillagerLife.LOGGER.error("vnaprec: writing to ffmpeg failed", e);
			}
		}

		void close() {
			long wavEnd = wavBytes();
			long nanos = System.nanoTime() - startNanos;
			try {
				queue.put(new Shot(MemoryUtil.memAlloc(4), -1));
				writer.join(60_000);
				ffmpeg.waitFor(120, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			for (ByteBuffer b : free) MemoryUtil.memFree(b);
			String wav = System.getenv("VL_WAV");
			String text = "{\"fps\":" + fps + ",\"frames\":" + written + ",\"dropped\":" + dropped + ",\"seconds\":" + nanos / 1e9
					+ ",\"wav\":" + (wav == null ? "null" : "\"" + wav + "\"") + ",\"wavStart\":" + wavStart + ",\"wavEnd\":" + wavEnd + "}\n";
			try {
				Files.writeString(json, text, StandardCharsets.UTF_8);
			} catch (IOException e) {
				VillagerLife.LOGGER.error("vnaprec: {} not written", json, e);
			}
			VillagerLife.LOGGER.info("vnaprec: stopped {}", text.strip());
		}

		private static long wavBytes() {
			String wav = System.getenv("VL_WAV");
			return wav == null ? -1 : new File(wav).length();
		}
	}
}
