package com.vnap.client.dev;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.vnap.Mc;
import com.vnap.VillagerLife;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A player-like bot for checking the mod in the dev client: a task drives the real player with the real keys (walk,
 * jump, strafe), turns its head, swings when the attack has charged, uses blocks — seeing only what a player sees (the
 * client's world and entities). Ways are found on the client's blocks, so a floor that cannot be walked shows up as
 * such. Every task writes to {@code <game>/devbot.log}; MCP only starts it ({@code /vnapbot <task> ...}) and reads the
 * log. Dev builds only (no command in production).
 */
public final class DevBot {
	/** One job the bot does, a tick at a time; done when it returns false. */
	public interface Task {
		boolean tick(DevBot bot);

		default void stopped(DevBot bot) {
		}
	}

	private static final int SEARCH = 30000;
	private static final DevBot BOT = new DevBot();
	private @Nullable Task task;
	private List<BlockPos> path = List.of();
	private int step;
	private @Nullable BlockPos pathGoal;
	private int pathAge;
	private int strafe;
	/** Ticks of the fight the bot was hit on lately, and how long it circles yet (a breath, a beam: out of it sideways). */
	private final java.util.ArrayDeque<Integer> hitAt = new java.util.ArrayDeque<>();
	private int circling;
	private int dead;

	private DevBot() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		if (Mc.production()) return;
		dispatcher.register(Commands.literal("vnapbot")
				.then(Commands.literal("stop").executes(ctx -> {
					BOT.start(null);
					return 1;
				}))
				.then(Commands.literal("outer").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
						.then(Commands.argument("seconds", IntegerArgumentType.integer(10, 36000)).executes(ctx -> {
							String kind = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind");
							int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
							BOT.mc().execute(() -> BOT.start(new OuterTask(BOT, kind, seconds)));
							return 1;
						}))))
				// the cursor at a point of the screen (GUI coordinates, as mcp's screen.state): a tooltip shows in a hidden window
				.then(Commands.literal("mouse").then(Commands.argument("x", IntegerArgumentType.integer(0))
						.then(Commands.argument("y", IntegerArgumentType.integer(0)).executes(ctx -> {
							int x = IntegerArgumentType.getInteger(ctx, "x");
							int y = IntegerArgumentType.getInteger(ctx, "y");
							BOT.mc().execute(() -> {
								// the cursor is in the window's own units (half the pixels on a Retina screen), as GameRenderer reads it
								var window = BOT.mc().getWindow();
								double sx = (double) window.getScreenWidth() / window.getGuiScaledWidth();
								double sy = (double) window.getScreenHeight() / window.getGuiScaledHeight();
								com.vnap.mixin.client.MouseHandlerAccessor mouse = (com.vnap.mixin.client.MouseHandlerAccessor) BOT.mc().mouseHandler;
								mouse.vnap$setX((x + 0.5) * sx);
								mouse.vnap$setY((y + 0.5) * sy);
							});
							return 1;
						}))))
				// a click where the cursor is (left, or right with "1"), as the player clicks a slot or a button of a screen
				.then(Commands.literal("click").executes(ctx -> click(0))
						.then(Commands.argument("button", IntegerArgumentType.integer(0, 1)).executes(ctx -> click(IntegerArgumentType.getInteger(ctx, "button")))))
				.then(Commands.literal("duel").executes(ctx -> duel(60))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600)).executes(ctx -> duel(IntegerArgumentType.getInteger(ctx, "seconds")))))
				.then(Commands.literal("live").executes(ctx -> live(ctx.getSource(), 3))
						.then(Commands.argument("days", IntegerArgumentType.integer(1, 100)).executes(ctx -> live(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "days")))))
				.then(Commands.literal("mine").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600)).executes(ctx -> {
					int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
					BOT.mc().execute(() -> BOT.start(new MineTask(BOT, seconds)));
					return 1;
				})))
				.then(Commands.literal("walk").then(Commands.argument("points", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
					List<BlockPos> points = new java.util.ArrayList<>();
					@Nullable Vec3 look = null;
					for (String part : com.google.common.base.Splitter.on(';').trimResults().omitEmptyStrings()
							.split(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "points"))) {
						List<String> n = com.google.common.base.Splitter.on(' ').omitEmptyStrings().splitToList(part);
						if (n.size() == 4 && n.get(0).equals("look")) {
							look = new Vec3(Double.parseDouble(n.get(1)), Double.parseDouble(n.get(2)), Double.parseDouble(n.get(3)));
						} else if (n.size() == 3) {
							points.add(BlockPos.containing(Double.parseDouble(n.get(0)), Double.parseDouble(n.get(1)), Double.parseDouble(n.get(2))));
						}
					}
					@Nullable Vec3 at = look;
					BOT.mc().execute(() -> BOT.start(new WalkTask(points, at)));
					return 1;
				})))
				.then(Commands.literal("tour").executes(ctx -> {
					BOT.mc().execute(() -> BOT.start(new TourTask(BOT)));
					say(ctx.getSource(), "devbot: going round the nearest town, log in devbot.log");
					return 1;
				}))
				.then(Commands.literal("climb").then(Commands.argument("from", IntegerArgumentType.integer(1, 1000))
						.then(Commands.argument("to", IntegerArgumentType.integer(1, 1000))
								.executes(ctx -> climb(ctx, 5, 480))
								.then(Commands.argument("hone", IntegerArgumentType.integer(0, 60))
										.executes(ctx -> climb(ctx, IntegerArgumentType.getInteger(ctx, "hone"), 480))
										.then(Commands.argument("seconds", IntegerArgumentType.integer(30, 3600))
												.executes(ctx -> climb(ctx, IntegerArgumentType.getInteger(ctx, "hone"),
														IntegerArgumentType.getInteger(ctx, "seconds")))))))));
	}

	private static int duel(int seconds) {
		BOT.mc().execute(() -> BOT.start(new DuelTask(seconds)));
		return 1;
	}

	private static int click(int button) {
		BOT.mc().execute(() -> {
			var screen = com.vnap.client.McClient.screen();
			if (screen == null) return;
			var window = BOT.mc().getWindow();
			double x = BOT.mc().mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
			double y = BOT.mc().mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
			com.vnap.client.McClient.click(screen, x, y, button);
		});
		return 1;
	}

	private static int climb(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, int hone, int seconds) {
		int from = IntegerArgumentType.getInteger(ctx, "from");
		int to = IntegerArgumentType.getInteger(ctx, "to");
		BOT.mc().execute(() -> BOT.start(new ClimbTask(BOT, from, to, hone, seconds)));
		say(ctx.getSource(), "devbot: climbing " + from + ".." + to + ", log in devbot.log");
		return 1;
	}

	public static void tick() {
		BOT.run();
	}

	/** A line the server said (its command feedback): the tour's places come this way. */
	public static void heard(String line) {
		if (line.startsWith("vnaptour ")) TourTask.HEARD.add(line);
	}

	public static boolean busy() {
		return BOT.task != null;
	}

	void start(@Nullable Task next) {
		if (task != null) task.stopped(this);
		release();
		task = next;
		path = List.of();
		pathGoal = null;
		if (next == null) log("stopped");
	}

	private void run() {
		Task now = task;
		if (now == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!now.tick(this)) start(null);
	}

	// ---- what the bot can do ------------------------------------------------------------------

	public Minecraft mc() {
		return Minecraft.getInstance();
	}

	public LocalPlayer player() {
		LocalPlayer player = mc().player;
		if (player == null) throw new IllegalStateException("no player");
		return player;
	}

	public Level level() {
		Level level = mc().level;
		if (level == null) throw new IllegalStateException("no level");
		return level;
	}

	/** A line in {@code devbot.log} and the client's log. */
	public void log(String line) {
		String stamped = java.time.LocalTime.now(java.time.ZoneId.systemDefault()).withNano(0) + " " + line;
		VillagerLife.LOGGER.info("devbot: {}", line);
		Path file = mc().gameDirectory.toPath().resolve("devbot.log");
		try {
			Files.writeString(file, stamped + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException error) {
			VillagerLife.LOGGER.warn("devbot log", error);
		}
	}

	/** A command as the player types it (a dev world: the player is an op). */
	public void command(String line) {
		player().connection.sendCommand(line);
	}

	/** Dead: respawn. True while it waits for that. */
	public boolean respawning() {
		LocalPlayer player = player();
		if (!player.isDeadOrDying() && !(com.vnap.client.McClient.screen() instanceof DeathScreen)) {
			dead = 0;
			return false;
		}
		release();
		// the death screen stays a while, as a player's would: filmed, it is a shot of its own
		if (com.vnap.client.McClient.screen() instanceof DeathScreen && ++dead > 60) player.respawn();
		return true;
	}

	public void closeScreen() {
		if (com.vnap.client.McClient.screen() != null && !(com.vnap.client.McClient.screen() instanceof DeathScreen)) player().closeContainer();
		if (com.vnap.client.McClient.screen() != null && !(com.vnap.client.McClient.screen() instanceof DeathScreen)) com.vnap.client.McClient.setScreen(null);
	}

	/** The way looked for again next step (after a sidestep: the one it had led into the same wall). */
	public void forgetPath() {
		pathGoal = null;
	}

	public void release() {
		var options = mc().options;
		for (KeyMapping key : new KeyMapping[] {options.keyUp, options.keyDown, options.keyLeft, options.keyRight, options.keyJump, options.keySprint}) {
			key.setDown(false);
		}
	}

	public void look(Vec3 at) {
		LocalPlayer player = player();
		Vec3 eye = player.getEyePosition();
		Vec3 way = at.subtract(eye);
		double flat = Math.sqrt(way.x * way.x + way.z * way.z);
		turn((float) (Math.toDegrees(Math.atan2(way.z, way.x)) - 90.0), (float) -Math.toDegrees(Math.atan2(way.y, flat)));
	}

	/**
	 * The head turned towards yaw/pitch as a player turns it, a part of the way each tick (at most MAX_TURN degrees):
	 * set at once, a shot from the bot's eyes jerked from one guard to the next and back.
	 */
	void turn(float yaw, float pitch) {
		LocalPlayer player = player();
		float dy = net.minecraft.util.Mth.wrapDegrees(yaw - player.getYRot());
		float dp = pitch - player.getXRot();
		float y = player.getYRot() + net.minecraft.util.Mth.clamp(dy * TURN, -MAX_TURN, MAX_TURN);
		float p = player.getXRot() + net.minecraft.util.Mth.clamp(dp * TURN, -MAX_TURN, MAX_TURN);
		player.setYRot(y);
		player.setXRot(net.minecraft.util.Mth.clamp(p, -89.0F, 89.0F));
		player.yHeadRot = y;
	}

	private static final float TURN = 0.35F;
	private static final float MAX_TURN = 18.0F;

	/** Uses a block as a right click on its top face. */
	public void use(BlockPos pos) {
		look(Vec3.atCenterOf(pos));
		var gameMode = mc().gameMode;
		if (gameMode == null) return;
		gameMode.useItemOn(player(), InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
		com.vnap.client.McClient.swing(player(), InteractionHand.MAIN_HAND);
	}

	/** Within sword reach of it (vanilla three blocks from the eyes to its box). */
	public boolean inReach(Entity target) {
		return player().getEyePosition().distanceTo(nearestOn(target)) < 2.9 && player().hasLineOfSight(target);
	}

	private Vec3 nearestOn(Entity target) {
		Vec3 eye = player().getEyePosition();
		var box = target.getBoundingBox();
		return new Vec3(Math.max(box.minX, Math.min(eye.x, box.maxX)), Math.max(box.minY, Math.min(eye.y, box.maxY)), Math.max(box.minZ, Math.min(eye.z, box.maxZ)));
	}

	private boolean retreating;

	/** The marks of the keeper the player fights, as the server last told them (x, z, inner, outer), and when. */
	private static List<double[]> marks = List.of();
	private static long marksAt;

	public static void marks(List<double[]> latest) {
		marks = List.copyOf(latest);
		marksAt = System.currentTimeMillis();
	}

	/** Which way out of the keeper's mark the player stands in, or null when it stands in none. */
	private @Nullable Vec3 markAway() {
		if (System.currentTimeMillis() - marksAt > 1500) return null;
		Vec3 me = player().position();
		for (double[] mark : marks) {
			Vec3 off = new Vec3(me.x - mark[0], 0.0, me.z - mark[1]);
			double d = off.length();
			if (d > mark[3] + 0.4 || d < mark[2] - 0.4) continue;
			// out the near side: from a ring inward if nearer the hole, else outward
			Vec3 way = d < 1.0E-3 ? new Vec3(1, 0, 0) : off.normalize();
			return mark[2] > 0 && d - mark[2] < mark[3] - d ? way.scale(-1.0) : way;
		}
		return null;
	}

	/**
	 * The way off a lit exit portal close by (2 blocks): blows knocked a bot fighting by a floor's entrance back into it,
	 * out of the spire, every half a minute.
	 */
	private @Nullable Vec3 exitAway() {
		BlockPos feet = player().blockPosition();
		Vec3 me = player().position();
		for (BlockPos at : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
			var state = player().level().getBlockState(at);
			if (!(state.getBlock() instanceof com.vnap.spire.SpirePortalBlock) || state.getValue(com.vnap.spire.SpirePortalBlock.KIND) != com.vnap.spire.SpirePortalBlock.Kind.EXIT) continue;
			Vec3 off = new Vec3(me.x - at.getX() - 0.5, 0.0, me.z - at.getZ() - 0.5);
			return off.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : off.normalize();
		}
		return null;
	}

	/** Stands and fights it: looks at it, swings once the blow has charged, a step aside now and then. */
	public void fight(Entity target) {
		var options = mc().options;
		look(target.getBoundingBox().getCenter());
		// a mark filling on the floor under it: out of it first, as a player reads the telegraph (the marks are the
		// server's; a world of one's own lets the bot see them as a player sees their particles)
		Vec3 away = markAway();
		if (away == null) away = exitAway();
		if (away != null) {
			look(player().position().add(away));
			options.keyUp.setDown(true);
			options.keySprint.setDown(true);
			options.keyDown.setDown(false);
			return;
		}
		// hurt badly: back off facing it until the wounds close, as a player would, instead of trading to the end
		float health = player().getHealth();
		if (health < 7.0F || (retreating && health < 15.0F)) {
			retreating = true;
			options.keyUp.setDown(false);
			options.keyDown.setDown(true);
			options.keyJump.setDown(player().horizontalCollision);
			return;
		}
		retreating = false;
		strafe++;
		// hit again and again where it stands (a cone of breath, a beam, a ring): round it sideways, as a player would
		if (player().hurtTime == 9) hitAt.addLast(strafe);
		while (!hitAt.isEmpty() && strafe - hitAt.peekFirst() > 40) hitAt.removeFirst();
		if (hitAt.size() >= 3 && circling <= 0) {
			circling = 60;
			hitAt.clear();
		}
		if (circling > 0) {
			circling--;
			boolean left = (strafe / 120) % 2 == 0;
			options.keyLeft.setDown(left);
			options.keyRight.setDown(!left);
			options.keySprint.setDown(true);
			options.keyUp.setDown(false);
			options.keyDown.setDown(player().distanceTo(target) < 3.0);
			options.keyJump.setDown(player().horizontalCollision);
			if (player().getAttackStrengthScale(0.0F) >= 0.95F && player().distanceTo(target) < 4.5) {
				var gameMode = mc().gameMode;
				if (gameMode != null) gameMode.attack(player(), target);
				com.vnap.client.McClient.swing(player(), InteractionHand.MAIN_HAND);
			}
			return;
		}
		options.keySprint.setDown(false);
		options.keyDown.setDown(false);
		options.keyUp.setDown(false);
		boolean left = (strafe / 30) % 2 == 0;
		options.keyLeft.setDown(left && strafe % 30 < 6);
		options.keyRight.setDown(!left && strafe % 30 < 6);
		if (player().getAttackStrengthScale(0.0F) >= 0.95F) {
			var gameMode = mc().gameMode;
			if (gameMode != null) gameMode.attack(player(), target);
			com.vnap.client.McClient.swing(player(), InteractionHand.MAIN_HAND);
		}
	}

	/** A blow at it once charged, without turning to it (on the way past, as a player cuts at what bites). */
	public void strike(Entity target) {
		if (player().getAttackStrengthScale(0.0F) < 0.95F) return;
		var gameMode = mc().gameMode;
		if (gameMode != null) gameMode.attack(player(), target);
		com.vnap.client.McClient.swing(player(), InteractionHand.MAIN_HAND);
	}

	/** Straight at it: faces it, walks, hops over what it bumps into. */
	public void approach(Entity target) {
		var options = mc().options;
		look(target.getBoundingBox().getCenter());
		options.keyLeft.setDown(false);
		options.keyRight.setDown(false);
		options.keyUp.setDown(true);
		options.keySprint.setDown(player().distanceTo(target) > 3.0);
		options.keyJump.setDown(player().horizontalCollision);
	}

	/**
	 * A step along a way to a cell {@code goal} accepts (found on the client's blocks, again when the goal moves or
	 * every two seconds). False when there is no way.
	 */
	public boolean walk(BlockPos aim, Predicate<BlockPos> goal) {
		LocalPlayer player = player();
		BlockPos feet = player.blockPosition();
		if (pathGoal == null || !pathGoal.equals(aim) || ++pathAge > 40 || step >= path.size()) {
			path = find(feet, goal, aim);
			step = 0;
			pathGoal = aim;
			pathAge = 0;
			if (path.isEmpty()) {
				release();
				return goal.test(feet);
			}
		}
		while (step < path.size() - 1 && reached(path.get(step))) step++;
		BlockPos next = path.get(step);
		var options = mc().options;
		// a door in the way: a touch, then wait for it to part
		for (BlockPos cell : new BlockPos[] {next, next.above(), path.get(Math.min(path.size() - 1, step + 1)), path.get(Math.min(path.size() - 1, step + 1)).above()}) {
			if (door(level().getBlockState(cell)) && player.getEyePosition().distanceTo(Vec3.atCenterOf(cell)) < 4.0) {
				release();
				if (!level().getBlockState(cell).getValue(com.vnap.spire.SpireDoorBlock.OPENING)) {
					look(Vec3.atCenterOf(cell));
					var gameMode = mc().gameMode;
					if (gameMode != null) gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(cell), Direction.UP, cell, false));
					com.vnap.client.McClient.swing(player, InteractionHand.MAIN_HAND);
					log("  opened a door at " + cell.toShortString());
				}
				return true;
			}
		}
		options.keyLeft.setDown(false);
		options.keyRight.setDown(false);
		Vec3 centre = Vec3.atBottomCenterOf(next);
		turn((float) (Math.toDegrees(Math.atan2(centre.z - player.getZ(), centre.x - player.getX())) - 90.0), 10.0F);
		options.keyUp.setDown(!(reached(next) && step == path.size() - 1));
		boolean ladder = level().getBlockState(feet).getBlock() instanceof LadderBlock;
		options.keyJump.setDown((next.getY() > feet.getY() && !ladder) || player.horizontalCollision || player.isInWater());
		// runs where the way goes straight on for a few cells (a sprint overshoots a turn)
		options.keySprint.setDown(!ladder && !player.isInWater() && straight(step));
		return true;
	}

	private boolean straight(int from) {
		if (from + 3 >= path.size()) return false;
		BlockPos a = path.get(from);
		int dx = Integer.signum(path.get(from + 1).getX() - a.getX());
		int dz = Integer.signum(path.get(from + 1).getZ() - a.getZ());
		for (int i = from + 1; i < from + 3; i++) {
			if (Integer.signum(path.get(i + 1).getX() - path.get(i).getX()) != dx || Integer.signum(path.get(i + 1).getZ() - path.get(i).getZ()) != dz) return false;
		}
		return true;
	}

	private boolean reached(BlockPos cell) {
		Vec3 at = player().position();
		double dx = at.x - (cell.getX() + 0.5);
		double dz = at.z - (cell.getZ() + 0.5);
		return dx * dx + dz * dz < 0.16 && Math.abs(at.y - cell.getY()) < 1.2;
	}

	/** The cells left on the way it walks now. */
	public int pathLeft() {
		return Math.max(0, path.size() - step);
	}

	/** Breadth first over the cells a player can stand in: steps up and down one, drops to three, ladders, water. */
	public List<BlockPos> find(BlockPos from, Predicate<BlockPos> goal, BlockPos aim) {
		Level level = level();
		Map<BlockPos, BlockPos> came = new HashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		came.put(from, from);
		queue.add(from);
		BlockPos best = from;
		double bestGap = from.distSqr(aim);
		BlockPos found = null;
		while (!queue.isEmpty() && came.size() < SEARCH) {
			BlockPos at = queue.poll();
			if (goal.test(at)) {
				found = at;
				break;
			}
			double gap = at.distSqr(aim);
			if (gap < bestGap) {
				best = at;
				bestGap = gap;
			}
			// swimming, a player climbs out onto a ledge a block above the water's top (a well's rim)
			boolean swimming = !level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty();
			// and it swims up through water to its top
			if (!level.getFluidState(at).isEmpty() && !came.containsKey(at.above()) && clear(level, at.above())) {
				came.put(at.above(), at);
				queue.add(at.above());
			}
			for (Direction side : Direction.Plane.HORIZONTAL) {
				for (int dy : swimming ? new int[] {0, 1, 2, -1, -2, -3} : new int[] {0, 1, -1, -2, -3}) {
					BlockPos to = at.relative(side).above(dy);
					if (came.containsKey(to) || !stand(level, to)) continue;
					if (dy > 0 && !clear(level, at.above(2))) continue;
					// stepping down, the player first goes across at its own height, head and all (a window box over the
					// step down kept the town bot from a door)
					if (dy < 0 && (!clear(level, at.relative(side)) || !clear(level, at.relative(side).above()))) continue;
					came.put(to, at);
					queue.add(to);
					break;
				}
			}
			if (level.getBlockState(at).getBlock() instanceof LadderBlock || level.getBlockState(at.above()).getBlock() instanceof LadderBlock) {
				for (BlockPos to : new BlockPos[] {at.above(), at.below()}) {
					if (!came.containsKey(to) && (stand(level, to) || level.getBlockState(to).getBlock() instanceof LadderBlock) && clear(level, to.above())) {
						came.put(to, at);
						queue.add(to);
					}
				}
			}
		}
		BlockPos end = found != null ? found : best;
		if (found == null && end.equals(from)) return List.of();
		List<BlockPos> way = new ArrayList<>();
		for (BlockPos at = end; at != null && !at.equals(from); at = came.get(at)) way.add(at);
		Collections.reverse(way);
		return way;
	}

	private static boolean exit(BlockState state) {
		return state.getBlock() instanceof com.vnap.spire.SpirePortalBlock && state.getValue(com.vnap.spire.SpirePortalBlock.KIND) == com.vnap.spire.SpirePortalBlock.Kind.EXIT;
	}

	private static boolean clear(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return door(state) || state.getBlock() instanceof LadderBlock || (state.getCollisionShape(level, pos).isEmpty() && !state.is(Blocks.LAVA));
	}

	/** A Builders' door is a way through: the bot opens it when it gets there. */
	static boolean door(BlockState state) {
		return state.getBlock() instanceof com.vnap.spire.SpireDoorBlock;
	}

	private static boolean low(Level level, BlockPos pos) {
		if (door(level.getBlockState(pos)) || level.getBlockState(pos).getBlock() instanceof LadderBlock) return true;
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.6;
	}

	/** A task that keeps its feet dry (a town's tour: a well is a way in, not a way on). */
	static boolean dry;

	static void dry(boolean on) {
		dry = on;
	}

	/** A cell a player can stand in: room for the body, something under the feet (or water, or a ladder). */
	public static boolean stand(Level level, BlockPos pos) {
		if (!low(level, pos) || !clear(level, pos.above())) return false;
		BlockState feet = level.getBlockState(pos);
		if (dry && !feet.getFluidState().isEmpty()) return false;
		// the exit is no floor: a path across it took the bot out of the spire (the way on is walked into on purpose)
		if (exit(feet) || exit(level.getBlockState(pos.above()))) return false;
		// a fence or a wall is taller than a step: no floor to walk along (the town bot ended up on a yard's fence)
		var shape = feet.getCollisionShape(level, pos);
		if (!shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) > 1.0) return false;
		var under = level.getBlockState(pos.below()).getCollisionShape(level, pos.below());
		if (!under.isEmpty() && under.max(net.minecraft.core.Direction.Axis.Y) > 1.0) return false;
		if (!feet.getCollisionShape(level, pos).isEmpty() || feet.getBlock() instanceof LadderBlock || !feet.getFluidState().isEmpty()) return true;
		return !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
	}

	private static int live(CommandSourceStack source, int days) {
		BOT.mc().execute(() -> BOT.start(new LiveTask(BOT, days)));
		say(source, "devbot: living in the nearest town, log in devbot.log");
		return 1;
	}

	static void say(CommandSourceStack source, String text) {
		source.sendSystemMessage(Component.literal(text));
	}
}
