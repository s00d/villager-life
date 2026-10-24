package com.vnap.client.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.vnap.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A film camera: the player (a spectator) is moved every frame along a smooth path through key points, or round a
 * point, with eased start and end, so a shot glides instead of walking. Dev builds only.
 * <pre>
 * /vnapcam path 8 "x y z yaw pitch; x y z yaw pitch; ..."   through the points in 8 seconds
 * /vnapcam orbit 12 x y z radius height fromDeg toDeg          round a point, looking at it
 * /vnapcam stop
 * </pre>
 */
public final class Camera {
	private record Key(Vec3 pos, float yaw, float pitch) {
	}

	private static final com.google.common.base.Splitter SPACE = com.google.common.base.Splitter.on(com.google.common.base.CharMatcher.whitespace()).omitEmptyStrings();
	private static final com.google.common.base.Splitter SEMI = com.google.common.base.Splitter.on(';');
	private static List<Key> keys = List.of();
	private static Vec3 centre = Vec3.ZERO;
	private static double radius;
	private static double height;
	private static double from;
	private static double to;
	private static boolean orbit;
	private static double bob;
	private static @org.jspecify.annotations.Nullable String follow;
	private static double back;
	private static @org.jspecify.annotations.Nullable Vec3 eased;
	/** A watch's place (null: a follow). */
	private static @org.jspecify.annotations.Nullable Vec3 fixed;
	private static float easedYaw;
	private static long startNanos;
	private static double seconds;

	private Camera() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		if (Mc.production()) return;
		dispatcher.register(Commands.literal("vnapcam")
				.then(Commands.literal("stop").executes(ctx -> {
					seconds = 0;
					return 1;
				}))
				// a walker's head bob on paths (0: none), as a player's camera on foot
				.then(Commands.literal("bob").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 1)).executes(ctx -> {
					bob = DoubleArgumentType.getDouble(ctx, "amount");
					return 1;
				})))
				.then(Commands.literal("fov").then(Commands.argument("degrees", com.mojang.brigadier.arguments.IntegerArgumentType.integer(30, 110)).executes(ctx -> {
					int fov = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "degrees");
					Minecraft.getInstance().execute(() -> Minecraft.getInstance().options.fov().set(fov));
					return 1;
				})))
				// behind and over a player (another client's bot fighting), easing after it: /vnapcam follow Dev 6 3 [seconds]
				.then(Commands.literal("follow").then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
					List<String> p = SPACE.splitToList(StringArgumentType.getString(ctx, "args"));
					if (p.size() < 3) return 0;
					Minecraft.getInstance().execute(() -> {
						follow = p.get(0);
						fixed = null;
						back = Double.parseDouble(p.get(1));
						height = Double.parseDouble(p.get(2));
						orbit = false;
						eased = null;
						begin(p.size() > 3 ? Double.parseDouble(p.get(3)) : 3600);
					});
					return 1;
				})))
				// from a fixed point, turning after a player as an operator would: /vnapcam watch Dev x y z
				.then(Commands.literal("watch").then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
					List<String> p = SPACE.splitToList(StringArgumentType.getString(ctx, "args"));
					if (p.size() < 4) return 0;
					Minecraft.getInstance().execute(() -> {
						follow = p.get(0);
						fixed = new Vec3(Double.parseDouble(p.get(1)), Double.parseDouble(p.get(2)), Double.parseDouble(p.get(3)));
						orbit = false;
						eased = null;
						begin(3600);
					});
					return 1;
				})))
				.then(Commands.literal("path").then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1))
						.then(Commands.argument("points", StringArgumentType.greedyString()).executes(ctx -> {
							List<Key> parsed = new ArrayList<>();
							for (String point : SEMI.split(StringArgumentType.getString(ctx, "points").replace("\"", ""))) {
								List<String> p = SPACE.splitToList(point);
								if (p.size() < 5) continue;
								parsed.add(new Key(new Vec3(Double.parseDouble(p.get(0)), Double.parseDouble(p.get(1)), Double.parseDouble(p.get(2))),
										Float.parseFloat(p.get(3)), Float.parseFloat(p.get(4))));
							}
							if (parsed.isEmpty()) {
								com.vnap.VillagerLife.LOGGER.warn("vnapcam: no points");
								return 0;
							}
							double secs = DoubleArgumentType.getDouble(ctx, "seconds");
							Minecraft.getInstance().execute(() -> {
								// yaws unwrapped, so a turn through north does not spin the long way round
								List<Key> unwrapped = new ArrayList<>();
								float last = parsed.get(0).yaw();
								for (Key k : parsed) {
									float yaw = last + Mth.wrapDegrees(k.yaw() - last);
									unwrapped.add(new Key(k.pos(), yaw, k.pitch()));
									last = yaw;
								}
								keys = unwrapped;
								orbit = false;
								follow = null;
								begin(secs);
							});
							return 1;
						}))))
				.then(Commands.literal("orbit").then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0.1))
						.then(Commands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
							List<String> p = SPACE.splitToList(StringArgumentType.getString(ctx, "args"));
							if (p.size() < 7) {
								com.vnap.VillagerLife.LOGGER.warn("vnapcam orbit <s> x y z radius height fromDeg toDeg");
								return 0;
							}
							double secs = DoubleArgumentType.getDouble(ctx, "seconds");
							Minecraft.getInstance().execute(() -> {
								centre = new Vec3(Double.parseDouble(p.get(0)), Double.parseDouble(p.get(1)), Double.parseDouble(p.get(2)));
								radius = Double.parseDouble(p.get(3));
								height = Double.parseDouble(p.get(4));
								from = Double.parseDouble(p.get(5));
								to = Double.parseDouble(p.get(6));
								orbit = true;
								follow = null;
								begin(secs);
							});
							return 1;
						})))));
	}

	private static void begin(double secs) {
		seconds = secs;
		startNanos = System.nanoTime();
	}

	public static boolean moving() {
		return seconds > 0;
	}

	/** Before the frame is drawn: the player where the shot is at this moment, with no motion to blend. */
	public static void onFrame(RenderFrameEvent.Pre event) {
		if (seconds <= 0) return;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			seconds = 0;
			return;
		}
		double t = Math.min(1, (System.nanoTime() - startNanos) / 1e9 / seconds);
		// ease in and out: the shot starts and settles gently
		double s = t * t * (3 - 2 * t);
		Vec3 pos;
		float yaw;
		float pitch;
		String who = follow;
		if (who != null) {
			net.minecraft.world.entity.player.Player target = player.level().players().stream()
					.filter(other -> com.vnap.Mc.name(other).equals(who)).findFirst().orElse(null);
			if (target == null) return;
			float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
			Vec3 at = target.getPosition(partial);
			Vec3 still = fixed;
			if (still != null) {
				// the point looked at eases after the player: the shot pans, it does not snap
				Vec3 aim = at.add(0, 1.2, 0);
				Vec3 seen = eased == null ? aim : eased.add(aim.subtract(eased).scale(0.05));
				eased = seen;
				Vec3 look = seen.subtract(still);
				pos = still;
				yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
				pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
				player.setDeltaMovement(Vec3.ZERO);
				player.setPos(pos);
				player.xo = player.xOld = pos.x;
				player.yo = player.yOld = pos.y;
				player.zo = player.zOld = pos.z;
				player.setYRot(yaw);
				player.setXRot(pitch);
				player.yRotO = yaw;
				player.xRotO = pitch;
				return;
			}
			if (eased == null) easedYaw = target.getViewYRot(partial);
			// the camera's heading follows the player's slowly: a fighter's quick turns do not swing the shot
			easedYaw += Mth.wrapDegrees(target.getViewYRot(partial) - easedYaw) * 0.03F;
			double r = Math.toRadians(easedYaw);
			Vec3 want = at.add(Math.sin(r) * back, height, -Math.cos(r) * back);
			Vec3 now = eased == null ? want : eased.add(want.subtract(eased).scale(0.06));
			eased = now;
			Vec3 look = at.add(0, 1.4, 0).subtract(now);
			pos = now;
			yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
			pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
		} else if (orbit) {
			double angle = Math.toRadians(from + (to - from) * s);
			pos = centre.add(-Math.sin(angle) * radius, height, Math.cos(angle) * radius);
			Vec3 look = centre.subtract(pos);
			yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
			pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
		} else if (keys.size() == 1) {
			pos = keys.get(0).pos();
			yaw = keys.get(0).yaw();
			pitch = keys.get(0).pitch();
		} else {
			double f = s * (keys.size() - 1);
			int i = Math.min(keys.size() - 2, (int) f);
			double u = f - i;
			Key a = keys.get(Math.max(0, i - 1));
			Key b = keys.get(i);
			Key c = keys.get(i + 1);
			Key d = keys.get(Math.min(keys.size() - 1, i + 2));
			pos = new Vec3(spline(a.pos().x, b.pos().x, c.pos().x, d.pos().x, u),
					spline(a.pos().y, b.pos().y, c.pos().y, d.pos().y, u),
					spline(a.pos().z, b.pos().z, c.pos().z, d.pos().z, u));
			yaw = (float) spline(a.yaw(), b.yaw(), c.yaw(), d.yaw(), u);
			pitch = (float) spline(a.pitch(), b.pitch(), c.pitch(), d.pitch(), u);
			if (bob > 0) {
				// two steps a second while it moves: up and down, a little side to side
				double moving = Math.min(1, keys.get(keys.size() - 1).pos().distanceTo(keys.get(0).pos()) / Math.max(0.1, seconds));
				double phase = (System.nanoTime() - startNanos) / 1e9 * Math.PI * 3.6;
				pos = pos.add(0, Math.abs(Math.sin(phase)) * bob * 0.12 * moving, 0);
				yaw += (float) (Math.sin(phase) * bob * 0.6 * moving);
				pitch += (float) (Math.abs(Math.cos(phase)) * bob * 0.5 * moving);
			}
		}
		player.setDeltaMovement(Vec3.ZERO);
		// a creative camera (it holds the town loaded, a spectator does not) flies instead of falling between frames
		if (player.isCreative()) player.getAbilities().flying = true;
		player.setPos(pos);
		player.xo = player.xOld = pos.x;
		player.yo = player.yOld = pos.y;
		player.zo = player.zOld = pos.z;
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.setYHeadRot(yaw);
		player.yHeadRotO = yaw;
		player.yBodyRot = yaw;
		player.yBodyRotO = yaw;
		if (t >= 1) seconds = 0;
	}

	private static double spline(double a, double b, double c, double d, double u) {
		// Catmull-Rom through b and c
		return 0.5 * (2 * b + (-a + c) * u + (2 * a - 5 * b + 4 * c - d) * u * u + (-a + 3 * b - 3 * c + d) * u * u * u);
	}
}
