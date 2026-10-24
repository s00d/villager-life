package com.vnap.spire;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.vnap.spire.guard.SpireGuard;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /vnap spire (debug): info on the spire of the nearest town, grow one there now, grow it to a floor, set the player's
 * floor, go in.
 */
public final class SpireCommand {
	private SpireCommand() {
	}

	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("spire")
				.executes(ctx -> info(ctx.getSource()))
				.then(Commands.literal("sprout").executes(ctx -> sprout(ctx.getSource())))
				.then(Commands.literal("grow").then(Commands.argument("floor", IntegerArgumentType.integer(1, 1000))
						.executes(ctx -> grow(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))))
				.then(Commands.literal("floor").then(Commands.argument("floor", IntegerArgumentType.integer(1, 100000))
						.executes(ctx -> floor(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))))
				.then(Commands.literal("rebuild").executes(ctx -> rebuild(ctx.getSource(), true)))
				// gone again, to rise by itself on the next night as a town's first spire does
				.then(Commands.literal("raze").executes(ctx -> rebuild(ctx.getSource(), false)))
				.then(Commands.literal("enter").executes(ctx -> enter(ctx.getSource())))
				.then(Commands.literal("route").executes(ctx -> route(ctx.getSource())))
				.then(Commands.literal("show").then(Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("door", "curio", "puzzle", "trap"), builder))
						.executes(ctx -> show(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what")))))
				.then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
				.then(Commands.literal("outer").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
						.suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("arena", "isles", "rush"), builder))
						.executes(ctx -> outer(ctx.getSource(), com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "kind")))))
				.then(Commands.literal("hero").then(Commands.argument("floor", IntegerArgumentType.integer(1, 100))
						.then(Commands.argument("hone", IntegerArgumentType.integer(0, 60))
								.executes(ctx -> hero(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"), IntegerArgumentType.getInteger(ctx, "hone"))))))
				.then(Commands.literal("lineup").then(Commands.argument("floor", IntegerArgumentType.integer(1, 100))
						.executes(ctx -> lineup(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor")))))
				.then(Commands.literal("keeper").then(Commands.argument("floor", IntegerArgumentType.integer(5, 1000))
						.executes(ctx -> keeper(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"), 1))
						.then(Commands.argument("phase", IntegerArgumentType.integer(1, 3))
								.executes(ctx -> keeper(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "floor"), IntegerArgumentType.getInteger(ctx, "phase"))))))
				.then(Commands.literal("outfit").then(Commands.argument("outfit", IntegerArgumentType.integer(0, SpireOutfits.NAMES.size()))
						.executes(ctx -> outfit(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "outfit")))));
	}

	private static Spires.@org.jspecify.annotations.Nullable Spire near(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		BlockPos at = BlockPos.containing(source.getPosition());
		// on one of its floors: that spire, whatever is near in the world
		SpireSlots.Slot slot = SpireSlots.isSpire(level) ? SpireSlots.at(at) : null;
		if (slot != null) return Spires.get(level.getServer()).byId(slot.key.spire());
		Spires.Spire best = null;
		for (Spires.Spire spire : Spires.get(level.getServer()).all()) {
			if (!spire.dimension.equals(level.dimension())) continue;
			if (best == null || spire.centre.distSqr(at) < best.centre.distSqr(at)) best = spire;
		}
		return best;
	}

	private static int info(CommandSourceStack source) {
		SpireSlots.Slot here = SpireSlots.isSpire(source.getLevel()) ? SpireSlots.at(BlockPos.containing(source.getPosition())) : null;
		if (here != null) {
			String line = "floor " + here.key.floor() + " of spire " + here.key.spire() + " (" + here.plan.theme + "): " + (here.cleared ? "cleared" : here.guards.size() + " guards")
					+ ", entrance " + here.world(here.plan.entrance).toShortString() + ", chest " + here.world(here.plan.chest).toShortString()
					+ ", onward " + here.world(here.plan.onwardPortal.get(0)).toShortString();
			source.sendSuccess(() -> Component.literal(line), false);
			return 1;
		}
		Spires.Spire spire = near(source);
		if (spire == null) {
			source.sendFailure(Component.literal("no spire"));
			return 0;
		}
		String line = "spire " + spire.id + " at " + spire.centre.toShortString() + " of bell " + spire.bell.toShortString() + ", best floor " + spire.best
				+ ", grown to " + spire.shown + ", gate " + spire.gate.toShortString() + ", slots " + SpireSlots.active().size();
		source.sendSuccess(() -> Component.literal(line), false);
		return 1;
	}

	private static int sprout(CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		com.vnap.village.home.Mayors.Office office = com.vnap.village.home.Mayors.get(level.getServer()).officeNear(level.dimension(),
				BlockPos.containing(source.getPosition()));
		if (office == null) {
			source.sendFailure(Component.literal("no town here"));
			return 0;
		}
		Spires.Spire spire = Spires.get(level.getServer()).ofTown(level.dimension(), office.bell);
		if (spire == null) spire = SpireGrowth.place(level, office.bell);
		if (spire == null) {
			source.sendFailure(Component.literal("no place for a spire round " + office.bell.toShortString()));
			return 0;
		}
		SpireGrowth.grow(level, spire, spire.shown, Math.max(1, spire.best));
		Spires.Spire grown = spire;
		source.sendSuccess(() -> Component.literal("spire " + grown.id + " rising at " + grown.centre.toShortString()), false);
		return 1;
	}

	private static int grow(CommandSourceStack source, int floor) {
		Spires.Spire spire = near(source);
		if (spire == null) {
			source.sendFailure(Component.literal("no spire"));
			return 0;
		}
		spire.best = floor;
		SpireGrowth.grow(source.getLevel(), spire, spire.shown, Math.min(floor, SpireExterior.FULL));
		source.sendSuccess(() -> Component.literal("growing to floor " + floor), false);
		return 1;
	}

	/** Debug: takes the nearest spire down to its plaza and grows it again as the code draws it now. */
	private static int rebuild(CommandSourceStack source, boolean regrow) {
		Spires.Spire spire = near(source);
		if (spire == null) return 0;
		ServerLevel level = source.getLevel();
		net.minecraft.world.level.levelgen.structure.BoundingBox box = spire.box;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), spire.centre.getY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(pos).isAir()) level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
		}
		// and every block of the grown shape past that box (the rift and the crane on the top stood on in the air)
		for (BlockPos rel : SpireExterior.shape(spire.seed, Math.max(1, Math.min(Math.max(spire.shown, spire.best), SpireExterior.FULL))).keySet()) {
			BlockPos pos = spire.centre.offset(rel.rotate(spire.rotation));
			if (pos.getY() > spire.centre.getY() && !level.getBlockState(pos).isAir()) {
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
			}
		}
		spire.shown = 0;
		Spires.get(level.getServer()).setDirty();
		if (!regrow) {
			source.sendSuccess(() -> Component.literal("razed: it rises again tonight"), false);
			return 1;
		}
		SpireGrowth.grow(level, spire, 0, Math.min(spire.best, SpireExterior.FULL));
		source.sendSuccess(() -> Component.literal("rebuilding to floor " + spire.best), false);
		return 1;
	}

	private static int floor(CommandSourceStack source, int floor) {
		ServerPlayer player = source.getPlayer();
		Spires.Spire spire = near(source);
		if (player == null || spire == null) return 0;
		SpireProgress.setFloor(player, spire.id, floor);
		for (int f = 1; f < floor; f++) SpireProgress.read(player, spire.id, f);
		source.sendSuccess(() -> Component.literal("floor " + floor), false);
		return 1;
	}

	/**
	 * Debug, for the climbing bot (tools/dev): what this player has to do next on its floor — read the tablet, beat
	 * the nearest guard, open the chest, go on — and the way there on foot, every few cells:
	 * "route <what> <cells> x,y,z;x,y,z;…" (world coordinates), or "route done" / "route lost".
	 */
	private static int route(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		SpireSlots.Slot slot = player == null || !SpireSlots.isSpire(source.getLevel()) ? null : SpireSlots.at(player.blockPosition());
		if (player == null || slot == null) {
			source.sendSuccess(() -> Component.literal("route lost"), false);
			return 0;
		}
		FloorPlan plan = slot.plan;
		int spire = slot.key.spire();
		int floor = slot.key.floor();
		String what;
		BlockPos goal;
		BlockPos note = plan.note;
		java.util.List<net.minecraft.world.entity.Entity> guards = new java.util.ArrayList<>();
		for (java.util.UUID id : slot.guards) {
			net.minecraft.world.entity.Entity guard = slot.level.getEntity(id);
			if (guard != null && guard.isAlive()) guards.add(guard);
		}
		guards.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
		if (note != null && SpireProgress.notesRead(player, spire) < floor) {
			what = "note";
			goal = note;
		} else if (!guards.isEmpty()) {
			what = "guard";
			goal = guards.get(0).blockPosition().subtract(slot.origin);
		} else if (slot.cleared && SpireProgress.firstChestPending(player, spire, floor)) {
			what = "chest";
			goal = plan.chest;
		} else {
			what = "onward";
			goal = plan.onwardPortal.get(0);
		}
		java.util.List<BlockPos> way = FloorGenerator.route(plan, player.blockPosition().subtract(slot.origin), goal);
		StringBuilder line = new StringBuilder("route " + what + " " + way.size() + " ");
		for (int i = 0; i < Math.min(way.size(), 24); i++) {
			BlockPos at = slot.world(way.get(i));
			line.append(at.getX()).append(',').append(at.getY()).append(',').append(at.getZ()).append(';');
		}
		BlockPos target = slot.world(goal);
		line.append(" target ").append(target.getX()).append(',').append(target.getY()).append(',').append(target.getZ());
		String out = line.toString();
		source.sendSuccess(() -> Component.literal(out), false);
		return 1;
	}

	/** Debug: into an Outer Gate's run of this kind at the nearest spire, past the hundredth floor or not. */
	private static int outer(CommandSourceStack source, String kind) {
		ServerPlayer player = source.getPlayer();
		Spires.Spire spire = near(source);
		if (player == null || spire == null) {
			source.sendFailure(Component.literal("no spire here"));
			return 0;
		}
		com.vnap.spire.outer.Outer.enter(player, com.vnap.spire.outer.Outer.Kind.valueOf(kind.toUpperCase(java.util.Locale.ROOT)), spire, true);
		source.sendSuccess(() -> Component.literal("outer " + kind), false);
		return 1;
	}

	/** In front of the floor's nearest door, curio, trap or riddle, facing it: for looking at them by eye. */
	private static int show(CommandSourceStack source, String what) {
		ServerPlayer player = source.getPlayer();
		SpireSlots.Slot slot = player == null || !SpireSlots.isSpire(source.getLevel()) ? null : SpireSlots.at(player.blockPosition());
		if (player == null || slot == null) return 0;
		BlockPos best = null;
		if (what.equals("puzzle")) {
			FloorPlan.Puzzle puzzle = slot.plan.puzzle;
			if (puzzle != null) best = slot.world(puzzle.cache());
		} else {
			BlockPos me = player.blockPosition();
			for (int x = 0; x < FloorPlan.SIZE_X; x++) {
				for (int y = 0; y < FloorPlan.SIZE_Y; y++) {
					for (int z = 0; z < FloorPlan.SIZE_Z; z++) {
						net.minecraft.world.level.block.state.BlockState state = slot.plan.get(x, y, z);
						if (state == null) continue;
						boolean match = what.equals("door") ? state.getBlock() instanceof SpireDoorBlock
								: what.equals("trap") ? state.getBlock() instanceof SpireTrapBlock
								: state.getBlock() instanceof SpireCurioBlock || state.is(SpireBlocks.CHIME);
						BlockPos at = slot.world(new BlockPos(x, y, z));
						if (match && (best == null || at.distSqr(me) < best.distSqr(me)) && at.distSqr(me) > 9) best = at;
					}
				}
			}
		}
		if (best == null) {
			source.sendFailure(Component.literal("no " + what + " on this floor"));
			return 0;
		}
		BlockPos target = best;
		// three blocks off it towards the middle of its room, looking at it
		int midX = Math.floorDiv(target.getX() - slot.world(BlockPos.ZERO).getX(), 16) * 16 + 8 + slot.world(BlockPos.ZERO).getX();
		int midZ = Math.floorDiv(target.getZ() - slot.world(BlockPos.ZERO).getZ(), 16) * 16 + 8 + slot.world(BlockPos.ZERO).getZ();
		net.minecraft.world.phys.Vec3 way = new net.minecraft.world.phys.Vec3(midX - target.getX(), 0, midZ - target.getZ());
		if (way.lengthSqr() < 1) way = new net.minecraft.world.phys.Vec3(1, 0, 0);
		way = way.normalize();
		double sx = target.getX() + 0.5 + way.x * 2;
		double sz = target.getZ() + 0.5 + way.z * 2;
		// the farthest spot up to four off with room to stand (a pillar or a prop in the way is skipped)
		for (double d = 4.0; d >= 1.5; d -= 0.5) {
			BlockPos stand = BlockPos.containing(target.getX() + 0.5 + way.x * d, target.getY(), target.getZ() + 0.5 + way.z * d);
			if (source.getLevel().getBlockState(stand).getCollisionShape(source.getLevel(), stand).isEmpty()
					&& source.getLevel().getBlockState(stand.above()).getCollisionShape(source.getLevel(), stand.above()).isEmpty()) {
				sx = stand.getX() + 0.5;
				sz = stand.getZ() + 0.5;
				break;
			}
		}
		net.minecraft.world.level.block.state.BlockState trap = source.getLevel().getBlockState(target);
		if (trap.getBlock() instanceof SpireTrapBlock dart && dart.kind() == SpireTrapBlock.Kind.DART) {
			// a dart wall: four along its line, where its shot goes
			net.minecraft.core.Direction facing = trap.getValue(SpireTrapBlock.FACING);
			sx = target.getX() + 0.5 + facing.getStepX() * 4;
			sz = target.getZ() + 0.5 + facing.getStepZ() * 4;
		}
		com.vnap.Mc.teleport(player, source.getLevel(), sx, target.getY(), sz, 0.0F, 0.0F);
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(target));
		StringBuilder extra = new StringBuilder();
		FloorPlan.Puzzle riddle = slot.plan.puzzle;
		if (what.equals("puzzle") && riddle != null) {
			extra.append(" kind ").append(riddle.kind()).append(" parts");
			for (BlockPos part : riddle.parts()) extra.append(' ').append(slot.world(part).toShortString().replace(" ", ""));
			extra.append(" glyphs");
			for (BlockPos glyph : riddle.glyphs()) extra.append(' ').append(slot.world(glyph).toShortString().replace(" ", ""));
			extra.append(" tune ").append(riddle.tune());
		}
		if (trap.getBlock() instanceof SpireTrapBlock) {
			extra.append(' ').append(trap.getValue(SpireTrapBlock.FACING)).append(" stage ").append(trap.getValue(SpireTrapBlock.STAGE))
					.append(source.getLevel().getBlockTicks().hasScheduledTick(target, trap.getBlock()) ? ", tick waiting" : ", NO tick");
		}
		source.sendSuccess(() -> Component.literal(what + " at " + target.toShortString() + extra), false);
		return 1;
	}

	private static int enter(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		Spires.Spire spire = near(source);
		ServerLevel spireLevel = SpireSlots.level(source.getServer());
		if (player == null || spire == null || spireLevel == null) return 0;
		SpireProgress.setReturn(player, spire.dimension, spire.front, spire.id);
		SpireTravel.go(player, spireLevel, spire, SpireProgress.floor(player, spire.id));
		return 1;
	}

	/**
	 * Debug: one guard of every body (and every chapter's keeper) of the floor's theme in a row before the player,
	 * standing still, to look them over.
	 */
	private static int lineup(CommandSourceStack source, int floor) {
		ServerLevel level = source.getLevel();
		BlockPos at = BlockPos.containing(source.getPosition());
		String theme = Genome.THEMES.get(Genome.chapter(floor) % Genome.THEMES.size());
		net.minecraft.world.phys.AABB room = new net.minecraft.world.phys.AABB(at).inflate(40);
		int placed = 0;
		for (Genome.Body body : Genome.Body.values()) {
			if (body == Genome.Body.GATEKEEPER) break;
			for (long seed = 0; seed < 20000; seed++) {
				Genome genome = Genome.decode(seed, floor, theme, body == Genome.Body.FISH || body == Genome.Body.RAY, false);
				if (genome.body() != body) continue;
				still(SpireGuards.make(level, genome, theme, at.offset(-14 + placed * 4, 0, 6), room, room));
				placed++;
				break;
			}
		}
		// the pair of the Schism, as they are and as each is left alone
		for (int i = 0; i < Genome.DUO.size(); i++) {
			still(SpireGuards.make(level, Genome.duo(i, 45, "galleries", Genome.MASON + i), "galleries", at.offset(-12 + i * 8, 0, 30), room, room));
		}
		// the trials of the half-chapters, a row further
		for (int chapter = 0; chapter < Genome.TRIALS.size(); chapter++) {
			if (chapter == 4) continue;
			String own = Genome.THEMES.get(chapter);
			still(SpireGuards.make(level, Genome.trial(chapter, chapter * 10 + 5, own), own, at.offset(-36 + chapter * 8, 0, 42), room, room));
			placed++;
		}
		// the ten keepers in a row behind, each of its own chapter
		for (int chapter = 0; chapter < Genome.KEEPERS.size(); chapter++) {
			String own = Genome.THEMES.get(chapter);
			still(SpireGuards.make(level, Genome.decode(chapter, chapter * 10 + 10, own, false, true), own, at.offset(-36 + chapter * 8, 0, 18), room, room));
			placed++;
		}
		int count = placed;
		source.sendSuccess(() -> Component.literal(count + " guards of " + theme), false);
		return placed;
	}

	private static void still(net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity guard) {
		if (guard == null) return;
		if (guard instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
		guard.setNoGravity(true);
		guard.setYRot(180.0F);
		guard.setYHeadRot(180.0F);
		if (guard instanceof net.minecraft.world.entity.LivingEntity living) living.yBodyRot = 180.0F;
	}

	/**
	 * Debug: the keeper of a floor's chapter, awake, 7 blocks before the player in a room 17 across round it, at the
	 * start of {@code phase} (its health brought down to it, the changes made) — to try its arts out of the spire.
	 */
	private static int keeper(CommandSourceStack source, int floor, int phase) {
		ServerLevel level = source.getLevel();
		ServerPlayer player = source.getPlayer();
		if (player == null) return 0;
		net.minecraft.world.phys.Vec3 ahead = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(7.0);
		BlockPos at = BlockPos.containing(player.position().add(ahead));
		String theme = Genome.THEMES.get(Genome.chapter(floor) % Genome.THEMES.size());
		net.minecraft.world.phys.AABB room = new net.minecraft.world.phys.AABB(at).inflate(8.0, 0.0, 8.0).expandTowards(0.0, 10.0, 0.0).move(0.0, -1.0, 0.0);
		if (Genome.schism(floor)) {
			// the pair, either side of the spot
			net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(-ahead.z, 0.0, ahead.x).normalize().scale(3.0);
			for (int i = 0; i < 2; i++) {
				BlockPos spot = BlockPos.containing(net.minecraft.world.phys.Vec3.atCenterOf(at).add(side.scale(i == 0 ? -1 : 1)));
				SpireGuard one = SpireGuards.make(level, Genome.duo(player.getRandom().nextLong(), floor, theme, Genome.MASON + i), theme, spot, room, room.inflate(4.0));
				if (one != null) one.setTarget(player);
			}
			source.sendSuccess(() -> Component.literal("the Schism's pair"), false);
			return 1;
		}
		Genome boss = Genome.trial(floor) ? Genome.trial(player.getRandom().nextLong(), floor, theme) : Genome.decode(player.getRandom().nextLong(), floor, theme, false, true);
		SpireGuard guard = SpireGuards.make(level, boss, theme, at, room, room.inflate(4.0));
		if (!(guard instanceof com.vnap.spire.guard.SpireKeeper keeper)) return 0;
		if (phase > 1) keeper.setHealth(keeper.getMaxHealth() * (phase == 2 ? 0.66F : 0.33F));
		keeper.setTarget(player);
		source.sendSuccess(() -> Component.literal(keeper.getDisplayName().getString() + ": " + Math.round(keeper.getMaxHealth()) + " health, "
				+ Math.round(keeper.damage() * 10) / 10.0 + " damage"), false);
		return 1;
	}

	/** Debug: arms the grown villagers within 6 blocks with a full set of the floor's chapter, honed to {@code hone}. */
	private static int hero(CommandSourceStack source, int floor, int hone) {
		java.util.List<net.minecraft.world.entity.npc.Villager> near = source.getLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
				new net.minecraft.world.phys.AABB(BlockPos.containing(source.getPosition())).inflate(6.0), villager -> !villager.isBaby());
		com.vnap.spire.gear.GearTier tier = com.vnap.spire.gear.GearTier.ofFloor(floor);
		for (net.minecraft.world.entity.npc.Villager villager : near) {
			for (com.vnap.spire.gear.GearPiece piece : com.vnap.spire.gear.GearPiece.values()) {
				if (piece == com.vnap.spire.gear.GearPiece.AXE || piece == com.vnap.spire.gear.GearPiece.BOW) continue;
				net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(com.vnap.spire.gear.SpireGearItems.get(tier, piece));
				com.vnap.spire.gear.SpireGear.setHoned(stack, hone);
				Heroes.wear(villager, piece, stack);
			}
		}
		source.sendSuccess(() -> Component.literal(near.size() + " armed"), false);
		return near.size();
	}

	/** Dresses the villagers within 6 blocks in the spire's clothes, one after another from {@code outfit} (0 undresses). */
	private static int outfit(CommandSourceStack source, int outfit) {
		java.util.List<net.minecraft.world.entity.npc.Villager> near = source.getLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
				new net.minecraft.world.phys.AABB(BlockPos.containing(source.getPosition())).inflate(6.0));
		for (int i = 0; i < near.size(); i++) SpireOutfits.wear(near.get(i), outfit == 0 ? 0 : 1 + Math.floorMod(outfit - 1 + i, SpireOutfits.NAMES.size()));
		source.sendSuccess(() -> Component.literal(near.size() + " dressed"), false);
		return near.size();
	}

	private static int leave(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) return 0;
		SpireTravel.leave(player);
		return 1;
	}
}
